package com.blackwatermacros.app.data.ai.local

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.annotation.StringRes
import com.blackwatermacros.app.BuildConfig
import com.blackwatermacros.app.R
import java.io.File

/**
 * A model the user can download to run on the phone (D6). All are LiteRT-LM
 * files published by Google's `litert-community` on Hugging Face: open (not
 * gated), Apache-2.0, good in Spanish. Checked against the SHA-256 below.
 */
data class LocalModelSpec(
    val id: String,
    val name: String,
    val repo: String,
    val fileName: String,
    val sizeBytes: Long,
    val sha256: String,
    /** Below this the model does not fit next to the system and other apps. */
    val minRamBytes: Long,
    /** Reads photos (else: coach only, photos use the cloud). */
    val vision: Boolean,
    @StringRes val noteRes: Int,
) {
    val url: String get() = "https://huggingface.co/litert-community/$repo/resolve/main/$fileName"
}

object LocalModels {
    val GEMMA_4_E2B = LocalModelSpec(
        id = "gemma-4-e2b",
        name = "Gemma 4 E2B",
        repo = "gemma-4-E2B-it-litert-lm",
        fileName = "gemma-4-E2B-it.litertlm",
        sizeBytes = 2_588_147_712L,
        sha256 = "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c",
        minRamBytes = 5_500_000_000L,
        vision = true,
        noteRes = R.string.local_model_note_e2b,
    )
    val GEMMA_4_E4B = LocalModelSpec(
        id = "gemma-4-e4b",
        name = "Gemma 4 E4B",
        repo = "gemma-4-E4B-it-litert-lm",
        fileName = "gemma-4-E4B-it.litertlm",
        sizeBytes = 3_659_530_240L,
        sha256 = "0b2a8980ce155fd97673d8e820b4d29d9c7d99b8fa6806f425d969b145bd52e0",
        minRamBytes = 7_500_000_000L,
        vision = true,
        noteRes = R.string.local_model_note_e4b,
    )
    val QWEN_3_1_7B = LocalModelSpec(
        id = "qwen3-1.7b",
        name = "Qwen3 1.7B",
        repo = "Qwen3-1.7B",
        fileName = "Qwen3-1.7B_dynamic_wi4b32_afp32.litertlm",
        sizeBytes = 977_184_032L,
        sha256 = "2eeffef7b51bc3e1225ea69fe7aa5f417397934b56a5b6c20cc068d6fd2c918b",
        minRamBytes = 3_500_000_000L,
        vision = false,
        noteRes = R.string.local_model_note_qwen,
    )

    val ALL = listOf(GEMMA_4_E2B, GEMMA_4_E4B, QWEN_3_1_7B)
    val DEFAULT = GEMMA_4_E2B

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
    }
}

enum class DeviceSupport { SUPPORTED, UNSUPPORTED_ABI, NOT_ENOUGH_RAM }

/**
 * LiteRT-LM ships arm64 code (x86_64 only for emulators). A debug build skips
 * the RAM check so the flow can be tried on an emulator.
 */
fun deviceSupport(abis: List<String>, totalRamBytes: Long, minRamBytes: Long, debug: Boolean): DeviceSupport = when {
    "arm64-v8a" !in abis && !(debug && "x86_64" in abis) -> DeviceSupport.UNSUPPORTED_ABI
    totalRamBytes < minRamBytes && !debug -> DeviceSupport.NOT_ENOUGH_RAM
    else -> DeviceSupport.SUPPORTED
}

fun totalRamBytes(context: Context): Long {
    val memory = ActivityManager.MemoryInfo()
    (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(memory)
    return memory.totalMem
}

fun deviceSupport(context: Context, model: LocalModelSpec): DeviceSupport =
    deviceSupport(Build.SUPPORTED_ABIS.toList(), totalRamBytes(context), model.minRamBytes, BuildConfig.DEBUG)
