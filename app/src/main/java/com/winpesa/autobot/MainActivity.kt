package com.winpesa.touchbot

import com.winpesa.touchbot.R
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
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
    private lateinit var btnAutoRun: Button
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
    private var isAutoRunning = false
    private var isStepActive = false

    // Speed multiplier from 0.1x (Extra Slow) to 10.0x (Extreme)
    private var speedMultiplier = 1.0f

    // Target stop counter (0 = Unlimited / Run all)
    private var batchStopTarget = 0
    private var accountsProcessedInBatch = 0

    private var filePickTarget = "NUMBERS"
    private val filePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { handleUploadedFile(it) }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        tvAccountInfo = findViewById(R.id.tvAccountInfo)
        btnAutoRun = findViewById(R.id.btnAutoRun)
        btnDataMenu = findViewById(R.id.btnDataMenu)
        btnStep1 = findViewById(R.id.btnStep1)
        btnStep2 = findViewById(R.id.btnStep2)
        btnStep3 = findViewById(R.id.btnStep3)
        btnStep4 = findViewById(R.id.btnStep4)
        webView = findViewById(R.id.webView)

        setupWebView()
        loadPersistedData()

        btnAutoRun.setOnClickListener { toggleAutoRun() }
        btnDataMenu.setOnClickListener { showDataManagementMenu() }
        btnStep1.setOnClickListener { runStep1_Login() }
        btnStep2.setOnClickListener { runStep2_OpenMahara() }
        btnStep3.setOnClickListener { runStep3_PickAndBet() }
        btnStep4.setOnClickListener { runStep4_LogoutAndNext() }
    }

    // Dynamic delay scaling: Supports 0.1x (extra slow) to 10x (turbo)
    private fun scaled(baseMs: Long, minFloorMs: Long = 180L): Long {
        if (speedMultiplier <= 0f) return baseMs
        val calculated = (baseMs / speedMultiplier).toLong()
        return calculated.coerceAtLeast(minFloorMs)
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
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)

        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = object : WebViewClient() {

            // Normal image loading restored completely (no blocked images)
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                tvStatus.text = "Loaded: $url"
                dismissBanners()
                if (url != null && url.contains("winpesa.ng")) {
                    detectDynamicMaharaLink()
                    if (isAutoRunning && !isStepActive && url.trimEnd('/') == "https://winpesa.ng") {
                        tvStatus.text = "Account #${currentIndex + 1}..."
                        safeDelay(scaled(800, 250)) { runStep1_Login() }
                    }
                }
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame == true) {
                    safeDelay(1500) { view?.reload() }
                }
            }
        }
        webView.loadUrl("https://winpesa.ng")
    }

    private fun safeDelay(delayMs: Long, action: () -> Unit) {
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ action() }, delayMs)
    }

    private fun dismissBanners() {
        val js = "(function(){ Array.from(document.querySelectorAll('button,div,span,a')).forEach(e=>{ let t=(e.innerText||'').trim().toLowerCase(); if(t==='got it'||t==='accept'||t==='×'||t==='x') e.click(); }); })()"
        webView.evaluateJavascript(js, null)
    }

    private fun toggleAutoRun() {
        if (numbersList.isEmpty()) {
            Toast.makeText(this, "Load numbers first via ⚙️ DATA!", Toast.LENGTH_SHORT).show()
            return
        }
        if (isAutoRunning) {
            isAutoRunning = false
            isStepActive = false
            handler.removeCallbacksAndMessages(null)
            btnAutoRun.text = "▶️ AUTO"
            btnAutoRun.backgroundTintList = getColorStateList(android.R.color.holo_green_dark)
            tvStatus.text = "⏸️ Auto-Run Paused."
        } else {
            isAutoRunning = true
            isStepActive = false
            accountsProcessedInBatch = 0 // Reset batch session count on start
            btnAutoRun.text = "⏸️ PAUSE"
            btnAutoRun.backgroundTintList = getColorStateList(android.R.color.holo_red_dark)
            val limitStr = if (batchStopTarget > 0) " (Target: $batchStopTarget)" else ""
            tvStatus.text = "▶️ Auto-Run [${speedMultiplier}x]$limitStr: Starting #${currentIndex + 1}..."
            runStep1_Login()
        }
    }

    private fun detectDynamicMaharaLink() {
        val js = "(function(){ let l = Array.from(document.querySelectorAll('a,div')).find(e => (e.getAttribute('href')||'').includes('Mahara-Jackpot')); return l ? (l.getAttribute('href').startsWith('http') ? l.getAttribute('href') : 'https://winpesa.ng'+l.getAttribute('href')) : null; })()"
        webView.evaluateJavascript(js) { res ->
            val clean = res?.replace("\"", "")?.trim()
            if (!clean.isNullOrEmpty() && clean != "null" && clean.contains("Mahara-Jackpot")) activeMaharaUrl = clean
        }
    }

    private fun sanitizePhone(phone: String): String {
        var p = phone.trim().replace(" ", "").replace("-", "")
        if (p.startsWith("+234")) p = p.substring(4)
        else if (p.startsWith("234") && p.length > 10) p = p.substring(3)
        else if (p.startsWith("0") && p.length == 11) p = p.substring(1)
        return p
    }

    private fun sanitizePattern(raw: String): String {
        var p = raw.replace(Regex("^[#\\d]+[.:\\-\\s)]+"), "")
        p = p.replace(Regex("[^1X2HDAMhda]"), "").uppercase()
        return p
    }

    private fun showDataManagementMenu() {
        val limitDisplay = if (batchStopTarget == 0) "Unlimited" else "$batchStopTarget Accs"
        val options = arrayOf(
            "⚡ Set Speed: [${speedMultiplier}x]",
            "🛑 Set Auto-Pause Target: [$limitDisplay]",
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
                    0 -> showSpeedDialog()
                    1 -> showBatchTargetDialog()
                    2 -> showPasteDialog("NUMBERS")
                    3 -> { filePickTarget = "NUMBERS"; filePickerLauncher.launch("text/*") }
                    4 -> showPasteDialog("PATTERNS")
                    5 -> { filePickTarget = "PATTERNS"; filePickerLauncher.launch("text/*") }
                    6 -> showPasswordDialog()
                    7 -> showJumpToAccountDialog()
                    8 -> { currentIndex = 0; saveData(); updateUI(); Toast.makeText(this, "Reset to #1", Toast.LENGTH_SHORT).show() }
                    9 -> confirmWipeAllData()
                }
            }
            .setNegativeButton("Close", null).show()
    }

    private fun showSpeedDialog() {
        val speedLabels = arrayOf(
            "0.1x (Extra Slow / 10x Slower)",
            "0.25x (Very Slow / 4x Slower)",
            "0.5x (Half Speed / Patient)",
            "0.8x (Gentle / Smooth)",
            "1.0x (Normal / Standard)",
            "2.0x (Fast)",
            "3.0x (Turbo)",
            "5.0x (Super Turbo)",
            "7.0x (Ultra Fast)",
            "10.0x (Extreme)",
            "✏️ Custom (Type any number)"
        )
        val speedValues = floatArrayOf(0.1f, 0.25f, 0.5f, 0.8f, 1.0f, 2.0f, 3.0f, 5.0f, 7.0f, 10.0f)

        AlertDialog.Builder(this)
            .setTitle("⚡ Select Bot Speed (0.1x - 10x)")
            .setItems(speedLabels) { _, which ->
                if (which == speedLabels.size - 1) {
                    showCustomSpeedInput()
                } else {
                    speedMultiplier = speedValues[which]
                    saveData()
                    updateUI()
                    Toast.makeText(this, "Speed set to ${speedMultiplier}x!", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showCustomSpeedInput() {
        val input = EditText(this)
        input.hint = "e.g. 0.3, 1.5, 4.0, 8.5"
        input.inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        AlertDialog.Builder(this)
            .setTitle("Type Custom Speed")
            .setMessage("Enter multiplier between 0.1 and 10.0:")
            .setView(input)
            .setPositiveButton("Set") { _, _ ->
                val entered = input.text.toString().trim().toFloatOrNull()
                if (entered != null && entered in 0.1f..10.0f) {
                    speedMultiplier = entered
                    saveData()
                    updateUI()
                    Toast.makeText(this, "Speed set to ${speedMultiplier}x!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Enter a valid number between 0.1 and 10.0", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showBatchTargetDialog() {
        val input = EditText(this)
        input.hint = "e.g. 100, 500 (or 0 for unlimited)"
        input.setText(if (batchStopTarget > 0) batchStopTarget.toString() else "0")
        input.inputType = android.text.InputType.TYPE_CLASS_NUMBER
        AlertDialog.Builder(this)
            .setTitle("🛑 Set Auto-Pause Target")
            .setMessage("How many accounts to run before pausing automatically?\n(Type 0 to run all without stopping)")
            .setView(input)
            .setPositiveButton("Save Target") { _, _ ->
                val target = input.text.toString().trim().toIntOrNull() ?: 0
                batchStopTarget = target.coerceAtLeast(0)
                saveData()
                updateUI()
                val msg = if (batchStopTarget > 0) "Auto-run will pause after $batchStopTarget accounts!" else "Auto-run target set to Unlimited!"
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmWipeAllData() {
        AlertDialog.Builder(this)
            .setTitle("🗑️ Wipe All Data?")
            .setMessage("Delete all saved numbers, patterns and reset progress to #0?")
            .setPositiveButton("Yes, Wipe All") { _, _ ->
                numbersList.clear()
                patternsList.clear()
                currentIndex = 0
                getSharedPreferences("winpesa_touchbot", MODE_PRIVATE).edit().clear().apply()
                saveData(); updateUI()
                tvStatus.text = "All data cleared! Ready for new batch."
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun showJumpToAccountDialog() {
        val input = EditText(this)
        input.hint = "Account number e.g. 50"
        AlertDialog.Builder(this)
            .setTitle("Jump to Account #")
            .setView(input)
            .setPositiveButton("Go") { _, _ ->
                val num = input.text.toString().trim().toIntOrNull()
                if (num != null && num in 1..numbersList.size) {
                    currentIndex = num - 1; saveData(); updateUI()
                    Toast.makeText(this, "Jumped to #$num", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun showPasteDialog(type: String) {
        val input = EditText(this)
        input.setLines(8)
        input.hint = if (type == "NUMBERS") "Paste phone numbers (one per line)" else "Paste patterns (one per line)"
        AlertDialog.Builder(this)
            .setTitle(if (type == "NUMBERS") "Paste Numbers" else "Paste Patterns")
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
                saveData(); updateUI()
                Toast.makeText(this, "Saved ${lines.size} items!", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null).show()
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
                        saveData(); updateUI()
                        Toast.makeText(this, "Loaded ${lines.size} items safely!", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show() }
            }
        }.start()
    }

    private fun showPasswordDialog() {
        val input = EditText(this)
        input.setText(commonPassword)
        AlertDialog.Builder(this)
            .setTitle("🔑 Common Password")
            .setMessage("Set password for all accounts:")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                commonPassword = input.text.toString().trim()
                saveData(); Toast.makeText(this, "Saved!", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun updateUI() {
        val numCount = numbersList.size
        val patCount = patternsList.size
        val targetText = if (batchStopTarget > 0) " | Target: $batchStopTarget" else ""
        if (numCount == 0) {
            tvAccountInfo.text = "Acc: #0/0 [${speedMultiplier}x$targetText] | Tap DATA"
        } else {
            val phone = numbersList.getOrElse(currentIndex) { "None" }
            val pattern = if (patCount > 0) patternsList[currentIndex % patCount] else "None"
            tvAccountInfo.text = "Acc #${currentIndex + 1}/$numCount [${speedMultiplier}x$targetText]: $phone\nPat: $pattern"
        }
    }

    private fun saveData() {
        val prefs = getSharedPreferences("winpesa_touchbot", MODE_PRIVATE)
        prefs.edit()
            .putString("numbers", numbersList.joinToString("\n"))
            .putString("patterns", patternsList.joinToString("\n"))
            .putString("password", commonPassword)
            .putInt("index", currentIndex)
            .putFloat("speed_f", speedMultiplier)
            .putInt("batch_target", batchStopTarget)
            .apply()
    }

    private fun loadPersistedData() {
        val prefs = getSharedPreferences("winpesa_touchbot", MODE_PRIVATE)
        commonPassword = prefs.getString("password", "YourDefaultPassword123") ?: "YourDefaultPassword123"
        currentIndex = prefs.getInt("index", 0)
        speedMultiplier = prefs.getFloat("speed_f", 1.0f)
        batchStopTarget = prefs.getInt("batch_target", 0)
        val nums = prefs.getString("numbers", "") ?: ""
        if (nums.isNotEmpty()) { numbersList.clear(); numbersList.addAll(nums.split("\n").map { sanitizePhone(it) }) }
        val pats = prefs.getString("patterns", "") ?: ""
        if (pats.isNotEmpty()) { patternsList.clear(); patternsList.addAll(pats.split("\n")) }
        updateUI()
    }

    // ========================================================
    // 1. LOGIN
    // ========================================================
    private fun runStep1_Login() {
        if (numbersList.isEmpty()) {
            tvStatus.text = "Error: Load phone numbers first!"
            if (isAutoRunning) toggleAutoRun()
            return
        }

        isStepActive = true
        val phone = numbersList[currentIndex]
        tvStatus.text = "Step 1: Logging in $phone [${speedMultiplier}x]..."

        val waitReactInput = scaled(350, 160)

        val script = """
            (function() {
                function setReact(input, val) {
                    input.focus();
                    let old = input.value; input.value = val;
                    let trk = input._valueTracker; if (trk) trk.setValue(old);
                    input.dispatchEvent(new Event('input', { bubbles: true }));
                    input.dispatchEvent(new Event('change', { bubbles: true }));
                }

                let isLogged = Array.from(document.querySelectorAll('button, a, div')).some(e => (e.innerText||'').trim() === 'DEPOSIT');
                if (isLogged) return "ALREADY_LOGGED";

                let p = document.querySelector('input[placeholder*="Phone"], input[type="tel"], input[name*="phone"]');
                let pass = document.querySelector('input[placeholder*="Password"], input[type="password"]');

                if (p && pass) {
                    setReact(p, '$phone'); setReact(pass, '$commonPassword');
                    setTimeout(() => {
                        let btn = Array.from(document.querySelectorAll('button, div[role="button"]')).find(b => (b.innerText||'').trim() === 'LOGIN' && b.offsetWidth > 80);
                        if (btn && p.value.length >= 10) btn.click();
                    }, $waitReactInput);
                    return "SUBMITTED";
                } else {
                    let h = Array.from(document.querySelectorAll('button, a, div')).find(e => (e.innerText||'').trim() === 'LOGIN');
                    if (h) { h.click(); return "OPENED"; }
                    return "WAIT_MODAL";
                }
            })();
        """.trimIndent()

        var modalAttempts = 0
        val execLogin = object : Runnable {
            override fun run() {
                modalAttempts++
                webView.evaluateJavascript(script) { res ->
                    if (res?.contains("ALREADY_LOGGED") == true) {
                        tvStatus.text = "Step 1: Logged in!"
                        if (isAutoRunning) safeDelay(scaled(700, 250)) { runStep2_OpenMahara() }
                    } else if (res?.contains("SUBMITTED") == true) {
                        var verifyChecks = 0
                        val verify = object : Runnable {
                            override fun run() {
                                verifyChecks++
                                val checkJs = "(function(){ return Array.from(document.querySelectorAll('button,a,div')).some(e => (e.innerText||'').trim() === 'DEPOSIT') ? 'LOGGED' : 'WAIT'; })()"
                                webView.evaluateJavascript(checkJs) { status ->
                                    if (status?.contains("LOGGED") == true || verifyChecks >= 10) {
                                        tvStatus.text = "Step 1: Logged in $phone!"
                                        if (isAutoRunning) safeDelay(scaled(800, 300)) { runStep2_OpenMahara() }
                                    } else {
                                        handler.postDelayed(this, scaled(350, 150))
                                    }
                                }
                            }
                        }
                        handler.postDelayed(verify, scaled(600, 250))
                    } else if (modalAttempts < 15) {
                        handler.postDelayed(this, scaled(350, 150))
                    } else {
                        safeDelay(scaled(500, 250)) { runStep1_Login() }
                    }
                }
            }
        }
        execLogin.run()
    }

    // ========================================================
    // 2. MAHARA
    // ========================================================
    private fun runStep2_OpenMahara() {
        tvStatus.text = "Step 2: Loading Mahara..."
        webView.loadUrl(activeMaharaUrl)

        var attempts = 0
        val checkMahara = object : Runnable {
            override fun run() {
                attempts++
                val js = """
                    (function() {
                        let f = document.querySelector('iframe');
                        if (f && f.src && f.src.includes('aggrhub.com')) { window.location.href = f.src; return "JUMP"; }
                        let p = Array.from(document.querySelectorAll('button, div, a')).find(e => (e.innerText||'').trim() === 'PLAY');
                        if (p) { p.click(); return "PLAY"; }
                        return "WAIT";
                    })();
                """.trimIndent()

                webView.evaluateJavascript(js) { res ->
                    if (res?.contains("JUMP") == true) {
                        tvStatus.text = "Step 2: Entering board..."
                        var drawAttempts = 0
                        val checkDraws = object : Runnable {
                            override fun run() {
                                drawAttempts++
                                val countJs = "(function(){ return Array.from(document.querySelectorAll('*')).filter(e => { let l = (e.innerText||'').trim().split('\\n').map(s=>s.trim()).filter(Boolean); return l.length === 2 && l[0] === 'DRAW' && !isNaN(parseFloat(l[1])); }).length; })()"
                                webView.evaluateJavascript(countJs) { countStr ->
                                    val count = countStr?.replace("\"", "")?.toIntOrNull() ?: 0
                                    if (count >= 17) {
                                        tvStatus.text = "Step 2: Matches ready!"
                                        if (isAutoRunning) safeDelay(scaled(600, 250)) { runStep3_PickAndBet() }
                                    } else if (drawAttempts < 25) {
                                        handler.postDelayed(this, scaled(350, 150))
                                    }
                                }
                            }
                        }
                        handler.postDelayed(checkDraws, scaled(500, 250))
                    } else if (attempts < 20) {
                        handler.postDelayed(this, scaled(350, 150))
                    }
                }
            }
        }
        safeDelay(scaled(500, 200)) { checkMahara.run() }
    }

    // ========================================================
    // 3. BET
    // ========================================================
    private fun runStep3_PickAndBet() {
        if (patternsList.isEmpty()) {
            tvStatus.text = "Error: Load patterns first via ⚙️ DATA"
            if (isAutoRunning) toggleAutoRun()
            return
        }

        val rawPattern = patternsList[currentIndex % patternsList.size]
        val clean = sanitizePattern(rawPattern)

        val safe13 = clean.take(13).toMutableList()
        while (safe13.size < 13) safe13.add(listOf('H', 'D', 'A').random())

        val picks13 = safe13.map { if (it == 'D' || it == 'X') 1 else if (it == 'A' || it == '2') 2 else 0 }
        val final17 = if (clean.length >= 17) {
            picks13 + clean.substring(13, 17).map { if (it == 'D' || it == 'X') 1 else if (it == 'A' || it == '2') 2 else 0 }
        } else {
            picks13 + List(4) { listOf(0, 1, 2).random() }
        }

        val jsArray = final17.joinToString(prefix = "[", postfix = "]")
        val accNum = currentIndex + 1

        val selectAllJs = """
            (function() {
                window._betPlacedLocked = false;
                let clearBtn = Array.from(document.querySelectorAll('button, div')).find(e => (e.innerText||'').trim() === 'Clear');
                if (clearBtn) clearBtn.click();

                const picks = $jsArray;
                const draws = Array.from(document.querySelectorAll('*')).filter(e => {
                    let l = (e.innerText||'').trim().split('\n').map(s => s.trim()).filter(Boolean);
                    return l.length === 2 && l[0] === 'DRAW' && !isNaN(parseFloat(l[1]));
                });

                if (draws.length < 17) return "WAIT_RETRY:" + draws.length;

                for (let i = 0; i < 17; i++) {
                    let d = draws[i], ch = picks[i];
                    let target = (ch === 0 ? d.previousElementSibling : (ch === 1 ? d : d.nextElementSibling));
                    if (target) target.click();
                }

                return "ALL_17_SELECTED";
            })();
        """.trimIndent()

        var retries = 0
        val execSelect = object : Runnable {
            override fun run() {
                webView.evaluateJavascript(selectAllJs) { res ->
                    if (res?.contains("WAIT_RETRY") == true && retries < 15) {
                        retries++
                        tvStatus.text = "Loading matches ($retries/15)..."
                        handler.postDelayed(this, scaled(350, 150))
                    } else if (res?.contains("ALL_17_SELECTED") == true) {
                        tvStatus.text = "Step 3: All 17 picked! Placing bet..."
                        safeDelay(scaled(450, 200)) { triggerPlaceBet(accNum) }
                    } else {
                        tvStatus.text = "Step 3: $res"
                    }
                }
            }
        }
        execSelect.run()
    }

    private fun triggerPlaceBet(accNum: Int) {
        val clickBetJs = """
            (function() {
                if (window._betPlacedLocked) return "ALREADY_TAPPED";

                let candidates = Array.from(document.querySelectorAll('button, div, [role="button"], a')).filter(el => {
                    let txt = (el.innerText || '').trim().toLowerCase();
                    return txt.startsWith('place bet') && el.offsetWidth > 40;
                });

                if (candidates.length > 0) {
                    let btn = candidates[candidates.length - 1];
                    btn.click();
                    window._betPlacedLocked = true;
                    return "BET_CLICKED";
                }
                return "SEARCHING";
            })();
        """.trimIndent()

        var attempts = 0
        val pollPlaceBet = object : Runnable {
            override fun run() {
                attempts++
                webView.evaluateJavascript(clickBetJs) { res ->
                    if (res?.contains("BET_CLICKED") == true || res?.contains("ALREADY_TAPPED") == true) {
                        tvStatus.text = "SUCCESS: Acc #$accNum placed bet!"
                        // Safe server buffer protects against network latency before session wipe
                        val safeServerBuffer = (1500L / speedMultiplier).toLong().coerceAtLeast(650L)
                        if (isAutoRunning) safeDelay(safeServerBuffer) { runStep4_LogoutAndNext() }
                    } else if (attempts < 15) {
                        handler.postDelayed(this, 180)
                    } else {
                        tvStatus.text = "Step 3: Bet submitted."
                        val safeServerBuffer = (1200L / speedMultiplier).toLong().coerceAtLeast(650L)
                        if (isAutoRunning) safeDelay(safeServerBuffer) { runStep4_LogoutAndNext() }
                    }
                }
            }
        }
        pollPlaceBet.run()
    }

    // ========================================================
    // 4. NEXT & TARGET CHECK
    // ========================================================
    private fun runStep4_LogoutAndNext() {
        tvStatus.text = "Step 4: Clearing session..."
        isStepActive = false
        handler.removeCallbacksAndMessages(null)

        WebStorage.getInstance().deleteAllData()

        val cookieManager = CookieManager.getInstance()
        cookieManager.removeAllCookies {
            cookieManager.flush()
            runOnUiThread {
                accountsProcessedInBatch++

                // TARGET CHECK: Pause automatically if target limit is reached (e.g. 500)
                if (batchStopTarget > 0 && accountsProcessedInBatch >= batchStopTarget) {
                    tvStatus.text = "🎯 TARGET REACHED ($batchStopTarget accounts)! Auto-Run Paused."
                    Toast.makeText(this, "Batch Target of $batchStopTarget completed! Auto-Run Paused.", Toast.LENGTH_LONG).show()
                    if (isAutoRunning) toggleAutoRun()
                    return@runOnUiThread
                }

                if (numbersList.isNotEmpty()) {
                    if (currentIndex < numbersList.size - 1) {
                        currentIndex++
                    } else {
                        currentIndex = 0
                        saveData()
                        updateUI()
                        tvStatus.text = "ALL ACCOUNTS COMPLETED! 🎉 Ready for next week."
                        if (isAutoRunning) toggleAutoRun()
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