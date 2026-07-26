package com.cybertkr.suboverlay

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.provider.Settings
import android.text.TextUtils
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.cybertkr.suboverlay.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val settings by lazy { SettingsStore(this) }

    private val notifPermLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val pickSrtLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri ?: return@registerForActivityResult
            loadSrt(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.pickSrt.setOnClickListener {
            pickSrtLauncher.launch(arrayOf("application/x-subrip", "text/plain", "*/*"))
        }

        binding.startOverlay.setOnClickListener {
            requestNotifIfNeeded()
            if (!Settings.canDrawOverlays(this)) {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                )
                Toast.makeText(this, getString(R.string.perm_hint), Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            startForegroundService(
                Intent(this, com.cybertkr.suboverlay.overlay.OverlayService::class.java)
            )
            Toast.makeText(this, getString(R.string.started_hint), Toast.LENGTH_SHORT).show()
        }

        setupOverlaySettings()
        renderRecent()
    }

    override fun onResume() {
        super.onResume()
        updatePermBanner()
    }

    private fun updatePermBanner() {
        val overlayOk = Settings.canDrawOverlays(this)
        val a11yOk = isAccessibilityEnabled()
        when {
            !overlayOk -> {
                binding.permBanner.visibility = View.VISIBLE
                binding.permBannerText.setText(R.string.perm_warn_overlay)
                binding.permBanner.setOnClickListener {
                    startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:$packageName")
                        )
                    )
                }
            }
            !a11yOk -> {
                binding.permBanner.visibility = View.VISIBLE
                binding.permBannerText.setText(R.string.perm_warn_a11y)
                binding.permBanner.setOnClickListener { showAccessibilityDisclosure() }
            }
            else -> binding.permBanner.visibility = View.GONE
        }
    }

    private fun showAccessibilityDisclosure() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.a11y_disc_title)
            .setMessage(R.string.a11y_disc_body)
            .setPositiveButton(R.string.a11y_disc_agree) { _, _ -> openAccessibilitySettings() }
            .setNegativeButton(R.string.a11y_disc_cancel) { d, _ -> d.dismiss() }
            .show()
    }

    private fun openAccessibilitySettings() {
        val cn = android.content.ComponentName(
            this, com.cybertkr.suboverlay.a11y.SubtitleSyncService::class.java
        ).flattenToString()
        val args = android.os.Bundle().apply { putString(":settings:fragment_args_key", cn) }
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            putExtra(":settings:fragment_args_key", cn)
            putExtra(":settings:show_fragment_args", args)
        }
        try {
            startActivity(intent)
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        Toast.makeText(this, getString(R.string.perm_a11y_hint), Toast.LENGTH_LONG).show()
    }

    private fun isAccessibilityEnabled(): Boolean {
        val flat = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val svc = "$packageName/${com.cybertkr.suboverlay.a11y.SubtitleSyncService::class.java.name}"
        return flat.split(':').any { it.equals(svc, ignoreCase = true) }
    }

    private fun setupOverlaySettings() {
        lifecycleScope.launch {
            val alpha = settings.getIdleAlphaPercent()
            val secs = settings.getFadeSeconds()
            binding.seekOpacity.value = alpha.coerceIn(0, 100).toFloat()
            binding.seekFade.value = secs.coerceIn(0, 20).toFloat()
            renderOpacityLabel(alpha)
            renderFadeLabel(secs)
        }
        binding.seekOpacity.addOnChangeListener { _, value, fromUser ->
            renderOpacityLabel(value.toInt())
            if (fromUser) applyLive()
        }
        binding.seekOpacity.addOnSliderTouchListener(object : com.google.android.material.slider.Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: com.google.android.material.slider.Slider) {}
            override fun onStopTrackingTouch(slider: com.google.android.material.slider.Slider) {
                lifecycleScope.launch { settings.setIdleAlphaPercent(slider.value.toInt()) }
            }
        })
        binding.seekFade.addOnChangeListener { _, value, fromUser ->
            renderFadeLabel(value.toInt())
            if (fromUser) applyLive()
        }
        binding.seekFade.addOnSliderTouchListener(object : com.google.android.material.slider.Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: com.google.android.material.slider.Slider) {}
            override fun onStopTrackingTouch(slider: com.google.android.material.slider.Slider) {
                lifecycleScope.launch { settings.setFadeSeconds(slider.value.toInt()) }
            }
        })
    }

    private fun applyLive() {
        com.cybertkr.suboverlay.overlay.OverlayService.applyLiveConfig(
            binding.seekOpacity.value.toInt(), binding.seekFade.value.toInt()
        )
    }

    private fun renderOpacityLabel(v: Int) {
        binding.opacityValue.text =
            if (v <= 0) getString(R.string.opacity_hidden) else getString(R.string.opacity_pct, v)
    }

    private fun renderFadeLabel(v: Int) {
        binding.fadeValue.text =
            if (v <= 0) getString(R.string.fade_never) else getString(R.string.fade_secs, v)
    }

    private fun loadSrt(uri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) {
        }
        val bytes = try {
            contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (_: Exception) {
            null
        }
        if (bytes == null) {
            Toast.makeText(this, getString(R.string.file_error), Toast.LENGTH_SHORT).show()
            lifecycleScope.launch { settings.removeRecent(uri.toString()); renderRecent() }
            return
        }
        val raw = com.cybertkr.suboverlay.core.SrtText.decode(bytes)
        val result = com.cybertkr.suboverlay.core.SrtParser.parse(raw)
        val name = displayName(uri)
        SubtitleRepository.cues = result.cues
        SubtitleRepository.title = name
        binding.srtInfo.text = getString(R.string.srt_loaded, name, result.cues.size)
        lifecycleScope.launch {
            SubtitleRepository.savedOffsetMs = settings.getOffset(name)
            settings.addRecent(uri.toString(), name)
            renderRecent()
        }
    }

    private fun displayName(uri: Uri): String {
        try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c ->
                    if (c.moveToFirst()) {
                        val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) c.getString(idx)?.let { return it }
                    }
                }
        } catch (_: Exception) {
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "altyazı"
    }

    private var recentExpanded = false

    private fun applyRecentExpanded() {
        binding.recentContainer.visibility = if (recentExpanded) View.VISIBLE else View.GONE
        val arrow = if (recentExpanded) " ▾" else " ▸"
        binding.recentLabel.text = getString(R.string.recent_label) + arrow
    }

    private fun renderRecent() {
        lifecycleScope.launch {
            val recent = settings.getRecent()
            if (recent.isEmpty()) {
                binding.recentLabel.visibility = View.GONE
                binding.recentContainer.visibility = View.GONE
            } else {
                binding.recentLabel.visibility = View.VISIBLE
                binding.recentLabel.isClickable = true
                binding.recentLabel.setOnClickListener {
                    recentExpanded = !recentExpanded
                    applyRecentExpanded()
                }
                applyRecentExpanded()
            }
            binding.recentContainer.removeAllViews()
            for ((uriStr, name) in recent) {
                val row = TextView(this@MainActivity).apply {
                    text = name
                    textSize = 14f
                    setPadding(dp(14), dp(12), dp(14), dp(12))
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.MIDDLE
                    isClickable = true
                    setBackgroundResource(R.drawable.btn_secondary)
                    setTextColor(0xFF2E7CF6.toInt())
                    setOnClickListener { loadSrt(Uri.parse(uriStr)) }
                }
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(8) }
                binding.recentContainer.addView(row, lp)
            }
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun requestNotifIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
