package com.winpesa.autobot

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
    // >>> CREDENTIALS <<<
    // ==========================================
    private val testPhone = "8104659422"          // Put your test phone number
    private val testPassword = "Olawale1?"      // Put your test password
    // ==========================================

    // ==========================================
    // >>> YOUR 13 PREDICTION PATTERN (H, D, A) <<<
    // H = Home Win, D = Draw, A = Away Win
    // ==========================================
    private val my13Pattern = "HDHAHDAHDHAHH"  // Exactly 13 predictions
    // ==========================================

    private val maharaUrl = "https://winpesa.ng/games/Mahara-Jackpot-9192"
    private val handler = Handler(Looper.getMainLooper())
    private var isTesting = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        btnRunTest = findViewById(R.id.btnRunSingleTest)
        webView = findViewById(R.id.webView)

        setupWebView()

        btnRunTest.setOnClickListener {
            isTesting = true
            tvStatus.text = "Status: Starting Test..."
            step1_DismissPopupAndLogin()
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
                dismissPromoPopup()
            }
        }

        webView.loadUrl("https://winpesa.ng")
    }

    private fun dismissPromoPopup() {
        val closeJs = """
            (function() {
                let closeBtns = Array.from(document.querySelectorAll('button, div, span, svg, i')).filter(el => {
                    let txt = (el.innerText || '').trim();
                    let cls = (el.className || '').toString().toLowerCase();
                    return txt === '×' || txt === 'X' || cls.includes('close');
                });
                if (closeBtns.length > 0) {
                    closeBtns[closeBtns.length - 1].click();
                }
            })();
        """.trimIndent()
        webView.evaluateJavascript(closeJs, null)
    }

    private fun step1_DismissPopupAndLogin() {
        tvStatus.text = "Status: Dismissing popups & Logging in..."

        val loginJs = """
            (function() {
                let closeBtns = Array.from(document.querySelectorAll('button, div, span')).filter(el => {
                    let txt = (el.innerText || '').trim();
                    return txt === '×' || txt === 'X';
                });
                if (closeBtns.length > 0) closeBtns[0].click();

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

                    return "SUBMITTED_LOGIN";
                } else {
                    let headerLoginBtn = Array.from(document.querySelectorAll('button, a, div'))
                        .find(el => el.innerText && el.innerText.trim() === 'LOGIN');
                    if (headerLoginBtn) {
                        headerLoginBtn.click();
                        return "OPENED_LOGIN";
                    }
                    return "NOT_FOUND";
                }
            })();
        """.trimIndent()

        webView.evaluateJavascript(loginJs) { result ->
            if (result?.contains("OPENED_LOGIN") == true) {
                handler.postDelayed({ step1_DismissPopupAndLogin() }, 1000)
            } else {
                tvStatus.text = "Status: Logged in! Loading Mahara..."
                handler.postDelayed({
                    webView.loadUrl(maharaUrl)
                    step2_HandleMaharaPlayModal()
                }, 2500)
            }
        }
    }

    private fun step2_HandleMaharaPlayModal() {
        tvStatus.text = "Status: Checking for 'PLAY' button..."

        val playJs = """
            (function() {
                let closeX = Array.from(document.querySelectorAll('button, div, span')).find(el => el.innerText && el.innerText.trim() === '×');
                if (closeX) closeX.click();

                let playBtn = Array.from(document.querySelectorAll('button, div')).find(el => {
                    let txt = (el.innerText || '').trim();
                    return txt === 'PLAY';
                });

                if (playBtn) {
                    playBtn.click();
                    return "CLICKED_PLAY";
                }
                return "WAITING";
            })();
        """.trimIndent()

        handler.postDelayed(object : Runnable {
            var attempts = 0
            override fun run() {
                attempts++
                webView.evaluateJavascript(playJs) { result ->
                    if (result?.contains("CLICKED_PLAY") == true) {
                        tvStatus.text = "Status: Mahara open! Applying H-D-A pattern + 4 reserves..."
                        handler.postDelayed({ step3_Select17MatchesAndBet() }, 2000)
                    } else if (attempts < 8) {
                        handler.postDelayed(this, 1000)
                    } else {
                        step3_Select17MatchesAndBet()
                    }
                }
            }
        }, 1500)
    }

    private fun step3_Select17MatchesAndBet() {
        tvStatus.text = "Status: Building 13 H-D-A picks + 4 auto-reserves..."

        // 1. Convert your 13 H-D-A pattern into column index [0=H, 1=D, 2=A]
        val clean13 = my13Pattern.replace(",", "").trim().uppercase()
        val picks13 = clean13.take(13).map { char ->
            when (char) {
                'H' -> 0 // Column 1 (Home Win)
                'D' -> 1 // Column 2 (Draw)
                'A' -> 2 // Column 3 (Away Win)
                else -> 0
            }
        }.toMutableList()

        // 2. Automatically generate 4 random reserves (0=H, 1=D, 2=A)
        val possibleOutcomes = listOf(0, 1, 2)
        val autoReserves4 = List(4) { possibleOutcomes.random() }

        // Combine to make exactly 17 picks
        val final17Picks = picks13 + autoReserves4
        val picksJsArray = final17Picks.joinToString(prefix = "[", postfix = "]")

        val betJs = """
            (function() {
                const picks = $picksJsArray;
                
                const allOddsButtons = Array.from(document.querySelectorAll('button, div')).filter(el => {
                    const txt = (el.innerText || '').trim();
                    return (txt.startsWith('WIN') || txt.startsWith('DRAW')) && el.children.length > 0;
                });

                // Pick all 17 matches (13 H-D-A + 4 Auto-Reserves)
                for (let i = 0; i < 17; i++) {
                    let outcome = picks[i];
                    let targetIndex = (i * 3) + outcome;
                    if (targetIndex < allOddsButtons.length) {
                        allOddsButtons[targetIndex].click();
                    }
                }

                // Wait 1 second for bottom drawer to open, then tap "Place bet"
                setTimeout(function() {
                    let placeBetBtn = Array.from(document.querySelectorAll('button, div')).find(el => {
                        let txt = (el.innerText || '').trim();
                        return txt.includes('Place bet');
                    });
                    if (placeBetBtn) {
                        placeBetBtn.click();
                    }
                }, 1000);

                return "DONE: 13 H-D-A + 4 Auto-Reserves Placed!";
            })();
        """.trimIndent()

        webView.evaluateJavascript(betJs) { result ->
            tvStatus.text = "Result: $result"
            isTesting = false
        }
    }
}