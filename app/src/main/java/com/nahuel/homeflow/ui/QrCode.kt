package com.nahuel.homeflow.ui

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

/** Renders a QR code for [content] as a Compose Image. Dependency: zxing core. */
@Composable
fun QrCode(content: String, sizePx: Int = 480, modifier: Modifier = Modifier) {
    val bitmap = remember(content, sizePx) { generate(content, sizePx) }
    if (bitmap != null) Image(bitmap = bitmap.asImageBitmap(), contentDescription = "QR-Code", modifier = modifier)
}

private fun generate(content: String, size: Int): Bitmap? = runCatching {
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size)
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    for (x in 0 until size) for (y in 0 until size) {
        bmp.setPixel(x, y, if (matrix[x, y]) AndroidColor.BLACK else AndroidColor.WHITE)
    }
    bmp
}.getOrNull()
