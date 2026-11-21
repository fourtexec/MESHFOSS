package ru.mesh.foss.app

import ru.mesh.foss.core.UiLanguage
import ru.mesh.foss.feature.cdz.CdzVideoScreen

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.fragment.app.FragmentActivity

/** Окно нативного просмотра видео-материала из домашнего задания. */
class CdzVideoActivity : FragmentActivity() {

    companion object {
        private const val EXTRA_MATERIAL_ID = "material_id"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_BROWSER_URL = "browser_url"

        fun intent(
            context: Context,
            materialId: String,
            title: String?,
            browserUrl: String?,
        ): Intent =
            Intent(context, CdzVideoActivity::class.java)
                .putExtra(EXTRA_MATERIAL_ID, materialId)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_BROWSER_URL, browserUrl)
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(UiLanguage.context(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val materialId = intent.getStringExtra(EXTRA_MATERIAL_ID).orEmpty()
        if (materialId.isBlank()) {
            finish()
            return
        }
        setContent {
            MeshRankerTheme {
                CdzVideoScreen(
                    materialId = materialId,
                    title = intent.getStringExtra(EXTRA_TITLE),
                    browserUrl = intent.getStringExtra(EXTRA_BROWSER_URL),
                    onClose = { finish() },
                )
            }
        }
    }
}
