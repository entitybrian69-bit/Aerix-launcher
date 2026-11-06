package net.kdt.pojavlaunch.aerix.resourcepack

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import java.io.IOException

object AndroidPackImageScaler : ResourcePackOptimizer.ImageScaler {
    override fun scalePng(image: ByteArray, maxDimension: Int): ByteArray? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(image, 0, image.size, bounds)
        if (bounds.outMimeType != "image/png" || bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        if (bounds.outWidth <= maxDimension && bounds.outHeight <= maxDimension) return null
        if (bounds.outWidth > 16384 || bounds.outHeight > 16384) throw IOException("Image dimensions exceed safe limits")
        var sample = 1
        while ((bounds.outWidth + sample - 1) / sample > maxDimension || (bounds.outHeight + sample - 1) / sample > maxDimension) sample *= 2
        val bitmap = BitmapFactory.decodeByteArray(image, 0, image.size, BitmapFactory.Options().apply {
            inSampleSize = sample
            inScaled = false
        }) ?: throw IOException("Could not decode a menu texture")
        return try {
            ByteArrayOutputStream().use { out ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) throw IOException("Could not save a smaller texture")
                out.toByteArray()
            }
        } finally { bitmap.recycle() }
    }
}
