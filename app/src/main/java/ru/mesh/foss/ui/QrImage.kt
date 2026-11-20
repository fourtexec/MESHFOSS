package ru.mesh.foss.ui

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** QR-изображение строки (одноразовой ссылки входа), если её удалось закодировать. */
@Composable
fun QrCodeImage(payload: String, size: Dp = 120.dp) {
    val bitmap = remember(payload) { encodeQrBitmap(payload) }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = payload,
            modifier = Modifier.size(size),
        )
    }
}
