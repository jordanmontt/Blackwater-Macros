package com.blackwatermacros.app.data.foods

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.blackwatermacros.app.BuildConfig
import com.blackwatermacros.app.core.LabelBasis
import com.blackwatermacros.app.core.NewProduct
import com.blackwatermacros.app.core.OFF_APP_PASSWORD
import com.blackwatermacros.app.core.OFF_APP_USER_ID
import com.blackwatermacros.app.core.OffCorrection
import com.blackwatermacros.app.core.OffDataPer
import com.blackwatermacros.app.core.OffWriteContext
import com.blackwatermacros.app.core.OffWriteOutcome
import com.blackwatermacros.app.core.newProductToFoodProduct
import com.blackwatermacros.app.core.offFieldsOutcome
import com.blackwatermacros.app.core.offImageFields
import com.blackwatermacros.app.core.offImageOutcome
import com.blackwatermacros.app.core.offLabelImageField
import com.blackwatermacros.app.core.offProductFields
import com.blackwatermacros.app.data.ai.KeystoreCipher
import com.blackwatermacros.app.data.ai.SecretCipher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Products the user added from a label («Añadir este producto», web
 * `lib/foods/own-products.ts`): kept on the phone and sent from it straight to
 * Open Food Facts (never through our server). A product stays [OwnProductState.PENDING]
 * until Open Food Facts has both the values and the label photo; the photo
 * waits in `noBackupFilesDir/off-photos` and is deleted once it is there.
 */

/** [NewProduct] as stored on the phone (`:core` has no serialization). */
@Serializable
data class StoredLabel(
    val code: String,
    val name: String,
    val brand: String? = null,
    val basis: String,
    val servingSize: String? = null,
    val calories: Double? = null,
    val protein: Double? = null,
    val carbs: Double? = null,
    val fat: Double? = null,
) {
    fun toNewProduct() = NewProduct(
        code, name, brand, LabelBasis.fromId(basis) ?: LabelBasis.PER_100G, servingSize, calories, protein, carbs, fat,
    )

    companion object {
        fun of(product: NewProduct) = StoredLabel(
            product.code, product.name, product.brand, product.basis.id, product.servingSize,
            product.calories, product.protein, product.carbs, product.fat,
        )
    }
}

/** [OffCorrection] as stored on the phone. */
@Serializable
data class StoredCorrection(val per: String, val base: StoredLabel, val lang: String? = null) {
    fun toCorrection() = OffCorrection(OffDataPer.fromId(per) ?: OffDataPer.PER_100G, base.toNewProduct(), lang)

    companion object {
        fun of(correction: OffCorrection) = StoredCorrection(correction.per.id, StoredLabel.of(correction.base), correction.lang)
    }
}

enum class OwnProductState { PENDING, SENT, ACCOUNT, REJECTED }

@Serializable
data class OwnProduct(
    val label: StoredLabel,
    /** The package's language (or the product's main language on Open Food Facts): names and photos go under it. */
    val lang: String,
    /** Open Food Facts already had it: only the values (and photo) are sent, never the name or brand. */
    val existing: Boolean,
    /** A label photo waits on the phone until Open Food Facts has it. */
    val hasPhoto: Boolean,
    /** The values (new or edited) still have to reach Open Food Facts. */
    val fieldsPending: Boolean,
    val state: OwnProductState = OwnProductState.PENDING,
    /** Open Food Facts' own words when it refused. */
    val detail: String = "",
    val updatedAt: Long = 0,
    /** «Corregir valores»: what Open Food Facts had, and the basis to send in. */
    val correction: StoredCorrection? = null,
) {
    val code: String get() = label.code
    val product: NewProduct get() = label.toNewProduct()
    val offCorrection: OffCorrection? get() = correction?.toCorrection()
}

/** The choice for the portion screen and the search («Tus productos»). */
fun OwnProduct.toChoice(): FoodChoice {
    val food = newProductToFoodProduct(product)
    return FoodChoice(
        key = "off:${food.code}",
        code = food.code,
        name = food.name,
        brand = food.brand,
        calories = food.per100g.calories,
        protein = food.per100g.protein,
        carbs = food.per100g.carbs,
        fat = food.per100g.fat,
        servingGrams = food.servingGrams,
        source = "off",
        incomplete = food.incomplete,
    )
}

