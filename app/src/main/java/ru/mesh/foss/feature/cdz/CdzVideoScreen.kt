@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package ru.mesh.foss.feature.cdz

import ru.mesh.foss.R
import ru.mesh.foss.core.Words
import ru.mesh.foss.ui.Design
import ru.mesh.foss.ui.LiquidTopBar
import ru.mesh.foss.ui.LocalDesign

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import org.json.JSONObject

/**
 * Полноэкранный просмотр видео-атома материала ДЗ.
 *
 * `materialId` — UUID из `/cms/materials/{uuid}/launch`; сам MP4 и cookie
 * подтягивает [CdzRemoteVideo] через сессионный клиент. [browserUrl] — исходная
 * одноразовая ссылка запуска: она нужна только как резервный путь в браузер.
 */
@Composable
fun CdzVideoScreen(
    materialId: String,
    title: String?,
    browserUrl: String?,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    val context = LocalContext.current
    val block = remember(materialId, title, browserUrl) {
        JSONObject().apply {
            put("atomic_id", materialId)
            title?.takeIf { it.isNotBlank() }?.let { put("description", it) }
            browserUrl?.takeIf { it.isNotBlank() }?.let { put("launch_url", it) }
        }
    }
    val heading = title?.takeIf { it.isNotBlank() } ?: Words.CdzWatch.text

    val liquid = LocalDesign.current == Design.LIQUID
    Scaffold(
        containerColor = if (LocalDesign.current == Design.PLAIN) MaterialTheme.colorScheme.background else androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            if (liquid) {
                LiquidTopBar(
                    title = heading,
                    actions = {
                        if (!browserUrl.isNullOrBlank()) {
                            IconButton(onClick = { openInBrowser(context, browserUrl) }) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_browser),
                                    contentDescription = Words.CdzBrowser.text,
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onClose) {
                            Text("‹", style = MaterialTheme.typography.headlineMedium)
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = { Text(heading) },
                    navigationIcon = {
                        IconButton(onClick = onClose) {
                            Text("‹", style = MaterialTheme.typography.headlineMedium)
                        }
                    },
                    actions = {
                        if (!browserUrl.isNullOrBlank()) {
                            IconButton(onClick = { openInBrowser(context, browserUrl) }) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_browser),
                                    contentDescription = Words.CdzBrowser.text,
                                )
                            }
                        }
                    },
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
        ) {
            CdzRemoteVideo(
                block = block,
                modifier = Modifier.fillMaxWidth(),
                showTitle = false,
            )
        }
    }
}

private fun openInBrowser(context: Context, url: String) {
    if (url.isBlank()) return
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}
