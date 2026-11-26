package ru.mesh.foss.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

/** Кодирует payload в QR (zxing) с тихой зоной и рисует Bitmap. */
fun encodeQrBitmap(payload: String): Bitmap? = runCatching {
    val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, 0, 0)
    val quiet = 4
    val scale = (1024 / (matrix.width + quiet * 2)).coerceAtLeast(1)
    val side = (matrix.width + quiet * 2) * scale
    val bitmap = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(Color.WHITE)
    val paint = Paint().apply {
        color = Color.BLACK
        isAntiAlias = false
    }
    val offset = quiet * scale
    for (y in 0 until matrix.height) {
        var x = 0
        while (x < matrix.width) {
            if (matrix.get(x, y)) {
                var run = x + 1
                while (run < matrix.width && matrix.get(run, y)) run++
                canvas.drawRect(
                    (offset + x * scale).toFloat(),
                    (offset + y * scale).toFloat(),
                    (offset + run * scale).toFloat(),
                    (offset + (y + 1) * scale).toFloat(),
                    paint
                )
                x = run
            } else {
                x++
            }
        }
    }
    bitmap
}.getOrNull()
