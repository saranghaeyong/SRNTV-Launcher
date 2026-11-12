package com.srntv.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.TransitionDrawable
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.ComponentName
import com.srntv.launcher.databinding.ActivityLauncherBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class LauncherActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLauncherBinding
    private val apps = mutableListOf<AppEntry>()
    private var selectedIndex = 0

    private val mainHandler = Handler(Looper.getMainLooper())
    private val clockRunnable = object : Runnable {
        override fun run() {
            updateClock()
            mainHandler.postDelayed(this, 30000) // Update every 30 seconds
        }
    }

    private var isQuickSettingsFocused = false
    private var focusedQuickSettingIndex = 2 // Default to Settings (0: Inputs, 1: Wi-Fi, 2: Settings)

    private val wallpaperCache = mutableMapOf<Int, Bitmap>()
    private var currentWallpaperIndex = -1

    data class AppEntry(
        val label: String,
        val packageName: String,
        val launcherActivity: String
    )

    private val packageChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refreshAppList()
        }
    }

    private val timeChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            updateClock()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLauncherBinding.inflate(layoutInflater)
        setContentView(binding.root)
        enableImmersiveMode()

        setupQuickSettingsFocusListeners()
        setupQuickSettingsClickListeners()

        refreshAppList()
        preloadWallpapers()
        registerPackageReceiver()
        registerTimeReceiver()
    }

    private fun setupQuickSettingsFocusListeners() {
        val buttons = listOf(binding.btnInputs, binding.btnWifi, binding.btnSettings)
        buttons.forEachIndexed { index, button ->
            button.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    isQuickSettingsFocused = true
                    focusedQuickSettingIndex = index
                    button.setTextColor(Color.WHITE)
                    button.setBackgroundResource(R.drawable.glass_button_focused)
                    button.animate().scaleX(1.1f).scaleY(1.1f).setDuration(200).start()
                } else {
                    button.setTextColor(Color.parseColor("#BBBBBB"))
                    button.setBackgroundResource(0)
                    button.animate().scaleX(1.0f).scaleY(1.0f).setDuration(200).start()
                }
            }
        }

        binding.tvAppName.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                isQuickSettingsFocused = false
                binding.tvAppName.setTextColor(Color.WHITE)
                binding.tvAppName.animate().scaleX(1.05f).scaleY(1.05f).setDuration(250).start()
            } else {
                binding.tvAppName.setTextColor(Color.WHITE)
                binding.tvAppName.animate().scaleX(1.0f).scaleY(1.0f).setDuration(250).start()
            }
        }
    }

    private fun setupQuickSettingsClickListeners() {
        binding.btnInputs.setOnClickListener { openInputSettings() }
        binding.btnWifi.setOnClickListener { openWifiSettings() }
        binding.btnSettings.setOnClickListener { openSystemSettings() }
    }

    private fun registerPackageReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(packageChangeReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(packageChangeReceiver, filter)
        }
    }

    private fun registerTimeReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        registerReceiver(timeChangeReceiver, filter)
    }

    override fun onResume() {
        super.onResume()
        enableImmersiveMode()
        refreshAppList()
        startClockUpdater()
        if (!isQuickSettingsFocused) {
            binding.tvAppName.requestFocus()
        }
    }

    override fun onPause() {
        super.onPause()
        stopClockUpdater()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(packageChangeReceiver)
        } catch (e: Exception) {
            Log.w(TAG, "Error unregistering packageReceiver: ${e.message}")
        }
        try {
            unregisterReceiver(timeChangeReceiver)
        } catch (e: Exception) {
            Log.w(TAG, "Error unregistering timeReceiver: ${e.message}")
        }
    }

    private fun enableImmersiveMode() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        
        @Suppress("DEPRECATION")
        val uiOptions = (View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
        
        window.decorView.systemUiVisibility = uiOptions
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
    }

    private fun refreshAppList() {
        val pm = packageManager
        val appMap = LinkedHashMap<String, AppEntry>()

        // 1. Discover Leanback TV Apps
        val leanbackIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        }
        val leanbackResolveInfos = pm.queryIntentActivities(leanbackIntent, 0)
        for (ri in leanbackResolveInfos) {
            val packageName = ri.activityInfo.packageName
            if (packageName == this.packageName) continue
            val label = ri.loadLabel(pm).toString().trim()
            if (label.isNotEmpty() && !appMap.containsKey(packageName)) {
                appMap[packageName] = AppEntry(
                    label = label,
                    packageName = packageName,
                    launcherActivity = ri.activityInfo.name
                )
            }
        }

        // 2. Discover Standard Launcher Apps (Fallback / Additional)
        val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val launcherResolveInfos = pm.queryIntentActivities(launcherIntent, 0)
        for (ri in launcherResolveInfos) {
            val packageName = ri.activityInfo.packageName
            if (packageName == this.packageName) continue
            // Skip system package duplicates if TV launcher already found it
            val label = ri.loadLabel(pm).toString().trim()
            if (label.isNotEmpty() && !appMap.containsKey(packageName)) {
                appMap[packageName] = AppEntry(
                    label = label,
                    packageName = packageName,
                    launcherActivity = ri.activityInfo.name
                )
            }
        }

        val sortedApps = appMap.values.sortedBy { it.label.lowercase(Locale.getDefault()) }

        apps.clear()
        apps.addAll(sortedApps)

        if (apps.isNotEmpty()) {
            if (selectedIndex >= apps.size) {
                selectedIndex = apps.size - 1
            }
            if (selectedIndex < 0) {
                selectedIndex = 0
            }
            updateDisplay(animate = false)
        } else {
            binding.tvAppName.text = "NO APPS FOUND"
            binding.tvHint.visibility = View.GONE
        }
    }

    private fun updateDisplay(animate: Boolean = true) {
        if (apps.isEmpty()) {
            binding.tvAppName.text = "NO APPS FOUND"
            binding.tvHint.visibility = View.GONE
            updateWallpaper(0)
            return
        }

        val app = apps.getOrNull(selectedIndex) ?: return
        binding.tvAppName.text = app.label
        binding.tvHint.visibility = View.VISIBLE

        updateWallpaper(selectedIndex % 4)

        if (animate) {
            applyTransition(binding.tvAppName)
        }
    }

    private fun preloadWallpapers() {
        // Run preloading in a background thread to avoid blocking main thread
        Thread {
            for (i in 0..3) {
                try {
                    val fileName = "wallpapers/${i + 1}.png"
                    val inputStream = assets.open(fileName)
                    
                    val options = BitmapFactory.Options().apply {
                        inJustDecodeBounds = true
                    }
                    BitmapFactory.decodeStream(inputStream, null, options)
                    inputStream.close()

                    // Match screen dimensions more closely
                    val targetWidth = 1920
                    var sampleSize = 1
                    if (options.outWidth > targetWidth) {
                        sampleSize = Math.round(options.outWidth.toFloat() / targetWidth.toFloat())
                    }

                    val decodeOptions = BitmapFactory.Options().apply {
                        inSampleSize = sampleSize
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    }
                    
                    val scaledStream = assets.open(fileName)
                    val bitmap = BitmapFactory.decodeStream(scaledStream, null, decodeOptions)
                    scaledStream.close()
                    
                    if (bitmap != null) {
                        wallpaperCache[i] = bitmap
                        Log.d(TAG, "Successfully cached wallpaper $i (${bitmap.width}x${bitmap.height})")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error preloading wallpaper ${i + 1}", e)
                }
            }
            // Initial wallpaper set on main thread
            mainHandler.post {
                if (apps.isNotEmpty()) {
                    updateWallpaper(selectedIndex % 4, immediate = true)
                }
            }
        }.start()
    }

    private fun updateWallpaper(index: Int, immediate: Boolean = false) {
        if (index == currentWallpaperIndex) return
        
        val bitmap = wallpaperCache[index]
        if (bitmap == null) {
            binding.ivWallpaper.setBackgroundColor(Color.BLACK)
            binding.ivWallpaper.setImageDrawable(null)
            currentWallpaperIndex = index
            return
        }

        binding.ivWallpaper.scaleType = ImageView.ScaleType.CENTER_CROP
        
        binding.ivWallpaper.post {
            if (immediate || binding.ivWallpaper.drawable == null) {
                binding.ivWallpaper.setImageBitmap(bitmap)
                currentWallpaperIndex = index
            } else {
                val oldDrawable = binding.ivWallpaper.drawable
                val newDrawable = BitmapDrawable(resources, bitmap)
                
                val transition = TransitionDrawable(arrayOf(oldDrawable, newDrawable))
                binding.ivWallpaper.setImageDrawable(transition)
                transition.isCrossFadeEnabled = true
                transition.startTransition(450)
                currentWallpaperIndex = index
            }
        }
    }

    private fun applyTransition(view: View) {
        view.animate().cancel()
        view.alpha = 0.3f
        view.scaleX = 0.96f
        view.scaleY = 0.96f
        view.animate()
            .alpha(1.0f)
            .scaleX(1.0f)
            .scaleY(1.0f)
            .setDuration(180)
            .start()
    }

    private fun selectNext() {
        if (apps.isEmpty()) return
        selectedIndex = (selectedIndex + 1) % apps.size
        updateDisplay(animate = true)
    }

    private fun selectPrevious() {
        if (apps.isEmpty()) return
        selectedIndex = (selectedIndex - 1 + apps.size) % apps.size
        updateDisplay(animate = true)
    }

    private fun launchSelectedApp() {
        if (apps.isEmpty()) return
        val entry = apps[selectedIndex]
        try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                setClassName(entry.packageName, entry.launcherActivity)
                addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Leanback launch failed, trying package launch intent: ${e.message}")
            try {
                val fallbackIntent = packageManager.getLaunchIntentForPackage(entry.packageName)
                if (fallbackIntent != null) {
                    fallbackIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                    startActivity(fallbackIntent)
                } else {
                    Log.e(TAG, "No launch intent available for ${entry.packageName}")
                }
            } catch (e2: Exception) {
                Log.e(TAG, "Failed to launch package ${entry.packageName}: ${e2.message}")
            }
        }
    }

    private fun openInputSettings() {
        val intents = listOf(
            Intent("android.settings.TV_INPUT_SETTINGS"),
            Intent().apply {
                setComponent(ComponentName("com.tcl.tv", "com.tcl.sourcemenu.sourcemanager.MainActivity"))
            },
            Intent().apply {
                setComponent(ComponentName("com.tcl.tvinput", "com.tcl.tvinput.setup.TvInputSetupActivity"))
            }
        )
        
        for (intent in intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                val resolveInfo = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
                if (resolveInfo != null) {
                    startActivity(intent)
                    return
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to launch intent: $intent", e)
            }
        }

        showInputsUnavailableDialog()
    }

    private fun showInputsUnavailableDialog() {
        AlertDialog.Builder(this, androidx.appcompat.R.style.Theme_AppCompat_Dialog_Alert)
            .setTitle("TV INPUTS")
            .setMessage("Input selection is unavailable through this device's exposed settings activity.")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun openWifiSettings() {
        val intents = listOf(
            Intent(Settings.ACTION_WIFI_SETTINGS),
            Intent("android.settings.NETWORK_OPERATOR_SETTINGS"),
            Intent(Settings.ACTION_SETTINGS)
        )
        for (intent in intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                return
            } catch (_: Exception) {
            }
        }
    }

    private fun openSystemSettings() {
        try {
            val intent = Intent(Settings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Unable to open system settings", e)
        }
    }

    private fun startClockUpdater() {
        stopClockUpdater()
        mainHandler.post(clockRunnable)
    }

    private fun stopClockUpdater() {
        mainHandler.removeCallbacks(clockRunnable)
    }

    private fun updateClock() {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val greeting = when (hour) {
            in 5..11 -> "Good Morning"
            in 12..16 -> "Good Afternoon"
            in 17..20 -> "Good Evening"
            else -> "Good Night"
        }

        val timeFormatter = SimpleDateFormat("h:mm a", Locale.getDefault())
        val dateFormatter = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault())
        val dateNow = Date()

        binding.tvGreeting.text = greeting
        binding.tvTime.text = timeFormatter.format(dateNow)
        binding.tvDate.text = dateFormatter.format(dateNow)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) {
            return super.dispatchKeyEvent(event)
        }

        when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (!isQuickSettingsFocused) {
                    selectNext()
                    return true
                }
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (!isQuickSettingsFocused) {
                    selectPrevious()
                    return true
                }
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                if (!isQuickSettingsFocused) {
                    launchSelectedApp()
                    return true
                }
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                if (!isQuickSettingsFocused) {
                    isQuickSettingsFocused = true
                    when (focusedQuickSettingIndex) {
                        0 -> binding.btnInputs.requestFocus()
                        1 -> binding.btnWifi.requestFocus()
                        else -> binding.btnSettings.requestFocus()
                    }
                    return true
                }
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (isQuickSettingsFocused) {
                    isQuickSettingsFocused = false
                    binding.tvAppName.requestFocus()
                    return true
                }
            }
            KeyEvent.KEYCODE_BACK -> {
                // Ignore back key on launcher home screen to prevent closing launcher
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    companion object {
        private const val TAG = "SRNTV_Launcher"
    }
}