/** The list (newest first) in the `own_products` prefs; label photos as files. */
class OwnProductStore(private val prefs: SharedPreferences, private val photoDir: File) {

    private val serializer = ListSerializer(OwnProduct.serializer())
    private val _products = MutableStateFlow(read())
    val products: StateFlow<List<OwnProduct>> = _products.asStateFlow()

    fun get(code: String?): OwnProduct? = code?.let { c -> _products.value.firstOrNull { it.code == c } }

    /**
     * Saves (or replaces, after «Editar») a product; [photo]: a new label photo
     * (JPEG), if any; [correction]: values Open Food Facts had that the user corrected.
     */
    @Synchronized
    fun save(
        product: NewProduct,
        lang: String,
        existing: Boolean,
        photo: ByteArray?,
        correction: OffCorrection? = null,
        now: Long = System.currentTimeMillis(),
    ): OwnProduct {
        val previous = get(product.code)
        val stored = previous?.correction ?: correction?.let(StoredCorrection::of)
        if (photo != null) {
            photoDir.mkdirs()
            photoFile(product.code).writeBytes(photo)
        }
        val item = OwnProduct(
            label = StoredLabel.of(product),
            lang = lang,
            existing = previous?.existing ?: (existing || stored != null),
            hasPhoto = photo != null || previous?.hasPhoto == true,
            fieldsPending = true,
            state = OwnProductState.PENDING,
            updatedAt = now,
            correction = stored,
        )
        write(listOf(item) + _products.value.filterNot { it.code == product.code })
        return item
    }

    @Synchronized
    fun update(code: String, transform: (OwnProduct) -> OwnProduct) {
        write(_products.value.map { if (it.code == code) transform(it) else it })
    }

    fun photo(code: String): ByteArray? = photoFile(code).takeIf { it.exists() }?.readBytes()

    /** Open Food Facts has the photo (or refused it for good): it leaves the phone. */
    fun dropPhoto(code: String) {
        photoFile(code).delete()
        update(code) { it.copy(hasPhoto = false) }
    }

    /** After the account changes, what the old one couldn't send is tried again. */
    fun retryAccountRefusals() {
        synchronized(this) {
            write(_products.value.map { if (it.state == OwnProductState.ACCOUNT) it.copy(state = OwnProductState.PENDING) else it })
        }
    }

    private fun photoFile(code: String) = File(photoDir, "${code.filter(Char::isDigit)}.jpg")

    /** Past [MAX] the oldest products already sent are forgotten. */
    private fun write(next: List<OwnProduct>) {
        val list = next.toMutableList()
        var index = list.lastIndex
        while (list.size > MAX && index >= 0) {
            if (list[index].state == OwnProductState.SENT) list.removeAt(index)
            index--
        }
        prefs.edit { putString(KEY, Json.encodeToString(serializer, list)) }
        _products.value = list
    }

    private fun read(): List<OwnProduct> =
        runCatching { Json.decodeFromString(serializer, prefs.getString(KEY, null) ?: "[]") }.getOrDefault(emptyList())

    companion object {
        const val PREFS_NAME = "own_products"
        const val PHOTO_DIR = "off-photos"
        private const val KEY = "products"
        private const val MAX = 50

        fun create(context: Context) = OwnProductStore(
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
            File(context.noBackupFilesDir, PHOTO_DIR),
        )
    }
}

data class OffAccount(val userId: String, val password: String)

/**
 * The user's own Open Food Facts account (Ajustes → Open Food Facts), with the
 * password encrypted like the AI keys, and this install's random `app_uuid`.
 * The prefs file is excluded from backups and device transfer.
 */
class OffAccountStore(private val prefs: SharedPreferences, private val cipher: SecretCipher) {

