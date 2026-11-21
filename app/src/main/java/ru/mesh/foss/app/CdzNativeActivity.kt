package ru.mesh.foss.app

import ru.mesh.foss.core.UiLanguage
import ru.mesh.foss.core.Words
import ru.mesh.foss.feature.cdz.CdzNativeScreen
import ru.mesh.foss.feature.cdz.CdzNativeViewModel

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider

/**
 * Окно нативного прохождения ЦДЗ.
 *
 * Activity — только хост: состояние, сеть и черновики живут в
 * [CdzNativeViewModel], интерфейс — в [CdzNativeScreen]. Тема и дизайн
 * берутся из общего `MeshRankerTheme`, поэтому окно выглядит так же, как
 * остальные экраны приложения.
 */
class CdzNativeActivity : FragmentActivity() {

    companion object {
        private const val EXTRA_URL = "url"
        private const val EXTRA_TITLE = "title"

        fun intent(context: Context, url: String, title: String?): Intent =
            Intent(context, CdzNativeActivity::class.java)
                .putExtra(EXTRA_URL, url)
                .putExtra(EXTRA_TITLE, title)
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(UiLanguage.context(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = intent.getStringExtra(EXTRA_TITLE)?.takeIf { it.isNotBlank() }
            ?: Words.CdzNativeTitle.text
        val viewModel = ViewModelProvider(
            this,
            CdzNativeViewModel.Factory(
                application = application,
                url = intent.getStringExtra(EXTRA_URL).orEmpty(),
            ),
        )[CdzNativeViewModel::class.java]
        setContent {
            MeshRankerTheme {
                CdzNativeScreen(
                    viewModel = viewModel,
                    onClose = { finish() },
                )
            }
        }
    }
}
