package com.walkout.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.*
import androidx.core.app.NotificationCompat

class WifiMonitorService : Service() {

    companion object {
        // How long to wait after WiFi drops before firing — covers extender handoff (~1–5s)
        private const val DEBOUNCE_MS = 10_000L

        private const val MONITORING_NOTIF_ID = 1000
        const val MONITORING_CHANNEL_ID = "walkout_monitoring"

        fun start(context: Context) {
            val intent = Intent(context, WifiMonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, WifiMonitorService::class.java))
        }

        /**
         * Returns the SSID of the currently connected WiFi network, or null if not on WiFi.
         * Tries the modern NetworkCapabilities API first, falls back to WifiManager if it
         * returns null (common on some devices/manufacturers).
         * SSIDs from Android APIs are wrapped in quotes ("MyWiFi") — we strip them.
         */
        fun getCurrentSSID(context: Context): String? {
            // Modern path (Android 10+): NetworkCapabilities → WifiInfo
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                val caps = cm.activeNetwork
                    ?.let { cm.getNetworkCapabilities(it) }
                    ?.takeIf { it.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) }
                val ssid = (caps?.transportInfo as? WifiInfo)?.ssid
                    ?.takeIf { it != WifiManager.UNKNOWN_SSID && it != "<unknown ssid>" }
                    ?.trim('"')
                if (ssid != null) return ssid
                // Fall through to WifiManager if transportInfo was null
            }

            // Legacy path — also used as fallback on Android 10+ when transportInfo is null
            @Suppress("DEPRECATION")
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            return wm.connectionInfo?.ssid
                ?.takeIf { it != WifiManager.UNKNOWN_SSID && it != "<unknown ssid>" }
                ?.trim('"')
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var connectivityManager: ConnectivityManager

    // True while the phone is confirmed connected to home WiFi
    private var isOnHomeWifi = false
    // True while the debounce timer is running
    private var alertPending = false

    /**
     * True if the current WiFi matches either configured home network (main router or extender).
     */
    private fun isOnHomeNetwork(): Boolean {
        val prefs = getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
        val ssid1 = prefs.getString(MainActivity.KEY_HOME_SSID, null)
        val ssid2 = prefs.getString(MainActivity.KEY_HOME_SSID_2, null)
        val current = getCurrentSSID(applicationContext) ?: return false
        return current == ssid1 || (!ssid2.isNullOrBlank() && current == ssid2)
    }

    /**
     * Fires after DEBOUNCE_MS if WiFi hasn't recovered.
     * Does a final network check before triggering — catches the case where the phone
     * briefly dropped then reconnected to the extender (same or different SSID).
     */
    private val triggerRunnable = Runnable {
        alertPending = false
        if (!isOnHomeNetwork()) {
            val text = getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
                .getString(MainActivity.KEY_NOTIF_TEXT, MainActivity.DEFAULT_TEXT)
                ?: MainActivity.DEFAULT_TEXT
            WalkOutAlert.trigger(applicationContext, text)
        }
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {

        // WiFi came up — if it's a home network, cancel any pending trigger
        override fun onAvailable(network: Network) {
            if (isOnHomeNetwork()) {
                isOnHomeWifi = true
                handler.removeCallbacks(triggerRunnable)
                alertPending = false
            }
        }

        // WiFi dropped — start the debounce timer only if we were on a home network
        override fun onLost(network: Network) {
            if (isOnHomeWifi && !alertPending) {
                isOnHomeWifi = false
                alertPending = true
                handler.postDelayed(triggerRunnable, DEBOUNCE_MS)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createMonitoringChannel()
        startForeground(MONITORING_NOTIF_ID, buildMonitoringNotification())

        // Initialise: are we currently on a home network?
        isOnHomeWifi = isOnHomeNetwork()

        connectivityManager.registerNetworkCallback(
            NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build(),
            networkCallback
        )

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(triggerRunnable)
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (_: Exception) {}
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildMonitoringNotification() =
        NotificationCompat.Builder(this, MONITORING_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("WalkOut active")
            .setContentText("Watching home WiFi…")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setSilent(true)
            .setOngoing(true)
            .build()

    private fun createMonitoringChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                MONITORING_CHANNEL_ID,
                "WalkOut Monitoring",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Silent indicator while WalkOut is running"
                setShowBadge(false)
            }
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }
}
