package com.example.antiprocrastinacion

import android.app.*
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.*
import android.content.pm.ServiceInfo
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.os.*
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.*
import androidx.core.app.NotificationCompat

class TrackingService : Service() {

    private lateinit var prefs: SharedPreferences
    private var accumulatedMillis: Long = 0
    private var isBlocked = false
    private val handler = Handler(Looper.getMainLooper())
    private var pollingRunnable: Runnable? = null
    private var lastAccumulationTime: Long = 0

    // Estado del foreground para cálculo de tiempo de uso
    private var lastEventTime: Long = 0
    private var foregroundPackage: String? = null
    private var foregroundStartTime: Long = 0

    // Overlay de bloqueo
    private var overlayView: View? = null
    private var windowManager: WindowManager? = null

    // Contador regresivo del bloqueo
    private var blockStartTime: Long = 0
    private var countdownRunnable: Runnable? = null
    private var countdownText: TextView? = null

    // Vistas del overlay de bloqueo (árbol/resistencia)
    private var blockTreeEmoji: TextView? = null
    private var blockTreeProgressBar: ProgressBar? = null
    private var blockTreeProgressText: TextView? = null

    // ─── MODO CONCENTRACIÓN ──────────────────────────────────────────────
    private var isConcentrationMode = false
    private var concentrationPhase: String = "studying"
    private var concentrationCurrentBlock = 0
    private var concentrationTotalBlocks = 0
    private var concentrationBlockDurationSecs = 0
    private var concentrationBlockStartTime: Long = 0
    private var concentrationRecommendation: String = ""

    // Referencias a vistas dinámicas del overlay de concentración
    private var concPhaseText: TextView? = null
    private var concBlockText: TextView? = null
    private var concRecommendationText: TextView? = null
    private var concCountdownText: TextView? = null
    private var concIconText: TextView? = null
    private var concTitleText: TextView? = null
    private var concMessageText: TextView? = null
    private var concRestanteLabel: TextView? = null
    private var concCancelBtn: TextView? = null
    private var concTreeEmojiText: TextView? = null
    private var concTreeProgressText: TextView? = null
    private var concTreeProgressBar: ProgressBar? = null

    // ─── RECEIVERS ──────────────────────────────────────────────────────────

