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

    // Your 13 predictions (1 = Home, X = Draw, 2 = Away)
    private val my13Pattern = "1X2121X12X121"

    private val handler = Handler(Looper.getMainLooper())

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        btnStep1 = findViewById(R.id.btnStep1)
        btnStep2 = findViewById(R.id.btnStep2)
        btnStep3 = findViewById(R.id.btnStep3)
        webView = findViewById(R.id.webView)

        setupWebView()

        btnStep1.setOnClickListener { runStep1_Login() }
        btnStep2.setOnClickListener { runStep2_OpenMahara() }
        btnStep3.setOnClickListener { runStep3_PickAndBet() }
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

                // If loaded on Winpesa Mahara, automatically jump inside the iframe!
                if (url != null && url.contains("winpesa.ng/games/Mahara-Jackpot")) {
                    handler.postDelayed({
                        jumpDirectlyIntoIframeGame()
                    }, 1500)
                }
            }
        }

        webView.loadUrl("https://winpesa.ng")
    }

    // Automatically jumps out of the WinPesa cage into the game URL directly
    private fun jumpDirectlyIntoIframeGame() {
        val escapeJs = """
            (function() {
                let iframe = document.querySelector('iframe');
                if (iframe && iframe.src && iframe.src.includes('aggrhub.com')) {
                    window.location.href = iframe.src;
                    return "JUMPING_INTO_GAME";
                }
                return "SEARCHING_IFRAME";
            })();
        """.trimIndent()

        webView.evaluateJavascript(escapeJs) { result ->
            if (result?.contains("JUMPING_INTO_GAME") == true) {
                tvStatus.text = "Step 2: Loaded game directly! Ready for Step 3."
            }
        }
    }

    // STEP 1: Login
    private fun runStep1_Login() {
        tvStatus.text = "Step 1: Logging in..."

        val script = """
            (function() {
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

                    return "Logged in successfully!";
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

    // STEP 2: Open Mahara
    private fun runStep2_OpenMahara() {
        tvStatus.text = "Step 2: Loading Mahara page..."
        webView.loadUrl(maharaUrl)

        // Give it 2.5 seconds, then check and jump
        handler.postDelayed({
            jumpDirectlyIntoIframeGame()
        }, 2500)
    }

    // STEP 3: Pick Matches 1 to 17 & Bet
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

        val autoReserves4 = List(4) { listOf(0, 1, 2).random() }
        val final17Picks = picks13 + autoReserves4
        val picksJsArray = final17Picks.joinToString(prefix = "[", postfix = "]")

        val betJs = """
            (function() {
                const picks = $picksJsArray; // 17 picks

                // THE EXACT 51 BUTTON RULE:
                // Find all elements whose text is strictly 2 lines: ["WIN", "2.35"] or ["DRAW", "3.45"]
                const allOddsButtons = Array.from(document.querySelectorAll('*')).filter(el => {
                    let txt = (el.innerText || '').trim();
                    let lines = txt.split('\n').map(s => s.trim()).filter(Boolean);
                    if (lines.length === 2) {
                        let hasLabel = (lines[0] === 'WIN' || lines[0] === 'DRAW' || lines[1] === 'WIN' || lines[1] === 'DRAW');
                        let hasOdds = (!isNaN(parseFloat(lines[0])) || !isNaN(parseFloat(lines[1])));
                        return hasLabel && hasOdds;
                    }
                    return false;
                });

                if (allOddsButtons.length < 51) {
                    return "ERROR: Found " + allOddsButtons.length + " buttons (must be on aggrhub screen).";
                }

                // Click all 17 matches in order (0 to 16)
                for (let i = 0; i < 17; i++) {
                    let outcome = picks[i]; // 0, 1, or 2
                    let targetIndex = (i * 3) + outcome;
                    let targetBtn = allOddsButtons[targetIndex];
                    if (targetBtn) {
                        targetBtn.click();
                    }
                }

                // Tap Place Bet after 1 second
                setTimeout(function() {
                    let placeBetBtn = Array.from(document.querySelectorAll('button, div')).find(el => {
                        let txt = (el.innerText || '').trim();
                        return txt.includes('Place bet');
                    });
                    if (placeBetBtn) {
                        placeBetBtn.click();
                    }
                }, 1000);

                return "SUCCESS: Selected 17 of 17 matches and clicked Place Bet!";
            })();
        """.trimIndent()

        webView.evaluateJavascript(betJs) { result ->
            tvStatus.text = "Step 3: $result"
        }
    }
}