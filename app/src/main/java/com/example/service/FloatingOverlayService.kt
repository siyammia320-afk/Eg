package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.model.AccountEntity
import com.example.repository.AppRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

class FloatingOverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var repository: AppRepository

    private var floatingButtonView: View? = null
    private var historyOverlayView: View? = null
    private var igOverlayView: View? = null

    private var floatParams: WindowManager.LayoutParams? = null

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var otpPollerJob: Job? = null

    private var currentAccountsList: List<AccountEntity> = emptyList()
    private var currentMode: String = "FB"

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        repository = AppRepository(this)
        currentMode = repository.getFloatingMode()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        startForegroundServiceNotification()
        setupFloatingButton()
        startOtpPolling()
        observeAccounts()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val newMode = intent?.getStringExtra("MODE") ?: repository.getFloatingMode()
        if (newMode != currentMode) {
            currentMode = newMode
            repository.setFloatingMode(newMode)
            updateFloatingButtonMode()
        }
        repository.setServiceActive(true)
        return START_STICKY
    }

    override fun onDestroy() {
        repository.setServiceActive(false)
        otpPollerJob?.cancel()

        removeFloatingButton()
        removeHistoryOverlay()
        removeIgOverlay()

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundServiceNotification() {
        val channelId = "fb_creator_service_channel"
        val channelName = "Creator Overlay Service"

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Creator Service Running [$currentMode]")
            .setContentText("Floating Button Active")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        startForeground(1001, notification)
    }

    private fun setupFloatingButton() {
        val sizePx = (56 * resources.displayMetrics.density).toInt()

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        floatParams = WindowManager.LayoutParams(
            sizePx,
            sizePx,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 50
            y = 200
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(4, 4, 4, 4)
        }

        val textView = TextView(this).apply {
            textSize = 17f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
        }

        container.addView(textView)
        floatingButtonView = container

        applyFloatingButtonStyle(container, textView)

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isLongPress = false

        val longPressRunnable = Runnable {
            isLongPress = true
            if (currentMode == "FB") {
                showToast("Opening History...")
                showHistoryOverlay()
            } else {
                showToast("Opening IG Creator...")
                showIgOverlay()
            }
        }

        container.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = floatParams?.x ?: 0
                    initialY = floatParams?.y ?: 0
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isLongPress = false
                    mainHandler.postDelayed(longPressRunnable, 800)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = abs(event.rawX - initialTouchX)
                    val dy = abs(event.rawY - initialTouchY)
                    if (dx > 10 || dy > 10) {
                        mainHandler.removeCallbacks(longPressRunnable)
                    }
                    floatParams?.x = initialX + (event.rawX - initialTouchX).toInt()
                    floatParams?.y = initialY + (event.rawY - initialTouchY).toInt()
                    try {
                        windowManager.updateViewLayout(floatingButtonView, floatParams)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    mainHandler.removeCallbacks(longPressRunnable)
                    val dx = abs(event.rawX - initialTouchX)
                    val dy = abs(event.rawY - initialTouchY)
                    if (!isLongPress && dx < 10 && dy < 10) {
                        if (currentMode == "FB") {
                            triggerCreateAccount()
                        } else {
                            showIgOverlay()
                        }
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    mainHandler.removeCallbacks(longPressRunnable)
                    true
                }
                else -> false
            }
        }

        try {
            windowManager.addView(floatingButtonView, floatParams)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun applyFloatingButtonStyle(container: LinearLayout, textView: TextView) {
        val density = resources.displayMetrics.density
        if (currentMode == "FB") {
            val shape = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#1877F2")) // FB Blue
                setStroke((2 * density).toInt(), Color.WHITE)
            }
            container.background = shape
            textView.text = "FB"
        } else {
            val shape = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(
                    Color.parseColor("#833AB4"), // Purple
                    Color.parseColor("#FD1D1D"), // Red
                    Color.parseColor("#FCAF45")  // Orange
                )
            ).apply {
                shape = GradientDrawable.OVAL
                setStroke((2 * density).toInt(), Color.WHITE)
            }
            container.background = shape
            textView.text = "IG"
        }
    }

    private fun updateFloatingButtonMode() {
        val container = floatingButtonView as? LinearLayout ?: return
        val textView = container.getChildAt(0) as? TextView ?: return
        applyFloatingButtonStyle(container, textView)
        showToast("Switched to $currentMode Floating Mode")
    }

    private val isCreatingInService = java.util.concurrent.atomic.AtomicBoolean(false)

    private fun triggerCreateAccount() {
        if (!isCreatingInService.compareAndSet(false, true)) {
            showToast("⚠️ Account creation already in progress...")
            return
        }

        val selectedRange = repository.getSelectedRange()
        if (selectedRange.isBlank()) {
            isCreatingInService.set(false)
            showToast("❌ No live range selected! Open app to select range.")
            return
        }

        showToast("⏳ [1/3] Requesting number from live range $selectedRange...")
        showStatusNotification("⏳ Requesting Number", "Fetching number for live range $selectedRange...")

        serviceScope.launch {
            try {
                val phone = repository.fetchNumber(selectedRange)
                if (phone.isNullOrEmpty()) {
                    withContext(Dispatchers.Main) {
                        showToast("❌ FAILED!\nNo live number available for $selectedRange")
                        showStatusNotification("❌ Fetch Failed", "No live number available for range $selectedRange")
                    }
                    return@launch
                }

                withContext(Dispatchers.Main) {
                    showToast("📱 [2/3] Number Received: $phone\n⚡ Registering Facebook Account...")
                    showStatusNotification("📱 Number Received: $phone", "⚡ Registering Facebook Account now...")
                }

                val result = repository.createAccountForNumber(phone, selectedRange)
                withContext(Dispatchers.Main) {
                    if (result.success) {
                        copyToClipboard("NUMBER", result.phone)
                        showToast("✅ [3/3] CREATED SUCCESSFUL!\nNumber Copied: ${result.phone}\nUID: ${result.uid}")
                        showStatusNotification("✅ Account Created Successfully!", "Phone: ${result.phone} | UID: ${result.uid}")
                    } else {
                        showToast("❌ CREATION FAILED!\nNumber: ${result.phone}\n${result.error.ifEmpty { "Registration rejected" }}")
                        showStatusNotification("❌ Creation Failed", "Number: ${result.phone} | Error: ${result.error}")
                    }
                }
            } finally {
                isCreatingInService.set(false)
            }
        }
    }

    // ============================================================
    // IG FLOATING OVERLAY PANEL (Country Setup, Number, User, Random, Create -> "সাকসেস")
    // ============================================================
    private fun showIgOverlay() {
        if (igOverlayView != null) {
            removeIgOverlay()
        }

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val displayMetrics = resources.displayMetrics
        val width = (displayMetrics.widthPixels * 0.92).toInt()

        val dialogParams = WindowManager.LayoutParams(
            width,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        val density = displayMetrics.density

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val bg = GradientDrawable().apply {
                setColor(Color.parseColor("#1A162B")) // Dark IG canvas
                cornerRadius = 12 * density
                setStroke((1.5f * density).toInt(), Color.parseColor("#833AB4"))
            }
            background = bg
            setPadding((16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt())
        }

        // Header Row
        val headerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, (8 * density).toInt())
        }

        val titleText = TextView(this).apply {
            text = "📸 IG CREATOR (Meta)"
            setTextColor(Color.WHITE)
            textSize = 15f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val closeBtn = Button(this).apply {
            text = "✕"
            setTextColor(Color.parseColor("#FF5252"))
            setBackgroundColor(Color.TRANSPARENT)
            textSize = 18f
            setOnClickListener { removeIgOverlay() }
        }

        headerLayout.addView(titleText)
        headerLayout.addView(closeBtn)
        rootLayout.addView(headerLayout)

        // 1. Country Selection Row: BD, MG, SL, US
        val countryLabel = TextView(this).apply {
            text = "Country (দেশ নির্বাচন করুন):"
            setTextColor(Color.parseColor("#B0BEC5"))
            textSize = 12f
            setPadding(0, (4 * density).toInt(), 0, (4 * density).toInt())
        }
        rootLayout.addView(countryLabel)

        var selectedCountry = repository.getIgCountry()
        val countryRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, (8 * density).toInt())
        }

        val countryButtons = mutableListOf<Button>()
        val countries = listOf("BD" to "🇧🇩 BD", "MG" to "🇲🇬 MG", "SL" to "🇱🇰 SL", "US" to "🇺🇸 US")

        // Helper to refresh country button colors
        fun updateCountryButtons() {
            for ((idx, btn) in countryButtons.withIndex()) {
                val code = countries[idx].first
                val isSel = code == selectedCountry
                val btnBg = GradientDrawable().apply {
                    setColor(if (isSel) Color.parseColor("#E1306C") else Color.parseColor("#2A2440"))
                    cornerRadius = 6 * density
                }
                btn.background = btnBg
                btn.setTextColor(Color.WHITE)
            }
        }

        // Forward declaration of fields for random generator
        val phoneInput = EditText(this).apply {
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            textSize = 13f
            hint = "Phone Number (+880...)"
            val bg = GradientDrawable().apply {
                setColor(Color.parseColor("#25203A"))
                cornerRadius = 6 * density
                setStroke((1 * density).toInt(), Color.parseColor("#4A3F69"))
            }
            background = bg
            setPadding((12 * density).toInt(), (10 * density).toInt(), (12 * density).toInt(), (10 * density).toInt())
            setText(repository.generateRandomPhone(selectedCountry))
        }

        val userInput = EditText(this).apply {
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            textSize = 13f
            hint = "Username (e.g. user_name_123)"
            val bg = GradientDrawable().apply {
                setColor(Color.parseColor("#25203A"))
                cornerRadius = 6 * density
                setStroke((1 * density).toInt(), Color.parseColor("#4A3F69"))
            }
            background = bg
            setPadding((12 * density).toInt(), (10 * density).toInt(), (12 * density).toInt(), (10 * density).toInt())
            setText(repository.generateRandomIgUsername())
        }

        val nameInput = EditText(this).apply {
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            textSize = 13f
            hint = "Display Name"
            val bg = GradientDrawable().apply {
                setColor(Color.parseColor("#25203A"))
                cornerRadius = 6 * density
                setStroke((1 * density).toInt(), Color.parseColor("#4A3F69"))
            }
            background = bg
            setPadding((12 * density).toInt(), (10 * density).toInt(), (12 * density).toInt(), (10 * density).toInt())
            setText("Arafat")
        }

        for (item in countries) {
            val cBtn = Button(this).apply {
                text = item.second
                textSize = 11f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                layoutParams = LinearLayout.LayoutParams(0, (36 * density).toInt(), 1f).apply {
                    setMargins((2 * density).toInt(), 0, (2 * density).toInt(), 0)
                }
                setOnClickListener {
                    selectedCountry = item.first
                    repository.setIgCountry(selectedCountry)
                    updateCountryButtons()
                    phoneInput.setText(repository.generateRandomPhone(selectedCountry))
                }
            }
            countryButtons.add(cBtn)
            countryRow.addView(cBtn)
        }
        updateCountryButtons()
        rootLayout.addView(countryRow)

        // 2. Phone Number input
        val phoneLabel = TextView(this).apply {
            text = "Number (নাম্বার বসান):"
            setTextColor(Color.parseColor("#B0BEC5"))
            textSize = 12f
            setPadding(0, (4 * density).toInt(), 0, (2 * density).toInt())
        }
        rootLayout.addView(phoneLabel)
        rootLayout.addView(phoneInput)

        // 3. Username input
        val userLabel = TextView(this).apply {
            text = "Username (ইউজারনেম):"
            setTextColor(Color.parseColor("#B0BEC5"))
            textSize = 12f
            setPadding(0, (6 * density).toInt(), 0, (2 * density).toInt())
        }
        rootLayout.addView(userLabel)
        rootLayout.addView(userInput)

        // 4. Action Buttons Row: [ 🎲 RANDOM ] and [ ⚡ CREATE ]
        val actionsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, (10 * density).toInt(), 0, (6 * density).toInt())
        }

        val randomBtn = Button(this).apply {
            text = "🎲 RANDOM"
            setTextColor(Color.WHITE)
            textSize = 12f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            val bg = GradientDrawable().apply {
                setColor(Color.parseColor("#5E35B1"))
                cornerRadius = 6 * density
            }
            background = bg
            layoutParams = LinearLayout.LayoutParams(0, (44 * density).toInt(), 1f).apply {
                setMargins(0, 0, (4 * density).toInt(), 0)
            }
            setOnClickListener {
                userInput.setText(repository.generateRandomIgUsername())
                phoneInput.setText(repository.generateRandomPhone(selectedCountry))
                showToast("🎲 Generated Random User & Number")
            }
        }

        val statusBox = TextView(this).apply {
            visibility = View.GONE
            textSize = 16f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding((8 * density).toInt(), (8 * density).toInt(), (8 * density).toInt(), (8 * density).toInt())
        }

        val createBtn = Button(this).apply {
            text = "⚡ CREATE"
            setTextColor(Color.WHITE)
            textSize = 12f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            val bg = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.parseColor("#833AB4"), Color.parseColor("#FD1D1D"), Color.parseColor("#FCAF45"))
            ).apply {
                cornerRadius = 6 * density
            }
            background = bg
            layoutParams = LinearLayout.LayoutParams(0, (44 * density).toInt(), 1.2f).apply {
                setMargins((4 * density).toInt(), 0, 0, 0)
            }
            setOnClickListener {
                val phone = phoneInput.text.toString().trim()
                val user = userInput.text.toString().trim()
                val disp = nameInput.text.toString().trim()

                if (phone.isBlank()) {
                    showToast("❌ Enter phone number!")
                    return@setOnClickListener
                }
                if (user.isBlank()) {
                    showToast("❌ Enter username!")
                    return@setOnClickListener
                }

                statusBox.visibility = View.VISIBLE
                statusBox.text = "⏳ Creating..."
                statusBox.setTextColor(Color.YELLOW)
                isEnabled = false

                serviceScope.launch {
                    val result = repository.createIgAccount(
                        phone = phone,
                        username = user,
                        displayName = disp,
                        countryCode = selectedCountry
                    )

                    withContext(Dispatchers.Main) {
                        isEnabled = true
                        if (result.success) {
                            // As explicitly requested: strictly show "সাকসেস" only!
                            statusBox.text = "✅ সাকসেস"
                            statusBox.setTextColor(Color.parseColor("#00E676")) // Bright Green
                            showToast("✅ সাকসেস")
                        } else {
                            statusBox.text = "❌ ${result.error.ifBlank { "Creation Failed" }}"
                            statusBox.setTextColor(Color.parseColor("#FF5252"))
                            showToast("❌ ${result.error}")
                        }
                    }
                }
            }
        }

        actionsRow.addView(randomBtn)
        actionsRow.addView(createBtn)
        rootLayout.addView(actionsRow)

        // 5. Success / Status Banner
        rootLayout.addView(statusBox)

        igOverlayView = rootLayout

        try {
            windowManager.addView(igOverlayView, dialogParams)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun removeIgOverlay() {
        if (igOverlayView != null) {
            try {
                windowManager.removeView(igOverlayView)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            igOverlayView = null
        }
    }

    private fun showHistoryOverlay() {
        if (historyOverlayView != null) {
            removeHistoryOverlay()
        }

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val displayMetrics = resources.displayMetrics
        val width = (displayMetrics.widthPixels * 0.90).toInt()
        val height = (displayMetrics.heightPixels * 0.75).toInt()

        val dialogParams = WindowManager.LayoutParams(
            width,
            height,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#1E1E2C"))
            setPadding(32, 32, 32, 32)
        }

        val headerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, 16)
        }

        val titleText = TextView(this).apply {
            text = "HISTORY"
            setTextColor(Color.WHITE)
            textSize = 16f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val contentContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val refreshOtpBtn = Button(this).apply {
            text = "OTP REFRESH"
            setTextColor(Color.WHITE)
            textSize = 10f
            val bg = GradientDrawable().apply {
                setColor(Color.parseColor("#1877F2"))
                cornerRadius = 4 * displayMetrics.density
            }
            background = bg
            setOnClickListener {
                serviceScope.launch {
                    withContext(Dispatchers.Main) { showToast("Checking OTPs...") }
                    repository.checkAndProcessOtps()
                    withContext(Dispatchers.Main) {
                        showToast("OTP Refresh Done!")
                        populateHistoryView(contentContainer)
                    }
                }
            }
        }

        val closeBtn = Button(this).apply {
            text = "✕"
            setTextColor(Color.RED)
            setBackgroundColor(Color.TRANSPARENT)
            textSize = 20f
            setOnClickListener {
                removeHistoryOverlay()
            }
        }

        headerLayout.addView(titleText)
        headerLayout.addView(refreshOtpBtn)
        headerLayout.addView(closeBtn)
        rootLayout.addView(headerLayout)

        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        populateHistoryView(contentContainer)
        scrollView.addView(contentContainer)
        rootLayout.addView(scrollView)

        historyOverlayView = rootLayout

        try {
            windowManager.addView(historyOverlayView, dialogParams)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun populateHistoryView(container: LinearLayout) {
        container.removeAllViews()
        val density = resources.displayMetrics.density

        if (currentAccountsList.isEmpty()) {
            val emptyText = TextView(this).apply {
                text = "No accounts recorded yet."
                setTextColor(Color.GRAY)
                textSize = 14f
                gravity = Gravity.CENTER
                setPadding(0, 32, 0, 0)
            }
            container.addView(emptyText)
            return
        }

        for (account in currentAccountsList) {
            val cardView = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                val bg = GradientDrawable().apply {
                    setColor(Color.parseColor("#2A2A3C"))
                    cornerRadius = 8 * density
                }
                background = bg
                setPadding((12 * density).toInt(), (10 * density).toInt(), (12 * density).toInt(), (10 * density).toInt())
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, 0, (10 * density).toInt())
                }
                layoutParams = lp
            }

            val tvPhone = TextView(this).apply {
                text = "📱 ${account.phone}"
                setTextColor(Color.WHITE)
                textSize = 14f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }

            val tvUid = TextView(this).apply {
                text = "🆔 UID: ${account.uid}"
                setTextColor(Color.LTGRAY)
                textSize = 12f
            }

            val otpVal = account.otp.orEmpty()
            val tvOtp = TextView(this).apply {
                text = if (otpVal.isNotBlank()) "🔑 OTP: $otpVal" else "🔑 OTP: Waiting..."
                setTextColor(if (otpVal.isNotBlank()) Color.GREEN else Color.YELLOW)
                textSize = 12f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }

            cardView.addView(tvPhone)
            cardView.addView(tvUid)
            cardView.addView(tvOtp)

            val copyRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, (8 * density).toInt(), 0, 0)
            }

            val btnParams = LinearLayout.LayoutParams(0, (36 * density).toInt(), 1f).apply {
                setMargins((2 * density).toInt(), 0, (2 * density).toInt(), 0)
            }

            val btnNum = Button(this).apply {
                text = "NUMBER"
                setTextColor(Color.WHITE)
                textSize = 11f
                val bg = GradientDrawable().apply {
                    setColor(Color.parseColor("#1877F2"))
                    cornerRadius = 4 * density
                }
                background = bg
                layoutParams = btnParams
                setOnClickListener {
                    copyToClipboard("NUMBER", account.phone)
                }
            }

            val btnCookie = Button(this).apply {
                text = "COOKIE"
                setTextColor(Color.WHITE)
                textSize = 11f
                val bg = GradientDrawable().apply {
                    setColor(Color.parseColor("#2E7D32"))
                    cornerRadius = 4 * density
                }
                background = bg
                layoutParams = btnParams
                setOnClickListener {
                    val cookieStr = com.example.api.NetworkClient.formatCleanCookie(
                        rawCookie = account.cookie,
                        uid = account.uid
                    )
                    copyToClipboard("COOKIE", cookieStr)
                }
            }

            val btnUid = Button(this).apply {
                text = "UID"
                setTextColor(Color.WHITE)
                textSize = 11f
                val bg = GradientDrawable().apply {
                    setColor(Color.parseColor("#F57C00"))
                    cornerRadius = 4 * density
                }
                background = bg
                layoutParams = btnParams
                setOnClickListener {
                    copyToClipboard("UID", account.uid)
                }
            }

            copyRow.addView(btnNum)
            copyRow.addView(btnCookie)
            copyRow.addView(btnUid)

            cardView.addView(copyRow)
            container.addView(cardView)
        }
    }

    private fun copyToClipboard(label: String, text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        showToast("Copied $label: $text")
    }

    private fun removeHistoryOverlay() {
        if (historyOverlayView != null) {
            try {
                windowManager.removeView(historyOverlayView)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            historyOverlayView = null
        }
    }

    private fun removeFloatingButton() {
        if (floatingButtonView != null) {
            try {
                windowManager.removeView(floatingButtonView)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            floatingButtonView = null
        }
    }

    private fun startOtpPolling() {
        otpPollerJob?.cancel()
        otpPollerJob = serviceScope.launch {
            while (isActive) {
                try {
                    val otps = repository.checkAndProcessOtps()
                    for (otp in otps) {
                        withContext(Dispatchers.Main) {
                            showToast("⚡ OTP RECEIVED: ${otp.otpCode}\nPhone: ${otp.number}")
                            showStatusNotification("⚡ OTP: ${otp.otpCode}", "Received for ${otp.number}")
                            copyToClipboard("OTP", otp.otpCode)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                delay(2500)
            }
        }
    }

    private fun observeAccounts() {
        serviceScope.launch {
            repository.allAccounts.collectLatest { list ->
                currentAccountsList = list
                withContext(Dispatchers.Main) {
                    if (historyOverlayView != null) {
                        val scrollView = (historyOverlayView as? LinearLayout)?.getChildAt(1) as? ScrollView
                        val contentContainer = scrollView?.getChildAt(0) as? LinearLayout
                        if (contentContainer != null) {
                            populateHistoryView(contentContainer)
                        }
                    }
                }
            }
        }
    }

    private fun showToast(msg: String) {
        mainHandler.post {
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showStatusNotification(title: String, message: String) {
        val channelId = "fb_creator_service_channel"
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
