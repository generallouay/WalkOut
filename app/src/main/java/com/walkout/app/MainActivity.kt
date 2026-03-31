package com.walkout.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    companion object {
        const val PREFS_NAME = "walkout_prefs"
        const val KEY_NOTIF_TEXT = "notification_text"
        const val KEY_HOME_SSID = "home_ssid"
        const val KEY_HOME_SSID_2 = "home_ssid_2"
        const val KEY_ACTIVE = "active"
        const val NOTIFICATION_CHANNEL_ID = "walkout_alerts"
        const val DEFAULT_TEXT = "Don't forget your keys!"
    }

    private lateinit var prefs: SharedPreferences
    private lateinit var etNotifText: EditText
    private lateinit var tvSsidStatus: TextView
    private lateinit var tvSsidStatus2: TextView
    private lateinit var tvServiceStatus: TextView
    private lateinit var btnSetWifi: Button
    private lateinit var btnSetWifi2: Button
    private lateinit var btnSave: Button

    // Which "Set WiFi" button was tapped — 1 or 2
    private var captureTarget = 1
    private var capturedSSID: String? = null
    private var capturedSSID2: String? = null

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            captureCurrentSSID()
        } else {
            Toast.makeText(
                this,
                "Location permission is required to read the WiFi network name",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* proceed regardless */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        createAlertNotificationChannel()
        requestNotificationPermissionIfNeeded()
        bindViews()
        loadSavedValues()
        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        updateStatusLabel(prefs.getBoolean(KEY_ACTIVE, false))
    }

    private fun bindViews() {
        etNotifText = findViewById(R.id.etNotifText)
        tvSsidStatus = findViewById(R.id.tvSsidStatus)
        tvSsidStatus2 = findViewById(R.id.tvSsidStatus2)
        tvServiceStatus = findViewById(R.id.tvServiceStatus)
        btnSetWifi = findViewById(R.id.btnSetWifi)
        btnSetWifi2 = findViewById(R.id.btnSetWifi2)
        btnSave = findViewById(R.id.btnSave)
    }

    private fun loadSavedValues() {
        etNotifText.setText(prefs.getString(KEY_NOTIF_TEXT, DEFAULT_TEXT))
        tvSsidStatus.text = prefs.getString(KEY_HOME_SSID, null)
            ?: "Tap below while on main router"
        tvSsidStatus2.text = prefs.getString(KEY_HOME_SSID_2, null)
            ?: "Tap below while on extender (optional)"
        updateStatusLabel(prefs.getBoolean(KEY_ACTIVE, false))
    }

    private fun setupListeners() {
        btnSetWifi.setOnClickListener {
            captureTarget = 1
            if (hasLocationPermission()) captureCurrentSSID()
            else requestLocationPermission()
        }

        btnSetWifi2.setOnClickListener {
            captureTarget = 2
            if (hasLocationPermission()) captureCurrentSSID()
            else requestLocationPermission()
        }

        btnSave.setOnClickListener { onSaveClicked() }
    }

    private fun requestLocationPermission() {
        locationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    private fun captureCurrentSSID() {
        val ssid = WifiMonitorService.getCurrentSSID(this)
        if (ssid != null) {
            if (captureTarget == 1) {
                capturedSSID = ssid
                tvSsidStatus.text = ssid
            } else {
                capturedSSID2 = ssid
                tvSsidStatus2.text = ssid
            }
            Toast.makeText(this, "Captured: $ssid", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(
                this,
                "Could not read WiFi name — make sure you're connected and location is on",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun onSaveClicked() {
        val ssid1 = capturedSSID ?: prefs.getString(KEY_HOME_SSID, null)
        if (ssid1.isNullOrBlank()) {
            Toast.makeText(this, "Set your main home WiFi first", Toast.LENGTH_SHORT).show()
            return
        }
        if (etNotifText.text.isBlank()) {
            Toast.makeText(this, "Enter a notification message", Toast.LENGTH_SHORT).show()
            return
        }

        val ssid2 = capturedSSID2 ?: prefs.getString(KEY_HOME_SSID_2, null)
        val text = etNotifText.text.toString().trim()

        prefs.edit()
            .putString(KEY_NOTIF_TEXT, text)
            .putString(KEY_HOME_SSID, ssid1)
            .apply {
                if (!ssid2.isNullOrBlank()) putString(KEY_HOME_SSID_2, ssid2)
                else remove(KEY_HOME_SSID_2)
            }
            .putBoolean(KEY_ACTIVE, true)
            .apply()

        WifiMonitorService.stop(this)
        WifiMonitorService.start(this)

        updateStatusLabel(true)
        Toast.makeText(this, "WalkOut is active!", Toast.LENGTH_SHORT).show()
    }

    private fun updateStatusLabel(active: Boolean) {
        tvServiceStatus.text = if (active) "Status: ACTIVE" else "Status: Inactive"
        tvServiceStatus.setTextColor(
            if (active) getColor(R.color.active_green) else getColor(R.color.secondary_text)
        )
    }

    private fun hasLocationPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

    private fun createAlertNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "WalkOut Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Exit alerts from WalkOut"
                enableVibration(false) // vibration is handled manually
            }
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
