package com.example.printer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import kotlin.math.ceil
import kotlin.math.roundToInt

object ReceiptBitmapRenderer {

  private sealed interface DrawOp {
    val heightPx: Float

    data class BitmapOp(
      val bitmap: Bitmap,
      val leftX: Float,
      val widthPx: Float,
      val drawHeightPx: Float,
      override val heightPx: Float
    ) : DrawOp

    data class TextOp(
      val text: String,
      val sizePx: Float,
      val typeface: Typeface,
      val alignment: ReceiptAlignment,
      override val heightPx: Float
    ) : DrawOp

    data class KeyValueOp(
      val left: String,
      val right: String,
      val sizePx: Float,
      val typeface: Typeface,
      override val heightPx: Float
    ) : DrawOp

    data class LineOp(
      val doubleLine: Boolean,
      override val heightPx: Float
    ) : DrawOp

    data class SpaceOp(override val heightPx: Float) : DrawOp
  }

  fun fitBitmapToDots(source: Bitmap, maxDotsPerLine: Int): Bitmap {
    val maxDots = maxDotsPerLine.coerceIn(128, 832)
    if (source.width <= maxDots || source.width <= 0 || source.height <= 0) {
      return source
    }
    val alignedWidth = ((maxDots / 8) * 8).coerceAtLeast(64)
    val scaledHeight = ((source.height.toFloat() / source.width.toFloat()) * alignedWidth)
      .roundToInt()
      .coerceAtLeast(16)
    return Bitmap.createScaledBitmap(source, alignedWidth, scaledHeight, true)
  }

