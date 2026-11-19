@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package ru.mesh.foss

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

@Composable
fun ProfileScreen(store: SessionStore, client: MeshClient, initialProfile: JSONObject?, onLogout: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(Words.Profile.text, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        ProfileBody(store, client, initialProfile)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onLogout, modifier = Modifier.fillMaxWidth()) { Text(Words.SignOut.text) }
    }
}

/** Общая часть профиля: используется и на экране «Профиль», и в объединённых настройках. */
@Composable
fun ProfileBody(
    store: SessionStore,
    client: MeshClient,
    initialProfile: JSONObject?,
    showToken: Boolean = true
) {
    val scope = rememberCoroutineScope()
    var profile by remember(initialProfile) { mutableStateOf(initialProfile) }
    var loading by remember { mutableStateOf(initialProfile == null) }
    var error by remember { mutableStateOf<String?>(null) }

    fun load() {
        loading = true
        error = null
        scope.launch {
            try {
                profile = withContext(Dispatchers.IO) { client.fetchProfile() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = MeshCore.describeError(e)
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(initialProfile) {
        if (initialProfile != null) {
            profile = initialProfile
            loading = false
        } else {
            load()
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        when {
            loading -> Box(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                LoadingIndicator()
            }
            error != null -> {
                Text(error!!, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(8.dp))
                Button(onClick = { load() }) { Text(Words.Retry.text) }
            }
            profile != null -> {
                val p = profile!!
                val children = (0 until (p.optJSONArray("children")?.length() ?: 0))
                    .mapNotNull { p.optJSONArray("children")?.optJSONObject(it) }
                val child = p.optJSONObject("defaultChild") ?: children.firstOrNull()
                val parent = p.optJSONObject("parent")

                ProfileSection(Words.Student.text) {
                    ProfileRow(Words.FullName.text, child?.optString("name").orEmpty().ifBlank { "—" })
                    ProfileRow(Words.Class.text, child?.optString("className").orEmpty().ifBlank { "—" })
                    val school = child?.optString("schoolName").orEmpty().ifBlank {
                        child?.optString("schoolShort").orEmpty()
                    }
                    ProfileRow(Words.School.text, school.ifBlank { "—" })
                    val studentEmail = child?.optString("email").orEmpty()
                    val studentPhone = MeshCore.formatPhone(child?.optString("phone"))
                    if (studentEmail.isNotBlank()) ProfileRow(Words.Email.text, studentEmail)
                    if (studentPhone.isNotBlank()) ProfileRow(Words.Phone.text, studentPhone)
                }

                if (children.size > 1) {
                    Spacer(Modifier.height(8.dp))
                    ProfileSection(Words.Children.text) {
                        children.forEach { c ->
                            ProfileRow(c.optString("name").ifBlank { Words.Student.text }, c.optString("className"))
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                ProfileSection(Words.Parent.text) {
                    ProfileRow(Words.FullName.text, parent?.optString("name").orEmpty().ifBlank { "—" })
                    val email = parent?.optString("email").orEmpty()
                    val phone = MeshCore.formatPhone(parent?.optString("phone"))
                    if (email.isNotBlank()) ProfileRow(Words.Email.text, email)
                    if (phone.isNotBlank()) ProfileRow(Words.Phone.text, phone)
                }

                if (showToken) {
                    Spacer(Modifier.height(8.dp))
                    ProfileSection(Words.MeshToken.text) {
                        val info = MeshCore.describeToken(store.token)
                        ProfileRow(Words.MaskedToken.text, info.optString("mask").ifBlank { "—" })
                        ProfileRow(
                            Words.Type.text,
                            when (info.optString("kind")) {
                                "jwt" -> "JWT"
                                "opaque" -> Words.OpaqueToken.text
                                else -> "—"
                            }
                        )
                        if (info.has("expiresAt")) {
                            val expired = info.optBoolean("expired")
                            ProfileRow(
                                Words.Expires.text,
                                MeshCore.dateShort(
                                    java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                                        .format(java.util.Date(info.optLong("expiresAt")))
                                ) + if (expired) Words.Expired.text else ""
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(6.dp))
            content()
        }
    }
}

@Composable
private fun ProfileRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(86.dp)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
    }
}
