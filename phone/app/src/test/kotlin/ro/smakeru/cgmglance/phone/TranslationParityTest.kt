package ro.smakeru.cgmglance.phone

import org.junit.jupiter.api.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Every shipped language says everything the English does, with the same placeholders.
 *
 * A missing key is not a crash: Android falls back to English, so the screen quietly turns
 * bilingual and nobody is told. A placeholder that differs is worse — `%1$d` translated into
 * `%1$s`, or dropped, fails only when that one sentence is finally shown. Both are cheap to
 * catch by reading the files, and expensive to catch by looking at four languages on a phone.
 */
class TranslationParityTest {
    private val languages = listOf("ro", "de", "fr")

    // Gradle runs module tests from the module directory.
    private val resourceRoots =
        listOf(
            File("src/main/res"),
            File("../../wear/app/src/main/res"),
        )

    private val placeholder = Regex("""%(\d+\$)?[sd]""")

    /** name -> text, with each plural quantity as `name#quantity`. */
    private fun entries(file: File): Map<String, String> {
        val root =
            DocumentBuilderFactory
                .newInstance()
                .newDocumentBuilder()
                .parse(file)
                .documentElement
        val result = linkedMapOf<String, String>()
        val children = root.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i) as? Element ?: continue
            if (node.getAttribute("translatable") == "false") continue
            val name = node.getAttribute("name")
            when (node.tagName) {
                "string" -> {
                    result[name] = node.textContent
                }

                "plurals" -> {
                    val items = node.getElementsByTagName("item")
                    for (j in 0 until items.length) {
                        val item = items.item(j) as Element
                        result["$name#${item.getAttribute("quantity")}"] = item.textContent
                    }
                }
            }
        }
        return result
    }

    private fun translatedFiles(): List<Triple<String, File, File>> =
        resourceRoots.flatMap { root ->
            assertTrue(root.isDirectory, "resource root not found: ${root.absolutePath}")
            val sources = File(root, "values").listFiles { f -> f.name.endsWith("strings.xml") }.orEmpty()
            assertTrue(sources.isNotEmpty(), "no string files under ${root.path}/values")
            sources.flatMap { source ->
                languages.map { language -> Triple(language, source, File(root, "values-$language/${source.name}")) }
            }
        }

    @Test
    fun `every language has every string and nothing extra`() {
        translatedFiles().forEach { (language, source, translated) ->
            assertTrue(translated.isFile, "missing ${translated.path}")

            // Plural quantities differ by language on purpose, so compare names only.
            val expected = entries(source).keys.map { it.substringBefore('#') }.toSet()
            val actual = entries(translated).keys.map { it.substringBefore('#') }.toSet()

            assertEquals(emptySet(), expected - actual, "$language is missing strings from ${source.path}")
            assertEquals(emptySet(), actual - expected, "$language has strings absent from ${source.path}")
        }
    }

    @Test
    fun `placeholders survive translation`() {
        translatedFiles().forEach { (language, source, translated) ->
            val english = entries(source)
            entries(translated).forEach { (key, text) ->
                val name = key.substringBefore('#')
                val reference =
                    english[key] ?: english.entries.first { it.key.substringBefore('#') == name }.value

                assertEquals(
                    placeholder
                        .findAll(reference)
                        .map { it.value }
                        .sorted()
                        .toList(),
                    placeholder
                        .findAll(text)
                        .map { it.value }
                        .sorted()
                        .toList(),
                    "$language/$key has different placeholders",
                )
            }
        }
    }

    @Test
    fun `no translation carries an unescaped apostrophe`() {
        // aapt drops a bare ' and everything after the next one. French is the language that
        // would find this out; its files use the typographic apostrophe instead.
        translatedFiles().forEach { (language, _, translated) ->
            entries(translated).forEach { (key, text) ->
                assertTrue(
                    !Regex("""(?<!\\)'""").containsMatchIn(text),
                    "$language/$key contains an unescaped apostrophe",
                )
            }
        }
    }
}
