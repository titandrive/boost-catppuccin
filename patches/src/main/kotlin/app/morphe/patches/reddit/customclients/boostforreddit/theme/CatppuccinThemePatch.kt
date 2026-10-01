package app.morphe.patches.reddit.customclients.boostforreddit.theme

import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import org.w3c.dom.Document
import org.w3c.dom.Element
import javax.xml.parsers.DocumentBuilderFactory

private object CatppuccinResources

private const val ATTRIBUTION =
    "This app uses code from Patcheddit. To learn more, visit https://reddit.com/r/patcheddit"

private fun Element.elements(): List<Element> =
    (0 until childNodes.length).mapNotNull { childNodes.item(it) as? Element }

/** Replace individual items so inherited widget behavior and resource IDs are retained. */
private fun mergeItems(target: Element, source: Element) {
    for (item in source.elements()) {
        target.elements().firstOrNull { it.getAttribute("name") == item.getAttribute("name") }
            ?.let { target.removeChild(it) }
        target.appendChild(target.ownerDocument.importNode(item, true))
    }
}

// Resource IDs are reserved explicitly because the theme registry lives in DEX.
private val themeStyleIds = listOf(0x7f141000, 0x7f141001)
private val themeLabelIds = listOf(0x7f131000, 0x7f131001)

private val catppuccinResourcesPatch = resourcePatch {
    execute {
        val templates: Document = checkNotNull(
            CatppuccinResources::class.java.classLoader.getResourceAsStream("catppuccin/styles.xml"),
        ).use { DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(it) }
        val styles = templates.documentElement.elements().associateBy { it.getAttribute("name") }
        val palette = checkNotNull(
            CatppuccinResources::class.java.classLoader.getResourceAsStream("catppuccin/palette.xml"),
        ).use { DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(it) }
        // Material navigation resolves text colors through TypedValue.resourceId.
        // Raw #hex style values have resourceId = 0 and crash during inflation.
        document("res/values/colors.xml").use { xml ->
            for (color in palette.documentElement.elements()) {
                xml.documentElement.elements()
                    .firstOrNull { it.getAttribute("name") == color.getAttribute("name") }
                    ?.let { xml.documentElement.removeChild(it) }
                xml.documentElement.appendChild(xml.importNode(color, true))
            }
        }

        // Undo earlier releases without replacing unrelated Patcheddit resources.
        val restore = checkNotNull(CatppuccinResources::class.java.classLoader
            .getResourceAsStream("catppuccin/restore.xml")).use {
            DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(it)
        }
        for (file in restore.documentElement.elements()) {
            val path = file.getAttribute("path")
            if (!get(path).exists()) continue
            document(path).use { xml ->
                if (file.getAttribute("mode") == "document") {
                    xml.replaceChild(xml.importNode(file.elements().single(), true), xml.documentElement)
                } else {
                    for (original in file.elements()) {
                        xml.documentElement.elements().firstOrNull {
                            it.getAttribute("name") == original.getAttribute("name")
                        }?.let { xml.documentElement.removeChild(it) }
                        xml.documentElement.appendChild(xml.importNode(original, true))
                    }
                }
                val namespaces = restore.documentElement.attributes
                for (index in 0 until namespaces.length) {
                    val attribute = namespaces.item(index)
                    if (attribute.nodeName.startsWith("xmlns:"))
                        xml.documentElement.setAttribute(attribute.nodeName, attribute.nodeValue)
                }
            }
        }
        document("res/values/styles.xml").use { xml ->
            for ((name, style) in styles) {
                xml.documentElement.elements().firstOrNull { it.getAttribute("name") == name }
                    ?.let { xml.documentElement.removeChild(it) }
                xml.documentElement.appendChild(xml.importNode(style, true))
            }
        }
        document("res/values/strings.xml").use { xml ->
            for ((index, flavor) in listOf("Latte", "Macchiato").withIndex()) {
                val name = "catppuccin_theme_${flavor.lowercase()}"
                xml.documentElement.elements().firstOrNull { it.getAttribute("name") == name }
                    ?.let { xml.documentElement.removeChild(it) }
                xml.documentElement.appendChild(xml.createElement("string").apply {
                    setAttribute("name", name)
                    textContent = "Catppuccin $flavor"
                })
            }
        }
        document("res/values/public.xml").use { xml ->
            for ((index, flavor) in listOf("Latte", "Macchiato").withIndex()) {
                for ((type, name, id) in listOf(
                    Triple("style", "Catppuccin.$flavor", themeStyleIds[index]),
                    Triple("string", "catppuccin_theme_${flavor.lowercase()}", themeLabelIds[index]),
                )) {
                    xml.documentElement.elements().firstOrNull {
                        it.getAttribute("type") == type && it.getAttribute("name") == name
                    }?.let { xml.documentElement.removeChild(it) }
                    check(xml.documentElement.elements().none { it.getAttribute("id") == "0x${id.toString(16)}" })
                    xml.documentElement.appendChild(xml.createElement("public").apply {
                        setAttribute("type", type); setAttribute("name", name)
                        setAttribute("id", "0x${id.toString(16)}")
                    })
                }
            }
        }
        document("res/values/arrays.xml").use { xml ->
            for (array in xml.documentElement.elements()) {
                if (array.getAttribute("name").startsWith("pref_theme_values")) {
                    for (value in listOf("17", "18")) {
                        if (array.elements().none { it.textContent == value })
                            array.appendChild(xml.createElement("item").apply { textContent = value })
                    }
                }
            }
        }

        // Keep the upstream project's required notice accessible in both settings UIs.
        for (path in listOf("res/xml/pref_about.xml", "res/xml/pref_about_v2.xml")) {
            if (!get(path).exists()) continue
            document(path).use { xml ->
                val preference = xml.createElement("Preference").apply {
                    setAttribute("android:key", "catppuccin_patch_attribution")
                    setAttribute("android:title", "Boost Catppuccin")
                    setAttribute("android:summary", ATTRIBUTION)
                    setAttribute("android:selectable", "false")
                }
                val existing = xml.getElementsByTagName("Preference")
                (0 until existing.length).mapNotNull { existing.item(it) as? Element }
                    .filter { it.getAttribute("android:key") == "catppuccin_patch_attribution" }
                    .forEach { it.parentNode.removeChild(it) }
                xml.documentElement.appendChild(preference)
            }
        }
    }
}

