package com.example.floating

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.audio.SpeechAudioEngine
import com.example.data.model.SupportedLanguages
import com.example.data.repository.LanguageAiRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs

class FloatingAssistService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var windowManager: WindowManager
    private lateinit var aiRepository: LanguageAiRepository
    private lateinit var audioEngine: SpeechAudioEngine

    private var bubbleView: View? = null
    private var panelView: View? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var panelParams: WindowManager.LayoutParams? = null

    private var targetLangCode: String = "es-ES"

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        aiRepository = LanguageAiRepository()
        audioEngine = SpeechAudioEngine(applicationContext)
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        _isRunning.value = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.getStringExtra(EXTRA_TARGET_LANG)?.let {
            if (it.isNotBlank()) targetLangCode = it
        }
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (bubbleView == null && panelView == null) {
            showBubble()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        removeViews()
        audioEngine.release()
        serviceScope.cancel()
        _isRunning.value = false
        super.onDestroy()
    }

    private fun overlayType(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showBubble() {
        removeViews()
        val bubble = TextView(this).apply {
            text = "LF"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            val sizePx = dp(54)
            width = sizePx
            height = sizePx
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#2047D3"))
                setStroke(dp(2), Color.parseColor("#FFFFFF"))
            }
            elevation = dp(8).toFloat()
            contentDescription = "Open LingoFloat quick translator"
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(18)
            y = dp(180)
        }

        var initialX = 0
        var initialY = 0
        var touchX = 0f
        var touchY = 0f
        var moved = false

        bubble.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchX).toInt()
                    val dy = (event.rawY - touchY).toInt()
                    if (abs(dx) > 10 || abs(dy) > 10) moved = true
                    params.x = initialX + dx
                    params.y = initialY + dy
                    windowManager.updateViewLayout(bubble, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        showExpandedPanel()
                    }
                    true
                }
                else -> false
            }
        }

        bubbleParams = params
        bubbleView = bubble
        windowManager.addView(bubble, params)
    }

    private fun showExpandedPanel() {
        removeViews()
        val lang = SupportedLanguages.byCode(targetLangCode)

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(20).toFloat()
                setColor(Color.parseColor("#FAF8F4"))
                setStroke(dp(1), Color.parseColor("#DAD5C9"))
            }
            elevation = dp(12).toFloat()
        }

        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val title = TextView(this).apply {
            text = "LingoFloat Quick Read (${lang.shortBadge})"
            setTextColor(Color.parseColor("#151923"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val minimizeBtn = Button(this).apply {
            text = "Min"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setOnClickListener { showBubble() }
        }
        val closeBtn = Button(this).apply {
            text = "Close"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setOnClickListener { stopSelf() }
        }
        headerRow.addView(title)
        headerRow.addView(minimizeBtn)
        headerRow.addView(closeBtn)

        val input = EditText(this).apply {
            hint = "Paste or type text to read & translate..."
            setTextColor(Color.parseColor("#151923"))
            setHintTextColor(Color.parseColor("#565E70"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            maxLines = 3
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = dp(8)
            layoutParams = lp
        }

        val resultText = TextView(this).apply {
            text = "Tap Paste or Translate to hear pronunciation and IPA."
            setTextColor(Color.parseColor("#2047D3"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = dp(8)
            layoutParams = lp
        }

        var lastTranslated = ""

        val actionsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = dp(10)
            layoutParams = lp
        }

        val pasteBtn = Button(this).apply {
            text = "Paste"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener {
                val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                val clip = cm.primaryClip?.getItemAt(0)?.coerceToText(this@FloatingAssistService)?.toString()
                if (!clip.isNullOrBlank()) {
                    input.setText(clip)
                } else {
                    resultText.text = "Clipboard is empty. Type a phrase above."
                }
            }
        }

        val translateBtn = Button(this).apply {
            text = "Translate"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener {
                val query = input.text.toString().trim()
                if (query.isEmpty()) {
                    resultText.text = "Enter text first."
                    return@setOnClickListener
                }
                resultText.text = "Translating..."
                serviceScope.launch {
                    val res = aiRepository.translateAndAnalyze(query, "en-US", targetLangCode)
                    res.onSuccess { breakdown ->
                        lastTranslated = breakdown.translatedText
                        resultText.text = "${breakdown.translatedText}\n${breakdown.ipaPhonetics}"
                    }.onFailure { err ->
                        resultText.text = err.localizedMessage ?: "Translation failed."
                    }
                }
            }
        }

        val speakBtn = Button(this).apply {
            text = "Speak"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener {
                val toSpeak = lastTranslated.ifBlank { input.text.toString().trim() }
                if (toSpeak.isEmpty()) {
                    resultText.text = "Nothing to pronounce yet."
                    return@setOnClickListener
                }
                serviceScope.launch {
                    val speakLang = if (lastTranslated.isNotBlank()) targetLangCode else "en-US"
                    audioEngine.speakText(toSpeak, speakLang, 0.95f)
                }
            }
        }

        val studioBtn = Button(this).apply {
            text = "Coach"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener {
                val textForStudio = lastTranslated.ifBlank { input.text.toString().trim() }
                val launchIntent = Intent(this@FloatingAssistService, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra(EXTRA_PREFILL_TEXT, textForStudio)
                }
                startActivity(launchIntent)
                showBubble()
            }
        }

        actionsRow.addView(pasteBtn)
        actionsRow.addView(translateBtn)
        actionsRow.addView(speakBtn)
        actionsRow.addView(studioBtn)

        container.addView(headerRow)
        container.addView(input)
        container.addView(resultText)
        container.addView(actionsRow)

        val widthPx = (resources.displayMetrics.widthPixels * 0.90f).toInt()
        val params = WindowManager.LayoutParams(
            widthPx,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(90)
        }

        panelParams = params
        panelView = container
        windowManager.addView(container, params)
    }

    private fun removeViews() {
        bubbleView?.let { runCatching { windowManager.removeView(it) } }
        panelView?.let { runCatching { windowManager.removeView(it) } }
        bubbleView = null
        panelView = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.floating_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.floating_channel_desc)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.floating_notification_title))
            .setContentText(getString(R.string.floating_notification_body))
            .setContentIntent(openIntent)
            .setOngoing(true)
            .build()
    }

    private fun dp(value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics
        ).toInt()
    }

    companion object {
        const val CHANNEL_ID = "lingofloat_overlay_channel"
        const val NOTIFICATION_ID = 4102
        const val EXTRA_TARGET_LANG = "extra_target_lang"
        const val EXTRA_PREFILL_TEXT = "extra_prefill_text"

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        fun start(context: Context, targetLanguageCode: String) {
            val intent = Intent(context, FloatingAssistService::class.java).apply {
                putExtra(EXTRA_TARGET_LANG, targetLanguageCode)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FloatingAssistService::class.java))
        }
    }
}
