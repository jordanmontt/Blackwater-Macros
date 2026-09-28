package com.blackwatermacros.app.data.ai

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import com.blackwatermacros.app.core.AiConfig
import com.blackwatermacros.app.core.AiProvider
import com.blackwatermacros.app.core.DEFAULT_MODELS
import com.blackwatermacros.app.core.isAiConfigured
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Where each AI feature runs: the cloud or the model on the phone. */
enum class AiEngineChoice(val id: String) {
    CLOUD("cloud"),
    DEVICE("device");

    companion object {
        fun fromId(id: String?): AiEngineChoice = entries.firstOrNull { it.id == id } ?: CLOUD
    }
}

/** The engine a feature can use right now, or null when the chosen one is not set up. */
fun usableEngine(choice: AiEngineChoice, cloudReady: Boolean, deviceReady: Boolean): AiEngineChoice? = when (choice) {
    AiEngineChoice.CLOUD -> AiEngineChoice.CLOUD.takeIf { cloudReady }
    AiEngineChoice.DEVICE -> AiEngineChoice.DEVICE.takeIf { deviceReady }
}

/** Ajustes → IA (web `lib/ai/settings.ts`): one key and model per provider. */
data class AiSettings(
    val provider: AiProvider = AiProvider.GEMINI,
    val apiKeys: Map<AiProvider, String> = emptyMap(),
    /** Missing or blank = the provider's default model. */
    val models: Map<AiProvider, String> = emptyMap(),
    /** Only for [AiProvider.CUSTOM] (Ollama, LM Studio…). */
    val baseUrl: String = "",
    /** The coach receives a summary of your data with each question. */
    val coachSeesData: Boolean = true,
    val photoEngine: AiEngineChoice = AiEngineChoice.CLOUD,
    val coachEngine: AiEngineChoice = AiEngineChoice.CLOUD,
) {
    val config: AiConfig
        get() = AiConfig(
            provider = provider,
            apiKey = apiKeys[provider].orEmpty(),
            model = models[provider]?.trim().orEmpty().ifEmpty { DEFAULT_MODELS.getValue(provider) },
            baseUrl = baseUrl,
        )

    val ready: Boolean get() = isAiConfigured(config)
}

/** Encrypts the API keys at rest. */
interface SecretCipher {
    fun encrypt(plain: String): String
    /** Null when the text cannot be read back (e.g. the Keystore key is gone). */
    fun decrypt(encoded: String): String?
}

/**
 * AES-GCM with a key that lives in the Android Keystore and never leaves it.
 * The prefs file is also excluded from backups (`res/xml/backup_rules.xml`, `data_extraction_rules.xml`), so the
 * keys stay on this phone.
 */
class KeystoreCipher : SecretCipher {
    private fun key(): SecretKey {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    override fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val sealed = cipher.iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(sealed, Base64.NO_WRAP)
    }

    override fun decrypt(encoded: String): String? = runCatching {
        val sealed = Base64.decode(encoded, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, sealed, 0, IV_BYTES))
        String(cipher.doFinal(sealed, IV_BYTES, sealed.size - IV_BYTES), Charsets.UTF_8)
    }.getOrNull()

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "blackwater_ai_keys"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
    }
}

/** Local only: not synced, not sent to the Blackwater server, not backed up. */
class AiSettingsStore(private val prefs: SharedPreferences, private val cipher: SecretCipher) {

    constructor(context: Context) : this(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE), KeystoreCipher())

    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<AiSettings> = _settings.asStateFlow()

    val current: AiSettings get() = _settings.value

    fun update(transform: (AiSettings) -> AiSettings) {
        val next = transform(current)
        prefs.edit {
            putString(KEY_PROVIDER, next.provider.id)
            putString(KEY_BASE_URL, next.baseUrl)
            putBoolean(KEY_COACH_DATA, next.coachSeesData)
            putString(KEY_PHOTO_ENGINE, next.photoEngine.id)
            putString(KEY_COACH_ENGINE, next.coachEngine.id)
            for (provider in AiProvider.entries) {
                val key = next.apiKeys[provider].orEmpty()
                if (key != current.apiKeys[provider].orEmpty()) {
                    if (key.isEmpty()) remove(KEY_API_KEY + provider.id) else putString(KEY_API_KEY + provider.id, cipher.encrypt(key))
                }
                val model = next.models[provider].orEmpty()
                if (model.isEmpty()) remove(KEY_MODEL + provider.id) else putString(KEY_MODEL + provider.id, model)
            }
        }
        _settings.value = next
    }

    private fun read(): AiSettings = AiSettings(
        provider = AiProvider.fromId(prefs.getString(KEY_PROVIDER, null)) ?: AiProvider.GEMINI,
        apiKeys = AiProvider.entries.mapNotNull { provider ->
            prefs.getString(KEY_API_KEY + provider.id, null)?.let(cipher::decrypt)?.let { provider to it }
        }.toMap(),
        models = AiProvider.entries.mapNotNull { provider ->
            prefs.getString(KEY_MODEL + provider.id, null)?.let { provider to it }
        }.toMap(),
        baseUrl = prefs.getString(KEY_BASE_URL, null).orEmpty(),
        coachSeesData = prefs.getBoolean(KEY_COACH_DATA, true),
        photoEngine = AiEngineChoice.fromId(prefs.getString(KEY_PHOTO_ENGINE, null)),
        coachEngine = AiEngineChoice.fromId(prefs.getString(KEY_COACH_ENGINE, null)),
    )

    companion object {
        /** Named in `res/xml/backup_rules.xml` and `data_extraction_rules.xml`. */
        const val PREFS_NAME = "ai_settings"
        private const val KEY_PROVIDER = "provider"
        private const val KEY_API_KEY = "apiKey."
        private const val KEY_MODEL = "model."
        private const val KEY_BASE_URL = "baseUrl"
        private const val KEY_COACH_DATA = "coachSeesData"
        private const val KEY_PHOTO_ENGINE = "photoEngine"
        private const val KEY_COACH_ENGINE = "coachEngine"
    }
}
