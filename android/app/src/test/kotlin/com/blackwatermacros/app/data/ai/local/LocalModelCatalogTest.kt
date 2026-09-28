package com.blackwatermacros.app.data.ai.local

import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Test
import java.io.File

/**
 * The phone models on offer come from the Blackwater site
 * (`public/models/local-models.json`): new models without a new app version,
 * wrong entries skipped, downloads only from huggingface.co.
 */
class LocalModelCatalogTest {

    @After
    fun reset() = LocalModels.useCatalog(emptyList())

    private fun entry(
        id: String = "new-model",
        url: String = "https://huggingface.co/litert-community/new/resolve/main/new.litertlm",
        fileName: String = "new.litertlm",
        sha: String = "a".repeat(64),
        extra: String = "",
    ) = """{"id":"$id","name":"New","url":"$url","fileName":"$fileName","sizeBytes":1000,"sha256":"$sha","recommendedPhoneGb":6,"vision":true,"notes":{"en":"Hi","es":"Hola"}$extra}"""

    @Test
    fun `a valid entry becomes a model, with its notes by language`() {
        val models = parseLocalModelCatalog("""{"models":[${entry()}]}""", appVersionCode = 1)
        val model = models.single()
        assertThat(model.id).isEqualTo("new-model")
        assertThat(model.vision).isTrue()
        assertThat(model.note("es")).isEqualTo("Hola")
        assertThat(model.note("de")).isEqualTo("Hi") // English when the language is missing
    }

    @Test
    fun `wrong entries are skipped, the rest stays`() {
        val bad = listOf(
            entry(id = "evil", url = "https://example.com/new.litertlm"), // not Hugging Face
            entry(id = "short-sha", sha = "abc"),
            entry(id = "path", fileName = "../new.litertlm", url = "https://huggingface.co/x/../new.litertlm"),
            entry(id = "newer-app", extra = ""","minAppVersionCode":999"""),
            entry(id = "new-model"),
            entry(id = "new-model"), // a repeated id counts once
        )
        val models = parseLocalModelCatalog("""{"models":[${bad.joinToString(",")}]}""", appVersionCode = 1)
        assertThat(models.map { it.id }).containsExactly("new-model")
        assertThat(parseLocalModelCatalog("not json", 1)).isEmpty()
    }

    @Test
    fun `the site's list comes first and built-in models it does not mention stay known`() {
        LocalModels.useCatalog(parseLocalModelCatalog("""{"models":[${entry()},${entry(id = "gemma-4-e2b", extra = ""","hidden":true""")}]}""", 1))
        assertThat(LocalModels.ALL.map { it.id }).containsExactly("new-model", "gemma-4-e2b", "gemma-4-e4b", "qwen3-1.7b").inOrder()
        // A hidden model is still recognised (someone may have it) but not offered.
        assertThat(LocalModels.byId("gemma-4-e2b")).isNotNull()
        assertThat(LocalModels.choices.map { it.id }).doesNotContain("gemma-4-e2b")
    }

    @Test
    fun `the file on the site is valid and matches the built-in models`() {
        val models = parseLocalModelCatalog(File("../../public/models/local-models.json").readText(), appVersionCode = 1)
        assertThat(models).isNotEmpty()
        LocalModels.BUILT_IN.forEach { builtIn ->
            val listed = models.firstOrNull { it.id == builtIn.id } ?: return@forEach
            assertThat(listed.url).isEqualTo(builtIn.url)
            assertThat(listed.sizeBytes).isEqualTo(builtIn.sizeBytes)
            assertThat(listed.sha256).isEqualTo(builtIn.sha256)
            assertThat(listed.recommendedPhoneGb).isEqualTo(builtIn.recommendedPhoneGb)
            assertThat(listed.vision).isEqualTo(builtIn.vision)
        }
    }
}
