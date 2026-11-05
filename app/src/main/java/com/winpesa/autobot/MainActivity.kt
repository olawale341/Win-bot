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
    private lateinit var btnStep1: Button
    private lateinit var btnStep2: Button
    private lateinit var btnStep3: Button

    // ==========================================
    // >>> YOUR TEST ACCOUNT CREDENTIALS <<<
    // ==========================================
    private val testPhone = "8104659422"          // Put your test phone number
    private val testPassword = "Olawale1?"      // Put your test password
    // ==========================================

    private val maharaUrl = "https://winpesa.ng/games/Mahara-Jackpot-9192"

    // Your 13 predictions
    private val my13Pattern = "1X2121X12X121"

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Enable Chrome remote inspection
        WebView.setWebContentsDebuggingEnabled(true)

        tvStatus = findViewById(R.id.tvStatus)
        btnStep1 = findViewById(R.id.btnStep1)
        btnStep2 = findViewById(R.id.btnStep2)
        btnStep3 = findViewById(R.id.btnStep3)
        webView = findViewById(R.id.webView)

        setupWebView()

        // Step 1: Login
        btnStep1.setOnClickListener {
            runStep1_Login()
        }

        // Step 2: Navigate to Mahara & Click PLAY
        btnStep2.setOnClickListener {
            runStep2_OpenMahara()
        }

        // Step 3: Make all 17 picks & Place Bet
        btnStep3.setOnClickListener {
            runStep3_PickAndBet()
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

                // Inject Eruda Inspector on every page load
                injectErudaInspector()
            }
        }

        webView.loadUrl("https://winpesa.ng")
    }

    // Injects floating developer inspector icon
    private fun injectErudaInspector() {
        val erudaJs = """
            (function () {
                if (window.eruda) return;
                var script = document.createElement('script');
                script.src = "//cdn.jsdelivr.net/npm/eruda";
                document.body.appendChild(script);
                script.onload = function () {
                    if (window.eruda) eruda.init();
                };
            })();
        """.trimIndent()
        webView.evaluateJavascript(erudaJs, null)
    }

    // ========================================================
    // STEP 1: LOGIN
    // ========================================================
    private fun runStep1_Login() {
        tvStatus.text = "Step 1: Logging in..."

        val script = """
            (function() {
                // Close any promo popups first
                let closeBtns = Array.from(document.querySelectorAll('button, div, span, svg, i')).filter(el => {
                    let txt = (el.innerText || '').trim();
                    let cls = (el.className || '').toString().toLowerCase();
                    return txt === '×' || txt === 'X' || cls.includes('close');
                });
                if (closeBtns.length > 0) closeBtns[closeBtns.length - 1].click();

                let phoneInput = document.querySelector('input[placeholder*="Phone"], input[type="tel"], input[name*="phone"]');
                let passInput = document.querySelector('input[placeholder*="Password"], input[type="password"]');

                if (phoneInput && passInput) {
                    phoneInput.value = '$testPhone';
                    phoneInput.dispatchEvent(new Event('input', { bubbles: true }));
                    phoneInput.dispatchEvent(new Event('change', { bubbles: true }));

                    passInput.value = '$testPassword';
                    passInput.dispatchEvent(new Event('input', { bubbles: true }));
                    passInput.dispatchEvent(new Event('change', { bubbles: true }));

                    setTimeout(function() {
                        let submitBtn = Array.from(document.querySelectorAll('button, div[role="button"]'))
                            .find(el => el.innerText && el.innerText.trim() === 'LOGIN' && el.offsetWidth > 100);
                        if (submitBtn) submitBtn.click();
                    }, 400);

                    return "Credentials typed & Login clicked!";
                } else {
                    let headerLoginBtn = Array.from(document.querySelectorAll('button, a, div'))
                        .find(el => el.innerText && el.innerText.trim() === 'LOGIN');
                    if (headerLoginBtn) {
                        headerLoginBtn.click();
                        return "Header login clicked, tap Step 1 again in 1 sec";
                    }
                    return "Login fields not found";
                }
            })();
        """.trimIndent()

        webView.evaluateJavascript(script) { result ->
            tvStatus.text = "Step 1: $result"
        }
    }

    // ========================================================
    // STEP 2: OPEN MAHARA & TAP PLAY
    // ========================================================
    private fun runStep2_OpenMahara() {
        tvStatus.text = "Step 2: Loading Mahara page..."
        webView.loadUrl(maharaUrl)

        // Give it 3 seconds to load, then click PLAY
        webView.postDelayed({
            val clickPlayJs = """
                (function() {
                    // Close any daily promo modal
                    let closeX = Array.from(document.querySelectorAll('button, div, span')).find(el => el.innerText && el.innerText.trim() === '×');
                    if (closeX) closeX.click();

                    // Find and click the yellow PLAY button
                    let playBtn = Array.from(document.querySelectorAll('button, div')).find(el => {
                        let txt = (el.innerText || '').trim();
                        return txt === 'PLAY';
                    });

                    if (playBtn) {
                        playBtn.click();
                        return "SUCCESS: Clicked PLAY button!";
                    }
                    return "PLAY button not found yet (tap Step 2 again if still loading)";
                })();
            """.trimIndent()

            webView.evaluateJavascript(clickPlayJs) { result ->
                tvStatus.text = "Step 2: $result"
            }
        }, 3000)
    }

    // ========================================================
    // STEP 3: PICK MATCHES & PLACE BET
    // ========================================================
    private fun runStep3_PickAndBet() {
        tvStatus.text = "Step 3: Selecting predictions..."

        val clean13 = my13Pattern.replace(",", "").trim()
        val picks13 = clean13.take(13).map { char ->
            when (char.uppercaseChar()) {
                '1', 'H' -> 0
                'X', 'D' -> 1
                '2', 'A' -> 2
                else -> 0
            }
        }.toMutableList()

        // 4 random reserve picks
        val autoReserves4 = List(4) { listOf(0, 1, 2).random() }
        val final17Picks = picks13 + autoReserves4
        val picksJsArray = final17Picks.joinToString(prefix = "[", postfix = "]")

        val betJs = """
            (function() {
                const picks = $picksJsArray;
                
                // Find all match buttons containing WIN or DRAW
                const allOddsButtons = Array.from(document.querySelectorAll('button, div')).filter(el => {
                    const txt = (el.innerText || '').trim();
                    return (txt.startsWith('WIN') || txt.startsWith('DRAW')) && el.children.length > 0;
                });

                if (allOddsButtons.length < 51) {
                    return "ERROR: Found only " + allOddsButtons.length + " odds buttons. Need 51.";
                }

                // Click all 17 predictions
                for (let i = 0; i < 17; i++) {
                    let outcome = picks[i];
                    let targetIndex = (i * 3) + outcome;
                    if (targetIndex < allOddsButtons.length) {
                        allOddsButtons[targetIndex].click();
                    }
                }

                // Click "Place bet"
                setTimeout(function() {
                    let placeBetBtn = Array.from(document.querySelectorAll('button, div')).find(el => {
                        let txt = (el.innerText || '').trim();
                        return txt.includes('Place bet');
                    });
                    if (placeBetBtn) {
                        placeBetBtn.click();
                    }
                }, 1200);

                return "SUCCESS: 17 picks selected and Place Bet clicked!";
            })();
        """.trimIndent()

        webView.evaluateJavascript(betJs) { result ->
            tvStatus.text = "Step 3: $result"
        }
    }
}