package com.beckersuite.box

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.json.JSONObject

// ─────────────────────────────────────────────────────────────────────────────
// SET THIS to your web app URL (the one your local server proxies)
// ─────────────────────────────────────────────────────────────────────────────
private const val WEB_APP_URL = "http://192.168.0.151:5173/"
// ─────────────────────────────────────────────────────────────────────────────

private const val PERMISSIONS_REQUEST = 1

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var bleBridge: BleBridge
    private var pendingDeepLinkUrl: String? = null
    private var webAppLoaded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        pendingDeepLinkUrl = intent?.dataString

        webView = findViewById(R.id.webview)

        webView.settings.apply {
            javaScriptEnabled        = true
            domStorageEnabled        = true
            allowFileAccess          = false
            mediaPlaybackRequiresUserGesture = false
        }

        // Inject the native BLE bridge as window.AndroidBle in the page
        bleBridge = BleBridge(this, webView)
        webView.addJavascriptInterface(bleBridge, "AndroidBle")
        webView.post {
            webView.evaluateJavascript("window.inAndroidApp = true;", null)
        }

        webView.webViewClient = object : WebViewClient() {
            // Keep all navigation inside the WebView
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = false

            override fun onPageFinished(view: WebView?, url: String?) {
                webAppLoaded = true
                flushDeepLinkToJs()
            }
        }

        requestBlePermissions()
    }

    private fun requestBlePermissions() {
        val permissions = arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
        )

        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isEmpty()) {
            loadApp()
        } else {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), PERMISSIONS_REQUEST)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSIONS_REQUEST) {
            loadApp() // load regardless — BleBridge will surface an error if BT was denied
        }
    }

    private fun loadApp() {
        webView.loadUrl(WEB_APP_URL)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingDeepLinkUrl = intent.dataString
        flushDeepLinkToJs()
    }

    private fun flushDeepLinkToJs() {
        val url = pendingDeepLinkUrl ?: return
        if (!webAppLoaded) return

        val quotedUrl = JSONObject.quote(url)
        webView.evaluateJavascript(
            "if(typeof window.__onDeepLink==='function')window.__onDeepLink($quotedUrl);",
            null,
        )
        pendingDeepLinkUrl = null
    }

    override fun onDestroy() {
        bleBridge.disconnect()
        super.onDestroy()
    }
}