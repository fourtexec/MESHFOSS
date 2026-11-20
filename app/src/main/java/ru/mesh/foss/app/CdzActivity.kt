package ru.mesh.foss.app

import ru.mesh.foss.R
import ru.mesh.foss.core.*
import ru.mesh.foss.data.*

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity

/**
 * Окно прохождения ЦДЗ.
 *
 * Ссылка `/api/launcher/v1/launch` — цепочка OAuth-редиректов на cookie-сессии
 * school.mos.ru (docs/mesh-cdz-api.md §7.2, §12.4). Bearer-токена приложения для
 * неё мало, поэтому кладём `aupd_token` в cookie WebView и открываем ссылку здесь.
 *
 * Ссылка одноразовая: окно никогда не перезагружает стартовый адрес.
 */
class CdzActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_URL = "url"
        private const val EXTRA_TITLE = "title"
        private const val MENU_BROWSER = 1

        fun intent(context: Context, url: String, title: String?): Intent =
            Intent(context, CdzActivity::class.java)
                .putExtra(EXTRA_URL, url)
                .putExtra(EXTRA_TITLE, title)

        /** Хосты, которым нужен cookie сессии МЭШ. */
        private val SESSION_HOSTS = listOf("https://school.mos.ru", "https://uchebnik.mos.ru")
    }

    private lateinit var webView: WebView

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(UiLanguage.context(newBase))
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = intent.getStringExtra(EXTRA_TITLE)?.takeIf { it.isNotBlank() } ?: Words.CdzWindowTitle.text
        }

        val url = intent.getStringExtra(EXTRA_URL).orEmpty()
        if (url.isBlank()) {
            finish()
            return
        }

        webView = findViewById(R.id.loginWebView)
        val cookies = CookieManager.getInstance()
        cookies.setAcceptCookie(true)
        cookies.setAcceptThirdPartyCookies(webView, true)
        val store = SessionStore(this)
        // Долгоживущие куки QR-входа (Ltpatoken2, session-cookie…) помогают OAuth
        // к партнёру пройти без повторного входа; aupd_token — текущий токен.
        val jar = store.authCookies?.let { raw ->
            runCatching {
                val json = org.json.JSONObject(raw)
                json.keys().asSequence().associateWith { json.optString(it) }
            }.getOrNull()
        }.orEmpty()
        jar.forEach { (name, value) ->
            if (name.isBlank() || value.isBlank() || name == "aupd_token") return@forEach
            (SESSION_HOSTS + "https://login.mos.ru").forEach { host ->
                cookies.setCookie(host, "$name=$value; Path=/; Secure")
            }
        }
        store.token?.takeIf { it.isNotBlank() }?.let { token ->
            SESSION_HOSTS.forEach { host ->
                cookies.setCookie(host, "aupd_token=$token; Path=/; Secure")
            }
        }
        cookies.flush()

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = true
            displayZoomControls = false
            // Веб открывает материал через window.open — ведём его в этом же окне.
            setSupportMultipleWindows(false)
            javaScriptCanOpenWindowsAutomatically = true
            userAgentString = MeshCore.USER_AGENT
        }
        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val scheme = request.url.scheme?.lowercase()
                if (scheme == "http" || scheme == "https") return false
                // intent:, mailto:, tel: и т. п. — системе.
                runCatching { startActivity(Intent(Intent.ACTION_VIEW, request.url)) }
                return true
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                val title = view?.title?.takeIf { it.isNotBlank() && !it.startsWith("http") }
                if (title != null) supportActionBar?.subtitle = title
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Не возвращаемся на одноразовую ссылку запуска — просто закрываем окно.
                val back = webView.copyBackForwardList()
                val previous = if (back.currentIndex > 0) back.getItemAtIndex(back.currentIndex - 1)?.url else null
                if (webView.canGoBack() && previous != null && !previous.contains("/api/launcher/") &&
                    !previous.contains("/oauth/")
                ) {
                    webView.goBack()
                } else {
                    finish()
                }
            }
        })

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState)
        } else {
            webView.loadUrl(url)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (::webView.isInitialized) webView.saveState(outState)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(Menu.NONE, MENU_BROWSER, Menu.NONE, Words.CdzOpenInBrowser.text)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == MENU_BROWSER) {
            // Открываем текущую страницу партнёра (не стартовую одноразовую ссылку).
            val current = webView.url.orEmpty()
            if (current.isNotBlank() && !current.contains("/api/launcher/")) {
                runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(current))) }
                    .onFailure { Toast.makeText(this, Words.CouldNotOpenLink.text, Toast.LENGTH_SHORT).show() }
            }
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onDestroy() {
        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.destroy()
        }
        super.onDestroy()
    }
}
