package com.winpesa.autobot

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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

    // ==========================================
    // >>> CHANGE THESE TO YOUR TEST ACCOUNT <<<
    // ==========================================
    private val testPhone = "8104659422"      // 10 digits without leading 0, or with 0 (e.g. "080...")
    private val testPassword = "Olawale1?"  // Your actual test password
    // ==========================================

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        btnRunTest = findViewById(R.id.btnRunSingleTest)
        webView = findViewById(R.id.webView)

        setupWebView()

        btnRunTest.setOnClickListener {
            performAutomatedLogin()
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

        settings.userAgentString = settings.userAgentString.replace("; wv", "")

        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                tvStatus.text = "Loaded: $url"
            }
        }

        webView.loadUrl("https://winpesa.ng")
    }

    private fun performAutomatedLogin() {
        tvStatus.text = "Status: Injecting Login Credentials..."

        // Robust JS script: Targets phone, password, and triggers standard input events
        val jsScript = """
            (function() {
                // 1. If login modal isn't open yet, try to click the top header LOGIN button
                let headerLoginBtn = Array.from(document.querySelectorAll('button, a, div')).find(el => el.innerText && el.innerText.trim() === 'LOGIN');
                
                // 2. Find phone input
                let phoneInput = document.querySelector('input[placeholder*="Phone"], input[type="tel"], input[name*="phone"]');
                
                // 3. Find password input
                let passInput = document.querySelector('input[placeholder*="Password"], input[type="password"]');

                if (phoneInput && passInput) {
                    // Type phone
                    phoneInput.value = '$testPhone';
                    phoneInput.dispatchEvent(new Event('input', { bubbles: true }));
                    phoneInput.dispatchEvent(new Event('change', { bubbles: true }));

                    // Type password
                    passInput.value = '$testPassword';
                    passInput.dispatchEvent(new Event('input', { bubbles: true }));
                    passInput.dispatchEvent(new Event('change', { bubbles: true }));

                    // Find and click the Yellow Submit LOGIN button inside the modal
                    setTimeout(function() {
                        let submitBtn = Array.from(document.querySelectorAll('button, div[role="button"]'))
                            .find(el => el.innerText && el.innerText.trim() === 'LOGIN' && el.offsetWidth > 100);
                        if (submitBtn) {
                            submitBtn.click();
                        }
                    }, 500);

                    return "SUCCESS: Typed credentials and clicked Login";
                } else if (headerLoginBtn) {
                    headerLoginBtn.click();
                    return "OPENED_LOGIN: Clicked header login, retry in a moment";
                } else {
                    return "ERROR: Inputs not found";
                }
            })();
        """.trimIndent()

        webView.evaluateJavascript(jsScript) { result ->
            tvStatus.text = "Result: $result"
        }
    }
}