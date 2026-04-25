package com.beckersuite.box

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.net.http.SslError
import android.os.Bundle
import android.view.View
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

// ─────────────────────────────────────────────────────────────────────────────
// SET THIS to your web app URL (the one your local server proxies)
// ─────────────────────────────────────────────────────────────────────────────
private const val WEB_APP_URL_PROD = "https://box.beckersuite.com/app-landing.html"
private const val WEB_APP_URL_DEV = "https://r.box.beckersuite.com/o/?192.168.0.151/?p=/v2.2/"
private val WEB_APP_URL: String
    get() = if (BuildConfig.DEBUG) WEB_APP_URL_DEV else WEB_APP_URL_PROD
// ─────────────────────────────────────────────────────────────────────────────

private const val PERMISSIONS_REQUEST = 1

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var loadingIndicator: View
    private lateinit var bleBridge: BleBridge
    private var pendingDeepLinkUrl: String? = null
    private var isWebRuntimeShuttingDown = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        WebView.setWebContentsDebuggingEnabled(true)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        pendingDeepLinkUrl = intent?.dataString

        webView = findViewById(R.id.webview)
        loadingIndicator = findViewById(R.id.loading_indicator)

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

        webView.webViewClient = object : WebViewClient() {

            override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                setLoadingVisible(true)

                view.evaluateJavascript("""
                    (function() {
                        window.inAndroidApp = true;
                    })();
                """.trimIndent(), null)
            }

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

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError,
            ) {
                super.onReceivedError(view, request, error)
                if (request.isForMainFrame) {
                    setLoadingVisible(false)
                }
            }

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

                setLoadingVisible(false)
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    finish()
                }
            }
        })

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
        setLoadingVisible(true)
        webView.loadUrl(if (!deepLink.isNullOrEmpty()) deepLink else WEB_APP_URL)
    }

    private fun setLoadingVisible(visible: Boolean) {
        loadingIndicator.visibility = if (visible) View.VISIBLE else View.GONE
    }

    private fun shutdownWebRuntime() {
        if (isWebRuntimeShuttingDown) return
        isWebRuntimeShuttingDown = true

        // Ask the page to close common socket.io handles before WebView teardown.
        webView.evaluateJavascript(
            """
                (function() {
                    if (typeof window.__androidAppClosing === 'function') {
                        try { window.__androidAppClosing(); } catch (e) {}
                    }
                    var candidates = [window.socket, window.ioSocket, window.appSocket];
                    for (var i = 0; i < candidates.length; i++) {
                        var s = candidates[i];
                        if (s && typeof s.disconnect === 'function') {
                            try { s.disconnect(); } catch (e) {}
                        }
                    }
                })();
            """.trimIndent(),
            null,
        )

        setLoadingVisible(false)
        webView.stopLoading()
        webView.loadUrl("about:blank")
        webView.onPause()
        webView.pauseTimers()
        webView.removeJavascriptInterface("AndroidBle")
        webView.removeJavascriptInterface("AndroidVibrate")
        webView.removeAllViews()
        webView.destroy()
    }

    override fun onDestroy() {
        bleBridge.disconnect()
        if (isFinishing || !isChangingConfigurations) {
            shutdownWebRuntime()
        }
        super.onDestroy()
    }
}