  fun renderToEscPosBytes(
    receipt: FormattedReceipt,
    printerSettings: PrinterSettings,
    logoBitmap: Bitmap? = null
  ): ByteArray {
    val widthPx = receipt.layoutSpec.dotsPerLine.coerceIn(256, 832)
    val marginPx = 6f
    val contentWidthPx = (widthPx - marginPx * 2).coerceAtLeast(160f)

    val baseTextSizePx = when (printerSettings.fontSize) {
      ReceiptFontSize.SMALL -> 19f
      ReceiptFontSize.NORMAL -> 23f
      ReceiptFontSize.LARGE -> 28f
    }

    val regularTypeface = if (printerSettings.boldText) {
      Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    } else {
      Typeface.MONOSPACE
    }
    val boldTypeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)

    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.BLACK
      isSubpixelText = false
    }

    val drawOps = mutableListOf<DrawOp>()

    for (element in receipt.elements) {
      when (element) {
        is ReceiptElement.LogoHeader -> {
          if (logoBitmap != null && !logoBitmap.isRecycled && logoBitmap.width > 0 && logoBitmap.height > 0) {
            val maxLogoW = minOf(contentWidthPx, widthPx.toFloat() * 0.68f).coerceAtLeast(120f)
            val drawW = minOf(logoBitmap.width.toFloat(), maxLogoW)
            val drawH = (logoBitmap.height.toFloat() / logoBitmap.width.toFloat()) * drawW
            val leftX = ((widthPx - drawW) / 2f).coerceAtLeast(0f)
            drawOps.add(
              DrawOp.BitmapOp(
                bitmap = logoBitmap,
                leftX = leftX,
                widthPx = drawW,
                drawHeightPx = drawH,
                heightPx = drawH + 12f
              )
            )
          }
        }
        is ReceiptElement.BlankLine -> {
          drawOps.add(DrawOp.SpaceOp(baseTextSizePx * 0.6f))
        }
        is ReceiptElement.Divider -> {
          drawOps.add(DrawOp.LineOp(element.doubleLine, if (element.doubleLine) 14f else 10f))
        }
        is ReceiptElement.TextLine -> {
          val sizePx = if (element.largeHeader) baseTextSizePx * 1.32f else baseTextSizePx
          val tf = if (element.bold || element.largeHeader || printerSettings.boldText) boldTypeface else regularTypeface
          paint.textSize = sizePx
          paint.typeface = tf
          val lineHeight = paint.fontSpacing + 3f
          val wrappedLines = wrapByPixels(element.text, paint, contentWidthPx)
          wrappedLines.forEach { line ->
            drawOps.add(DrawOp.TextOp(line, sizePx, tf, element.alignment, lineHeight))
          }
        }
        is ReceiptElement.KeyValueLine -> {
          val tf = if (element.bold || printerSettings.boldText) boldTypeface else regularTypeface
          paint.textSize = baseTextSizePx
          paint.typeface = tf
          val lineHeight = paint.fontSpacing + 3f
          val rightW = paint.measureText(element.right)
          val leftMaxW = (contentWidthPx - rightW - 12f).coerceAtLeast(contentWidthPx * 0.4f)
          val wrappedLeft = wrapByPixels(element.left, paint, leftMaxW)
          if (wrappedLeft.isEmpty()) {
            drawOps.add(DrawOp.KeyValueOp("", element.right, baseTextSizePx, tf, lineHeight))
          } else {
            wrappedLeft.forEachIndexed { idx, part ->
              if (idx < wrappedLeft.lastIndex) {
                drawOps.add(DrawOp.TextOp(part, baseTextSizePx, tf, ReceiptAlignment.LEFT, lineHeight))
              } else {
                drawOps.add(DrawOp.KeyValueOp(part, element.right, baseTextSizePx, tf, lineHeight))
              }
            }
          }
        }
      }
    }

    val totalHeightPx = ceil(drawOps.sumOf { it.heightPx.toDouble() } + 24.0)
      .toInt()
      .coerceIn(64, 16000)

    val bitmap = Bitmap.createBitmap(widthPx, totalHeightPx, Bitmap.Config.ARGB_8888)
    try {
      val canvas = Canvas(bitmap)
      canvas.drawColor(Color.WHITE)

      var yCursor = 8f
      for (op in drawOps) {
        when (op) {
          is DrawOp.BitmapOp -> {
            val destRect = RectF(
              op.leftX,
              yCursor,
              op.leftX + op.widthPx,
              yCursor + op.drawHeightPx
            )
            canvas.drawBitmap(op.bitmap, null, destRect, null)
            yCursor += op.heightPx
          }
          is DrawOp.SpaceOp -> {
            yCursor += op.heightPx
          }
          is DrawOp.LineOp -> {
            paint.strokeWidth = 2f
            val midY = yCursor + op.heightPx * 0.5f
            if (op.doubleLine) {
              canvas.drawLine(marginPx, midY - 2.5f, widthPx - marginPx, midY - 2.5f, paint)
              canvas.drawLine(marginPx, midY + 2.5f, widthPx - marginPx, midY + 2.5f, paint)
            } else {
              canvas.drawLine(marginPx, midY, widthPx - marginPx, midY, paint)
            }
            yCursor += op.heightPx
          }
          is DrawOp.TextOp -> {
            paint.textSize = op.sizePx
            paint.typeface = op.typeface
            val baseline = yCursor - paint.ascent()
            when (op.alignment) {
              ReceiptAlignment.LEFT -> {
                paint.textAlign = Paint.Align.LEFT
                canvas.drawText(op.text, marginPx, baseline, paint)
              }
              ReceiptAlignment.CENTER -> {
                paint.textAlign = Paint.Align.CENTER
                canvas.drawText(op.text, widthPx / 2f, baseline, paint)
              }
              ReceiptAlignment.RIGHT -> {
                paint.textAlign = Paint.Align.RIGHT
                canvas.drawText(op.text, widthPx - marginPx, baseline, paint)
              }
            }
            yCursor += op.heightPx
          }
          is DrawOp.KeyValueOp -> {
            paint.textSize = op.sizePx
            paint.typeface = op.typeface
            val baseline = yCursor - paint.ascent()
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText(op.left, marginPx, baseline, paint)
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText(op.right, widthPx - marginPx, baseline, paint)
            yCursor += op.heightPx
          }
        }
      }

      val builder = EscPosCommandBuilder()
        .initialize()
        .setDensity(printerSettings.printDensity)
        .appendRasterBitmap(bitmap)
        .feedLines(4)
      if (printerSettings.autoCutPaper) {
        builder.cutPartial()
      }
      return builder.build()
    } finally {
      bitmap.recycle()
    }
  }

  private fun wrapByPixels(text: String, paint: Paint, maxWidthPx: Float): List<String> {
    if (text.isBlank()) return emptyList()
    val lines = mutableListOf<String>()
    for (paragraph in text.split('\n')) {
      var remaining = paragraph.trim()
      while (remaining.isNotEmpty()) {
        val count = paint.breakText(remaining, true, maxWidthPx, null).coerceAtLeast(1)
        if (count >= remaining.length) {
          lines.add(remaining)
          break
        }
        val splitIndex = remaining.lastIndexOf(' ', startIndex = count)
        val cutAt = if (splitIndex > 0) splitIndex else count
        lines.add(remaining.substring(0, cutAt).trimEnd())
        remaining = remaining.substring(cutAt).trimStart()
      }
    }
    return lines
  }
}
