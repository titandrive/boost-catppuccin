package app.morphe.patches.reddit.customclients.boostforreddit.theme

import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.removeInstructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import org.w3c.dom.Document
import org.w3c.dom.Element
import javax.xml.parsers.DocumentBuilderFactory

private object CatppuccinResources

private const val ATTRIBUTION =
    "This app uses code from Patcheddit. To learn more, visit https://reddit.com/r/patcheddit"

private fun Element.elements(): List<Element> {
    // Android getChildNodes() copies every child into a new NodeList. Fetching it
    // again for each item makes resource edits quadratic and takes minutes.
    val nodes = childNodes
    return (0 until nodes.length).mapNotNull { nodes.item(it) as? Element }
}

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
        // Media activities use a separate black theme and hard-coded backgrounds.
        // Keep their original behavior unless one of our two selections is active.
        // Saved custom toolbar colors can otherwise override the selected Catppuccin style.
        val headerMarker = "Catppuccin header palette"
        for ((name, colors) in mapOf(
            "k" to ("0xffe6e9ef" to "0xff1e2030"),
            "l" to ("0xffe6e9ef" to "0xff1e2030"),
            "w" to ("0xff4c4f69" to "0xff8aadf4"),
            "e" to ("0xff4c4f69" to "0xffcad3f5"),
            "o" to ("0xff6c6f85" to "0xffa5adcb"),
            "x" to ("0xff4c4f69" to "0xffcad3f5"),
            "f" to ("0xff8839ef" to "0xffc6a0f6"),
        )) {
            val method = utils.methods.single { it.name == name }
            // Rebuild w's prefix once, including when upgrading from the broken 0.2.5.
            // A single labeled insertion avoids stale branch offsets in Patcher 1.15.
            if (name == "w") {
                val original = method.implementation!!.instructions.indexOfFirst {
                    val ref = (it as? ReferenceInstruction)?.reference as? MethodReference
                    ref?.definingClass == "Lhe/f0;" && ref.name == "H"
                }
                require(original >= 0)
                if (original > 0) method.removeInstructions(0, original)
            }
            if (method.implementation!!.instructions.any {
                ((it as? ReferenceInstruction)?.reference as? StringReference)?.string == headerMarker
            }) continue
            method.addInstructionsWithLabels(0, """
                const-string v0, "$headerMarker"
                invoke-static {}, Lid/b;->v0()Lid/b;
                move-result-object v0
                invoke-virtual {v0}, Lid/b;->D3()I
                move-result v0
                const/16 v1, 17
                if-eq v0, v1, :cat_header_latte
                const/16 v1, 18
                if-ne v0, v1, :cat_header_original
                const v0, ${colors.second}
                return v0
                :cat_header_latte
                const v0, ${colors.first}
                return v0
                :cat_header_original
                nop
            """)
        }
        // Override saved rainbow palettes only for the two Catppuccin selections.
        val depth = mutableClassDefBy("Lid/b;").methods.single { it.name == "K1" }
        val commentMarker = "Catppuccin comment depth palette"
        if (depth.implementation!!.instructions.none {
            ((it as? ReferenceInstruction)?.reference as? StringReference)?.string == commentMarker
        }) depth.addInstructionsWithLabels(0, """
                const-string v0, "$commentMarker"
                invoke-virtual {p0}, Lid/b;->D3()I
                move-result v0
                const/16 v1, 17
                if-eq v0, v1, :cat_depth_latte
                const/16 v1, 18
                if-ne v0, v1, :cat_depth_original
                const/16 v0, 11
                new-array v0, v0, [I
                const v2, 0xffc6a0f6
                const/16 v1, 1
                aput v2, v0, v1
                const v2, 0xffed8796
                const/16 v1, 2
                aput v2, v0, v1
                const v2, 0xfff5a97f
                const/16 v1, 3
                aput v2, v0, v1
                const v2, 0xffeed49f
                const/16 v1, 4
                aput v2, v0, v1
                const v2, 0xffa6da95
                const/16 v1, 5
                aput v2, v0, v1
                const v2, 0xff8bd5ca
                const/16 v1, 6
                aput v2, v0, v1
                const v2, 0xff91d7e3
                const/16 v1, 7
                aput v2, v0, v1
                const v2, 0xff7dc4e4
                const/16 v1, 8
                aput v2, v0, v1
                const v2, 0xff8aadf4
                const/16 v1, 9
                aput v2, v0, v1
                const v2, 0xffb7bdf8
                const/16 v1, 10
                aput v2, v0, v1
                return-object v0
                :cat_depth_latte
                const/16 v0, 11
                new-array v0, v0, [I
                const v2, 0xff8839ef
                const/16 v1, 1
                aput v2, v0, v1
                const v2, 0xffd20f39
                const/16 v1, 2
                aput v2, v0, v1
                const v2, 0xfffe640b
                const/16 v1, 3
                aput v2, v0, v1
                const v2, 0xffdf8e1d
                const/16 v1, 4
                aput v2, v0, v1
                const v2, 0xff40a02b
                const/16 v1, 5
                aput v2, v0, v1
                const v2, 0xff179299
                const/16 v1, 6
                aput v2, v0, v1
                const v2, 0xff04a5e5
                const/16 v1, 7
                aput v2, v0, v1
                const v2, 0xff209fb5
                const/16 v1, 8
                aput v2, v0, v1
                const v2, 0xff1e66f5
                const/16 v1, 9
                aput v2, v0, v1
                const v2, 0xff7287fd
                const/16 v1, 10
                aput v2, v0, v1
                return-object v0
                :cat_depth_original
                nop
        """)
        if (utils.methods.none { it.name == "catppuccinUsername" }) {
            fun usernameHelper(name: String, params: List<String>, code: String) {
                utils.directMethods.add(ImmutableMethod(utils.type, name,
                    params.map { ImmutableMethodParameter(it, emptySet(), null) }, "V",
                    AccessFlags.PUBLIC.value or AccessFlags.STATIC.value, null, null,
                    MutableMethodImplementation(5)).toMutable().apply { addInstructions(0, code) })
            }
            usernameHelper("catppuccinUsername", listOf("Landroid/widget/TextView;", "I"), """
                invoke-static {}, Lid/b;->v0()Lid/b;
                move-result-object v0
                invoke-virtual {v0}, Lid/b;->D3()I
                move-result v0
                const/16 v1, 17
                if-eq v0, v1, :latte
                const/16 v1, 18
                if-ne v0, v1, :done
                const/16 v1, 0
                if-ne p1, v1, :m0
                const v0, 0xffc6a0f6
                goto :tint
                :m0
                const/16 v1, 1
                if-ne p1, v1, :m1
                const v0, 0xff8aadf4
                goto :tint
                :m1
                const/16 v1, 2
                if-ne p1, v1, :m2
                const v0, 0xffeed49f
                goto :tint
                :m2
                const/16 v1, 3
                if-ne p1, v1, :m3
                const v0, 0xffa6da95
                goto :tint
                :m3
                const v0, 0xffed8796
                goto :tint
                :latte
                const/16 v1, 0
                if-ne p1, v1, :l0
                const v0, 0xff8839ef
                goto :tint
                :l0
                const/16 v1, 1
                if-ne p1, v1, :l1
                const v0, 0xff1e66f5
                goto :tint
                :l1
                const/16 v1, 2
                if-ne p1, v1, :l2
                const v0, 0xff7287fd
                goto :tint
                :l2
                const/16 v1, 3
                if-ne p1, v1, :l3
                const v0, 0xff40a02b
                goto :tint
                :l3
                const v0, 0xffd20f39
                :tint
                invoke-virtual {p0}, Landroid/view/View;->getBackground()Landroid/graphics/drawable/Drawable;
                move-result-object v1
                if-eqz v1, :done
                invoke-virtual {v1}, Landroid/graphics/drawable/Drawable;->mutate()Landroid/graphics/drawable/Drawable;
                move-result-object v1
                invoke-virtual {v1, v0}, Landroid/graphics/drawable/Drawable;->setTint(I)V
                :done
                return-void
            """)
            usernameHelper("catppuccinUsernameText", listOf("Landroid/widget/TextView;", "I"), """
                const/4 v0, -1
                if-ne p1, v0, :apply
                invoke-static {}, Lid/b;->v0()Lid/b;
                move-result-object v0
                invoke-virtual {v0}, Lid/b;->D3()I
                move-result v0
                const/16 v1, 17
                if-eq v0, v1, :light
                const/16 v1, 18
                if-ne v0, v1, :apply
                const p1, 0xff24273a
                goto :apply
                :light
                const p1, 0xffeff1f5
                :apply
                invoke-virtual {p0, p1}, Landroid/widget/TextView;->setTextColor(I)V
                return-void
            """)
            val author = mutableClassDefBy("Lcom/rubenmayayo/reddit/ui/adapters/CommentViewHolder;").methods.single { it.name == "I" }
            val instructions = author.implementation!!.instructions.toList()
            for (index in instructions.indices.reversed()) {
                val ref = (instructions[index] as? ReferenceInstruction)?.reference
                if (ref is MethodReference && ref.name == "setTextColor") {
                    val call = instructions[index] as com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
                    author.replaceInstruction(index, "invoke-static {v${call.registerC}, v${call.registerD}}, Lhe/f0;->catppuccinUsernameText(Landroid/widget/TextView;I)V")
                }
                if (ref is MethodReference && ref.name == "setBackground") {
                    val field = (instructions[index - 1] as? ReferenceInstruction)?.reference as? com.android.tools.smali.dexlib2.iface.reference.FieldReference
                    val role = listOf("k", "j", "l", "m", "n").indexOf(field?.name)
                    require(role >= 0)
                    author.addInstructionsWithLabels(index + 1, "const/16 v0, $role\ninvoke-static {p1, v0}, Lhe/f0;->catppuccinUsername(Landroid/widget/TextView;I)V")
                }
            }
        }
        if (utils.methods.none { it.name == "catppuccinViewer" }) {
            fun helper(name: String, parameters: List<String>, registers: Int, code: String) {
                utils.directMethods.add(ImmutableMethod(utils.type, name,
                    parameters.map { ImmutableMethodParameter(it, emptySet(), null) }, "V",
                    AccessFlags.PUBLIC.value or AccessFlags.STATIC.value, null, null,
                    MutableMethodImplementation(registers)).toMutable().apply { addInstructions(0, code) })
            }
            helper("catppuccinViewerTheme", listOf("Landroid/app/Activity;"), 3, """
                invoke-static {}, Lid/b;->v0()Lid/b;
                move-result-object v0
                invoke-virtual {v0}, Lid/b;->D3()I
                move-result v0
                const/16 v1, 17
                if-eq v0, v1, :apply
                const/16 v1, 18
                if-ne v0, v1, :done
                :apply
                invoke-static {p0}, Lhe/f0;->N(Landroid/app/Activity;)I
                :done
                return-void
            """)
            helper("catppuccinViewerControls", listOf("Landroid/view/View;", "I"), 6, """
                if-eqz p0, :done
                instance-of v0, p0, Landroid/widget/TextView;
                if-eqz v0, :image
                move-object v0, p0
                check-cast v0, Landroid/widget/TextView;
                invoke-virtual {v0, p1}, Landroid/widget/TextView;->setTextColor(I)V
                :image
                instance-of v0, p0, Landroid/widget/ImageView;
                if-eqz v0, :children
                move-object v0, p0
                check-cast v0, Landroid/widget/ImageView;
                invoke-static {p1}, Landroid/content/res/ColorStateList;->valueOf(I)Landroid/content/res/ColorStateList;
                move-result-object v1
                invoke-virtual {v0, v1}, Landroid/widget/ImageView;->setImageTintList(Landroid/content/res/ColorStateList;)V
                :children
                instance-of v0, p0, Landroid/view/ViewGroup;
                if-eqz v0, :done
                check-cast p0, Landroid/view/ViewGroup;
                const/4 v0, 0
                :loop
                invoke-virtual {p0}, Landroid/view/ViewGroup;->getChildCount()I
                move-result v1
                if-ge v0, v1, :done
                invoke-virtual {p0, v0}, Landroid/view/ViewGroup;->getChildAt(I)Landroid/view/View;
                move-result-object v2
                invoke-static {v2, p1}, Lhe/f0;->catppuccinViewerControls(Landroid/view/View;I)V
                add-int/lit8 v0, v0, 1
                goto :loop
                :done
                return-void
            """)
            // Reserve image space between controls instead of painting opaque panels over zoomed content.
            helper("catppuccinViewer", listOf("Landroid/app/Activity;"), 7, """
                invoke-static {}, Lid/b;->v0()Lid/b;
                move-result-object v0
                invoke-virtual {v0}, Lid/b;->D3()I
                move-result v0
                const/16 v1, 17
                if-eq v0, v1, :latte
                const/16 v1, 18
                if-ne v0, v1, :done
                const v1, 0xff24273a
                const v2, 0xff24273a
                const v3, 0xffcad3f5
                goto :apply
                :latte
                const v1, 0xffeff1f5
                const v2, 0xffeff1f5
                const v3, 0xff4c4f69
                :apply
                invoke-virtual {p0}, Landroid/app/Activity;->getWindow()Landroid/view/Window;
                move-result-object v0
                invoke-virtual {v0}, Landroid/view/Window;->getDecorView()Landroid/view/View;
                move-result-object v4
                invoke-virtual {v4, v1}, Landroid/view/View;->setBackgroundColor(I)V
                invoke-virtual {v0, v2}, Landroid/view/Window;->setStatusBarColor(I)V
                invoke-virtual {v0, v2}, Landroid/view/Window;->setNavigationBarColor(I)V
                const v0, 0x7f0a01ac
                invoke-virtual {p0, v0}, Landroid/app/Activity;->findViewById(I)Landroid/view/View;
                move-result-object v0
                if-eqz v0, :main
                invoke-virtual {v0, v1}, Landroid/view/View;->setBackgroundColor(I)V
                :main
                const v0, 0x7f0a0374
                invoke-virtual {p0, v0}, Landroid/app/Activity;->findViewById(I)Landroid/view/View;
                move-result-object v0
                if-eqz v0, :bottom
                invoke-virtual {v0, v1}, Landroid/view/View;->setBackgroundColor(I)V
                :bottom
                const v0, 0x7f0a015c
                invoke-virtual {p0, v0}, Landroid/app/Activity;->findViewById(I)Landroid/view/View;
                move-result-object v0
                if-eqz v0, :toolbar
                const/4 v4, 0
                invoke-virtual {v0, v4}, Landroid/view/View;->setBackgroundColor(I)V
                invoke-static {v0, v3}, Lhe/f0;->catppuccinViewerControls(Landroid/view/View;I)V
                :toolbar
                const v0, 0x7f0a0668
                invoke-virtual {p0, v0}, Landroid/app/Activity;->findViewById(I)Landroid/view/View;
                move-result-object v0
                if-eqz v0, :done
                const/4 v4, 0
                invoke-virtual {v0, v4}, Landroid/view/View;->setBackgroundColor(I)V
                invoke-static {v0, v3}, Lhe/f0;->catppuccinViewerControls(Landroid/view/View;I)V
                :done
                return-void
            """)
            helper("catppuccinViewerMenu", listOf("Landroid/view/Menu;"), 7, """
                invoke-static {}, Lid/b;->v0()Lid/b;
                move-result-object v0
                invoke-virtual {v0}, Lid/b;->D3()I
                move-result v0
                const/16 v1, 17
                if-eq v0, v1, :latte
                const/16 v1, 18
                if-ne v0, v1, :done
                const v1, 0xffcad3f5
                const v4, 0xffc6a0f6
                goto :apply
                :latte
                const v1, 0xff4c4f69
                const v4, 0xff8839ef
                :apply
                invoke-static {v1}, Landroid/content/res/ColorStateList;->valueOf(I)Landroid/content/res/ColorStateList;
                move-result-object v1
                invoke-static {v4}, Landroid/content/res/ColorStateList;->valueOf(I)Landroid/content/res/ColorStateList;
                move-result-object v4
                const/4 v0, 0
                :loop
                invoke-interface {p0}, Landroid/view/Menu;->size()I
                move-result v2
                if-ge v0, v2, :done
                invoke-interface {p0, v0}, Landroid/view/Menu;->getItem(I)Landroid/view/MenuItem;
                move-result-object v2
                invoke-interface {v2}, Landroid/view/MenuItem;->getItemId()I
                move-result v3
                const v5, 0x7f0a004f
                if-eq v3, v5, :accent
                const v5, 0x7f0a0065
                if-eq v3, v5, :accent
                invoke-interface {v2, v1}, Landroid/view/MenuItem;->setIconTintList(Landroid/content/res/ColorStateList;)Landroid/view/MenuItem;
                goto :next
                :accent
                invoke-interface {v2, v4}, Landroid/view/MenuItem;->setIconTintList(Landroid/content/res/ColorStateList;)Landroid/view/MenuItem;
                :next
                add-int/lit8 v0, v0, 1
                goto :loop
                :done
                return-void
            """)
            for (name in listOf("ImageActivity", "MediaImageActivity", "HDImageActivity", "GalleryActivity")) {
                val activity = mutableClassDefBy("Lcom/rubenmayayo/reddit/ui/activities/$name;")
                activity.methods.firstOrNull { it.name == "onCreateOptionsMenu" }?.let { menu ->
                    val inflate = menu.implementation!!.instructions.indexOfFirst {
                        val reference = (it as? ReferenceInstruction)?.reference as? MethodReference
                        reference?.definingClass == "Landroid/view/MenuInflater;" && reference.name == "inflate"
                    }
                    check(inflate >= 0)
                    menu.addInstructions(inflate + 1,
                        """
                            invoke-static {p0}, Lhe/f0;->catppuccinViewer(Landroid/app/Activity;)V
                            invoke-static {p1}, Lhe/f0;->catppuccinViewerMenu(Landroid/view/Menu;)V
                        """)
                }
                val create = activity.methods.single { it.name == "onCreate" }
                val content = create.implementation!!.instructions.indexOfFirst {
                    ((it as? ReferenceInstruction)?.reference as? MethodReference)?.name == "setContentView"
                }
                check(content >= 0)
                create.addInstructions(content + 1,
                    "invoke-static {p0}, Lhe/f0;->catppuccinViewer(Landroid/app/Activity;)V")
                create.addInstructions(0,
                    "invoke-static {p0}, Lhe/f0;->catppuccinViewerTheme(Landroid/app/Activity;)V")
            }
        }
    }
}
