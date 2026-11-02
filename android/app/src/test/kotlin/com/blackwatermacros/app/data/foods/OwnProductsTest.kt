package com.blackwatermacros.app.data.foods

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.blackwatermacros.app.core.FoodLang
import com.blackwatermacros.app.core.LabelBasis
import com.blackwatermacros.app.core.NewProduct
import com.blackwatermacros.app.data.ai.SecretCipher
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.net.URLDecoder
import java.util.Base64

/**
 * «Añadir este producto» (mirrors `add-food-new-product.test.tsx`): products added
 * from a label are kept on the phone and sent straight to Open Food Facts with
 * the app's account or the user's own, values first and then the label photo,
 * which leaves the phone once Open Food Facts has it. What may work later waits
 * (WorkManager retries); what Open Food Facts refuses says so.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class OwnProductsTest {

    private val server = MockWebServer()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var photoDir: File
    private lateinit var store: OwnProductStore
    private lateinit var account: OffAccountStore
    private var scheduled = 0

    private val milk = NewProduct("7771234000123", "Leche entera", "PIL", LabelBasis.SERVING, "200 ml", 124.0, 6.4, 9.6, 6.6)
    private val jpeg = byteArrayOf(1, 2, 3, 4)

    @Before
    fun setUp() {
        server.start()
        photoDir = File(context.noBackupFilesDir, "test-off-photos").apply { deleteRecursively() }
        store = OwnProductStore(context.getSharedPreferences("own_products_test", Context.MODE_PRIVATE).apply { edit().clear().commit() }, photoDir)
        account = OffAccountStore(context.getSharedPreferences("off_account_test", Context.MODE_PRIVATE).apply { edit().clear().commit() }, ReversingCipher)
    }

    @After
    fun tearDown() {
        server.shutdown()
        photoDir.deleteRecursively()
    }

    private fun serverUrl() = server.url("").toString().trimEnd('/')

    /** Nothing listens there: like having no connection. */
    private val unreachable = "http://127.0.0.1:1"

    private fun products(appPassword: String = "app-password", at: String = serverUrl()) = OwnProducts(
        store = store,
        account = account,
        uploader = OffUploader.create(at),
        appVersion = "1.4.0",
        appPassword = appPassword,
        schedule = { scheduled++ },
    )

    private fun ok() = MockResponse().setBody("""{"status":1,"status_verbose":"fields saved"}""")
    private fun photoOk() = MockResponse().setBody("""{"status":"status ok","imgid":1}""")

    private fun form(body: String): Map<String, String> = body.split("&").associate { pair ->
        val (key, value) = pair.split("=", limit = 2)
        URLDecoder.decode(key, "UTF-8") to URLDecoder.decode(value, "UTF-8")
    }

    @Test
    fun `values then the label photo go to Open Food Facts, and the photo leaves the phone`() = runBlocking {
        val products = products()
        products.save(milk, FoodLang.ES, existing = false, photo = jpeg)
        assertThat(scheduled).isEqualTo(1)
        assertThat(File(photoDir, "7771234000123.jpg").readBytes()).isEqualTo(jpeg)
        server.enqueue(ok())
        server.enqueue(photoOk())

        assertThat(products.sendPending()).isTrue()

        val fields = server.takeRequest()
        assertThat(fields.path).isEqualTo("/cgi/product_jqm2.pl")
        assertThat(fields.getHeader("User-Agent")).startsWith(OpenFoodFactsClient.USER_AGENT_PREFIX)
        val sent = form(fields.body.readUtf8())
        assertThat(sent["user_id"]).isEqualTo("blackwater-macros-app")
        assertThat(sent["password"]).isEqualTo("app-password")
        assertThat(sent["product_name_es"]).isEqualTo("Leche entera")
        assertThat(sent["nutrition_data_per"]).isEqualTo("serving")
        assertThat(sent["serving_size"]).isEqualTo("200 ml")
        assertThat(sent["nutriment_energy-kcal"]).isEqualTo("124")
        assertThat(sent["app_uuid"]).matches("[0-9a-f-]{36}")

        val photo = server.takeRequest()
        assertThat(photo.path).isEqualTo("/cgi/product_image_upload.pl")
        val multipart = photo.body.readUtf8()
        assertThat(multipart).containsMatch("name=\"imagefield\"\r\n(Content-Length: \\d+\r\n)?\r\nnutrition_es")
        assertThat(multipart).contains("name=\"imgupload_nutrition_es\"; filename=\"label.jpg\"")

        val saved = store.get("7771234000123")!!
        assertThat(saved.state).isEqualTo(OwnProductState.SENT)
        assertThat(saved.hasPhoto).isFalse()
        assertThat(File(photoDir, "7771234000123.jpg").exists()).isFalse()
    }

    @Test
    fun `offline or a busy Open Food Facts waits, and the next run sends it`() = runBlocking {
        products(at = unreachable).save(milk, FoodLang.ES, existing = false, photo = jpeg)
        assertThat(products(at = unreachable).sendPending()).isFalse()
        assertThat(store.get(milk.code)!!.state).isEqualTo(OwnProductState.PENDING)
        val products = products()

        server.enqueue(ok())
        server.enqueue(MockResponse().setResponseCode(503))
        assertThat(products.sendPending()).isFalse()
        assertThat(store.get(milk.code)!!.fieldsPending).isFalse()
        assertThat(store.get(milk.code)!!.hasPhoto).isTrue()

        server.enqueue(photoOk())
        assertThat(products.sendPending()).isTrue()
        assertThat(store.get(milk.code)!!.state).isEqualTo(OwnProductState.SENT)
    }

    @Test
    fun `the user's own account is used, and one Open Food Facts refuses is tried again after changing it`() = runBlocking {
        val products = products()
        products.setAccount(OffAccount("maria", "wrong"))
        products.save(milk, FoodLang.FR, existing = false, photo = null)
        server.enqueue(MockResponse().setResponseCode(403))
        assertThat(products.sendPending()).isTrue()
        val first = form(server.takeRequest().body.readUtf8())
        assertThat(first["user_id"]).isEqualTo("maria")
        assertThat(first["product_name_fr"]).isEqualTo("Leche entera")
        assertThat(store.get(milk.code)!!.state).isEqualTo(OwnProductState.ACCOUNT)

        products.setAccount(OffAccount("maria", "right"))
        assertThat(store.get(milk.code)!!.state).isEqualTo(OwnProductState.PENDING)
        server.enqueue(ok())
        assertThat(products.sendPending()).isTrue()
        assertThat(form(server.takeRequest().body.readUtf8())["password"]).isEqualTo("right")
        assertThat(store.get(milk.code)!!.state).isEqualTo(OwnProductState.SENT)
    }

    @Test
    fun `what Open Food Facts refuses says so and is not retried`() = runBlocking {
        val products = products()
        products.save(milk, FoodLang.ES, existing = false, photo = null)
        server.enqueue(MockResponse().setBody("""{"status":0,"status_verbose":"no code or invalid code"}"""))
        assertThat(products.sendPending()).isTrue()
        val refused = store.get(milk.code)!!
        assertThat(refused.state).isEqualTo(OwnProductState.REJECTED)
        assertThat(refused.detail).isEqualTo("no code or invalid code")
        assertThat(products.sendPending()).isTrue()
        assertThat(server.requestCount).isEqualTo(1)
    }

    @Test
    fun `a product Open Food Facts had keeps its name, and an edit is sent again`() = runBlocking {
        val products = products()
        products.save(milk, FoodLang.ES, existing = true, photo = null)
        server.enqueue(ok())
        products.sendPending()
        assertThat(form(server.takeRequest().body.readUtf8())).doesNotContainKey("product_name_es")

        products.save(milk.copy(calories = 130.0), FoodLang.ES, existing = false, photo = null)
        assertThat(store.get(milk.code)!!.existing).isTrue()
        server.enqueue(ok())
        products.sendPending()
        val edit = form(server.takeRequest().body.readUtf8())
        assertThat(edit["nutriment_energy-kcal"]).isEqualTo("130")
        assertThat(edit).doesNotContainKey("product_name_es")
        assertThat(store.products.value).hasSize(1)
    }

    @Test
    fun `without the app account's password nothing is sent`() = runBlocking {
        val products = products(appPassword = "")
        products.save(milk, FoodLang.ES, existing = false, photo = null)
        assertThat(products.sendPending()).isTrue()
        assertThat(server.requestCount).isEqualTo(0)
        assertThat(store.get(milk.code)!!.state).isEqualTo(OwnProductState.PENDING)
    }

    @Test
    fun `products survive a restart and become choices for the portion screen`() {
        products().save(milk, FoodLang.ES, existing = false, photo = null)
        val reopened = OwnProductStore(context.getSharedPreferences("own_products_test", Context.MODE_PRIVATE), photoDir)
        val choice = reopened.get(milk.code)!!.toChoice()
        assertThat(choice.code).isEqualTo(milk.code)
        assertThat(choice.name).isEqualTo("Leche entera")
        assertThat(choice.calories).isEqualTo(62.0)
        assertThat(choice.servingGrams).isEqualTo(200.0)
    }

    @Test
    fun `the account check asks Open Food Facts`() = runBlocking {
        val uploader = OffUploader.create(serverUrl())
        server.enqueue(MockResponse().setBody("{}"))
        assertThat(uploader.check("maria", "right")).isEqualTo(AccountCheck.OK)
        assertThat(form(server.takeRequest().body.readUtf8())).isEqualTo(mapOf("user_id" to "maria", "password" to "right"))
        server.enqueue(MockResponse().setResponseCode(403))
        assertThat(uploader.check("maria", "wrong")).isEqualTo(AccountCheck.WRONG)
        assertThat(OffUploader.create(unreachable).check("maria", "right")).isEqualTo(AccountCheck.OFFLINE)
    }

    @Test
    fun `the password is stored encrypted, the install id stays, and both are kept out of backups`() {
        account.set(OffAccount("maria", "secret-password"))
        val prefs = context.getSharedPreferences("off_account_test", Context.MODE_PRIVATE)
        val id = account.installId()
        assertThat(prefs.all.values.joinToString()).doesNotContain("secret-password")
        val reopened = OffAccountStore(prefs, ReversingCipher)
        assertThat(reopened.account.value).isEqualTo(OffAccount("maria", "secret-password"))
        assertThat(reopened.installId()).isEqualTo(id)

        val xml = File("src/main/res/xml/backup_rules.xml").readText() + File("src/main/res/xml/data_extraction_rules.xml").readText()
        assertThat(Regex("""path="${OffAccountStore.PREFS_NAME}\.xml"""").findAll(xml).count()).isEqualTo(3)
    }

    /** Robolectric has no Android Keystore; this stands in (reversible, not plain text). */
    private object ReversingCipher : SecretCipher {
        override fun encrypt(plain: String): String = Base64.getEncoder().encodeToString(plain.reversed().toByteArray())
        override fun decrypt(encoded: String): String? = String(Base64.getDecoder().decode(encoded)).reversed()
    }
}
