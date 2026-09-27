package com.blackwatermacros.app.data.ai.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.blackwatermacros.app.core.AiProvider
import com.blackwatermacros.app.data.ai.AiEngineChoice
import com.blackwatermacros.app.data.ai.AiSettingsStore
import com.blackwatermacros.app.data.ai.SecretCipher
import com.blackwatermacros.app.data.ai.usableEngine
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.security.MessageDigest

/**
 * On-device model (D5/D6): the resumable, checksum-verified download; which
 * phones can run it; where each feature runs. The model itself (native code)
 * is checked on a device, not here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class LocalModelTest {

    @get:Rule val folder = TemporaryFolder()
    private val server = MockWebServer()
    private val downloader = ModelDownloader(OkHttpClient())
    private val content = ByteArray(300_000) { (it % 251).toByte() }
    private val sha = MessageDigest.getInstance("SHA-256").digest(content).joinToString("") { "%02x".format(it) }

    @Before
    fun setUp() = server.start()

    @After
    fun tearDown() = server.shutdown()

    private fun files() = File(folder.root, "model.litertlm") to File(folder.root, "model.litertlm.part")

    @Test
    fun `downloads, checks the SHA-256 and moves the file into place`() = runBlocking {
        server.enqueue(MockResponse().setBody(Buffer().write(content)))
        val (target, partial) = files()
        val progress = mutableListOf<Long>()
        downloader.download(server.url("/m").toString(), target, partial, content.size.toLong(), sha) { done, _ -> progress += done }

        assertThat(target.readBytes()).isEqualTo(content)
        assertThat(partial.exists()).isFalse()
        assertThat(progress.last()).isEqualTo(content.size.toLong())
    }

    @Test
    fun `a stopped download continues where it was with a Range request`() = runBlocking {
        val (target, partial) = files()
        partial.writeBytes(content.copyOfRange(0, 100_000))
        server.enqueue(MockResponse().setResponseCode(206).setBody(Buffer().write(content.copyOfRange(100_000, content.size))))

        downloader.download(server.url("/m").toString(), target, partial, content.size.toLong(), sha) { _, _ -> }

        assertThat(server.takeRequest().getHeader("Range")).isEqualTo("bytes=100000-")
        assertThat(target.readBytes()).isEqualTo(content)
    }

    @Test
    fun `a server that ignores Range sends everything again and it still works`() = runBlocking {
        val (target, partial) = files()
        partial.writeBytes(content.copyOfRange(0, 100_000))
        server.enqueue(MockResponse().setResponseCode(200).setBody(Buffer().write(content)))

        downloader.download(server.url("/m").toString(), target, partial, content.size.toLong(), sha) { _, _ -> }

        assertThat(target.readBytes()).isEqualTo(content)
    }

    @Test
    fun `a damaged file is deleted instead of used`() = runBlocking {
        val (target, partial) = files()
        val damaged = content.copyOf().also { it[5] = 0 }
        server.enqueue(MockResponse().setBody(Buffer().write(damaged)))
        try {
            downloader.download(server.url("/m").toString(), target, partial, content.size.toLong(), sha) { _, _ -> }
            fail("expected a checksum error")
        } catch (e: ChecksumException) {
            assertThat(target.exists()).isFalse()
            assertThat(partial.exists()).isFalse()
        }
    }

    @Test
    fun `only 64-bit ARM phones with enough RAM for the chosen model can use it (debug allows emulators)`() {
        val gb = 1_000_000_000L
        val e2b = LocalModels.GEMMA_4_E2B.minRamBytes
        assertThat(deviceSupport(listOf("arm64-v8a"), 8 * gb, e2b, debug = false)).isEqualTo(DeviceSupport.SUPPORTED)
        assertThat(deviceSupport(listOf("arm64-v8a"), 4 * gb, e2b, debug = false)).isEqualTo(DeviceSupport.NOT_ENOUGH_RAM)
        assertThat(deviceSupport(listOf("arm64-v8a"), 4 * gb, LocalModels.QWEN_3_1_7B.minRamBytes, debug = false))
            .isEqualTo(DeviceSupport.SUPPORTED)
        assertThat(deviceSupport(listOf("arm64-v8a"), 6 * gb, LocalModels.GEMMA_4_E4B.minRamBytes, debug = false))
            .isEqualTo(DeviceSupport.NOT_ENOUGH_RAM)
        assertThat(deviceSupport(listOf("armeabi-v7a"), 8 * gb, e2b, debug = false)).isEqualTo(DeviceSupport.UNSUPPORTED_ABI)
        assertThat(deviceSupport(listOf("x86_64"), 3 * gb, e2b, debug = false)).isEqualTo(DeviceSupport.UNSUPPORTED_ABI)
        assertThat(deviceSupport(listOf("x86_64"), 3 * gb, e2b, debug = true)).isEqualTo(DeviceSupport.SUPPORTED)
    }

    @Test
    fun `the catalog offers open models with pinned checksums, one of them light and text-only`() {
        assertThat(LocalModels.ALL.map { it.id }.toSet()).hasSize(LocalModels.ALL.size)
        LocalModels.ALL.forEach { model ->
            assertThat(model.sha256).matches("[0-9a-f]{64}")
            assertThat(model.url).startsWith("https://huggingface.co/litert-community/")
        }
        assertThat(LocalModels.ALL.any { !it.vision && it.sizeBytes < 1_500_000_000L }).isTrue()
        assertThat(LocalModels.DEFAULT).isEqualTo(LocalModels.GEMMA_4_E2B)
    }

    @Test
    fun `a new model replaces the old one on disk`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val old = LocalModels.file(context, LocalModels.GEMMA_4_E2B).apply { parentFile!!.mkdirs(); writeText("old") }
        val partial = LocalModels.partialFile(context, LocalModels.QWEN_3_1_7B).apply { writeText("part") }
        LocalModels.deleteAllExcept(context, keep = LocalModels.QWEN_3_1_7B)
        assertThat(old.exists()).isFalse()
        assertThat(partial.exists()).isTrue()
        LocalModels.deleteAllExcept(context, keep = null)
        assertThat(partial.exists()).isFalse()
    }

    @Test
    fun `each feature runs where the user chose, only when that engine is set up`() {
        assertThat(usableEngine(AiEngineChoice.CLOUD, cloudReady = true, deviceReady = false)).isEqualTo(AiEngineChoice.CLOUD)
        assertThat(usableEngine(AiEngineChoice.CLOUD, cloudReady = false, deviceReady = true)).isNull()
        assertThat(usableEngine(AiEngineChoice.DEVICE, cloudReady = true, deviceReady = true)).isEqualTo(AiEngineChoice.DEVICE)
        assertThat(usableEngine(AiEngineChoice.DEVICE, cloudReady = true, deviceReady = false)).isNull()
    }

    @Test
    fun `the engine choices are remembered`() {
        val prefs = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("lm-ai", Context.MODE_PRIVATE)
        val cipher = object : SecretCipher {
            override fun encrypt(plain: String) = plain.reversed()
            override fun decrypt(encoded: String) = encoded.reversed()
        }
        val store = AiSettingsStore(prefs, cipher)
        assertThat(store.current.photoEngine).isEqualTo(AiEngineChoice.CLOUD)
        store.update { it.copy(provider = AiProvider.GEMINI, coachEngine = AiEngineChoice.DEVICE) }
        val reopened = AiSettingsStore(prefs, cipher).current
        assertThat(reopened.coachEngine).isEqualTo(AiEngineChoice.DEVICE)
        assertThat(reopened.photoEngine).isEqualTo(AiEngineChoice.CLOUD)
    }

    @Test
    fun `the model lives outside backups`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        LocalModels.ALL.forEach { model ->
            assertThat(LocalModels.file(context, model).absolutePath).startsWith(context.noBackupFilesDir.absolutePath)
        }
    }
}
