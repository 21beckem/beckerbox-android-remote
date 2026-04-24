package com.beckersuite.box

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.net.http.SslError
import android.os.Bundle
import android.webkit.SslErrorHandler
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

// ─────────────────────────────────────────────────────────────────────────────
// SET THIS to your web app URL (the one your local server proxies)
// ─────────────────────────────────────────────────────────────────────────────
private const val WEB_APP_URL = "https://192.168.0.151/"
// ─────────────────────────────────────────────────────────────────────────────

private const val PERMISSIONS_REQUEST = 1

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var bleBridge: BleBridge
    private var pendingDeepLinkUrl: String? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        WebView.setWebContentsDebuggingEnabled(true)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        pendingDeepLinkUrl = intent?.dataString

        webView = findViewById(R.id.webview)

        webView.settings.apply {
            javaScriptEnabled                = true
            domStorageEnabled                = true
            allowFileAccess                  = false
            mediaPlaybackRequiresUserGesture = false
        }

        // Inject the native BLE bridge as window.AndroidBle in the page
        bleBridge = BleBridge(this, webView)
        webView.addJavascriptInterface(bleBridge, "AndroidBle")
        webView.addJavascriptInterface(VibrateBridge(this), "AndroidVibrate")
        webView.post {
            webView.evaluateJavascript("window.inAndroidApp = true;", null)
        }

        webView.webViewClient = object : WebViewClient() {

            @SuppressLint("WebViewClientOnReceivedSslError")
            override fun onReceivedSslError(
                view: WebView,
                handler: SslErrorHandler,
                error: SslError
            ) {
                handler.proceed()
            }
            // Keep all navigation inside the WebView
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = false

            override fun onPageFinished(view: WebView, url: String) {
                // Override navigator.vibrate so existing web-app code works unchanged.
                // The WebView blocks navigator.vibrate natively, so we route it through
                // AndroidVibrate.vibrate() which calls the Android Vibrator API directly.
                view.evaluateJavascript("""
                    (function() {
                        navigator.vibrate = function(pattern) {
                            window.AndroidVibrate.vibrate(
                                Array.isArray(pattern)
                                    ? JSON.stringify(pattern)
                                    : String(pattern)
                            );
                            return true;
                        };
                    })();
                """.trimIndent(), null)
            }
        }

        requestBlePermissions()
    }

    private fun requestBlePermissions() {
        val needed = arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
        ).filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (needed.isEmpty()) loadApp()
        else ActivityCompat.requestPermissions(this, needed.toTypedArray(), PERMISSIONS_REQUEST)
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
        // If the activity was launched via deep link, load that URL directly
        val deepLink = intent?.data?.toString()
        webView.loadUrl(if (!deepLink.isNullOrEmpty()) deepLink else WEB_APP_URL)
    }

    override fun onDestroy() {
        bleBridge.disconnect()
        super.onDestroy()
    }
}