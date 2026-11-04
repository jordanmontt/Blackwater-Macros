package com.blackwatermacros.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import com.blackwatermacros.app.core.AiProvider
import com.blackwatermacros.app.core.LabelBasis
import com.blackwatermacros.app.core.NewProduct
import com.blackwatermacros.app.data.ai.AiClient
import com.blackwatermacros.app.data.ai.AiSettingsStore
import com.blackwatermacros.app.data.ai.LabelReader
import com.blackwatermacros.app.data.ai.SecretCipher
import com.blackwatermacros.app.data.ai.local.LocalModelState
import com.blackwatermacros.app.data.foods.OpenFoodFactsClient
import com.blackwatermacros.app.data.foods.OwnProductStore
import com.blackwatermacros.app.ui.foods.LabelReadState
import com.blackwatermacros.app.ui.foods.NewProductViewModel
import com.blackwatermacros.app.ui.foods.ProductDraft
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * «Nuevo producto» (web `add-food-new-product.test.tsx`): a label photo alone
 * reads nothing until «Rellenar con la IA» (the phone's model can take a while),
 * which also reads the photo already saved; Save without any label photo asks
 * once, except when editing. Never calls a real provider.
 */
@OptIn(ExperimentalCoroutinesApi::class, kotlinx.coroutines.DelicateCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE) // real JPEGs for the photo and its thumbnail
class NewProductViewModelTest {

    private val server = MockWebServer()
    private val main = newSingleThreadContext("main")
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var store: OwnProductStore
    private lateinit var viewModel: NewProductViewModel

    private val reading = """{"name":"Leche entera","brand":"PIL","language":"pt","basis":"serving","servingSize":"200 ml","calories":124,"protein":6.4,"carbs":9.6,"fat":6.6}"""

    @Before
    fun setUp() {
        Dispatchers.setMain(main)
        server.start()
        val mock = server.url("/")
        val http = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val url = chain.request().url.newBuilder().scheme(mock.scheme).host(mock.host).port(mock.port).build()
                chain.proceed(chain.request().newBuilder().url(url).build())
            }
            .build()
        val settings = AiSettingsStore(context.getSharedPreferences("new-product-ai", Context.MODE_PRIVATE).also { it.edit().clear().commit() }, PlainCipher)
        settings.update { it.copy(provider = AiProvider.GEMINI, apiKeys = mapOf(AiProvider.GEMINI to "fake-key")) }
        val photoDir = File(context.noBackupFilesDir, "new-product-photos").apply { deleteRecursively() }
        store = OwnProductStore(context.getSharedPreferences("new-product-own", Context.MODE_PRIVATE).also { it.edit().clear().commit() }, photoDir)
        viewModel = NewProductViewModel(
            reader = LabelReader(AiClient(http), settings),
            settings = settings,
            localModel = MutableStateFlow(LocalModelState.NotDownloaded),
            ownProducts = store,
            openFoodFacts = OpenFoodFactsClient(http, productBaseUrl = mock.toString()),
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
        Dispatchers.resetMain()
        main.close()
    }

    private fun gemini(text: String) = MockResponse().setBody(
        """{"candidates":[{"content":{"parts":[{"text":${JsonPrimitive(text)}}]}}]}""",
    )

    private fun jpeg(): ByteArray = ByteArrayOutputStream().use { out ->
        Bitmap.createBitmap(48, 32, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.JPEG, 80, out)
        out.toByteArray()
    }

    private fun milk() = viewModel.edit {
        it.copy(name = "Leche entera", basis = LabelBasis.SERVING, serving = "200 ml", calories = "124", protein = "6,4")
    }

    private fun readAndWait() = runBlocking {
        viewModel.fillWithAi()
        withTimeout(10_000) { viewModel.reading.first { it == LabelReadState.Done || it is LabelReadState.Failed } }
    }

    @Test
    fun `a photo alone reads nothing until «Rellenar con la IA»`() {
        viewModel.start(ProductDraft(code = "7771234000123", existing = false))
        val file = File(context.cacheDir, "label.jpg").apply { writeBytes(jpeg()) }
        viewModel.addPhoto(context, Uri.fromFile(file))
        runBlocking { withTimeout(10_000) { viewModel.photo.first { it != null } } }
        assertThat(viewModel.reading.value).isEqualTo(LabelReadState.Idle)
        assertThat(server.requestCount).isEqualTo(0)
        assertThat(viewModel.fields.value.name).isEmpty()

        server.enqueue(gemini(reading))
        assertThat(readAndWait()).isEqualTo(LabelReadState.Done)
        val fields = viewModel.fields.value
        assertThat(fields.name).isEqualTo("Leche entera")
        assertThat(fields.basis).isEqualTo(LabelBasis.SERVING)
        assertThat(fields.calories).isEqualTo("124")
        assertThat(fields.lang).isEqualTo("pt")
        // With the photo, Save doesn't ask for one.
        assertThat(viewModel.submit("7771234000123")).isNotNull()
        assertThat(viewModel.askPhoto.value).isFalse()
    }

    @Test
    fun `without a label photo Save asks once, and «Guardar sin foto» saves`() {
        viewModel.start(ProductDraft(code = "7771234000123", existing = false))
        // Missing values come first.
        assertThat(viewModel.submit("7771234000123")).isNull()
        assertThat(viewModel.fields.value.tried).isTrue()
        assertThat(viewModel.askPhoto.value).isFalse()

        milk()
        assertThat(viewModel.submit("7771234000123")).isNull()
        assertThat(viewModel.askPhoto.value).isTrue()
        val (product, photo, _) = viewModel.submit("7771234000123", withoutPhoto = true)!!
        assertThat(product.calories).isEqualTo(124.0)
        assertThat(photo).isNull()
    }

    @Test
    fun `editing doesn't ask for the photo, and the AI reads the one waiting on the phone`() {
        val saved = NewProduct("7771234000123", "Leche", null, LabelBasis.PER_100G, null, 60.0, 3.0, null, null)
        val bytes = jpeg()
        store.save(saved, "es", existing = false, photo = bytes)
        viewModel.start(ProductDraft(code = saved.code, existing = false, initial = saved, lang = "es"))
        runBlocking { withTimeout(10_000) { viewModel.savedPhoto.first { it != null } } }
        assertThat(viewModel.savedPhoto.value!!.fromDevice).isTrue()

        server.enqueue(gemini(reading))
        assertThat(readAndWait()).isEqualTo(LabelReadState.Done)
        assertThat(server.takeRequest().body.readUtf8()).contains(Base64.encodeToString(bytes, Base64.NO_WRAP))
        assertThat(viewModel.fields.value.calories).isEqualTo("124")
        // Open Food Facts already has its language, and the name typed before stays.
        assertThat(viewModel.fields.value.name).isEqualTo("Leche")

        assertThat(viewModel.submit(saved.code, askForPhoto = false)).isNotNull()
        assertThat(viewModel.askPhoto.value).isFalse()
    }

    private object PlainCipher : SecretCipher {
        override fun encrypt(plain: String) = plain.reversed()
        override fun decrypt(encoded: String) = encoded.reversed()
    }
}
