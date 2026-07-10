package com.example.antiprocrastinacion

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import java.io.ByteArrayOutputStream

class MainActivity : FlutterActivity() {
    private val CHANNEL = "com.focus.blocker/native"

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL)
            .setMethodCallHandler { call, result ->
                val prefs: SharedPreferences = getSharedPreferences("block_prefs", MODE_PRIVATE)
                when (call.method) {
                    "setConcentrationMode" -> {
                        val active = call.argument<Boolean>("active") ?: false
                        val phase = call.argument<String>("phase") ?: ""
                        val currentBlock = call.argument<Int>("currentBlock") ?: 0
                        val totalBlocks = call.argument<Int>("totalBlocks") ?: 0
                        val blockDurationSeconds = call.argument<Int>("blockDurationSeconds") ?: 0
                        val progress = call.argument<Double>("progress") ?: 0.0
                        val restRecommendation = call.argument<String>("restRecommendation") ?: ""
                        val allowed = call.argument<List<String>>("allowedApps") ?: emptyList()

                        val editor = prefs.edit()
                            .putBoolean("concentration_active", active)
                            .putString("concentration_phase", phase)
                            .putInt("concentration_block", currentBlock)
                            .putInt("concentration_total_blocks", totalBlocks)
                            .putInt("concentration_block_duration", blockDurationSeconds)
                            .putFloat("concentration_progress", progress.toFloat())
                            .putString("concentration_recommendation", restRecommendation)

                        if (active) {
                            editor.putStringSet("allowed_apps", allowed.toSet())
                        }

                        editor.apply()

                        val intent = Intent(TrackingService.CONCENTRATION_MODE_ACTION).apply {
                            putExtra("active", active)
                            putExtra("phase", phase)
                            putExtra("currentBlock", currentBlock)
                            putExtra("totalBlocks", totalBlocks)
                            putExtra("blockDurationSeconds", blockDurationSeconds)
                            putExtra("restRecommendation", restRecommendation)
                        }
                        sendBroadcast(intent)
                        result.success(true)
                    }
                    "getTrackingState" -> {
                        val seconds = prefs.getInt("accumulated_seconds", 0)
                        val blocked = prefs.getBoolean("block_active", false)
                        val concActive = prefs.getBoolean("concentration_active", false)
                        val blockStartTime = prefs.getLong("block_start_time", 0)
                        val blockRemaining = if (blocked && blockStartTime > 0) {
                            val elapsed = System.currentTimeMillis() - blockStartTime
                            val remaining = (TrackingService.BLOCK_DURATION_MS - elapsed) / 1000
                            remaining.toInt().coerceAtLeast(0)
                        } else 0
                        val effectiveBlocked = blocked && blockRemaining > 0
                        result.success(mapOf(
                            "accumulatedSeconds" to seconds,
                            "isBlocked" to effectiveBlocked,
                            "concentrationActive" to concActive,
                            "blockRemainingSeconds" to blockRemaining
                        ))
                    }
                    "startTracking" -> {
                        val intent = Intent(this, TrackingService::class.java).apply {
                            action = TrackingService.ACTION_START
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            startForegroundService(intent)
                        } else {
                            startService(intent)
                        }
                        result.success(true)
                    }
                    "stopTracking" -> {
                        val intent = Intent(this, TrackingService::class.java).apply {
                            action = TrackingService.ACTION_STOP
                        }
                        startService(intent)
                        result.success(true)
                    }
                    "pauseTracking" -> {
                        val intent = Intent(this, TrackingService::class.java).apply {
                            action = TrackingService.ACTION_PAUSE
                        }
                        startService(intent)
                        result.success(true)
                    }
                    "resumeTracking" -> {
                        val intent = Intent(this, TrackingService::class.java).apply {
                            action = TrackingService.ACTION_RESUME
                        }
                        startService(intent)
                        result.success(true)
                    }
                    "getInstalledApps" -> {
                        val apps = packageManager.getInstalledApplications(0).map { app ->
                            val iconBytes = try {
                                val drawable = app.loadIcon(packageManager)
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
                                val scaled = Bitmap.createScaledBitmap(bitmap, 48, 48, true)
                                val stream = ByteArrayOutputStream()
                                scaled.compress(Bitmap.CompressFormat.PNG, 100, stream)
                                stream.toByteArray()
                            } catch (e: Exception) {
                                null
                            }
                            mapOf(
                                "package" to app.packageName,
                                "label" to app.loadLabel(packageManager).toString(),
                                "icon" to iconBytes
                            )
                        }
                        result.success(apps)
                    }
                    "updateDistractingApps" -> {
                        val apps = call.arguments as? List<String> ?: emptyList()
                        prefs.edit().putStringSet("distracting_apps", apps.toSet()).apply()
                        result.success(true)
                    }
                    "updateAllowedApps" -> {
                        val apps = call.arguments as? List<String> ?: emptyList()
                        prefs.edit().putStringSet("allowed_apps", apps.toSet()).apply()
                        result.success(true)
                    }
                    "hasUsageStatsPermission" -> {
                        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
                        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            appOps.unsafeCheckOpNoThrow(
                                AppOpsManager.OPSTR_GET_USAGE_STATS,
                                Process.myUid(),
                                packageName
                            )
                        } else {
                            appOps.checkOpNoThrow(
                                AppOpsManager.OPSTR_GET_USAGE_STATS,
                                Process.myUid(),
                                packageName
                            )
                        }
                        result.success(mode == AppOpsManager.MODE_ALLOWED)
                    }
                    "openUsageAccessSettings" -> {
                        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                        startActivity(intent)
                        result.success(true)
                    }
                    "canDrawOverlays" -> {
                        result.success(Settings.canDrawOverlays(this))
                    }
                    "openOverlaySettings" -> {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:$packageName")
                        )
                        startActivity(intent)
                        result.success(true)
                    }
                    else -> result.notImplemented()
                }
            }
    }
}