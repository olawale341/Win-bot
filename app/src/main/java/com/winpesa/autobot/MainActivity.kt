package com.winpesa.autobot

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.net.Uri
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
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var tvStatus: TextView
    private lateinit var tvAccountInfo: TextView
    private lateinit var btnDataMenu: Button

    private lateinit var btnStep1: Button
    private lateinit var btnStep2: Button
    private lateinit var btnStep3: Button
    private lateinit var btnStep4: Button

    private val maharaUrl = "https://winpesa.ng/games/Mahara-Jackpot-9192"
    private val handler = Handler(Looper.getMainLooper())

    // Separate Stores for Numbers and Patterns
    private val numbersList = mutableListOf<String>()
    private val patternsList = mutableListOf<String>()
    private var currentIndex = 0
    private var commonPassword = "YourDefaultPassword123" // Change anytime in DATA menu

    // File picker state
    private var filePickTarget: String = "NUMBERS" // "NUMBERS" or "PATTERNS"
    private val filePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { handleUploadedFile(it) }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        tvAccountInfo = findViewById(R.id.tvAccountInfo)
        btnDataMenu = findViewById(R.id.btnDataMenu)

        btnStep1 = findViewById(R.id.btnStep1)
        btnStep2 = findViewById(R.id.btnStep2)
        btnStep3 = findViewById(R.id.btnStep3)
        btnStep4 = findViewById(R.id.btnStep4)
        webView = findViewById(R.id.webView)

        setupWebView()
        loadPersistedData()

        btnDataMenu.setOnClickListener { showDataManagementMenu() }

        btnStep1.setOnClickListener { runStep1_Login() }
        btnStep2.setOnClickListener { runStep2_OpenMahara() }
        btnStep3.setOnClickListener { runStep3_PickAndBet() }
        btnStep4.setOnClickListener { runStep4_LogoutAndNext() }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val s = webView.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.databaseEnabled = true
        s.useWideViewPort = true
        s.loadWithOverviewMode = true
        s.userAgentString = s.userAgentString.replace("; wv", "")

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
    // ⚙️ DATA MANAGEMENT MENU (Paste or Upload .txt)
    // ========================================================
    private fun showDataManagementMenu() {
        val options = arrayOf(
            "📱 Numbers: Paste Text",
            "📁 Numbers: Upload .txt File",
            "🎯 Patterns: Paste Text (H-D-A)",
            "📁 Patterns: Upload .txt File",
            "🔑 Set Common Password",
            "🔄 Reset Progress to Account #1"
        )

        AlertDialog.Builder(this)
            .setTitle("⚙️ Data Manager")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showPasteDialog("NUMBERS")
                    1 -> { filePickTarget = "NUMBERS"; filePickerLauncher.launch("text/*") }
                    2 -> showPasteDialog("PATTERNS")
                    3 -> { filePickTarget = "PATTERNS"; filePickerLauncher.launch("text/*") }
                    4 -> showPasswordDialog()
                    5 -> {
                        currentIndex = 0
                        saveData()
                        updateUI()
                        Toast.makeText(this, "Reset to #1", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showPasteDialog(type: String) {
        val input = EditText(this)
        input.setLines(10)
        input.hint = if (type == "NUMBERS") "Paste phone numbers (one per line)" else "Paste 13 patterns like HDAHHDAHDAHDA (one per line)"

        AlertDialog.Builder(this)
            .setTitle(if (type == "NUMBERS") "Paste Phone Numbers" else "Paste 13-Match Patterns")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val lines = input.text.toString().split("\n").map { it.trim() }.filter { it.isNotEmpty() }
                if (type == "NUMBERS") {
                    numbersList.clear()
                    numbersList.addAll(lines)
                } else {
                    patternsList.clear()
                    patternsList.addAll(lines)
                }
                saveData()
                updateUI()
                Toast.makeText(this, "Loaded ${lines.size} items!", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun handleUploadedFile(uri: Uri) {
        try {
            contentResolver.openInputStream(uri)?.use { stream ->
                val text = stream.bufferedReader().readText()
                val lines = text.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
                if (filePickTarget == "NUMBERS") {
                    numbersList.clear()
                    numbersList.addAll(lines)
                } else {
                    patternsList.clear()
                    patternsList.addAll(lines)
                }
                saveData()
                updateUI()
                Toast.makeText(this, "File loaded: ${lines.size} lines!", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error reading file: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun showPasswordDialog() {
        val input = EditText(this)
        input.setText(commonPassword)
        AlertDialog.Builder(this)
            .setTitle("🔑 Common Password")
            .setMessage("Set the password used across all phone numbers:")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                commonPassword = input.text.toString().trim()
                saveData()
                Toast.makeText(this, "Password updated!", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateUI() {
        val numCount = numbersList.size
        val patCount = patternsList.size

        if (numCount == 0) {
            tvAccountInfo.text = "Acc: #0/0 | Tap ⚙️ DATA to load"
        } else {
            val phone = numbersList.getOrElse(currentIndex) { "None" }
            val pattern = if (patCount > 0) patternsList[currentIndex % patCount] else "None"
            tvAccountInfo.text = "Acc #${currentIndex + 1}/$numCount: $phone\nPat: $pattern"
        }
    }

    private fun saveData() {
        val prefs = getSharedPreferences("winpesa_bot", MODE_PRIVATE)
        prefs.edit()
            .putString("numbers", numbersList.joinToString("\n"))
            .putString("patterns", patternsList.joinToString("\n"))
            .putString("password", commonPassword)
            .putInt("index", currentIndex)
            .apply()
    }

    private fun loadPersistedData() {
        val prefs = getSharedPreferences("winpesa_bot", MODE_PRIVATE)
        commonPassword = prefs.getString("password", "YourDefaultPassword123") ?: "YourDefaultPassword123"
        currentIndex = prefs.getInt("index", 0)

        val nums = prefs.getString("numbers", "") ?: ""
        if (nums.isNotEmpty()) {
            numbersList.clear()
            numbersList.addAll(nums.split("\n"))
        }

        val pats = prefs.getString("patterns", "") ?: ""
        if (pats.isNotEmpty()) {
            patternsList.clear()
            patternsList.addAll(pats.split("\n"))
        }
        updateUI()
    }

    // ========================================================
    // 1. LOGIN
    // ========================================================
    private fun runStep1_Login() {
        if (numbersList.isEmpty()) {
            tvStatus.text = "Error: Load phone numbers first via ⚙️ DATA"
            return
        }

        val phone = numbersList[currentIndex]
        tvStatus.text = "Step 1: Logging in $phone..."

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
                    setReactInput(phoneInput, '$phone');
                    setReactInput(passInput, '$commonPassword');

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
                tvStatus.text = "Step 1: Logged in $phone! Ready for Step 2."
            }
        }
    }

    // ========================================================
    // 2. MAHARA
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
    // 3. BET (Parses H-D-A or 1-X-2 + 4 Auto-Reserves)
    // ========================================================
    private fun runStep3_PickAndBet() {
        if (patternsList.isEmpty()) {
            tvStatus.text = "Error: Load 13 patterns first via ⚙️ DATA"
            return
        }

        // Get pattern for this account (cycles if patterns < numbers)
        val rawPattern = patternsList[currentIndex % patternsList.size]
        tvStatus.text = "Step 3: Applying pattern ($rawPattern)..."

        // Clean out spaces/commas and convert H/D/A or 1/X/2 to indices [0, 1, 2]
        val clean13 = rawPattern.replace(Regex("[^1X2HDAMhda]"), "").uppercase()
        val picks13 = clean13.take(13).map { char ->
            when (char) {
                'H', '1' -> 0
                'D', 'X' -> 1
                'A', '2' -> 2
                else -> 0
            }
        }.toMutableList()

        // 4 Auto-Reserves
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

                return "SUCCESS: 17/17 Selected & Place Bet Clicked!";
            })();
        """.trimIndent()

        webView.evaluateJavascript(betJs) { result ->
            tvStatus.text = "Step 3: $result"
        }
    }

    // ========================================================
    // 4. NEXT (Instant Logout & Advance Index)
    // ========================================================
    private fun runStep4_LogoutAndNext() {
        tvStatus.text = "Step 4: Clearing session & loading next..."

        val cookieManager = CookieManager.getInstance()
        cookieManager.removeAllCookies {
            cookieManager.flush()
            runOnUiThread {
                if (numbersList.isNotEmpty()) {
                    if (currentIndex < numbersList.size - 1) {
                        currentIndex++
                    } else {
                        tvStatus.text = "FINISHED ALL NUMBERS! 🎉"
                        updateUI()
                        return@runOnUiThread
                    }
                }
                saveData()
                updateUI()
                webView.loadUrl("https://winpesa.ng")
            }
        }
    }
}