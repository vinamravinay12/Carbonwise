package com.rivi.carbonwise.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.core.graphics.scale
import java.io.ByteArrayOutputStream

/** An attached image ready to send to Gemini, plus a downscaled [preview] bitmap for the UI. */
data class AttachedImage(
    val base64: String,
    val mime: String,
    val preview: Bitmap,
)

/**
 * Loads an image from a gallery URI or camera Bitmap, downscales it (so uploads stay small),
 * and JPEG-encodes it to base64 for the Gemini REST API. Runs synchronously — call from an
 * IO coroutine.
 */
object ImageEncoder {

    private const val MAX_DIM = 1024
    private const val QUALITY = 85

    fun fromUri(context: Context, uri: Uri): AttachedImage? {
        val bitmap = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it)
        } ?: return null
        return encode(bitmap)
    }

    fun encode(bitmap: Bitmap): AttachedImage {
        val scaled = scaleDown(bitmap)
        val bytes = ByteArrayOutputStream().use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)
            out.toByteArray()
        }
        return AttachedImage(
            base64 = Base64.encodeToString(bytes, Base64.NO_WRAP),
            mime = "image/jpeg",
            preview = scaled,
        )
    }

    private fun scaleDown(bitmap: Bitmap): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= MAX_DIM) return bitmap
        val factor = MAX_DIM.toFloat() / longest
        return bitmap.scale((bitmap.width * factor).toInt(), (bitmap.height * factor).toInt())
    }
}
