package com.blackwatermacros.app.data.ai.local

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest

/** The download finished but the file is not the published one (it is deleted). */
class ChecksumException : IOException("checksum mismatch")

/**
 * Downloads a large file resumably: bytes go to [partial] and a new attempt
 * continues from its length with an HTTP Range request. Once complete the
 * SHA-256 is checked and the file is moved to [target]; a mismatch deletes it.
 */
class ModelDownloader(private val http: OkHttpClient) {

    suspend fun download(
        url: String,
        target: File,
        partial: File,
        expectedSize: Long,
        expectedSha256: String,
        onProgress: (downloaded: Long, total: Long) -> Unit,
    ) {
        target.parentFile?.mkdirs()
        var offset = if (partial.isFile) partial.length() else 0L
        if (offset > expectedSize) {
            partial.delete()
            offset = 0L
        }
        if (offset < expectedSize) {
            val request = Request.Builder().url(url).apply { if (offset > 0) header("Range", "bytes=$offset-") }.build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                // A server that ignores Range sends everything again: start over.
                val append = offset > 0 && response.code == 206
                if (!append) offset = 0L
                val body = response.body ?: throw IOException("empty body")
                FileOutputStream(partial, append).use { out ->
                    val buffer = ByteArray(DEFAULT_BUFFER)
                    val input = body.byteStream()
                    var downloaded = offset
                    var reported = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        out.write(buffer, 0, read)
                        downloaded += read
                        if (downloaded - reported >= REPORT_EVERY || downloaded == expectedSize) {
                            reported = downloaded
                            onProgress(downloaded, expectedSize)
                        }
                    }
                }
            }
        }
        if (partial.length() != expectedSize) throw IOException("incomplete: ${partial.length()} of $expectedSize")
        if (sha256(partial) != expectedSha256.lowercase()) {
            partial.delete()
            throw ChecksumException()
        }
        if (!partial.renameTo(target)) throw IOException("could not move the model into place")
        onProgress(expectedSize, expectedSize)
    }

    private suspend fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER)
            while (true) {
                currentCoroutineContext().ensureActive()
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val DEFAULT_BUFFER = 256 * 1024
        const val REPORT_EVERY = 8L * 1024 * 1024
    }
}