@Suppress("unused")
val catppuccinThemePatch = bytecodePatch(
    name = "Catppuccin theme",
    description = "Adds separate Catppuccin Latte and Macchiato choices while preserving Boost's original themes.",
    default = false,
) {
    dependsOn(catppuccinResourcesPatch)
    compatibleWith(Compatibility(name = "Boost for Reddit", packageName = "com.rubenmayayo.reddit",
        targets = listOf(AppTarget(version = "1.12.12"))))
    execute {
        val utils = mutableClassDefBy("Lhe/f0;")
        val init = utils.methods.single { it.name == "<clinit>" }
        val alreadyPatched = init.implementation!!.instructions.any {
            ((it as? ReferenceInstruction)?.reference as? StringReference)?.string == "Catppuccin.Latte"
        }
        if (!alreadyPatched) {
            val code = StringBuilder()
            for ((field, type, values) in listOf(
                Triple("b", "I", listOf("17", "18")),
                Triple("c", "I", themeStyleIds.map { "0x${it.toString(16)}" }),
                Triple("e", "I", themeLabelIds.map { "0x${it.toString(16)}" }),
                Triple("d", "Ljava/lang/String;", listOf("Catppuccin.Latte", "Catppuccin.Macchiato")),
            )) {
                val copyType = if (type == "I") "I" else "Ljava/lang/Object;"
                code.append("""
                    sget-object v0, Lhe/f0;->$field:[$type
                    const/16 v1, 19
                    invoke-static {v0, v1}, Ljava/util/Arrays;->copyOf([${copyType}I)[$copyType
                    move-result-object v0
                    check-cast v0, [$type
                """)
                for ((i, value) in values.withIndex()) {
                    code.append("\nconst/16 v1, ${17 + i}\n")
                    if (type == "I") code.append("const v2, $value\naput v2, v0, v1\n")
                    else code.append("const-string v2, \"$value\"\naput-object v2, v0, v1\n")
                }
                code.append("sput-object v0, Lhe/f0;->$field:[$type\n")
            }
            init.addInstructions(init.implementation!!.instructions.indexOfFirst { it.opcode == Opcode.RETURN_VOID }, code.toString())
            // Both new slots use Material widgets; Latte also needs light-theme behavior.
            utils.methods.single { it.name == "J" }.addInstructions(0, """
                invoke-static {}, Lid/b;->v0()Lid/b;
                move-result-object v0
                invoke-virtual {v0}, Lid/b;->D3()I
                move-result v0
                const/16 v1, 17
                if-eq v0, v1, :cat_material
                const/16 v1, 18
                if-ne v0, v1, :cat_original
                :cat_material
                const/4 v0, 1
                return v0
                :cat_original
                nop
            """)
            utils.methods.single { it.name == "D" }.addInstructions(0, """
                invoke-static {}, Lid/b;->v0()Lid/b;
                move-result-object v0
                invoke-virtual {v0}, Lid/b;->D3()I
                move-result v0
                const/16 v1, 17
                if-ne v0, v1, :cat_original
                const/16 v0, 100
                return v0
                :cat_original
                nop
            """)
            utils.methods.single { it.name == "H" }.addInstructions(0, """
                invoke-static {}, Lid/b;->v0()Lid/b;
                move-result-object v0
                invoke-virtual {v0}, Lid/b;->D3()I
                move-result v0
                add-int/lit8 v0, v0, -17
                if-ltz v0, :cat_original
                add-int/lit8 v0, v0, -1
                if-gtz v0, :cat_original
                const/4 v0, 0
                return v0
                :cat_original
                nop
            """)
            val menu = mutableClassDefBy("Lcom/rubenmayayo/reddit/ui/compose/FormatActivity;")
                .methods.single { it.name == "onCreateOptionsMenu" }
            menu.addInstructions(0, """
                const-string v0, "Catppuccin send accent"
            """)
            // Apply the tint after inflation, only for the two dedicated selections.
            menu.addInstructionsWithLabels(menu.implementation!!.instructions.indexOfFirst { it.opcode == Opcode.RETURN } - 1, """
                invoke-static {}, Lid/b;->v0()Lid/b;
                move-result-object v0
                invoke-virtual {v0}, Lid/b;->D3()I
                move-result v0
                const/16 v1, 17
                if-eq v0, v1, :cat_latte
                const/16 v1, 18
                if-ne v0, v1, :cat_done
                const v0, 0xffc6a0f6
                goto :cat_tint
                :cat_latte
                const v0, 0xff8839ef
                :cat_tint
                invoke-static {v0}, Landroid/content/res/ColorStateList;->valueOf(I)Landroid/content/res/ColorStateList;
                move-result-object v0
                const v1, 0x7f0a009b
                invoke-interface {p1, v1}, Landroid/view/Menu;->findItem(I)Landroid/view/MenuItem;
                move-result-object v1
                invoke-interface {v1, v0}, Landroid/view/MenuItem;->setIconTintList(Landroid/content/res/ColorStateList;)Landroid/view/MenuItem;
                :cat_done
                const/4 p1, 1
            """)
        }
    }
}
