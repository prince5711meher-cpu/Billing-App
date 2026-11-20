package com.example.printer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import com.example.R
import kotlin.math.roundToInt

interface ReceiptLogoProvider {
  fun getLogoBitmap(maxDotsPerLine: Int): Bitmap?
}

class AndroidReceiptLogoProvider(
  context: Context
) : ReceiptLogoProvider {
  private val appContext = context.applicationContext
  private val lock = Any()
  private var sourceBitmap: Bitmap? = null
  private val scaledCache = LinkedHashMap<Int, Bitmap>(4, 0.75f, true)

  override fun getLogoBitmap(maxDotsPerLine: Int): Bitmap? = synchronized(lock) {
    val targetDots = computeTargetWidthDots(maxDotsPerLine)
    scaledCache[targetDots]?.let { cached ->
      if (!cached.isRecycled) return@synchronized cached
    }

    val src = loadSourceBitmap() ?: return@synchronized null
    if (src.width <= 0 || src.height <= 0) return@synchronized null

    val targetHeight = ((src.height.toFloat() / src.width.toFloat()) * targetDots)
      .roundToInt()
      .coerceAtLeast(32)

    val scaled = Bitmap.createScaledBitmap(src, targetDots, targetHeight, true)
    val whiteBacked = ensureWhiteOpaqueBackground(scaled)
    if (scaled !== whiteBacked && scaled !== src) {
      scaled.recycle()
    }

    if (scaledCache.size >= 4) {
      val eldestKey = scaledCache.keys.firstOrNull()
      if (eldestKey != null) {
        scaledCache.remove(eldestKey)
      }
    }
    scaledCache[targetDots] = whiteBacked
    return@synchronized whiteBacked
  }

  private fun loadSourceBitmap(): Bitmap? {
    sourceBitmap?.let { if (!it.isRecycled) return it }
    val opts = BitmapFactory.Options().apply {
      inScaled = false
      inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    val decoded = runCatching {
      BitmapFactory.decodeResource(appContext.resources, R.drawable.meher_receipt_logo, opts)
    }.getOrNull()
    sourceBitmap = decoded
    return decoded
  }

  companion object {
    fun computeTargetWidthDots(maxDotsPerLine: Int): Int {
      val safeMax = maxDotsPerLine.coerceIn(256, 832)
      val raw = (safeMax * 0.62f).roundToInt().coerceIn(192, minOf(safeMax, 480))
      val aligned = (raw / 8) * 8
      return aligned.coerceIn(128, safeMax)
    }

    private fun ensureWhiteOpaqueBackground(src: Bitmap): Bitmap {
      val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
      val canvas = Canvas(out)
      canvas.drawColor(Color.WHITE)
      canvas.drawBitmap(src, 0f, 0f, null)
      return out
    }
  }
}
