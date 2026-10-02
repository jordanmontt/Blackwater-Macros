package com.blackwatermacros.app.data

import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import java.io.File

/**
 * The privacy promise in the README («Private by design») lists every place the
 * app can connect to. This test keeps that list honest: a new host anywhere in
 * the app's code fails here until it is added below **and** to the README.
 */
class NetworkHostsTest {

    /** Host → when the app talks to it. Keep in sync with README.md «Private by design». */
    private val allowed = mapOf(
        "world.openfoodfacts.org" to "barcode lookup, only when you scan or type a barcode",
        "search.openfoodfacts.org" to "food search, only when you search a product online",
        "raw.githubusercontent.com" to "list of phone AI models, only when you open Settings → AI",
        "huggingface.co" to "phone AI model download, only when you tap download",
        "generativelanguage.googleapis.com" to "Google Gemini, only with your own key",
        "api.openai.com" to "OpenAI, only with your own key",
        "api.anthropic.com" to "Anthropic, only with your own key",
        "openrouter.ai" to "OpenRouter, only with your own key",
        "blackwater-macros.jordanmontt.fr" to "account server, only after logging in (AccountInterceptor)",
        // Not contacted by the app:
        "aistudio.google.com" to "link opened in the browser to get a free Gemini key",
        "github.com" to "source link and the Open Food Facts User-Agent text",
        "ko-fi.com" to "donation link opened in the browser (Settings)",
        "example.com" to "placeholder text in a field",
    )

    private val url = Regex("""https?://([a-zA-Z0-9.-]+\.[a-zA-Z]{2,})""")

    @Test
    fun `the app only knows the hosts the README lists`() {
        val sources = listOf(File("src/main/kotlin"), File("../core/src/main/kotlin"))
            .flatMap { dir -> dir.walkTopDown().filter { it.extension == "kt" }.toList() } +
            File("build.gradle.kts")
        val found = sources.flatMap { file -> url.findAll(file.readText()).map { it.groupValues[1] to file.name } }
            .filterNot { (host, _) -> host == "schemas.android.com" }
        for ((host, file) in found) {
            assertWithMessage("$host (in $file) is not in the list of hosts the README promises").that(allowed).containsKey(host)
        }
    }
}
