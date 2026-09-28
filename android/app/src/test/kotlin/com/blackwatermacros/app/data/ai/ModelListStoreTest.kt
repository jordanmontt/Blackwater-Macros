package com.blackwatermacros.app.data.ai

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.blackwatermacros.app.core.AiConfig
import com.blackwatermacros.app.core.AiModelOption
import com.blackwatermacros.app.core.AiProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The model dropdown's list (web `lib/ai/model-list.ts`): asked to the provider,
 * kept on the phone for offline use, asked again after a day, when forced or
 * when the key changes; the key itself is never stored with it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class ModelListStoreTest {

    private val prefs = ApplicationProvider.getApplicationContext<Context>()
        .getSharedPreferences("model-lists-test", Context.MODE_PRIVATE).apply { edit().clear().commit() }
    private var clock = 1_000_000L
    private var calls = 0
    private val store = ModelListStore(prefs, fetch = { calls++; listOf(AiModelOption("gemini-3-flash", "Gemini 3 Flash")) }, now = { clock })
    private val key1 = AiConfig(AiProvider.GEMINI, "key-one", "")
    private val key2 = key1.copy(apiKey = "key-two")

    @Test
    fun `a list is reused for a day, then asked again`() = runBlocking {
        assertThat(store.refresh(key1)!!.options.map { it.id }).containsExactly("gemini-3-flash")
        store.refresh(key1)
        assertThat(calls).isEqualTo(1)
        clock += ModelListStore.MAX_AGE_MS
        store.refresh(key1)
        assertThat(calls).isEqualTo(2)
        store.refresh(key1, force = true)
        assertThat(calls).isEqualTo(3)
    }

    @Test
    fun `another key needs its own list and the stored list survives a restart without the key`() = runBlocking {
        store.refresh(key1)
        assertThat(store.cached(key2)).isNull()
        val restarted = ModelListStore(prefs, fetch = { error("offline") }, now = { clock })
        assertThat(restarted.cached(key1)!!.options.single().label).isEqualTo("Gemini 3 Flash")
        assertThat(prefs.all.values.joinToString()).doesNotContain("key-one")
    }

    @Test
    fun `without a key nothing is asked`() = runBlocking {
        assertThat(store.refresh(key1.copy(apiKey = ""))).isNull()
        assertThat(calls).isEqualTo(0)
    }
}
