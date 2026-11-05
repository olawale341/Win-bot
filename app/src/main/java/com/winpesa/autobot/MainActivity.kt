package com.winpesa.autobot

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebStorage
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

    private var activeMaharaUrl = "https://winpesa.ng/games/Mahara-Jackpot-9192"
    private val handler = Handler(Looper.getMainLooper())

    private val numbersList = mutableListOf<String>()
    private val patternsList = mutableListOf<String>()
    private var currentIndex = 0
    private var commonPassword = "YourDefaultPassword123"

    private var filePickTarget: String = "NUMBERS"
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
        s.cacheMode = WebSettings.LOAD_DEFAULT
        s.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        s.userAgentString = s.userAgentString.replace("; wv", "")

        // Hardware Acceleration for smooth page transitions
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)

        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                tvStatus.text = "Loaded: $url"

                if (url != null && url.contains("winpesa.ng")) {
                    detectDynamicMaharaLink()
                }
            }
        }
        webView.loadUrl("https://winpesa.ng")
    }

    private fun detectDynamicMaharaLink() {
        val detectJs = """
            (function() {
                let link = Array.from(document.querySelectorAll('a, div')).find(el => {
                    let href = el.getAttribute('href') || '';
                    return href.includes('Mahara-Jackpot');
                });
                if (link) {
                    let href = link.getAttribute('href');
                    return href.startsWith('http') ? href : ('https://winpesa.ng' + href);
                }
                return null;
            })();
        """.trimIndent()

        webView.evaluateJavascript(detectJs) { result ->
            val clean = result?.replace("\"", "")?.trim()
            if (!clean.isNullOrEmpty() && clean != "null" && clean.contains("Mahara-Jackpot")) {
                activeMaharaUrl = clean
            }
        }
    }

    private fun sanitizePhone(phone: String): String {
        var p = phone.trim().replace(" ", "").replace("-", "")
        if (p.startsWith("+234")) p = p.substring(4)
        else if (p.startsWith("234") && p.length > 10) p = p.substring(3)
        else if (p.startsWith("0") && p.length == 11) p = p.substring(1)
        return p
    }

    // ========================================================
    // ⚙️ DATA MANAGEMENT MENU (With Wipe All Data Feature)
    // ========================================================
    private fun showDataManagementMenu() {
        val options = arrayOf(
            "📱 Numbers: Paste Text",
            "📁 Numbers: Upload .txt File",
            "🎯 Patterns: Paste Text (H-D-A)",
            "📁 Patterns: Upload .txt File",
            "🔑 Set Common Password",
            "🔢 Jump to Specific Account #",
            "🔄 Reset Progress to Account #1",
            "🗑️ WIPE ALL (Clear Numbers, Patterns & Reset)"
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
                    5 -> showJumpToAccountDialog()
                    6 -> {
                        currentIndex = 0
                        saveData()
                        updateUI()
                        Toast.makeText(this, "Reset to #1", Toast.LENGTH_SHORT).show()
                    }
                    7 -> confirmWipeAllData()
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    // Completely wipes all data for a new batch
    private fun confirmWipeAllData() {
        AlertDialog.Builder(this)
            .setTitle("🗑️ Wipe All Data?")
            .setMessage("This will delete all saved phone numbers, patterns, and reset progress to #0 so you can start a fresh batch. Continue?")
            .setPositiveButton("Yes, Wipe All") { _, _ ->
                numbersList.clear()
                patternsList.clear()
                currentIndex = 0

                val prefs = getSharedPreferences("winpesa_bot", MODE_PRIVATE)
                prefs.edit().clear().apply()

                saveData()
                updateUI()
                tvStatus.text = "All data cleared! Ready for new batch."
                Toast.makeText(this, "Cleaned up completely!", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showJumpToAccountDialog() {
        val input = EditText(this)
        input.hint = "Enter number e.g. 50"
        AlertDialog.Builder(this)
            .setTitle("Jump to Account #")
            .setView(input)
            .setPositiveButton("Go") { _, _ ->
                val num = input.text.toString().trim().toIntOrNull()
                if (num != null && num in 1..numbersList.size) {
                    currentIndex = num - 1
                    saveData()
                    updateUI()
                    Toast.makeText(this, "Jumped to #$num", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Invalid account number", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showPasteDialog(type: String) {
        val input = EditText(this)
        input.setLines(8)
        input.hint = if (type == "NUMBERS") "Paste phone numbers (one per line)" else "Paste patterns (one per line, e.g. HDAHHDAHDAHDA)"

        AlertDialog.Builder(this)
            .setTitle(if (type == "NUMBERS") "Paste Phone Numbers" else "Paste Patterns")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val lines = input.text.toString().split("\n").map { it.trim() }.filter { it.isNotEmpty() }
                if (type == "NUMBERS") {
                    numbersList.clear()
                    numbersList.addAll(lines.map { sanitizePhone(it) })
                } else {
                    patternsList.clear()
                    patternsList.addAll(lines)
                }
                saveData()
                updateUI()
                Toast.makeText(this, "Saved ${lines.size} items!", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun handleUploadedFile(uri: Uri) {
        Thread {
            try {
                contentResolver.openInputStream(uri)?.use { stream ->
                    val lines = stream.bufferedReader().readLines().map { it.trim() }.filter { it.isNotEmpty() }
                    runOnUiThread {
                        if (filePickTarget == "NUMBERS") {
                            numbersList.clear()
                            numbersList.addAll(lines.map { sanitizePhone(it) })
                        } else {
                            patternsList.clear()
                            patternsList.addAll(lines)
                        }
                        saveData()
                        updateUI()
                        Toast.makeText(this, "Loaded ${lines.size} items safely!", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "Error reading file: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
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
                Toast.makeText(this, "Password saved!", Toast.LENGTH_SHORT).show()
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
            numbersList.addAll(nums.split("\n").map { sanitizePhone(it) })
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
    // 2. MAHARA (With High Latency Auto-Retry)
    // ========================================================
    private fun runStep2_OpenMahara() {
        tvStatus.text = "Step 2: Loading Mahara..."
        webView.loadUrl(activeMaharaUrl)

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
                    } else if (attempts < 20) {
                        // Checks up to 20 times (giving it plenty of time on slow mobile networks)
                        handler.postDelayed(this, 750)
                    }
                }
            }
        }, 1200)
    }

    // ========================================================
    // 3. BET (With Network Auto-Wait until 17 Matches Arrive)
    // ========================================================
    private fun runStep3_PickAndBet() {
        if (patternsList.isEmpty()) {
            tvStatus.text = "Error: Load patterns first via ⚙️ DATA"
            return
        }

        val rawPattern = patternsList[currentIndex % patternsList.size]
        val clean = rawPattern.replace(Regex("[^1X2HDAMhda]"), "").uppercase()

        val safe13 = clean.take(13).toMutableList()
        while (safe13.size < 13) {
            safe13.add(listOf('H', 'D', 'A').random())
        }

        val picks13 = safe13.map { char ->
            when (char) {
                'H', '1' -> 0
                'D', 'X' -> 1
                'A', '2' -> 2
                else -> 0
            }
        }

        val final17Picks = if (clean.length >= 17) {
            picks13 + clean.substring(13, 17).map { char ->
                when (char) {
                    'H', '1' -> 0
                    'D', 'X' -> 1
                    'A', '2' -> 2
                    else -> 0
                }
            }
        } else {
            picks13 + List(4) { listOf(0, 1, 2).random() }
        }

        val picksJsArray = final17Picks.joinToString(prefix = "[", postfix = "]")
        val currentAccountNum = currentIndex + 1

        val betJs = """
            (function() {
                const picks = $picksJsArray;

                let clearBtn = Array.from(document.querySelectorAll('button, div')).find(el => (el.innerText || '').trim() === 'Clear');
                if (clearBtn) clearBtn.click();

                const allDrawButtons = Array.from(document.querySelectorAll('*')).filter(el => {
                    let txt = (el.innerText || '').trim();
                    let lines = txt.split('\n').map(s => s.trim()).filter(Boolean);
                    return lines.length === 2 && lines[0] === 'DRAW' && !isNaN(parseFloat(lines[1]));
                });

                if (allDrawButtons.length < 17) {
                    return "WAIT_RETRY:" + allDrawButtons.length;
                }

                for (let i = 0; i < 17; i++) {
                    let drawBtn = allDrawButtons[i];
                    let chosen = picks[i];
                    let target = null;

                    if (chosen === 0) target = drawBtn.previousElementSibling;
                    else if (chosen === 1) target = drawBtn;
                    else if (chosen === 2) target = drawBtn.nextElementSibling;

                    if (target) target.click();
                }

                setTimeout(function() {
                    let placeBetBtn = Array.from(document.querySelectorAll('button, div')).find(el => (el.innerText || '').includes('Place bet'));
                    if (placeBetBtn) placeBetBtn.click();
                }, 800);

                return "SUCCESS: Account #" + $currentAccountNum + " applied pattern: " + "$clean".substring(0, Math.min(13, "$clean".length));
            })();
        """.trimIndent()

        // Auto-waits if page is still downloading data packets
        var retries = 0
        val execBet = object : Runnable {
            override fun run() {
                webView.evaluateJavascript(betJs) { result ->
                    if (result?.contains("WAIT_RETRY") == true && retries < 8) {
                        retries++
                        tvStatus.text = "Network: Loading matches ($retries/8)..."
                        handler.postDelayed(this, 800)
                    } else {
                        tvStatus.text = "Step 3: $result"
                    }
                }
            }
        }
        execBet.run()
    }

    // ========================================================
    // 4. NEXT
    // ========================================================
    private fun runStep4_LogoutAndNext() {
        tvStatus.text = "Step 4: Clearing session & loading next account..."

        WebStorage.getInstance().deleteAllData()
        webView.clearCache(true)

        val cookieManager = CookieManager.getInstance()
        cookieManager.removeAllCookies {
            cookieManager.flush()
            runOnUiThread {
                if (numbersList.isNotEmpty()) {
                    if (currentIndex < numbersList.size - 1) {
                        currentIndex++
                    } else {
                        tvStatus.text = "ALL ACCOUNTS COMPLETED! 🎉"
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