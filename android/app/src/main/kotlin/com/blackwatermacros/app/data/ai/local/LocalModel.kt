package com.blackwatermacros.app.data.ai.local

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.annotation.StringRes
import com.blackwatermacros.app.BuildConfig
import com.blackwatermacros.app.R
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A model the user can download to run on the phone (D6): a LiteRT-LM file on
 * Hugging Face, checked against its SHA-256. The built-in ones below are
 * joined by the catalog on the Blackwater site (`LocalModelCatalog`), so new
 * models appear without a new version of the app.
 */
data class LocalModelSpec(
    val id: String,
    val name: String,
    /** Always on huggingface.co (the catalog refuses anything else). */
    val url: String,
    val fileName: String,
    val sizeBytes: Long,
    val sha256: String,
    /**
     * The RAM a phone should have, as sold («8 GB»). With less it may run out of
     * memory (Android then closes the app): the download is still allowed, with a
     * warning. Measured on the CPU on a Pixel 10a («8 GB»): Gemma 4 E4B peaked at ~4.2 GB.
     */
    val recommendedPhoneGb: Int,
    /** Reads photos (else: coach only, photos use the cloud). */
    val vision: Boolean,
    /** The built-in models' note, translated in `strings.xml`… */
    @StringRes val noteRes: Int? = null,
    /** …or the catalog's, by language code (`en` is the fallback). */
    val notes: Map<String, String> = emptyMap(),
    /** Still recognised when installed, but no longer offered for download. */
    val hidden: Boolean = false,
) {
    fun note(language: String): String? = notes[language] ?: notes["en"]

    /** A phone sold as «8 GB» reports ~7.5–7.8 GB to apps (the rest is reserved). */
    val recommendedRamBytes: Long get() = recommendedPhoneGb * 880_000_000L

    /**
     * On first use the CPU engine writes a working copy of the weights to the cache
     * (~65 % of the file for Gemma 4 E4B); it is what keeps the RAM low.
     */
    val workingCopyBytes: Long get() = (sizeBytes * 0.65).toLong()
}

private fun huggingFace(repo: String, fileName: String) = "https://huggingface.co/litert-community/$repo/resolve/main/$fileName"

object LocalModels {
    val GEMMA_4_E2B = LocalModelSpec(
        id = "gemma-4-e2b",
        name = "Gemma 4 E2B",
        url = huggingFace("gemma-4-E2B-it-litert-lm", "gemma-4-E2B-it.litertlm"),
        fileName = "gemma-4-E2B-it.litertlm",
        sizeBytes = 2_588_147_712L,
        sha256 = "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c",
        recommendedPhoneGb = 6,
        vision = true,
        noteRes = R.string.local_model_note_e2b,
    )
    val GEMMA_4_E4B = LocalModelSpec(
        id = "gemma-4-e4b",
        name = "Gemma 4 E4B",
        url = huggingFace("gemma-4-E4B-it-litert-lm", "gemma-4-E4B-it.litertlm"),
        fileName = "gemma-4-E4B-it.litertlm",
        sizeBytes = 3_659_530_240L,
        sha256 = "0b2a8980ce155fd97673d8e820b4d29d9c7d99b8fa6806f425d969b145bd52e0",
        recommendedPhoneGb = 8,
        vision = true,
        noteRes = R.string.local_model_note_e4b,
    )
    val QWEN_3_1_7B = LocalModelSpec(
        id = "qwen3-1.7b",
        name = "Qwen3 1.7B",
        url = huggingFace("Qwen3-1.7B", "Qwen3-1.7B_dynamic_wi4b32_afp32.litertlm"),
        fileName = "Qwen3-1.7B_dynamic_wi4b32_afp32.litertlm",
        sizeBytes = 977_184_032L,
        sha256 = "2eeffef7b51bc3e1225ea69fe7aa5f417397934b56a5b6c20cc068d6fd2c918b",
        recommendedPhoneGb = 4,
        vision = false,
        noteRes = R.string.local_model_note_qwen,
    )

    val BUILT_IN = listOf(GEMMA_4_E2B, GEMMA_4_E4B, QWEN_3_1_7B)
    val DEFAULT = GEMMA_4_E2B

    private val _catalog = MutableStateFlow(BUILT_IN)

    /** Built-in models plus the site's catalog (which wins for the same id). */
    val catalog: StateFlow<List<LocalModelSpec>> = _catalog.asStateFlow()

    /** Every known model, hidden ones included (an installed model must stay recognisable). */
    val ALL: List<LocalModelSpec> get() = _catalog.value

    /** The ones offered for download. */
    val choices: List<LocalModelSpec> get() = ALL.filterNot { it.hidden }

    /** The site's catalog, in its order, then the built-in models it does not mention. */
    fun useCatalog(remote: List<LocalModelSpec>) {
        _catalog.value = remote + BUILT_IN.filter { builtIn -> remote.none { it.id == builtIn.id } }
    }

    fun byId(id: String?): LocalModelSpec? = ALL.firstOrNull { it.id == id }

    private fun dir(context: Context) = File(context.noBackupFilesDir, "models")

    fun file(context: Context, model: LocalModelSpec): File = File(dir(context), model.fileName)

    fun partialFile(context: Context, model: LocalModelSpec): File = File(dir(context), "${model.fileName}.part")

    fun isDownloaded(context: Context, model: LocalModelSpec): Boolean =
        file(context, model).let { it.isFile && it.length() == model.sizeBytes }

    /** Only one model is kept at a time: the one on disk, if any. */
    fun installed(context: Context): LocalModelSpec? = ALL.firstOrNull { isDownloaded(context, it) }

    /** Removes every model file except [keep] (a new download replaces the old model). */
    fun deleteAllExcept(context: Context, keep: LocalModelSpec?) {
        dir(context).listFiles()?.forEach { file ->
            val belongsToKept = keep != null && file.name.startsWith(keep.fileName)
            if (!belongsToKept) file.delete()
        }
        // The engine's working copies in the cache («<model file>_…xnnpack_cache», GPU caches).
        context.cacheDir.listFiles()?.forEach { file ->
            val ofAModel = ALL.any { file.name.startsWith(it.fileName) }
            val ofKept = keep != null && file.name.startsWith(keep.fileName)
            if (ofAModel && !ofKept) file.delete()
        }
    }
}

/** [LOW_RAM]: it can be downloaded, with a warning that the phone may run out of memory. */
enum class DeviceSupport { SUPPORTED, UNSUPPORTED_ABI, LOW_RAM }

/** LiteRT-LM ships arm64 code (x86_64 only for emulators, in debug builds). */
fun deviceSupport(abis: List<String>, totalRamBytes: Long, recommendedRamBytes: Long, debug: Boolean): DeviceSupport = when {
    "arm64-v8a" !in abis && !(debug && "x86_64" in abis) -> DeviceSupport.UNSUPPORTED_ABI
    totalRamBytes < recommendedRamBytes -> DeviceSupport.LOW_RAM
    else -> DeviceSupport.SUPPORTED
}

fun totalRamBytes(context: Context): Long {
    val memory = ActivityManager.MemoryInfo()
    (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(memory)
    return memory.totalMem
}

fun deviceSupport(context: Context, model: LocalModelSpec): DeviceSupport =
    deviceSupport(Build.SUPPORTED_ABIS.toList(), totalRamBytes(context), model.recommendedRamBytes, BuildConfig.DEBUG)
