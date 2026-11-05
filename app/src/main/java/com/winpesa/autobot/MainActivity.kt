package com.winpesa.autobot

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

data class AccountItem(
    val phone: String,
    val pass: String,
    val pattern: String
)

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var tvStatus: TextView
    private lateinit var tvAccountInfo: TextView
    private lateinit var btnLoadAccounts: Button

    private lateinit var btnStep1: Button
    private lateinit var btnStep2: Button
    private lateinit var btnStep3: Button
    private lateinit var btnStep4: Button

    private val maharaUrl = "https://winpesa.ng/games/Mahara-Jackpot-9192"
    private val handler = Handler(Looper.getMainLooper())

    // List of accounts loaded
    private val accountList = mutableListOf<AccountItem>()
    private var currentIndex = 0

    // Common default password if a line doesn't specify one
    private val defaultPassword = "YourDefaultPassword123"

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        tvAccountInfo = findViewById(R.id.tvAccountInfo)
        btnLoadAccounts = findViewById(R.id.btnLoadAccounts)

        btnStep1 = findViewById(R.id.btnStep1)
        btnStep2 = findViewById(R.id.btnStep2)
        btnStep3 = findViewById(R.id.btnStep3)
        btnStep4 = findViewById(R.id.btnStep4)
        webView = findViewById(R.id.webView)

        setupWebView()
        loadSavedAccountsFromStorage()

        btnLoadAccounts.setOnClickListener { showPasteAccountsDialog() }

        btnStep1.setOnClickListener { runStep1_Login() }
        btnStep2.setOnClickListener { runStep2_OpenMahara() }
        btnStep3.setOnClickListener { runStep3_PickAndBet() }
        btnStep4.setOnClickListener { runStep4_LogoutAndNext() }
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

    // ========================================================
    // DIALOG: PASTE ACCOUNTS & PATTERNS
    // ========================================================
    private fun showPasteAccountsDialog() {
        val input = EditText(this)
        input.hint = "Paste lines:\n08011111111, pass, 1X2121X12X121\nor\n08011111111, 1X2121X12X121"
        input.setLines(10)

        // Preload existing text if available
        val prefs = getSharedPreferences("winpesa_data", MODE_PRIVATE)
        input.setText(prefs.getString("raw_accounts", ""))

        AlertDialog.Builder(this)
            .setTitle("Load Accounts & Patterns")
            .setMessage("Paste your phone numbers and patterns below (one per line):")
            .setView(input)
            .setPositiveButton("Save & Load") { _, _ ->
                val text = input.text.toString().trim()
                prefs.edit().putString("raw_accounts", text).apply()
                parseAccounts(text)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun parseAccounts(raw: String) {
        accountList.clear()
        val lines = raw.split("\n").map { it.trim() }.filter { it.isNotEmpty() }

        for (line in lines) {
            val parts = line.split(",").map { it.trim() }
            if (parts.size >= 3) {
                // phone, password, pattern
                accountList.add(AccountItem(parts[0], parts[1], parts[2]))
            } else if (parts.size == 2) {
                // phone, pattern (uses default password)
                accountList.add(AccountItem(parts[0], defaultPassword, parts[1]))
            }
        }

        currentIndex = 0
        updateAccountUI()
    }

    private fun loadSavedAccountsFromStorage() {
        val prefs = getSharedPreferences("winpesa_data", MODE_PRIVATE)
        val saved = prefs.getString("raw_accounts", "") ?: ""
        if (saved.isNotEmpty()) {
            parseAccounts(saved)
        }
    }

    private fun updateAccountUI() {
        if (accountList.isEmpty()) {
            tvAccountInfo.text = "Acc: #0/0 | Tap LOAD to paste"
        } else {
            val acc = accountList[currentIndex]
            tvAccountInfo.text = "Acc: #${currentIndex + 1}/${accountList.size}: ${acc.phone}\nPattern: ${acc.pattern}"
        }
    }

    // ========================================================
    // STEP 1: LOGIN WITH CURRENT ACCOUNT
    // ========================================================
    private fun runStep1_Login() {
        if (accountList.isEmpty()) {
            tvStatus.text = "Error: No accounts loaded! Tap LOAD first."
            return
        }

        val current = accountList[currentIndex]
        tvStatus.text = "Step 1: Logging in ${current.phone}..."

        val loginScript = """
            (function() {
                function setReactInput(input, value) {
                    let lastValue = input.value;
                    input.value = value;
                    let event = new Event('input', { bubbles: true });
                    let tracker = input._valueTracker;
                    if (tracker) tracker.setValue(lastValue);
                    input.dispatchEvent(event);
                    input.dispatchEvent(new Event('change', { bubbles: true }));
                }

                let closeBtns = Array.from(document.querySelectorAll('button, div, span, svg, i')).filter(el => {
                    let txt = (el.innerText || '').trim();
                    let cls = (el.className || '').toString().toLowerCase();
                    return txt === '×' || txt === 'X' || cls.includes('close');
                });
                if (closeBtns.length > 0) closeBtns[0].click();

                let phoneInput = document.querySelector('input[placeholder*="Phone"], input[type="tel"], input[name*="phone"]');
                let passInput = document.querySelector('input[placeholder*="Password"], input[type="password"]');

                if (phoneInput && passInput) {
                    setReactInput(phoneInput, '${current.phone}');
                    setReactInput(passInput, '${current.pass}');

                    setTimeout(function() {
                        let submitBtn = Array.from(document.querySelectorAll('button, div[role="button"]'))
                            .find(el => el.innerText && el.innerText.trim() === 'LOGIN' && el.offsetWidth > 100);
                        if (submitBtn) submitBtn.click();
                    }, 500);

                    return "TYPED_AND_SUBMITTED";
                } else {
                    let headerLoginBtn = Array.from(document.querySelectorAll('button, a, div'))
                        .find(el => el.innerText && el.innerText.trim() === 'LOGIN');
                    if (headerLoginBtn) {
                        headerLoginBtn.click();
                        return "OPENED_MODAL";
                    }
                    return "NOT_FOUND";
                }
            })();
        """.trimIndent()

        webView.evaluateJavascript(loginScript) { result ->
            if (result?.contains("OPENED_MODAL") == true) {
                handler.postDelayed({ runStep1_Login() }, 800)
            } else if (result?.contains("TYPED_AND_SUBMITTED") == true) {
                tvStatus.text = "Step 1: Logged in ${current.phone}! Ready for Step 2."
            }
        }
    }

    // ========================================================
    // STEP 2: OPEN MAHARA & JUMP INSIDE
    // ========================================================
    private fun runStep2_OpenMahara() {
        tvStatus.text = "Step 2: Loading Mahara Jackpot..."
        webView.loadUrl(maharaUrl)

        var attempts = 0
        handler.postDelayed(object : Runnable {
            override fun run() {
                attempts++
                val watcherJs = """
                    (function() {
                        let iframe = document.querySelector('iframe');
                        if (iframe && iframe.src && iframe.src.includes('aggrhub.com')) {
                            window.location.href = iframe.src;
                            return "JUMPING_IFRAME";
                        }
                        let playBtn = Array.from(document.querySelectorAll('button, div, a')).find(el => (el.innerText || '').trim() === 'PLAY');
                        if (playBtn) {
                            playBtn.click();
                            return "CLICKED_PLAY";
                        }
                        return "SEARCHING";
                    })();
                """.trimIndent()

                webView.evaluateJavascript(watcherJs) { result ->
                    if (result?.contains("JUMPING_IFRAME") == true) {
                        tvStatus.text = "Step 2: Inside match screen! Ready for Step 3."
                    } else if (attempts < 12) {
                        handler.postDelayed(this, 800)
                    }
                }
            }
        }, 1500)
    }

    // ========================================================
    // STEP 3: PICK CURRENT ACCOUNT'S 13 PATTERN + 4 RESERVES
    // ========================================================
    private fun runStep3_PickAndBet() {
        if (accountList.isEmpty()) return

        val current = accountList[currentIndex]
        tvStatus.text = "Step 3: Selecting pattern for ${current.phone}..."

        val clean13 = current.pattern.replace(",", "").trim()
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
                const picks = $picksJsArray;

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
                    return "WAIT: Found " + allOddsButtons.length + " buttons. Wait 2 seconds and tap 3. BET again!";
                }

                for (let i = 0; i < 17; i++) {
                    let outcome = picks[i];
                    let targetIndex = (i * 3) + outcome;
                    let targetBtn = allOddsButtons[targetIndex];
                    if (targetBtn) targetBtn.click();
                }

                setTimeout(function() {
                    let placeBetBtn = Array.from(document.querySelectorAll('button, div')).find(el => (el.innerText || '').includes('Place bet'));
                    if (placeBetBtn) placeBetBtn.click();
                }, 800);

                return "SUCCESS: Bet submitted for current account!";
            })();
        """.trimIndent()

        webView.evaluateJavascript(betJs) { result ->
            tvStatus.text = "Step 3: $result"
        }
    }

    // ========================================================
    // STEP 4: INSTANT LOGOUT & SHIFT TO NEXT ACCOUNT
    // ========================================================
    private fun runStep4_LogoutAndNext() {
        tvStatus.text = "Step 4: Clearing session & loading next account..."

        // 1. Wipe all session cookies instantly
        val cookieManager = CookieManager.getInstance()
        cookieManager.removeAllCookies {
            cookieManager.flush()
            runOnUiThread {
                // 2. Advance to next account index
                if (accountList.isNotEmpty()) {
                    if (currentIndex < accountList.size - 1) {
                        currentIndex++
                    } else {
                        tvStatus.text = "FINISHED ALL ACCOUNTS! 🎉"
                        updateAccountUI()
                        return@runOnUiThread
                    }
                }
                updateAccountUI()

                // 3. Reload WinPesa home fresh for next account
                webView.loadUrl("https://winpesa.ng")
            }
        }
    }
}