    constructor(context: Context) : this(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE), KeystoreCipher())

    private val _account = MutableStateFlow(read())
    val account: StateFlow<OffAccount?> = _account.asStateFlow()

    fun set(account: OffAccount?) {
        prefs.edit {
            if (account == null) {
                remove(KEY_USER)
                remove(KEY_PASSWORD)
            } else {
                putString(KEY_USER, account.userId)
                putString(KEY_PASSWORD, cipher.encrypt(account.password))
            }
        }
        _account.value = account
    }

    /** A random id for this install, sent as `app_uuid` (no personal data: it only tells installs apart). */
    @Synchronized
    fun installId(): String =
        prefs.getString(KEY_INSTALL_ID, null) ?: UUID.randomUUID().toString().also { id -> prefs.edit { putString(KEY_INSTALL_ID, id) } }

    private fun read(): OffAccount? {
        val user = prefs.getString(KEY_USER, null) ?: return null
        val password = prefs.getString(KEY_PASSWORD, null)?.let(cipher::decrypt) ?: return null
        return OffAccount(user, password)
    }

    companion object {
        /** Named in `res/xml/backup_rules.xml` and `data_extraction_rules.xml`. */
        const val PREFS_NAME = "off_account"
        private const val KEY_USER = "userId"
        private const val KEY_PASSWORD = "password"
        private const val KEY_INSTALL_ID = "installId"
    }
}

enum class AccountCheck { OK, WRONG, OFFLINE }

/** Open Food Facts' write endpoints, called from the phone with the app's User-Agent. */
class OffUploader(private val http: OkHttpClient, private val server: String = BuildConfig.OFF_SERVER) {

    data class Answer(val outcome: OffWriteOutcome, val detail: String = "")

    suspend fun sendFields(fields: List<Pair<String, String>>): Answer = post(
        "/cgi/product_jqm2.pl",
        FormBody.Builder().apply { fields.forEach { (key, value) -> add(key, value) } }.build(),
        ::offFieldsOutcome,
    )

    suspend fun sendPhoto(fields: List<Pair<String, String>>, imageField: String, jpeg: ByteArray): Answer = post(
        "/cgi/product_image_upload.pl",
        MultipartBody.Builder().setType(MultipartBody.FORM).apply {
            fields.forEach { (key, value) -> addFormDataPart(key, value) }
            addFormDataPart("imgupload_$imageField", "label.jpg", jpeg.toRequestBody("image/jpeg".toMediaType()))
        }.build(),
        ::offImageOutcome,
    )

    /** Whether the username and password are right (`/cgi/auth.pl`). */
    suspend fun check(userId: String, password: String): AccountCheck = withContext(Dispatchers.IO) {
        try {
            val body = FormBody.Builder().add("user_id", userId).add("password", password).build()
            http.newCall(Request.Builder().url("$server/cgi/auth.pl").post(body).build()).execute().use { response ->
                when {
                    response.isSuccessful -> AccountCheck.OK
                    response.code == 401 || response.code == 403 -> AccountCheck.WRONG
                    else -> AccountCheck.OFFLINE
                }
            }
        } catch (e: IOException) {
            AccountCheck.OFFLINE
        }
    }

    private suspend fun post(
        path: String,
        body: okhttp3.RequestBody,
        outcome: (Int, JsonElement?) -> OffWriteOutcome,
    ): Answer = withContext(Dispatchers.IO) {
        try {
            http.newCall(Request.Builder().url("$server$path").post(body).build()).execute().use { response ->
                val json = runCatching { Json.parseToJsonElement(response.body!!.string()) }.getOrNull()
                val detail = (json as? kotlinx.serialization.json.JsonObject)?.let { record ->
                    listOf("status_verbose", "error").firstNotNullOfOrNull { key ->
                        (record[key] as? kotlinx.serialization.json.JsonPrimitive)?.content
                    }
                }.orEmpty()
                Answer(outcome(response.code, json), detail)
            }
        } catch (e: IOException) {
            Answer(OffWriteOutcome.RETRY)
        }
    }

