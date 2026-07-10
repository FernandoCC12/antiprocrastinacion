package com.example.antiprocrastinacion

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class BlockAccessibilityService : AccessibilityService() {

    companion object {
        var isActive = false
    }

    /**
     * Paquetes del sistema que deben ignorarse completamente para evitar
     * disparar acciones al interactuar con la UI del sistema operativo.
     * Se ha ampliado para cubrir launchers y gestores de ventana comunes.
     */
    private val ignoredPackages = setOf(
        "android",
        "com.android.systemui",
        "com.android.inputmethod.latin",
        "com.google.android.inputmethod.latin",
        "com.samsung.android.honeyboard",
        "com.google.android.googlequicksearchbox",
        "com.android.settings",
        "com.android.packageinstaller",
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller",
        "com.google.android.gms"
        // Los launchers han sido removidos para garantizar que el bloqueo cubra la pantalla de inicio
    )

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOWS_CHANGED) return

        val prefs: SharedPreferences = getSharedPreferences("block_prefs", MODE_PRIVATE)
        if (!prefs.getBoolean("block_active", false)) return

        val packageName: String? = if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            event.packageName?.toString()
        } else {
            getForegroundPackage()
        }

        if (packageName != null && packageName in ignoredPackages) return

        val isAllowed = isAllowedApp(packageName, prefs)

        Log.d("BlockAccessibilityService", "Foreground: $packageName | isAllowed=$isAllowed | eventType=${event.eventType}")

        if (isAllowed) {
            val hideIntent = Intent(TrackingService.HIDE_OVERLAY_ACTION).apply {
                putExtra("package", packageName)
            }
            sendBroadcast(hideIntent)
        } else {
            sendBroadcast(Intent(TrackingService.SHOW_OVERLAY_ACTION))
        }
    }

    private fun getForegroundPackage(): String? {
        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return null
        val now = System.currentTimeMillis()
        val events: UsageEvents = try {
            usageStatsManager.queryEvents(now - 10000, now)
        } catch (e: Exception) {
            return null
        }
        var lastResumed: String? = null
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                lastResumed = event.packageName
            }
        }
        return lastResumed
    }

    /**
     * Devuelve true si [packageName] pertenece (comparte UID) con alguna
     * de las apps blandas configuradas en SharedPreferences.
     *
     * Al comparar UIDs en lugar de nombres de paquete, cubrimos automáticamente:
     *  - Sub-paquetes (ej. com.whatsapp.w4b para WhatsApp Business)
     *  - Componentes internos de la app que generan eventos de ventana
     *
     * Si [packageName] es null (launcher o pantalla de inicio), devuelve false
     * para que se muestre el overlay.
     */
    private fun isAllowedApp(packageName: String?, prefs: SharedPreferences): Boolean {
        if (packageName == null) return false
        if (packageName == "com.example.antiprocrastinacion") return true

        val allowedApps = prefs.getStringSet("allowed_apps", emptySet()) ?: emptySet()
        if (allowedApps.isEmpty()) return false

        val currentUid = try {
            packageManager.getApplicationInfo(packageName, 0).uid
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

    override fun onServiceConnected() {
        super.onServiceConnected()
        isActive = true
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                    AccessibilityEvent.TYPE_WINDOWS_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 100
        }
        setServiceInfo(info)
        Log.d("BlockAccessibilityService", "Servicio conectado y configurado")
    }

    override fun onInterrupt() {
        Log.w("BlockAccessibilityService", "Servicio interrumpido")
    }

    override fun onDestroy() {
        super.onDestroy()
        isActive = false
        Log.d("BlockAccessibilityService", "Servicio destruido")
    }
}