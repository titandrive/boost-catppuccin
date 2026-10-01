package app.morphe.patches.reddit.customclients.boostforreddit.theme

import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.resourcePatch
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

private fun isAccentVariant(name: String, base: String): Boolean =
    name.startsWith("$base.") && name.removePrefix("$base.").matches(Regex("[A-Za-z]+[0-9]+"))

private fun flavorForStyle(name: String): String? = when {
    name == "LightTheme" || name.startsWith("LightTheme.") -> "Latte"
    name == "DarkTheme" || name.startsWith("DarkTheme.") -> "Macchiato"
    name == "MaterialLightTheme" || name == "MaterialLightTheme.Dynamic" || isAccentVariant(name, "MaterialLightTheme") -> "Latte"
    name == "MaterialDarkTheme" || name == "MaterialDarkTheme.Dynamic" || isAccentVariant(name, "MaterialDarkTheme") -> "Macchiato"
    else -> null
}

@Suppress("unused")
val catppuccinThemePatch = resourcePatch(
    name = "Catppuccin theme",
    description = "Adds Latte and Macchiato with mauve accents to Boost's classic and Material theme pickers. " +
        "Select a Catppuccin theme and disable wallpaper colors to use the palette.",
    default = false,
) {
    compatibleWith(
        Compatibility(
            name = "Boost for Reddit",
            packageName = "com.rubenmayayo.reddit",
            targets = listOf(AppTarget(version = "1.12.12")),
        ),
    )

    execute {
        val templates: Document = checkNotNull(
            CatppuccinResources::class.java.classLoader.getResourceAsStream("catppuccin/styles.xml"),
        ).use { DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(it) }
        val styles = templates.documentElement.elements().associateBy { it.getAttribute("name") }
        val seen = mutableSetOf<String>()
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

        // Update qualified resources too, so an API-specific style cannot undo the palette.
        for (directory in get("res").listFiles().orEmpty()) {
            if (!directory.isDirectory || !directory.name.startsWith("values")) continue
            val path = "res/${directory.name}/styles.xml"
            if (!get(path).exists()) continue
            document(path).use { xml ->
                for (style in xml.documentElement.elements()) {
                    val name = style.getAttribute("name")
                    val flavor = flavorForStyle(name) ?: continue
                    mergeItems(style, styles.getValue("Catppuccin.$flavor"))
                    seen += name
                }
                if (directory.name == "values") {
                    for ((name, style) in styles) {
                        if (name in setOf("Catppuccin.Latte", "Catppuccin.Macchiato")) continue
                        xml.documentElement.elements()
                            .firstOrNull { it.getAttribute("name") == name }
                            ?.let { xml.documentElement.removeChild(it) }
                        xml.documentElement.appendChild(xml.importNode(style, true))
                    }
                }
            }
        }
        check(seen.containsAll(listOf("LightTheme", "DarkTheme", "MaterialLightTheme", "MaterialDarkTheme"))) {
            "Boost's expected theme resources were not found. Use Boost 1.12.12."
        }

        // These custom controls otherwise inherit wallpaper accents from Material overlays.
        for (file in get("res/layout").listFiles().orEmpty()) {
            if (!file.name.endsWith(".xml")) continue
            document("res/layout/${file.name}").use { xml ->
                val nodes = xml.getElementsByTagName("*")
                for (index in 0 until nodes.length) {
                    val element = nodes.item(index) as Element
                    for (attribute in listOf("fab:menu_colorNormal", "fab:menu_colorPressed")) {
                        if (element.hasAttribute(attribute))
                            element.setAttribute(attribute, "?attr/HighlightTextColor")
                    }
                    if (element.getAttribute("android:id") == "@id/edit_text")
                        element.setAttribute("android:textColorHint", "?attr/SecondaryTextColor")
                    if (element.getAttribute("android:id") == "@id/send_button")
                        element.setAttribute("android:tint", "?attr/HighlightTextColor")
                }
            }
        }
        document("res/menu/menu_reply.xml").use { xml ->
            val items = xml.getElementsByTagName("item")
            for (index in 0 until items.length) {
                val item = items.item(index) as Element
                if (item.getAttribute("android:id") == "@id/action_send")
                    item.setAttribute("app:iconTint", "?attr/HighlightTextColor")
            }
        }

        val labels = mapOf(
            "theme_light" to "Catppuccin Latte",
            "theme_dark" to "Catppuccin Macchiato",
            "theme_material_light" to "Catppuccin Latte (Material)",
            "theme_material_dark" to "Catppuccin Macchiato (Material)",
        )
        for (directory in get("res").listFiles().orEmpty()) {
            if (!directory.isDirectory || !directory.name.startsWith("values")) continue
            val path = "res/${directory.name}/strings.xml"
            if (!get(path).exists()) continue
            document(path).use { xml ->
                for (string in xml.documentElement.elements()) {
                    labels[string.getAttribute("name")]?.let { string.textContent = it }
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
