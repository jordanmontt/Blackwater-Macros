package com.blackwatermacros.app.data.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.blackwatermacros.app.core.AiImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Longest side of a photo sent to the AI (web `lib/ai/images.ts`). */
const val MAX_PHOTO_SIDE = 1024
const val MAX_PHOTOS = 5

/** The size that fits [maxSide] on the longest side, never enlarging. */
fun fitWithin(width: Int, height: Int, maxSide: Int = MAX_PHOTO_SIDE): Pair<Int, Int> {
    val scale = minOf(1.0, maxSide.toDouble() / max(width, height))
    return max(1, (width * scale).roundToInt()) to max(1, (height * scale).roundToInt())
}

/** A downscaled photo: the JPEG for the AI and a bitmap for the thumbnail. */
class ScaledPhoto(val image: AiImage, val preview: Bitmap)

/**
 * Reads a photo into memory as a small JPEG (~80 %, ≤ 1024 px, upright).
 * Nothing is written anywhere: the caller deletes any camera file right after
 * (photos are never stored).
 */
object PhotoCodec {
    fun downscale(context: Context, uri: Uri, quality: Int = 80): ScaledPhoto {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        // With inJustDecodeBounds the decoder returns null by design: only the sizes are filled in.
        val stream = resolver.openInputStream(uri) ?: throw IOException("unreadable")
        stream.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("not an image")

        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_PHOTO_SIDE) sample *= 2
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: throw IOException("not an image")

        val rotation = runCatching {
            resolver.openInputStream(uri)?.use { ExifInterface(it).rotationDegrees() } ?: 0
        }.getOrDefault(0)
        val (width, height) = fitWithin(decoded.width, decoded.height)
        val matrix = Matrix().apply {
            postScale(width.toFloat() / decoded.width, height.toFloat() / decoded.height)
            if (rotation != 0) postRotate(rotation.toFloat())
        }
        val scaled = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        if (scaled !== decoded) decoded.recycle()

        val jpeg = ByteArrayOutputStream().use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
            out.toByteArray()
        }
        return ScaledPhoto(AiImage("image/jpeg", Base64.encodeToString(jpeg, Base64.NO_WRAP)), scaled)
    }

    /** [downscale] off the main thread; null when it is not a readable image. [deleteAfter]: a camera file, deleted at once. */
    suspend fun read(context: Context, uri: Uri, deleteAfter: File? = null): ScaledPhoto? = withContext(Dispatchers.IO) {
        try {
            runCatching { downscale(context.applicationContext, uri) }
                .onFailure { Log.w("PhotoCodec", "Could not read a photo", it) }
                .getOrNull()
        } finally {
            deleteAfter?.delete()
        }
    }

    private fun ExifInterface.rotationDegrees(): Int = when (
        getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    ) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90
        ExifInterface.ORIENTATION_ROTATE_180 -> 180
        ExifInterface.ORIENTATION_ROTATE_270 -> 270
        else -> 0
    }
}
