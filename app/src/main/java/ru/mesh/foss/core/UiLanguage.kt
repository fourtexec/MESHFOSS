package ru.mesh.foss.core

import ru.mesh.foss.R
import ru.mesh.foss.ui.LocalAppLanguageSetter

import android.content.Context
import android.content.res.Configuration
import android.view.ContextThemeWrapper
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

internal object UiLanguage {
    val names = mapOf("ru" to "Русский", "en" to "English", "hu" to "Magyar", "es" to "Español", "fr" to "Français")
    var code by mutableStateOf("ru")
        private set
    val locale: Locale get() = Locale.forLanguageTag(code)

    fun select(value: String?) {
        code = value?.takeIf { it in names } ?: "ru"
    }

    fun context(base: Context): Context {
        val config = Configuration(base.resources.configuration).apply { setLocale(UiLanguage.locale) }
        return ContextThemeWrapper(base, R.style.Theme_MeshRanker).apply { applyOverrideConfiguration(config) }
    }
}

@Composable
internal fun LanguagePicker() {
    val select = LocalAppLanguageSetter.current
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) { Text(UiLanguage.names.getValue(UiLanguage.code) + " ▾") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            UiLanguage.names.forEach { (code, name) ->
                DropdownMenuItem(text = { Text(name) }, onClick = {
                    open = false
                    select(code)
                })
            }
        }
    }
}