    private val showOverlayReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (isBlocked) {
                Log.d("TrackingService", "SHOW_OVERLAY recibido → mostrando overlay")
                if (overlayView != null) {
                    overlayView?.visibility = View.VISIBLE
                } else if (isConcentrationMode) {
                    showConcentrationOverlay()
                } else {
                    showBlockOverlay()
                }
            }
        }
    }

    private val hideOverlayReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (isBlocked) {
                Log.d("TrackingService", "HIDE_OVERLAY recibido → ocultando overlay temporalmente")
                val pkg = intent?.getStringExtra("package")
                if (pkg != null) {
                    lastKnownForegroundPkg = pkg
                }
                overlayView?.visibility = View.GONE
            }
        }
    }

    private val concentrationModeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val active = intent?.getBooleanExtra("active", false) ?: return
            val phase = intent.getStringExtra("phase") ?: ""
            Log.d("TrackingService", "CONCENTRATION_MODE: active=$active phase=$phase")

            if (!active) {
                finishConcentrationMode()
                return
            }

            isConcentrationMode = true
            isBlocked = true
            concentrationPhase = phase
            concentrationCurrentBlock = intent.getIntExtra("currentBlock", 0)
            concentrationTotalBlocks = intent.getIntExtra("totalBlocks", 0)
            concentrationBlockDurationSecs = intent.getIntExtra("blockDurationSeconds", 0)
            concentrationRecommendation = intent.getStringExtra("restRecommendation") ?: ""

            if (overlayView == null) {
                showConcentrationOverlay()
                startConcentrationCountdown()
            } else {
                updateConcentrationOverlay()
                startConcentrationCountdown()
            }
        }
    }

    // ─── CICLO DE VIDA ───────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        Log.d("TrackingService", "onCreate: iniciando servicio")
        prefs = getSharedPreferences("block_prefs", MODE_PRIVATE)
        val seconds = prefs.getInt("accumulated_seconds", 0)
        accumulatedMillis = seconds * 1000L
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        isBlocked = prefs.getBoolean("block_active", false)

        if (isBlocked) {
            blockStartTime = prefs.getLong("block_start_time", System.currentTimeMillis())
            val elapsed = System.currentTimeMillis() - blockStartTime
            if (elapsed >= BLOCK_DURATION_MS) {
                releaseBlock()
            } else {
                showBlockOverlay()
                startCountdown()
            }
            startPolling()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            registerReceiver(showOverlayReceiver, IntentFilter(SHOW_OVERLAY_ACTION), RECEIVER_NOT_EXPORTED)
            registerReceiver(hideOverlayReceiver, IntentFilter(HIDE_OVERLAY_ACTION), RECEIVER_NOT_EXPORTED)
            registerReceiver(concentrationModeReceiver, IntentFilter(CONCENTRATION_MODE_ACTION), RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(showOverlayReceiver, IntentFilter(SHOW_OVERLAY_ACTION))
            registerReceiver(hideOverlayReceiver, IntentFilter(HIDE_OVERLAY_ACTION))
            registerReceiver(concentrationModeReceiver, IntentFilter(CONCENTRATION_MODE_ACTION))
        }

        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(1, notification)
        }

        lastEventTime = System.currentTimeMillis()
        foregroundStartTime = lastEventTime
        lastAccumulationTime = lastEventTime
        Log.d("TrackingService", "onCreate: servicio iniciado correctamente")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            when (it.action) {
                ACTION_START  -> startPolling()
                ACTION_STOP   -> stopPolling()
                ACTION_PAUSE  -> pausePolling()
                ACTION_RESUME -> resumePolling()
            }
        }

        if (isBlocked && pollingRunnable == null) {
            startPolling()
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(showOverlayReceiver)
        unregisterReceiver(hideOverlayReceiver)
        unregisterReceiver(concentrationModeReceiver)
        countdownRunnable?.let { handler.removeCallbacks(it) }
        countdownRunnable = null
        hideBlockOverlay()
        pausePolling()
        pollingRunnable = null
        Log.d("TrackingService", "onDestroy: servicio detenido")
    }

    // ─── CICLO PRINCIPAL DE SONDEO (conteo + respaldo de visibilidad) ─────

    private fun startPolling() {
        if (pollingRunnable != null) {
            Log.d("TrackingService", "Polling ya activo, ignorando")
            return
        }
        Log.d("TrackingService", "Iniciando ciclo de monitoreo")
        pollingRunnable = object : Runnable {
            override fun run() {
                // 1. Sumar tiempo en apps distractoras (solo si no estamos bloqueados)
                val millis = updateUsage()
                if (!isBlocked && millis > 0) {
                    accumulatedMillis += millis
                    lastAccumulationTime = System.currentTimeMillis()
                    val accumulatedSeconds = (accumulatedMillis / 1000).toInt()
                    prefs.edit().putInt("accumulated_seconds", accumulatedSeconds).apply()
                    Log.d("TrackingService", "Segundos acumulados: $accumulatedSeconds / $DISTRACT_LIMIT")
                    if (accumulatedSeconds >= DISTRACT_LIMIT) {
                        triggerBlock()
                    }
                }

                // 2. Reconciliación autoritativa del estado del overlay.
                //    El AccessibilityService puede perder eventos (gesture nav,
                //    fast switching, API 30+). Este ciclo verifica cada 2 s que
                //    la visibilidad del overlay coincida con la app en primer plano.
                if (isBlocked) {
                    val allowedApps = prefs.getStringSet("allowed_apps", emptySet()) ?: emptySet()
                    val currentPkg = getForegroundPackageSafe()
                    val isAllowed = isAppAllowed(currentPkg, allowedApps)

                    if (!isAllowed) {
                        // App distractor → overlay DEBE ser visible
                        val overlayMissing = overlayView == null ||
                                !(overlayView?.isAttachedToWindow ?: false)
                        if (overlayMissing) {
                            if (isConcentrationMode) {
                                showConcentrationOverlay()
                            } else {
                                showBlockOverlay()
                            }
                            Log.d("TrackingService", "Polling: overlay recreado, app distractor: $currentPkg")
                        } else if (overlayView?.visibility != View.VISIBLE) {
                            overlayView?.visibility = View.VISIBLE
                            Log.d("TrackingService", "Polling: overlay restaurado a VISIBLE, app distractor: $currentPkg")
                        }
                    } else {
                        // App permitida → overlay DEBE estar oculto
                        if (overlayView != null && overlayView?.visibility == View.VISIBLE) {
                            overlayView?.visibility = View.GONE
                            Log.d("TrackingService", "Polling: overlay ocultado, app permitida: $currentPkg")
                        }
                    }
                }

                // 3. Reinicio por inactividad
                if (!isBlocked && accumulatedMillis > 0 &&
                    System.currentTimeMillis() - lastAccumulationTime >= INACTIVITY_RESET_MS
                ) {
                    Log.d("TrackingService", "Inactividad de distractoras detectada → reiniciando contador")
                    resetCounter()
                }

                handler.postDelayed(this, 2000)
            }
        }
        handler.post(pollingRunnable!!)
    }

    private var lastKnownForegroundPkg: String? = null

    private fun getForegroundPackageSafe(): String? {
        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return lastKnownForegroundPkg
        val now = System.currentTimeMillis()
        val events: UsageEvents = try {
            usageStatsManager.queryEvents(now - 10000, now)
        } catch (e: Exception) {
            return lastKnownForegroundPkg
        }
        var lastResumed: String? = null
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                lastResumed = event.packageName
            }
        }
        if (lastResumed != null) {
            lastKnownForegroundPkg = lastResumed
        }
        return lastKnownForegroundPkg
    }

    private fun pausePolling() {
        pollingRunnable?.let { handler.removeCallbacks(it) }
    }

    private fun stopPolling() {
        pausePolling()
        stopSelf()
    }

    private fun resumePolling() {
        if (pollingRunnable == null) {
            startPolling()
        } else {
            handler.post(pollingRunnable!!)
        }
    }

    // ─── CONTEO DE TIEMPO ─────────────────────────────────────────────────────

    private fun updateUsage(): Long {
        val distractingApps = prefs.getStringSet("distracting_apps", emptySet()) ?: return 0
        if (distractingApps.isEmpty()) return 0

        // No acumular tiempo si la pantalla está apagada
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!powerManager.isInteractive) {
            lastEventTime = System.currentTimeMillis()
            return 0
        }

        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return 0

        val now = System.currentTimeMillis()
        if (lastEventTime >= now) return 0

        val events: UsageEvents = try {
            usageStatsManager.queryEvents(lastEventTime, now)
        } catch (e: Exception) {
            Log.e("TrackingService", "Error consultando eventos: ${e.message}")
            return 0
        }

        val event = UsageEvents.Event()
        var addedMillis = 0L

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    if (foregroundPackage in distractingApps) {
                        addedMillis += (event.timeStamp - foregroundStartTime)
                    }
                    foregroundPackage = event.packageName
                    foregroundStartTime = event.timeStamp
                }
                UsageEvents.Event.ACTIVITY_PAUSED,
                UsageEvents.Event.ACTIVITY_STOPPED -> {
                    if (event.packageName == foregroundPackage &&
                        foregroundPackage in distractingApps
                    ) {
                        addedMillis += (event.timeStamp - foregroundStartTime)
                        foregroundPackage = null
                    }
                }
            }
        }

        if (foregroundPackage in distractingApps) {
            addedMillis += (now - foregroundStartTime)
            foregroundStartTime = now
        }

        lastEventTime = now
        return addedMillis
    }

    // ─── GESTIÓN DEL BLOQUEO ────────────────────────────────────────────────

    private fun triggerBlock() {
        Log.d("TrackingService", "Límite alcanzado → activando bloqueo")
        blockStartTime = System.currentTimeMillis()
        isBlocked = true
        prefs.edit()
            .putBoolean("block_active", true)
            .putLong("block_start_time", blockStartTime)
            .apply()
        showBlockOverlay()
        startCountdown()
    }

    private fun startCountdown() {
        countdownRunnable?.let { handler.removeCallbacks(it) }
        countdownRunnable = object : Runnable {
            override fun run() {
                val elapsed = System.currentTimeMillis() - blockStartTime
                val remaining = BLOCK_DURATION_MS - elapsed
                if (remaining <= 0) {
                    releaseBlock()
                    return
                }
                val totalSecs = (remaining / 1000).toInt()
                val mins = totalSecs / 60
                val secs = totalSecs % 60
                countdownText?.text = String.format("%02d:%02d", mins, secs)
                updateBlockTree(remaining)
                handler.postDelayed(this, 1000)
            }
        }
        handler.post(countdownRunnable!!)
    }

    private fun releaseBlock() {
        Log.d("TrackingService", "Bloqueo finalizado → liberando")
        countdownRunnable?.let { handler.removeCallbacks(it) }
        countdownRunnable = null
        hideBlockOverlay()
        isBlocked = false
        accumulatedMillis = 0
        prefs.edit()
            .putBoolean("block_active", false)
            .putInt("accumulated_seconds", 0)
            .remove("block_start_time")
            .apply()

        foregroundPackage = getForegroundPackageSafe()
        foregroundStartTime = System.currentTimeMillis()
        lastEventTime = foregroundStartTime
        lastAccumulationTime = foregroundStartTime
    }

    private fun resetCounter() {
        accumulatedMillis = 0
        lastAccumulationTime = System.currentTimeMillis()
        prefs.edit().putInt("accumulated_seconds", 0).apply()
        foregroundPackage = getForegroundPackageSafe()
        foregroundStartTime = System.currentTimeMillis()
        lastEventTime = foregroundStartTime
    }

    private fun isAppAllowed(pkg: String?, allowedApps: Set<String>): Boolean {
        if (pkg == null) return false
        if (pkg == "com.example.antiprocrastinacion") return true
        if (pkg in allowedApps) return true
        
        val currentUid = try {
            packageManager.getApplicationInfo(pkg, 0).uid
        } catch (e: Exception) {
            return false
        }
        return allowedApps.any { allowedPkg ->
            try {
                packageManager.getApplicationInfo(allowedPkg, 0).uid == currentUid
            } catch (e: Exception) {
                false
            }
        }
    }

    // ─── OVERLAY DE BLOQUEO (normal) ──────────────────────────────────────────

    private fun showBlockOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            Log.e("TrackingService", "Sin permiso de superposición (SYSTEM_ALERT_WINDOW)")
            return
        }

        if (overlayView != null) {
            if (overlayView?.isAttachedToWindow == true) {
                overlayView?.visibility = View.VISIBLE
                return
            } else {
                hideBlockOverlay()
            }
        }

        val allowedApps = prefs.getStringSet("allowed_apps", emptySet()) ?: emptySet()
        val appList = allowedApps.toList()

        val scrollView = ScrollView(this).apply {
            isFillViewport = true
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#1B2320"))
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(36, 52, 36, 36)
            descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
            @Suppress("DEPRECATION")
            systemUiVisibility = (
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
            )
        }

        // ─── 1. STATUS BADGE ────────────────────────────────────────
        val badgeBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 20f
            setColor(Color.parseColor("#2A1A18"))
            setStroke(1, Color.parseColor("#3D2420"))
        }
        val badge = TextView(this).apply {
            text = "BLOQUEADO"
            textSize = 11f
            setTextColor(Color.parseColor("#E8847A"))
            gravity = Gravity.CENTER
            setPadding(24, 8, 24, 8)
            background = badgeBg
            letterSpacing = 0.15f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        root.addView(badge)

        // ─── 2. LOCK ICON IN CIRCLE ────────────────────────────────
        val iconOuter = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            setPadding(0, 28, 0, 20)
        }
        val iconBg = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            setPadding(24, 24, 24, 24)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#252D2A"))
                setStroke(2, Color.parseColor("#3A4A43"))
            }
        }
        val lockIcon = TextView(this).apply {
            text = "\uD83D\uDD12"
            textSize = 42f
            gravity = Gravity.CENTER
        }
        iconBg.addView(lockIcon)
        iconOuter.addView(iconBg)
        root.addView(iconOuter)

        // ─── 3. COUNTDOWN TIMER ─────────────────────────────────────
        val countdownTv = TextView(this).apply {
            text = "00:00"
            textSize = 56f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.create("monospace", android.graphics.Typeface.BOLD)
            letterSpacing = 0.04f
        }
        val blockElapsed = System.currentTimeMillis() - blockStartTime
        val blockRemaining = (BLOCK_DURATION_MS - blockElapsed).coerceAtLeast(0)
        val blockSecs = (blockRemaining / 1000).toInt()
        countdownTv.text = String.format("%02d:%02d", blockSecs / 60, blockSecs % 60)
        root.addView(countdownTv)
        countdownText = countdownTv

        val restanteLabel = TextView(this).apply {
            text = "tiempo restante"
            textSize = 13f
            setTextColor(Color.parseColor("#77FFFFFF"))
            gravity = Gravity.CENTER
            setPadding(0, 4, 0, 8)
        }
        root.addView(restanteLabel)

        // ─── 4. MESSAGE ─────────────────────────────────────────────
        val messageText = TextView(this).apply {
            text = "Has excedido el límite de uso de apps distractoras"
            textSize = 14f
            setTextColor(Color.parseColor("#99FFFFFF"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 24)
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        root.addView(messageText)

        // ─── 5. DIVIDER ─────────────────────────────────────────────
        root.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1
            ).apply { setMargins(0, 0, 0, 24) }
            setBackgroundColor(Color.parseColor("#1EFFFFFF"))
        })

        // ─── 6. TREE / RESISTANCE SECTION ───────────────────────────
        val treeContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, 4, 0, 24)
        }

        val treeEmoji = TextView(this).apply {
            text = "🌱"
            textSize = 48f
            gravity = Gravity.CENTER
        }
        treeContainer.addView(treeEmoji)
        blockTreeEmoji = treeEmoji

        val treeBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
            layoutParams = LinearLayout.LayoutParams(220, 10).apply { topMargin = 12 }
            progressTintList = ColorStateList.valueOf(Color.parseColor("#7FB98A"))
            progressBackgroundTintList = ColorStateList.valueOf(Color.parseColor("#22FFFFFF"))
        }
        treeContainer.addView(treeBar)
        blockTreeProgressBar = treeBar

        val treeLabel = TextView(this).apply {
            text = "Resistencia: 0%"
            textSize = 12f
            setTextColor(Color.parseColor("#99FFFFFF"))
            gravity = Gravity.CENTER
            setPadding(0, 6, 0, 0)
        }
        treeContainer.addView(treeLabel)
        blockTreeProgressText = treeLabel

        root.addView(treeContainer)

        // Initialize tree progress from current time
        updateBlockTree(blockRemaining)

        // ─── 7. DIVIDER ─────────────────────────────────────────────
        root.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1
            ).apply { setMargins(0, 0, 0, 20) }
            setBackgroundColor(Color.parseColor("#1EFFFFFF"))
        })

        // ─── 8. ALLOWED APPS ────────────────────────────────────────
        if (appList.isNotEmpty()) {
            val appsLabel = TextView(this).apply {
                text = "Apps permitidas"
                textSize = 13f
                setTextColor(Color.parseColor("#88FFFFFF"))
                gravity = Gravity.CENTER
                letterSpacing = 0.1f
                setPadding(0, 0, 0, 14)
            }
            root.addView(appsLabel)

            val gridContainer = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
            }

            var currentRow: LinearLayout? = null
            for ((index, pkg) in appList.withIndex()) {
                if (index % 2 == 0) {
                    currentRow = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER
                    }
                }

                val appInfo = try {
                    val info = packageManager.getApplicationInfo(pkg, 0)
                    val label = packageManager.getApplicationLabel(info).toString()
                    val drawable = info.loadIcon(packageManager)
                    val bitmap = if (drawable is BitmapDrawable) {
                        drawable.bitmap
                    } else {
                        val bmp = Bitmap.createBitmap(
                            drawable.intrinsicWidth.coerceAtLeast(1),
                            drawable.intrinsicHeight.coerceAtLeast(1),
                            Bitmap.Config.ARGB_8888
                        )
                        val canvas = Canvas(bmp)
                        drawable.setBounds(0, 0, canvas.width, canvas.height)
                        drawable.draw(canvas)
                        bmp
                    }
                    val scaled = Bitmap.createScaledBitmap(bitmap, 64, 64, true)
                    val iconDrawable = BitmapDrawable(resources, scaled)
                    Pair(label, iconDrawable as android.graphics.drawable.Drawable?)
                } catch (e: Exception) {
                    val fallbackName = try {
                        val info = packageManager.getApplicationInfo(pkg, 0)
                        packageManager.getApplicationLabel(info).toString()
                    } catch (_: Exception) {
                        "App"
                    }
                    Pair(fallbackName, null)
                }

                val cardBg = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 16f
                    setColor(Color.parseColor("#252D2A"))
                    setStroke(1, Color.parseColor("#323D39"))
                }

                val cardLayout = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    setPadding(10, 12, 10, 12)

                    val lp = LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        0.5f
                    ).apply { setMargins(5, 5, 5, 5) }
                    layoutParams = lp
                    background = cardBg
                    clipToOutline = true

                    setOnClickListener {
                        val launchIntent = packageManager.getLaunchIntentForPackage(pkg)
                        if (launchIntent != null) {
                            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            startActivity(launchIntent)
                            lastKnownForegroundPkg = pkg
                            overlayView?.visibility = View.GONE
                            Log.d("TrackingService", "App blanda abierta: $pkg, overlay oculto")
                        }
                    }
                }

                val iconView = ImageView(this).apply {
                    val iconSize = 48
                    layoutParams = LinearLayout.LayoutParams(iconSize, iconSize)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    if (appInfo.second != null) {
                        setImageDrawable(appInfo.second)
                    } else {
                        setImageResource(android.R.drawable.sym_def_app_icon)
                    }
                }
                cardLayout.addView(iconView)

                val nameText = TextView(this).apply {
                    text = appInfo.first
                    textSize = 11f
                    setTextColor(Color.parseColor("#BBFFFFFF"))
                    gravity = Gravity.CENTER
                    setPadding(4, 6, 4, 2)
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                }
                cardLayout.addView(nameText)

                currentRow?.addView(cardLayout)

                if (index % 2 == 1 || index == appList.size - 1) {
                    gridContainer.addView(currentRow)
                }
            }

            root.addView(gridContainer)
        } else {
            val noAppsLabel = TextView(this).apply {
                text = "No hay apps permitidas configuradas"
                textSize = 14f
                setTextColor(Color.parseColor("#55FFFFFF"))
                gravity = Gravity.CENTER
                setPadding(0, 16, 0, 16)
            }
            root.addView(noAppsLabel)
        }

        // ─── 9. HINT ────────────────────────────────────────────────
        val closeHint = TextView(this).apply {
            text = "Toca una app para usarla durante el bloqueo"
            textSize = 11f
            setTextColor(Color.parseColor("#44FFFFFF"))
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 8)
        }
        root.addView(closeHint)

        scrollView.addView(root)
        overlayView = scrollView

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.CENTER

        try {
            windowManager?.addView(overlayView, params)
            Log.d("TrackingService", "Overlay de bloqueo mostrado")
        } catch (e: Exception) {
            Log.e("TrackingService", "Error agregando overlay: ${e.message}")
            overlayView = null
        }
    }

    private fun updateBlockTree(remainingMs: Long) {
        val elapsed = (BLOCK_DURATION_MS - remainingMs).coerceAtLeast(0)
        val progress = (elapsed.toFloat() / BLOCK_DURATION_MS).coerceIn(0f, 1f)
        val emoji = when {
            progress < 0.25f -> "🌱"
            progress < 0.50f -> "🌿"
            progress < 0.75f -> "🪴"
            else -> "🌳"
        }
        blockTreeEmoji?.text = emoji
        blockTreeProgressBar?.progress = (progress * 100).toInt()
        blockTreeProgressText?.text = "Resistencia: ${(progress * 100).toInt()}%"
    }

    private fun hideBlockOverlay() {
        countdownText = null
        blockTreeEmoji = null
        blockTreeProgressBar = null
        blockTreeProgressText = null
        concPhaseText = null
        concBlockText = null
        concRecommendationText = null
        concCountdownText = null
        concIconText = null
        concTitleText = null
        concMessageText = null
        concRestanteLabel = null
        concCancelBtn = null
        concTreeEmojiText = null
        concTreeProgressText = null
        concTreeProgressBar = null
        overlayView?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {
                Log.w("TrackingService", "Error al remover overlay: ${e.message}")
            }
            overlayView = null
        }
    }

    // ─── MODO CONCENTRACIÓN ──────────────────────────────────────────────────

    private fun showConcentrationOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            Log.e("TrackingService", "Sin permiso de superposición (SYSTEM_ALERT_WINDOW)")
            return
        }

        if (overlayView != null) {
            if (overlayView?.isAttachedToWindow == true) {
                overlayView?.visibility = View.VISIBLE
                updateConcentrationOverlay()
                return
            } else {
                hideBlockOverlay()
            }
        }

        val allowedApps = prefs.getStringSet("allowed_apps", emptySet()) ?: emptySet()
        val appList = allowedApps.toList()

        val scrollView = ScrollView(this).apply {
            isFillViewport = true
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#1B2320"))
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(36, 52, 36, 24)
            descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
            @Suppress("DEPRECATION")
            systemUiVisibility = (
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
            )
        }

        // ─── 1. STATUS BADGE ────────────────────────────────────────
        val badgeBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 20f
            setColor(Color.parseColor("#1A2618"))
            setStroke(1, Color.parseColor("#2A3D26"))
        }
        val badge = TextView(this).apply {
            text = "CONCENTRACIÓN"
            textSize = 11f
            setTextColor(Color.parseColor("#7FB98A"))
            gravity = Gravity.CENTER
            setPadding(24, 8, 24, 8)
            background = badgeBg
            letterSpacing = 0.15f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            id = View.generateViewId()
        }
        root.addView(badge)
        concTitleText = badge

        // ─── 2. LOCK ICON IN CIRCLE ────────────────────────────────
        val iconOuter = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            setPadding(0, 28, 0, 20)
        }
        val iconBg = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            setPadding(24, 24, 24, 24)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#252D2A"))
                setStroke(2, Color.parseColor("#3A4A43"))
            }
        }
        val iconTv = TextView(this).apply {
            text = "\uD83D\uDD12"
            textSize = 42f
            gravity = Gravity.CENTER
            id = View.generateViewId()
        }
        iconBg.addView(iconTv)
        iconOuter.addView(iconBg)
        root.addView(iconOuter)
        concIconText = iconTv

        // ─── 3. PHASE ──────────────────────────────────────────────
        val phaseTv = TextView(this).apply {
            textSize = 22f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 4)
        }
        root.addView(phaseTv)
        concPhaseText = phaseTv

        // ─── 4. BLOCK INFO ─────────────────────────────────────────
        val blockTv = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.parseColor("#99FFFFFF"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 16)
        }
        root.addView(blockTv)
        concBlockText = blockTv

        // ─── 5. COUNTDOWN TIMER ─────────────────────────────────────
        val countdownTv = TextView(this).apply {
            text = "00:00"
            textSize = 56f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.create("monospace", android.graphics.Typeface.BOLD)
            letterSpacing = 0.04f
        }
        root.addView(countdownTv)
        concCountdownText = countdownTv

        val restLabel = TextView(this).apply {
            text = "tiempo restante del bloque"
            textSize = 13f
            setTextColor(Color.parseColor("#77FFFFFF"))
            gravity = Gravity.CENTER
            setPadding(0, 4, 0, 8)
        }
        root.addView(restLabel)
        concRestanteLabel = restLabel

        // ─── 6. DIVIDER ─────────────────────────────────────────────
        root.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1
            ).apply { setMargins(0, 0, 0, 24) }
            setBackgroundColor(Color.parseColor("#1EFFFFFF"))
        })

        // ─── 7. TREE / GROWTH SECTION ──────────────────────────────
        val treeContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, 4, 0, 24)
        }

        val treeEmoji = TextView(this).apply {
            text = "🌱"
            textSize = 48f
            gravity = Gravity.CENTER
        }
        treeContainer.addView(treeEmoji)
        concTreeEmojiText = treeEmoji

        val treeProgressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
            layoutParams = LinearLayout.LayoutParams(220, 10).apply { topMargin = 12 }
            progressTintList = ColorStateList.valueOf(Color.parseColor("#7FB98A"))
            progressBackgroundTintList = ColorStateList.valueOf(Color.parseColor("#22FFFFFF"))
        }
        treeContainer.addView(treeProgressBar)
        concTreeProgressBar = treeProgressBar

        val treeProgressText = TextView(this).apply {
            text = "Crecimiento: 0%"
            textSize = 12f
            setTextColor(Color.parseColor("#99FFFFFF"))
            gravity = Gravity.CENTER
            setPadding(0, 6, 0, 0)
        }
        treeContainer.addView(treeProgressText)
        concTreeProgressText = treeProgressText

        root.addView(treeContainer)

        // ─── 8. RECOMMENDATION ─────────────────────────────────────
        val recTv = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.parseColor("#AAD4F5"))
            gravity = Gravity.CENTER
            setPadding(0, 4, 0, 20)
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            visibility = View.GONE
        }
        root.addView(recTv)
        concRecommendationText = recTv

        // ─── 9. DIVIDER ─────────────────────────────────────────────
        root.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1
            ).apply { setMargins(0, 0, 0, 20) }
            setBackgroundColor(Color.parseColor("#1EFFFFFF"))
        })

        // ─── 10. ALLOWED APPS ──────────────────────────────────────
        if (appList.isNotEmpty()) {
            val appsLabel = TextView(this).apply {
                text = "Apps permitidas"
                textSize = 13f
                setTextColor(Color.parseColor("#88FFFFFF"))
                gravity = Gravity.CENTER
                letterSpacing = 0.1f
                setPadding(0, 0, 0, 14)
            }
            root.addView(appsLabel)

            val gridContainer = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
            }

            var currentRow: LinearLayout? = null
            for ((index, pkg) in appList.withIndex()) {
                if (index % 2 == 0) {
                    currentRow = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER
                    }
                }

                val appInfo = try {
                    val info = packageManager.getApplicationInfo(pkg, 0)
                    val label = packageManager.getApplicationLabel(info).toString()
                    val drawable = info.loadIcon(packageManager)
                    val bitmap = if (drawable is BitmapDrawable) {
                        drawable.bitmap
                    } else {
                        val bmp = Bitmap.createBitmap(
                            drawable.intrinsicWidth.coerceAtLeast(1),
                            drawable.intrinsicHeight.coerceAtLeast(1),
                            Bitmap.Config.ARGB_8888
                        )
                        val canvas = Canvas(bmp)
                        drawable.setBounds(0, 0, canvas.width, canvas.height)
                        drawable.draw(canvas)
                        bmp
                    }
                    val scaled = Bitmap.createScaledBitmap(bitmap, 64, 64, true)
                    val iconDrawable = BitmapDrawable(resources, scaled)
                    Pair(label, iconDrawable as android.graphics.drawable.Drawable?)
                } catch (e: Exception) {
                    val fallbackName = try {
                        val info = packageManager.getApplicationInfo(pkg, 0)
                        packageManager.getApplicationLabel(info).toString()
                    } catch (_: Exception) {
                        "App"
                    }
                    Pair(fallbackName, null)
                }

                val cardBg = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 16f
                    setColor(Color.parseColor("#252D2A"))
                    setStroke(1, Color.parseColor("#323D39"))
                }

                val cardLayout = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    setPadding(10, 12, 10, 12)

                    val lp = LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        0.5f
                    ).apply { setMargins(5, 5, 5, 5) }
                    layoutParams = lp
                    background = cardBg
                    clipToOutline = true

                    setOnClickListener {
                        val launchIntent = packageManager.getLaunchIntentForPackage(pkg)
                        if (launchIntent != null) {
                            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            startActivity(launchIntent)
                            lastKnownForegroundPkg = pkg
                            overlayView?.visibility = View.GONE
                            Log.d("TrackingService", "App blanda abierta: $pkg, overlay oculto (concentración)")
                        }
                    }
                }

                val iconView = ImageView(this).apply {
                    val iconSize = 48
                    layoutParams = LinearLayout.LayoutParams(iconSize, iconSize)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    if (appInfo.second != null) {
                        setImageDrawable(appInfo.second)
                    } else {
                        setImageResource(android.R.drawable.sym_def_app_icon)
                    }
                }
                cardLayout.addView(iconView)

                val nameText = TextView(this).apply {
                    text = appInfo.first
                    textSize = 11f
                    setTextColor(Color.parseColor("#BBFFFFFF"))
                    gravity = Gravity.CENTER
                    setPadding(4, 6, 4, 2)
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                }
                cardLayout.addView(nameText)

                currentRow?.addView(cardLayout)

                if (index % 2 == 1 || index == appList.size - 1) {
                    gridContainer.addView(currentRow)
                }
            }

            root.addView(gridContainer)
        } else {
            val noAppsLabel = TextView(this).apply {
                text = "No hay apps permitidas configuradas"
                textSize = 14f
                setTextColor(Color.parseColor("#55FFFFFF"))
                gravity = Gravity.CENTER
                setPadding(0, 16, 0, 16)
            }
            root.addView(noAppsLabel)
        }

        // ─── 11. CANCEL BUTTON ─────────────────────────────────────
        val cancelBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 24f
            setColor(Color.parseColor("#252D2A"))
            setStroke(1, Color.parseColor("#3A4A43"))
        }

        val cancelContainer = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 8)
        }

        val cancelTv = TextView(this).apply {
            text = "Cancelar sesión"
            textSize = 13f
            setTextColor(Color.parseColor("#99FFFFFF"))
            gravity = Gravity.CENTER
            setPadding(28, 12, 28, 12)
            background = cancelBg
            letterSpacing = 0.03f
            setOnClickListener {
                Log.d("TrackingService", "Cancelar sesión de concentración")
                finishConcentrationMode()
            }
        }
        cancelContainer.addView(cancelTv)
        root.addView(cancelContainer)
        concCancelBtn = cancelTv

        scrollView.addView(root)
        overlayView = scrollView

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.CENTER

        try {
            windowManager?.addView(overlayView, params)
            Log.d("TrackingService", "Overlay de concentración mostrado")
        } catch (e: Exception) {
            Log.e("TrackingService", "Error agregando overlay: ${e.message}")
            overlayView = null
        }

        updateConcentrationOverlay()
    }

    private fun updateConcentrationOverlay() {
        val isRest = concentrationPhase == "resting"
        val icon = if (isRest) "\uD83E\uDDD8" else "\uD83D\uDD12"
        val phaseLabel = if (isRest) "Descanso" else "Estudiando"
        val blockLabel = "Bloque $concentrationCurrentBlock de $concentrationTotalBlocks"

        concIconText?.text = icon
        concPhaseText?.text = phaseLabel
        concBlockText?.text = blockLabel
        concTitleText?.text = "MODO CONCENTRACIÓN"

        if (isRest && concentrationRecommendation.isNotEmpty()) {
            concRecommendationText?.text = concentrationRecommendation
            concRecommendationText?.visibility = View.VISIBLE
        } else {
            concRecommendationText?.visibility = View.GONE
        }

        val elapsed = System.currentTimeMillis() - concentrationBlockStartTime
        val remainingSecs = concentrationBlockDurationSecs - (elapsed / 1000).toInt()
        updateTreeProgress(remainingSecs.coerceAtLeast(0))
    }

    private fun updateTreeProgress(remainingSecs: Int) {
        val blockProgress = if (concentrationBlockDurationSecs > 0) {
            1.0f - (remainingSecs.toFloat() / concentrationBlockDurationSecs)
        } else 0f
        val overallProgress = if (concentrationTotalBlocks > 0) {
            ((concentrationCurrentBlock - 1) + blockProgress.coerceIn(0f, 1f)) / concentrationTotalBlocks
        } else 0f

        val clampedProgress = overallProgress.coerceIn(0f, 1f)
        val emoji = when {
            clampedProgress < 0.25f -> "🌱"
            clampedProgress < 0.50f -> "🌿"
            clampedProgress < 0.75f -> "🪴"
            else -> "🌳"
        }
        concTreeEmojiText?.text = emoji
        concTreeProgressBar?.progress = (clampedProgress * 100).toInt()
        concTreeProgressText?.text = "Crecimiento: ${(clampedProgress * 100).toInt()}%"
    }

    private fun startConcentrationCountdown() {
        countdownRunnable?.let { handler.removeCallbacks(it) }
        concentrationBlockStartTime = System.currentTimeMillis()

        countdownRunnable = object : Runnable {
            override fun run() {
                val elapsed = System.currentTimeMillis() - concentrationBlockStartTime
                val remainingSecs = concentrationBlockDurationSecs - (elapsed / 1000).toInt()
                if (remainingSecs <= 0) {
                    concCountdownText?.text = "00:00"
                    concRestanteLabel?.text = "esperando transición..."
                    updateTreeProgress(0)
                    return
                }
                val mins = remainingSecs / 60
                val secs = remainingSecs % 60
                concCountdownText?.text = String.format("%02d:%02d", mins, secs)
                updateTreeProgress(remainingSecs)
                handler.postDelayed(this, 1000)
            }
        }
        handler.post(countdownRunnable!!)
    }

    private fun finishConcentrationMode() {
        Log.d("TrackingService", "Finalizando modo concentración")
        countdownRunnable?.let { handler.removeCallbacks(it) }
        countdownRunnable = null
        isConcentrationMode = false
        isBlocked = false
        hideBlockOverlay()

        prefs.edit()
            .putBoolean("concentration_active", false)
            .putBoolean("block_active", false)
            .apply()
    }

    // ─── UTILIDADES ──────────────────────────────────────────────────────────

    private fun createNotification(): Notification {
        val channelId = "tracking_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Focus Tracking",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Focus Blocker activo")
            .setContentText("Monitorizando uso de apps")
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    // ─── CONSTANTES ──────────────────────────────────────────────────────────

    companion object {
        const val ACTION_START  = "start_tracking"
        const val ACTION_STOP   = "stop_tracking"
        const val ACTION_PAUSE  = "pause_tracking"
        const val ACTION_RESUME = "resume_tracking"

        const val SHOW_OVERLAY_ACTION   = "com.example.antiprocrastinacion.SHOW_OVERLAY"
        const val HIDE_OVERLAY_ACTION   = "com.example.antiprocrastinacion.HIDE_OVERLAY"
        const val CONCENTRATION_MODE_ACTION = "com.example.antiprocrastinacion.CONCENTRATION_MODE"

        const val DISTRACT_LIMIT        = 20 * 60           // 20 min de vigilancia
        const val BLOCK_DURATION_MS     = 15 * 60 * 1000L   // 15 min de bloqueo
        const val INACTIVITY_RESET_MS   = 25 * 60 * 1000L
    }
}
