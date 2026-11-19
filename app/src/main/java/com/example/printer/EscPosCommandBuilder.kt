package com.example.printer

import android.graphics.Bitmap
import android.graphics.Color
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

class EscPosCommandBuilder {
  private val buffer = ByteArrayOutputStream(2048)

  fun initialize(): EscPosCommandBuilder = apply {
    writeBytes(0x1B, 0x40) // ESC @
  }

  fun setCodePageCp437(): EscPosCommandBuilder = apply {
    writeBytes(0x1B, 0x74, 0x00) // ESC t 0
  }

  fun setAlignment(alignment: ReceiptAlignment): EscPosCommandBuilder = apply {
    writeBytes(0x1B, 0x61, alignment.escPosCode) // ESC a n
  }

  fun setBold(enabled: Boolean): EscPosCommandBuilder = apply {
    writeBytes(0x1B, 0x45, if (enabled) 1 else 0) // ESC E n
  }

  fun setFontSize(size: ReceiptFontSize): EscPosCommandBuilder = apply {
    when (size) {
      ReceiptFontSize.SMALL -> {
        writeBytes(0x1B, 0x4D, 1) // ESC M 1 (Font B)
        writeBytes(0x1D, 0x21, 0x00) // GS ! 0
      }
      ReceiptFontSize.NORMAL -> {
        writeBytes(0x1B, 0x4D, 0) // ESC M 0 (Font A)
        writeBytes(0x1D, 0x21, 0x00) // GS ! 0
      }
      ReceiptFontSize.LARGE -> {
        writeBytes(0x1B, 0x4D, 0) // ESC M 0 (Font A)
        writeBytes(0x1D, 0x21, 0x01) // Double height for readability without losing column width
      }
    }
  }

  fun setDoubleSizeHeader(enabled: Boolean): EscPosCommandBuilder = apply {
    writeBytes(0x1B, 0x4D, 0)
    writeBytes(0x1D, 0x21, if (enabled) 0x11 else 0x00)
  }

  /**
   * Standard ESC/POS GS ( K density tuning. Only sent for non-default density levels.
   * Printers that do not implement GS ( K ignore it cleanly.
   */
  fun setDensity(density: PrintDensity): EscPosCommandBuilder = apply {
    if (density != PrintDensity.NORMAL) {
      buffer.write(
        byteArrayOf(
          0x1D, 0x28, 0x4B, 0x02, 0x00, 0x31, density.escPosLevel
        )
      )
    }
  }

  fun writeLine(text: String = ""): EscPosCommandBuilder = apply {
    val sanitized = sanitizeForEscPosText(text)
    buffer.write(sanitized.toByteArray(StandardCharsets.US_ASCII))
    buffer.write('\n'.code)
  }

  fun feedLines(lines: Int): EscPosCommandBuilder = apply {
    val clamped = lines.coerceIn(0, 255)
    if (clamped > 0) {
      writeBytes(0x1B, 0x64, clamped) // ESC d n
    }
  }

  fun cutPartial(): EscPosCommandBuilder = apply {
    writeBytes(0x1D, 0x56, 0x42, 0x00) // GS V 66 0
  }

  /**
   * Encodes a monochrome/grayscale Android Bitmap using ESC/POS GS v 0 in safe 128-row chunks
   * so limited thermal printer SRAM buffers never overflow.
   */
  fun appendRasterBitmap(bitmap: Bitmap, stripHeight: Int = 128): EscPosCommandBuilder = apply {
    val width = bitmap.width
    val height = bitmap.height
    if (width <= 0 || height <= 0) return@apply

    val widthBytes = (width + 7) / 8
    val xL = widthBytes and 0xFF
    val xH = (widthBytes shr 8) and 0xFF
    val rowPixels = IntArray(width)

    var startY = 0
    while (startY < height) {
      val chunkHeight = minOf(stripHeight, height - startY)
      val yL = chunkHeight and 0xFF
      val yH = (chunkHeight shr 8) and 0xFF

      writeBytes(0x1D, 0x76, 0x30, 0x00, xL, xH, yL, yH)

      for (rowOffset in 0 until chunkHeight) {
        val y = startY + rowOffset
        bitmap.getPixels(rowPixels, 0, width, 0, y, width, 1)
        for (byteIndex in 0 until widthBytes) {
          var slice = 0
          for (bit in 0 until 8) {
            val x = byteIndex * 8 + bit
            if (x < width) {
              val pixel = rowPixels[x]
              val alpha = Color.alpha(pixel)
              if (alpha > 64) {
                val lum = (Color.red(pixel) * 299 + Color.green(pixel) * 587 + Color.blue(pixel) * 114) / 1000
                if (lum < 165) {
                  slice = slice or (0x80 ushr bit)
                }
              }
            }
          }
          buffer.write(slice)
        }
      }
      startY += chunkHeight
    }
  }

  fun build(): ByteArray = buffer.toByteArray()

  private fun writeBytes(vararg bytes: Int) {
    for (b in bytes) {
      buffer.write(b and 0xFF)
    }
  }

  companion object {
    fun sanitizeForEscPosText(input: String): String {
      if (input.isEmpty()) return ""
      val replaced = input
        .replace("₹", "Rs.")
        .replace("×", "x")
        .replace("•", "-")
        .replace("—", "-")
        .replace("–", "-")
        .replace("’", "'")
        .replace("‘", "'")
        .replace("“", "\"")
        .replace("”", "\"")

      val sb = StringBuilder(replaced.length)
      for (ch in replaced) {
        when {
          ch == '\t' -> sb.append(' ')
          ch.code in 0x20..0x7E -> sb.append(ch)
          ch == '\n' || ch == '\r' -> sb.append(' ')
          // Drop unsupported multi-byte / regional characters in pure ASCII text mode
          // (Users needing regional scripts or ₹ symbol can pick Raster/Graphics mode)
        }
      }
      return sb.toString()
    }
  }
}
