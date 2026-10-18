package com.raj.hub

import android.content.ContentValues
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Message
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Base64
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.webkit.WebViewAssetLoader
import java.io.ByteArrayInputStream
import java.io.IOException

/** Runs one saved web app full screen. Press Back twice quickly to return to the lobby. */
class AppActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ID = "app_id"

        // Lets a page save files it creates (backups, PDFs) to Downloads. Android's WebView cannot do this alone.
        private const val DOWNLOAD_HOOK_JS = """
(function () {
  if (window.__rajHook) return;
  window.__rajHook = true;
  function send(url, name) {
    fetch(url).then(function (r) { return r.blob(); }).then(function (b) {
      var fr = new FileReader();
      fr.onloadend = function () {
        var s = String(fr.result);
        RajBridge.save(name || 'download', b.type || 'application/octet-stream', s.substring(s.indexOf(',') + 1));
      };
      fr.readAsDataURL(b);
    });
  }
  var origClick = HTMLAnchorElement.prototype.click;
  HTMLAnchorElement.prototype.click = function () {
    if (this.hasAttribute('download') && /^(blob:|data:)/.test(this.href)) {
      send(this.href, this.getAttribute('download'));
      return;
    }
    return origClick.apply(this, arguments);
  };
  document.addEventListener('click', function (e) {
    var a = e.target && e.target.closest ? e.target.closest('a[download]') : null;
    if (a && /^(blob:|data:)/.test(a.href)) {
      e.preventDefault();
      send(a.href, a.getAttribute('download'));
    }
  }, true);
})();
"""
    }

    private lateinit var store: AppStore
    private lateinit var app: AppInfo
    private lateinit var root: FrameLayout
    private lateinit var web: WebView
    private lateinit var loader: WebViewAssetLoader

    private var customView: View? = null
    private var customCallback: WebChromeClient.CustomViewCallback? = null
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var lastBack = 0L
    private var ready = false

    private val chooser = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val cb = fileCallback
        fileCallback = null
        cb?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(r.resultCode, r.data))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = AppStore(this)

        val id = intent.getStringExtra(EXTRA_ID)
        val found = AppRegistry.load(this).firstOrNull { it.id == id }
        if (found == null || !store.isInstalled(found)) {
            startActivity(Intent(this, LobbyActivity::class.java))
            finish()
            return
        }
        app = found

        val lp = window.attributes
        lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        window.attributes = lp

        root = FrameLayout(this)
        root.setBackgroundColor(Color.parseColor("#0B1020"))
        web = WebView(this)
        root.addView(web, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        setContentView(root)

        loader = WebViewAssetLoader.Builder()
            .setDomain(app.host)
            .addPathHandler("/", WebViewAssetLoader.InternalStoragePathHandler(this, store.siteDir(app)))
            .build()

        setupWebView()
        hideSystemBars()
        ready = true

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (customView != null) {
                    hideCustomView()
                    return
                }
                val now = SystemClock.elapsedRealtime()
                if (now - lastBack < 2000) {
                    goToLobby()
                } else {
                    lastBack = now
                    Toast.makeText(this@AppActivity, "Press Back again to switch app", Toast.LENGTH_SHORT).show()
                }
            }
        })

        web.loadUrl("https://${app.host}/${app.entry}")
        checkForUpdate()
    }

    private fun setupWebView() {
        val s = web.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        @Suppress("DEPRECATION")
        s.databaseEnabled = true
        s.mediaPlaybackRequiresUserGesture = false
        s.allowFileAccess = false
        s.allowContentAccess = false
        s.useWideViewPort = true
        s.setSupportMultipleWindows(true)
        s.javaScriptCanOpenWindowsAutomatically = true
        s.textZoom = 100

        web.setBackgroundColor(Color.parseColor("#0B1020"))
        web.addJavascriptInterface(Bridge(), "RajBridge")

        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                val req = request ?: return null
                val u = req.url
                val local = loader.shouldInterceptRequest(u)
                if (local != null) return local
                if (u.host == app.host) {
                    return WebResourceResponse("text/plain", "UTF-8", 404, "Not Found", HashMap<String, String>(), ByteArrayInputStream(ByteArray(0)))
                }
                if (req.method == "GET") {
                    val saved = store.extResponse(app, u.toString())
                    if (saved != null) return saved
                }
                return null
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val u = request?.url ?: return false
                if (u.host == app.host) return false
                return openExternal(u)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                view?.evaluateJavascript(DOWNLOAD_HOOK_JS, null)
            }
        }

        web.webChromeClient = object : WebChromeClient() {
            override fun onShowCustomView(view: View?, callback: WebChromeClient.CustomViewCallback?) {
                if (view == null) return
                if (customView != null) {
                    callback?.onCustomViewHidden()
                    return
                }
                customView = view
                customCallback = callback
                root.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                web.visibility = View.GONE
            }

            override fun onHideCustomView() {
                hideCustomView()
            }

            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: WebChromeClient.FileChooserParams?
            ): Boolean {
                if (filePathCallback == null || fileChooserParams == null) return false
                fileCallback?.onReceiveValue(null)
                fileCallback = filePathCallback
                return try {
                    chooser.launch(fileChooserParams.createIntent())
                    true
                } catch (e: Exception) {
                    fileCallback = null
                    false
                }
            }

            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message?
            ): Boolean {
                val msg = resultMsg ?: return false
                val transport = msg.obj as? WebView.WebViewTransport ?: return false
                val temp = WebView(this@AppActivity)
                temp.webViewClient = ExternalLinkClient()
                transport.webView = temp
                msg.sendToTarget()
                return true
            }
        }
    }

    /** Links that go outside the app (WhatsApp, websites) open in the phone's own apps. */
    private inner class ExternalLinkClient : WebViewClient() {
        private var handled = false

        private fun handle(view: WebView?, uri: Uri?) {
            if (handled || uri == null) return
            val scheme = uri.scheme ?: return
            if (scheme == "about" || scheme == "blob" || scheme == "javascript") return
            handled = true
            openExternal(uri)
            view?.let { v -> v.post { v.destroy() } }
        }

        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
            handle(view, request?.url)
            return true
        }

        override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
            if (url != null) handle(view, Uri.parse(url))
        }
    }

    private fun openExternal(uri: Uri): Boolean {
        val scheme = uri.scheme ?: return false
        val ok = scheme == "http" || scheme == "https" || scheme == "mailto" || scheme == "tel" || scheme == "sms" || scheme == "geo"
        if (!ok) return false
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (e: Exception) {
            Toast.makeText(this, "No app found to open this link", Toast.LENGTH_SHORT).show()
        }
        return true
    }

    private fun hideCustomView() {
        val v = customView ?: return
        root.removeView(v)
        customView = null
        customCallback?.onCustomViewHidden()
        customCallback = null
        web.visibility = View.VISIBLE
    }

    private fun goToLobby() {
        Prefs.setLastApp(this, null)
        startActivity(Intent(this, LobbyActivity::class.java))
        finish()
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, true)
        val c = WindowInsetsControllerCompat(window, window.decorView)
        c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        c.hide(WindowInsetsCompat.Type.systemBars())
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && ready) hideSystemBars()
    }

    /** Quietly refreshes the saved copy from GitHub (at most every 6 hours) when online. */
    private fun checkForUpdate() {
        if (!Net.isOnline(this)) return
        val sixHours = 6L * 60 * 60 * 1000
        if (System.currentTimeMillis() - Prefs.lastCheck(this, app.id) < sixHours) return
        Bg.run {
            try {
                val changed = store.sync(app) { }
                Prefs.setLastCheck(this, app.id)
                if (changed) {
                    runOnUiThread {
                        if (!isFinishing && !isDestroyed) {
                            Toast.makeText(this, "${app.name} updated. It will use the new version next time you open it.", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            } catch (e: Exception) {
                // offline or GitHub unreachable: keep using the saved copy
            }
        }
    }

    /** Called from the page's JavaScript to save a file into Downloads/RajHub. */
    private inner class Bridge {
        @JavascriptInterface
        fun save(name: String, mime: String, base64: String) {
            Bg.run {
                try {
                    val bytes = Base64.decode(base64, Base64.DEFAULT)
                    val safe = name.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "download" }
                    val values = ContentValues()
                    values.put(MediaStore.Downloads.DISPLAY_NAME, safe)
                    values.put(MediaStore.Downloads.MIME_TYPE, if (mime.isBlank()) "application/octet-stream" else mime)
                    values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/RajAppStore")
                    values.put(MediaStore.Downloads.IS_PENDING, 1)
                    val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                        ?: throw IOException("Could not create file")
                    val out = contentResolver.openOutputStream(uri) ?: throw IOException("Could not open file")
                    out.use { it.write(bytes) }
                    val done = ContentValues()
                    done.put(MediaStore.Downloads.IS_PENDING, 0)
                    contentResolver.update(uri, done, null, null)
                    runOnUiThread {
                        Toast.makeText(this@AppActivity, "Saved to Downloads/RajAppStore/$safe", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        Toast.makeText(this@AppActivity, "Could not save the file", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (ready) web.onResume()
    }

    override fun onPause() {
        if (ready) web.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        if (ready) {
            root.removeAllViews()
            web.destroy()
        }
        super.onDestroy()
    }
}