    companion object {
        fun create(server: String = BuildConfig.OFF_SERVER) = OffUploader(
            OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    chain.proceed(
                        chain.request().newBuilder()
                            .header("User-Agent", "${OpenFoodFactsClient.USER_AGENT_PREFIX}${BuildConfig.VERSION_NAME} (Android; https://github.com/jordanmontt/Blackwater-Macros)")
                            .build(),
                    )
                }
                .build(),
            server,
        )
    }
}

/**
 * Saving and sending: [save] stores the product and asks [schedule] for a run
 * (WorkManager waits for a connection); [sendPending] sends values, then the
 * photo, oldest first, and says whether to try again later.
 */
class OwnProducts(
    val store: OwnProductStore,
    val account: OffAccountStore,
    private val uploader: OffUploader,
    private val appVersion: String = BuildConfig.VERSION_NAME,
    private val appPassword: String = OFF_APP_PASSWORD,
    private val schedule: () -> Unit = {},
) {
    private val mutex = Mutex()

    fun save(product: NewProduct, lang: String, existing: Boolean, photo: ByteArray?, correction: OffCorrection? = null): OwnProduct =
        store.save(product, lang, existing, photo, correction).also { schedule() }

    /** Saves (or with null, forgets) the user's account; what it refused is tried again. */
    fun setAccount(next: OffAccount?) {
        account.set(next)
        store.retryAccountRefusals()
        schedule()
    }

    suspend fun checkAccount(userId: String, password: String): AccountCheck = uploader.check(userId, password)

    /** True when nothing is left to retry (all sent, or refused for good). */
    suspend fun sendPending(): Boolean = mutex.withLock {
        val own = account.account.value
        val userId = own?.userId ?: OFF_APP_USER_ID
        val password = own?.password ?: appPassword
        // Without the app account's password (a build before RELEASING.md's step) everything waits.
        if (password.isEmpty()) return@withLock true
        val appUuid = account.installId()
        for (queued in store.products.value.filter { it.state == OwnProductState.PENDING }.reversed()) {
            val item = store.get(queued.code)?.takeIf { it.state == OwnProductState.PENDING } ?: continue
            val lang = item.lang
            val context = OffWriteContext(lang, userId, password, appVersion, appUuid, item.existing, item.offCorrection?.per)
            if (item.fieldsPending) {
                val sent = item.label
                val answer = uploader.sendFields(offProductFields(sent.toNewProduct(), context))
                when (answer.outcome) {
                    OffWriteOutcome.RETRY -> return@withLock false
                    OffWriteOutcome.ACCOUNT, OffWriteOutcome.REJECTED -> {
                        store.update(item.code) {
                            it.copy(state = if (answer.outcome == OffWriteOutcome.ACCOUNT) OwnProductState.ACCOUNT else OwnProductState.REJECTED, detail = answer.detail)
                        }
                        continue
                    }
                    // An edit made while this was on its way is sent next time.
                    OffWriteOutcome.OK -> store.update(item.code) { if (it.label == sent) it.copy(fieldsPending = false) else it }
                }
            }
            val photo = store.photo(item.code)
            if (photo != null) {
                val answer = uploader.sendPhoto(offImageFields(item.code, context), offLabelImageField(lang), photo)
                when (answer.outcome) {
                    OffWriteOutcome.RETRY -> return@withLock false
                    OffWriteOutcome.ACCOUNT -> {
                        store.update(item.code) { it.copy(state = OwnProductState.ACCOUNT) }
                        continue
                    }
                    // Sent, or refused for good (too small…): either way the photo leaves the phone.
                    OffWriteOutcome.OK, OffWriteOutcome.REJECTED -> store.dropPhoto(item.code)
                }
            } else if (item.hasPhoto) {
                store.update(item.code) { it.copy(hasPhoto = false) }
            }
            store.update(item.code) {
                if (it.state == OwnProductState.PENDING && !it.fieldsPending && !it.hasPhoto) it.copy(state = OwnProductState.SENT) else it
            }
        }
        // Edited while sending: still pending, so one more run.
        store.products.value.none { it.state == OwnProductState.PENDING }
    }
}
