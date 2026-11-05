package com.winpesa.autobot

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var tvStatus: TextView
    private lateinit var btnRunTest: Button

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        btnRunTest = findViewById(R.id.btnRunSingleTest)
        webView = findViewById(R.id.webView)

        setupWebView()

        btnRunTest.setOnClickListener {
            startSingleTest()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true

        // Clean user agent so WinPesa serves its proper responsive mobile layout
        settings.userAgentString = settings.userAgentString.replace("; wv", "")

        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                tvStatus.text = "Loaded: $url"
            }
        }

        // Preload WinPesa
        webView.loadUrl("https://winpesa.ng")
    }

    private fun startSingleTest() {
        tvStatus.text = "Status: Resetting session & loading WinPesa..."

        // Step 1: Wipe any active session cookies for a clean test
        val cookieManager = CookieManager.getInstance()
        cookieManager.removeAllCookies {
            cookieManager.flush()
            runOnUiThread {
                // Step 2: Reload the page fresh
                webView.loadUrl("https://winpesa.ng")
            }
        }
    }

    fun runJs(script: String, onResult: ((String) -> Unit)? = null) {
        webView.evaluateJavascript("(function() { $script })();") { result ->
            onResult?.invoke(result)
        }
    }
}