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

        renderRecent()
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

    private fun renderRecent() {
        lifecycleScope.launch {
            val recent = settings.getRecent()
            binding.recentLabel.visibility = if (recent.isEmpty()) View.GONE else View.VISIBLE
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
