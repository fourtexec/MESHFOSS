@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package ru.mesh.foss.feature.settings

import ru.mesh.foss.core.*
import ru.mesh.foss.data.*
import ru.mesh.foss.ui.*
import ru.mesh.foss.R
import ru.mesh.foss.feature.profile.*

import android.content.Intent
import android.net.Uri
import androidx.biometric.BiometricManager
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import ru.mesh.foss.lock.PinManager

@Composable
fun SettingsScreen(store: SessionStore, client: MeshClient, profile: JSONObject?, onLogout: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var refreshing by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var checkingUpdates by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var updateMessage by remember { mutableStateOf<String?>(null) }
    val tokenInfo = MeshCore.describeToken(store.token)

    // Оформление и язык хранятся в общих настройках (mesh_settings) и применяются
    // глобально через CompositionLocal из MeshRankerTheme.
    val prefs = remember(context) { context.getSharedPreferences("mesh_settings", android.content.Context.MODE_PRIVATE) }
    val applyTheme = LocalAppTheme.current
    var theme by remember { mutableStateOf(prefs.getString("theme_id", AppTheme.DEFAULT_ID) ?: AppTheme.DEFAULT_ID) }
    var customName by remember { mutableStateOf(prefs.getString("custom_theme_name", "") ?: "") }
    var customColors by remember {
        mutableStateOf(prefs.getString("theme_colors", "67E8F9,071923,102A3A") ?: "67E8F9,071923,102A3A")
    }
    var hapticsEnabled by remember { mutableStateOf(prefs.getBoolean("haptics_enabled", true)) }
    var moskvenokLog by remember { mutableStateOf(MeshLog.enabled) }
    var showMoskvenokLog by remember { mutableStateOf(false) }
    var pinEnabled by remember { mutableStateOf(PinManager.isEnabled(context)) }
    var biometricEnabled by remember { mutableStateOf(PinManager.isBiometricEnabled(context)) }
    var showPinDialog by remember { mutableStateOf(false) }
    var pinDraft by remember { mutableStateOf("") }
    var pinRepeat by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }
    val biometricAvailable = remember(context) {
        BiometricManager.from(context).canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.BIOMETRIC_WEAK
        ) == BiometricManager.BIOMETRIC_SUCCESS
    }
    var updateChannel by remember {
        mutableStateOf(
            prefs.getString("update_channel", UpdateChecker.CHANNEL_RELEASES) ?: UpdateChecker.CHANNEL_RELEASES
        )
    }
    var section by rememberSaveable { mutableStateOf("appearance") }
    val noUpdatesText = Words.UpToDate.text
    val myThemeLabel = Words.MyTheme.text

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        val sections = listOf("appearance" to Words.Appearance.text, "app" to Words.Application.text, "account" to Words.Account.text)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            sections.forEach { (id, title) ->
                item(key = id) {
                    FilterChip(selected = section == id, onClick = { section = id }, label = { Text(title) })
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        key(section) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                when (section) {
                    "appearance" -> {
                        GlassCard(Modifier.fillMaxWidth()) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(Words.Language.text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                                LanguagePicker()
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        GlassCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                Text(
                                    Words.Design.text,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.height(4.dp))
                                val design = LocalDesign.current
                                val setDesign = LocalDesignSetter.current
                                listOf(
                                    Triple(Design.PLAIN, Words.DesignPlain.text, Words.DesignPlainHint.text),
                                    Triple(Design.GLASS, Words.DesignGlass.text, Words.DesignGlassHint.text),
                                    Triple(Design.LIQUID, Words.DesignLiquid.text, Words.DesignLiquidHint.text),
                                    Triple(Design.FROSTED, Words.DesignFrosted.text, Words.DesignFrostedHint.text)
                                ).forEach { (id, title, hint) ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth()
                                            .selectable(
                                                selected = design == id,
                                                role = Role.RadioButton
                                            ) { setDesign(id) }
                                            .padding(vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(selected = design == id, onClick = null)
                                        Spacer(Modifier.width(10.dp))
                                        Column {
                                            Text(title, style = MaterialTheme.typography.bodyMedium)
                                            Text(
                                                hint,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    Words.Appearance.text,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.height(4.dp))
                                AppTheme.ids.chunked(2).forEach { pair ->
                                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        pair.forEach { id ->
                                            val selected = theme == id
                                            val palette = if (id == "dynamic") MaterialTheme.colorScheme else AppTheme.colors(id, customColors)
                                            Row(
                                                Modifier.weight(1f).fillMaxHeight().heightIn(min = 52.dp)
                                                    .selectionPane(MaterialTheme.shapes.medium, if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)
                                                    .selectable(selected, role = Role.RadioButton) { theme = id; applyTheme(id) }
                                                    .padding(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(Modifier.size(22.dp).background(palette.primary, RoundedCornerShape(50)), contentAlignment = Alignment.Center) {
                                                    if (selected) Text("✓", color = palette.onPrimary, style = MaterialTheme.typography.labelSmall)
                                                }
                                                Spacer(Modifier.width(8.dp))
                                                Text(AppTheme.label(id, customName.ifBlank { myThemeLabel }), style = MaterialTheme.typography.labelLarge)
                                            }
                                        }
                                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                                    }
                                    Spacer(Modifier.height(8.dp))
                                }
                                if (theme == "custom") {
                                    OutlinedTextField(
                                        value = customName,
                                        onValueChange = { customName = it; prefs.edit().putString("custom_theme_name", it).apply() },
                                        label = { Text(Words.ThemeName.text) },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )
                                    OutlinedTextField(
                                        value = customColors,
                                        onValueChange = { customColors = it },
                                        isError = !AppTheme.validCustom(customColors),
                                        label = { Text(Words.AccentBackgroundSurfaceHex.text) },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )
                                    Text(
                                        Words.CustomPaletteExample.text,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    OutlinedButton(
                                        onClick = {
                                            prefs.edit().putString("theme_colors", customColors).apply()
                                            applyTheme("custom")
                                        },
                                        enabled = AppTheme.validCustom(customColors),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(Words.ApplyTheme.text)
                                    }
                                }
                            }
                        }
                    }
                    "app" -> {
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    Words.Application.text,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.height(6.dp))
                                SettingsRow(Words.Server.text, MeshCore.HOST.removePrefix("https://"))
                                SettingsRow(
                                    Words.Storage.text,
                                    if (store.isEncrypted()) Words.Encrypted.text else Words.Private.text
                                )
                                SettingsRow(Words.NameCache.text, Words.Enabled.text)
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(Words.PinCode.text, style = MaterialTheme.typography.bodyMedium)
                                        Text(
                                            if (pinEnabled) Words.PinEnabledHint.text else Words.PinDisabledHint.text,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Switch(
                                        checked = pinEnabled,
                                        onCheckedChange = { value ->
                                            if (value) {
                                                pinDraft = ""
                                                pinRepeat = ""
                                                pinError = null
                                                showPinDialog = true
                                            } else {
                                                PinManager.clear(context)
                                                pinEnabled = false
                                                biometricEnabled = false
                                            }
                                        }
                                    )
                                }
                                if (pinEnabled) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text(Words.Biometrics.text, style = MaterialTheme.typography.bodyMedium)
                                            if (!biometricAvailable) {
                                                Text(
                                                    Words.BiometricUnavailable.text,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        Switch(
                                            checked = biometricEnabled,
                                            enabled = biometricAvailable,
                                            onCheckedChange = { value ->
                                                biometricEnabled = value
                                                PinManager.setBiometricEnabled(context, value)
                                            }
                                        )
                                    }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        Words.Vibration.text,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Switch(
                                        checked = hapticsEnabled,
                                        onCheckedChange = { value ->
                                            hapticsEnabled = value
                                            prefs.edit().putBoolean("haptics_enabled", value).apply()
                                        }
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            Words.MoskvenokLog.text,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            Words.MoskvenokLogHint.text,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Switch(
                                        checked = moskvenokLog,
                                        onCheckedChange = { value ->
                                            moskvenokLog = value
                                            MeshLogStore.setEnabled(value)
                                        }
                                    )
                                }
                                if (moskvenokLog) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(onClick = { showMoskvenokLog = true }) {
                                            Text(Words.ShowMoskvenokLog.text)
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                MeshLogStore.clear()
                                                showMoskvenokLog = false
                                            }
                                        ) {
                                            Text(Words.ClearMoskvenokLog.text)
                                        }
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    Words.UpdateChannel.text,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.height(4.dp))
                                // Content-lambda ButtonGroup не @Composable — переводим заранее.
                                val releasesLabel = Words.Stable.text
                                val prereleasesLabel = Words.PreReleases.text
                                ButtonGroup(
                                    overflowIndicator = { ButtonGroupDefaults.OverflowIndicator(it) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    toggleableItem(
                                        checked = updateChannel == UpdateChecker.CHANNEL_RELEASES,
                                        label = releasesLabel,
                                        onCheckedChange = {
                                            updateChannel = UpdateChecker.CHANNEL_RELEASES
                                            prefs.edit().putString("update_channel", UpdateChecker.CHANNEL_RELEASES).apply()
                                        },
                                        weight = 1f
                                    )
                                    toggleableItem(
                                        checked = updateChannel == UpdateChecker.CHANNEL_PRERELEASES,
                                        label = prereleasesLabel,
                                        onCheckedChange = {
                                            updateChannel = UpdateChecker.CHANNEL_PRERELEASES
                                            prefs.edit().putString("update_channel", UpdateChecker.CHANNEL_PRERELEASES).apply()
                                        },
                                        weight = 1f
                                    )
                                }
                                Text(
                                    Words.IncludesBetaRcAndNightlyBuildsCheckManually.text,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(10.dp))
                                OutlinedButton(
                                    onClick = {
                                        if (!checkingUpdates) {
                                            checkingUpdates = true
                                            updateMessage = null
                                            scope.launch {
                                                val found = withContext(Dispatchers.IO) {
                                                    UpdateChecker.check(context, includeNightly = true)
                                                }
                                                store.lastUpdateCheck = System.currentTimeMillis()
                                                updateInfo = found
                                                updateMessage = if (found == null) noUpdatesText else null
                                                checkingUpdates = false
                                            }
                                        }
                                    },
                                    enabled = !checkingUpdates,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    if (checkingUpdates) LoadingIndicator(modifier = Modifier.size(20.dp))
                                    else Text(Words.CheckForUpdates.text)
                                }
                                updateMessage?.let {
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        it,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                    "account" -> {
                        ProfileBody(store, client, profile, showToken = false)
                        Spacer(Modifier.height(8.dp))
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(Words.MeshToken.text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.height(6.dp))
                                SettingsRow(Words.MaskedToken.text, tokenInfo.optString("mask").ifBlank { "—" })
                                SettingsRow(
                                    Words.Type.text,
                                    when (tokenInfo.optString("kind")) {
                                        "jwt" -> "JWT"
                                        "opaque" -> Words.OpaqueToken.text
                                        else -> "—"
                                    }
                                )
                                if (tokenInfo.has("length")) SettingsRow(Words.Length.text, tokenInfo.optInt("length").toString())
                                if (tokenInfo.has("expiresAt")) {
                                    SettingsRow(Words.Expires.text, MeshCore.dateShort(
                                        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                                            .format(java.util.Date(tokenInfo.optLong("expiresAt")))
                                    ))
                                }
                                Spacer(Modifier.height(8.dp))
                                Button(
                                    onClick = {
                                        refreshing = true
                                        message = null
                                        error = null
                                        scope.launch {
                                            val fresh = withContext(Dispatchers.IO) { client.refreshToken() }
                                            if (fresh.isNullOrBlank()) {
                                                error = Words.MeshCouldNotRenewYourTokenSignIn.text
                                            } else {
                                                store.token = fresh
                                                client.token = fresh
                                                message = Words.TokenRefreshed.text
                                            }
                                            refreshing = false
                                        }
                                    },
                                    enabled = !refreshing,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    if (refreshing) LoadingIndicator(modifier = Modifier.size(20.dp))
                                    else Text(Words.RefreshToken.text)
                                }
                                message?.let {
                                    Spacer(Modifier.height(6.dp))
                                    Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                                }
                                error?.let {
                                    Spacer(Modifier.height(6.dp))
                                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(Words.Account.text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    if (profile != null) Words.ProfileLoaded.text
                                    else Words.ProfileNotLoadedYet.text,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Spacer(Modifier.height(10.dp))
                                OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) {
                                    Text(Words.SignOutAndDeleteToken.text)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showPinDialog) {
        AlertDialog(
            modifier = Modifier.dialogGlass(),
            containerColor = Color.Transparent,
            onDismissRequest = { showPinDialog = false },
            title = { Text(Words.SetPinCode.text) },
            text = {
                Column {
                    Text(
                        Words.PinDigitsHint.text,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = pinDraft,
                        onValueChange = {
                            if (it.length <= PinManager.MAX_LENGTH && it.all(Char::isDigit)) {
                                pinDraft = it
                                pinError = null
                            }
                        },
                        label = { Text(Words.Pin.text) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = pinRepeat,
                        onValueChange = {
                            if (it.length <= PinManager.MAX_LENGTH && it.all(Char::isDigit)) {
                                pinRepeat = it
                                pinError = null
                            }
                        },
                        label = { Text(Words.RepeatPin.text) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    pinError?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        when {
                            pinDraft.length < PinManager.MIN_LENGTH -> pinError = Words.PinTooShort.text
                            pinDraft != pinRepeat -> pinError = Words.PinMismatch.text
                            else -> {
                                if (PinManager.setPin(context, pinDraft)) {
                                    pinEnabled = true
                                    biometricEnabled = false
                                    showPinDialog = false
                                } else {
                                    pinError = Words.PinTooShort.text
                                }
                            }
                        }
                    }
                ) { Text(Words.Save.text) }
            },
            dismissButton = {
                TextButton(onClick = { showPinDialog = false }) { Text(Words.Cancel.text) }
            }
        )
    }

    updateInfo?.let { info ->
        AlertDialog(
            modifier = Modifier.dialogGlass(),
            containerColor = Color.Transparent,
            onDismissRequest = { updateInfo = null },
            title = { Text(Words.UpdateAvailable.text) },
            text = {
                Text(Words.UpdateVersionAvailable(info.versionName))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        updateInfo = null
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info.downloadUrl)))
                        }
                    }
                ) {
                    Text(Words.Download.text)
                }
            },
            dismissButton = {
                TextButton(onClick = { updateInfo = null }) {
                    Text(Words.Later.text)
                }
            }
        )
    }

    if (showMoskvenokLog) {
        val logText = remember { MeshLogStore.read(context) }
        val clipboard = LocalClipboardManager.current
        AlertDialog(
            modifier = Modifier.dialogGlass(),
            containerColor = Color.Transparent,
            onDismissRequest = { showMoskvenokLog = false },
            title = { Text(Words.MoskvenokLog.text) },
            text = {
                if (logText.isBlank()) {
                    Text(Words.MoskvenokLogEmpty.text, style = MaterialTheme.typography.bodyMedium)
                } else {
                    Column(
                        Modifier
                            .heightIn(max = 420.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            logText,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showMoskvenokLog = false }) {
                    Text(Words.Close.text)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { clipboard.setText(AnnotatedString(logText)) },
                    enabled = logText.isNotBlank()
                ) {
                    Text(Words.CopyMoskvenokLog.text)
                }
            }
        )
    }
}

@Composable
private fun SettingsRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(.8f)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
    }
}
