package com.blackwatermacros.app.ui

import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Every language has every text, with the same placeholders — a missing key
 * would silently fall back to English, a wrong placeholder would crash.
 */
class TranslationsTest {

    private val resDir = File("src/main/res")
    private val languages = listOf("es", "fr", "it", "de")
    private val placeholder = Regex("%(\\d+\\$)?[sd]")

    /** name -> list of texts (one for a string, one per quantity for plurals). */
    private fun load(dir: String): Map<String, List<String>> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(resDir, "$dir/strings.xml"))
        val out = mutableMapOf<String, List<String>>()
        val strings = doc.getElementsByTagName("string")
        for (i in 0 until strings.length) {
            val node = strings.item(i)
            if (node.attributes.getNamedItem("translatable")?.nodeValue == "false") continue
            out[node.attributes.getNamedItem("name").nodeValue] = listOf(node.textContent)
        }
        val plurals = doc.getElementsByTagName("plurals")
        for (i in 0 until plurals.length) {
            val node = plurals.item(i)
            val items = node.childNodes
            out[node.attributes.getNamedItem("name").nodeValue] =
                (0 until items.length).map { items.item(it) }.filter { it.nodeName == "item" }.map { it.textContent }
        }
        return out
    }

    @Test
    fun `every language has every text with the same placeholders`() {
        val english = load("values")
        for (lang in languages) {
            val translated = load("values-$lang")
            assertWithMessage("keys in $lang").that(translated.keys).containsExactlyElementsIn(english.keys)
            for ((key, texts) in english) {
                val expected = placeholder.findAll(texts.last()).map { it.value }.toSet()
                translated.getValue(key).forEach { text ->
                    val actual = placeholder.findAll(text).map { it.value }.toSet()
                    assertWithMessage("placeholders of $key in $lang").that(actual).isEqualTo(expected)
                }
            }
        }
    }
}
