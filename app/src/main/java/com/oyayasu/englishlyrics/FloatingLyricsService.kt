package com.oyayasu.englishlyrics

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class FloatingLyricsService : Service() {
    companion object {
        const val ACTION_START = "com.oyayasu.englishlyrics.START"
        const val ACTION_STOP = "com.oyayasu.englishlyrics.STOP"
        const val ACTION_REFRESH = "com.oyayasu.englishlyrics.REFRESH"
        private const val CHANNEL_ID = "lyrics_overlay"
        private const val NOTIFICATION_ID = 1501
        @Volatile var isRunning = false
            private set
    }

    private enum class Page { BUBBLE, SEARCH, LYRICS, MANUAL }

    private lateinit var manager: WindowManager
    private lateinit var store: LyricsStore
    private val repository = LyricsRepository()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var overlay: View? = null
    private var parameters: WindowManager.LayoutParams? = null
    private var page = Page.BUBBLE
    private var activeSong: Song? = null
    private var activeLyrics = ""
    private var searchText = ""
    private var searchJob: Job? = null
    private var lyricsJob: Job? = null
    private var resultList: LinearLayout? = null
    private var statusLabel: TextView? = null
    private var lyricsLabel: TextView? = null
    private var panelHeight = 0

    private val dark get() = Prefs.isDark(this)
    private val ink get() = if (dark) Color.rgb(238, 244, 250) else Color.rgb(35, 49, 67)
    private val secondary get() = if (dark) Color.rgb(176, 190, 207) else Color.rgb(107, 123, 144)
    private val surface get() = if (dark) Color.rgb(38, 51, 66) else Color.WHITE
    private val accent get() = if (dark) Color.rgb(159, 208, 239) else Color.rgb(62, 106, 143)

    override fun onCreate() {
        super.onCreate()
        manager = getSystemService(WINDOW_SERVICE) as WindowManager
        store = LyricsStore(this)
        createChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!isRunning) {
            startForeground(NOTIFICATION_ID, createNotification())
            isRunning = true
            showBubble()
        } else if (intent?.action == ACTION_REFRESH) {
            when (page) {
                Page.BUBBLE -> showBubble()
                Page.SEARCH -> showSearch(false)
                Page.LYRICS -> showLyricsPanel()
                Page.MANUAL -> showManual()
            }
        } else if (intent?.action == ACTION_START) {
            showBubble()
        }
        return START_NOT_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        when (page) {
            Page.BUBBLE -> showBubble()
            Page.SEARCH -> showSearch(false)
            Page.LYRICS -> showLyricsPanel()
            Page.MANUAL -> showManual()
        }
    }

    override fun onDestroy() {
        isRunning = false
        searchJob?.cancel()
        lyricsJob?.cancel()
        scope.cancel()
        removeOverlay()
        super.onDestroy()
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, getString(R.string.notification_channel), NotificationManager.IMPORTANCE_LOW
        )
        channel.description = "Mostra a lupa e permite encerrar o leitor flutuante"
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, FloatingLyricsService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_search)
            .setContentTitle("English Lyrics")
            .setContentText("Leitor flutuante ativo")
            .setContentIntent(open)
            .addAction(R.drawable.ic_close, "Encerrar", stop)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

    private fun rounded(fill: Int, radius: Int = 22, stroke: Int? = null): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(fill)
            cornerRadius = dp(radius).toFloat()
            if (stroke != null) setStroke(dp(1), stroke)
        }

    private fun panelBackground(): GradientDrawable {
        val alpha = (Prefs.opacity(this) * 255).toInt()
        val fill = Color.argb(alpha, Color.red(surface), Color.green(surface), Color.blue(surface))
        val border = if (dark) Color.argb(100, 224, 238, 252) else Color.argb(175, 255, 255, 255)
        return rounded(fill, 27, border)
    }

    private fun text(
        content: String,
        size: Float = 15f,
        color: Int = ink,
        bold: Boolean = false
    ): TextView = TextView(this).apply {
        this.text = content
        setTextColor(color)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
        typeface = Typeface.create(if (bold) "sans-serif-medium" else "sans-serif", Typeface.NORMAL)
        includeFontPadding = true
        gravity = Gravity.CENTER_VERTICAL
    }

    private fun iconButton(drawable: Int, description: String, action: () -> Unit): ImageView =
        ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(36))
            setImageResource(drawable)
            setColorFilter(ink)
            setPadding(dp(9), dp(9), dp(9), dp(9))
            background = rounded(if (dark) Color.argb(80, 255, 255, 255) else Color.argb(165, 230, 238, 245), 13)
            isClickable = true
            isFocusable = true
            contentDescription = description
            setOnClickListener { action() }
        }

    private fun spacer(width: Int = 0): View = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(dp(width), 1)
    }

    private fun labelButton(label: String, strong: Boolean = false, action: () -> Unit): TextView =
        text(label, 13f, if (strong) Color.WHITE else ink, strong).apply {
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = rounded(
                if (strong) accent else if (dark) Color.argb(70, 255, 255, 255) else Color.rgb(233, 240, 246),
                14
            )
            setOnClickListener { action() }
        }

    private fun header(title: String, back: (() -> Unit)? = null, actions: List<View> = emptyList()): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(-1, dp(43))
            if (back != null) {
                addView(iconButton(R.drawable.ic_back, "Voltar", back))
                addView(spacer(7))
            }
            addView(text(title, 17f, ink, true).apply {
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }, LinearLayout.LayoutParams(0, -2, 1f))
            actions.forEach { button ->
                addView(spacer(6))
                addView(button)
            }
        }

    private fun outerPanel(fullHeight: Boolean): Pair<FrameLayout, LinearLayout> {
        val outer = FrameLayout(this).apply { setPadding(dp(11), dp(2), dp(11), dp(9)) }
        val inner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = panelBackground()
            elevation = dp(17).toFloat()
            setPadding(dp(17), dp(10), dp(17), dp(15))
        }
        outer.addView(inner, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            if (fullHeight) FrameLayout.LayoutParams.MATCH_PARENT else FrameLayout.LayoutParams.WRAP_CONTENT
        ))
        return outer to inner
    }

    private fun layoutParams(width: Int, height: Int, focusable: Boolean): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            width, height, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                (if (focusable) 0 else WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE),
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }

    private fun removeOverlay() {
        val prior = overlay
        overlay = null
        parameters = null
        if (prior != null) {
            try { manager.removeViewImmediate(prior) } catch (_: Exception) { }
        }
    }

    private fun attach(view: View, lp: WindowManager.LayoutParams) {
        removeOverlay()
        try {
            manager.addView(view, lp)
            overlay = view
            parameters = lp
        } catch (_: Exception) {
            stopSelf()
        }
    }

    private fun dismissKeyboard() {
        val token = overlay?.windowToken ?: return
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(token, 0)
    }

    private fun showBubble() {
        page = Page.BUBBLE
        searchJob?.cancel()
        dismissKeyboard()
        val size = dp(62)
        val root = FrameLayout(this).apply {
            background = rounded(
                if (dark) Color.argb(244, 40, 59, 77) else Color.argb(246, 251, 253, 255),
                22,
                if (dark) Color.argb(100, 240, 247, 255) else Color.argb(190, 255, 255, 255)
            )
            elevation = dp(13).toFloat()
            contentDescription = "Pesquisar letras"
        }
        root.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_search)
            setColorFilter(if (dark) Color.WHITE else Color.rgb(39, 67, 92))
            setPadding(dp(15), dp(15), dp(15), dp(15))
        }, FrameLayout.LayoutParams(-1, -1))

        val params = layoutParams(size, size, false).apply {
            gravity = Gravity.TOP or Gravity.START
            x = Prefs.bubbleX(this@FloatingLyricsService).coerceIn(0, max(0, resources.displayMetrics.widthPixels - size))
            y = Prefs.bubbleY(this@FloatingLyricsService).coerceIn(0, max(0, resources.displayMetrics.heightPixels - size - dp(30)))
        }
        val slop = ViewConfiguration.get(this).scaledTouchSlop
        var downX = 0f
        var downY = 0f
        var initialX = 0
        var initialY = 0
        var moved = false
        root.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    initialX = params.x
                    initialY = params.y
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (abs(dx) > slop || abs(dy) > slop) moved = true
                    if (moved) {
                        params.x = (initialX + dx.toInt()).coerceIn(0, max(0, resources.displayMetrics.widthPixels - size))
                        params.y = (initialY + dy.toInt()).coerceIn(0, max(0, resources.displayMetrics.heightPixels - size - dp(30)))
                        try { manager.updateViewLayout(root, params) } catch (_: Exception) { }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (moved) Prefs.setBubblePosition(this, params.x, params.y) else showSearch()
                    true
                }
                else -> true
            }
        }
        attach(root, params)
    }

    private fun showSearch(openKeyboard: Boolean = true) {
        page = Page.SEARCH
        searchJob?.cancel()
        val (root, panel) = outerPanel(false)
        val close = iconButton(R.drawable.ic_close, "Encerrar", ::stopSelf)
        val minimize = iconButton(R.drawable.ic_minus, "Minimizar", ::showBubble)
        panel.addView(header("Buscar letras", actions = listOf(minimize, close)))
        panel.addView(spacerHeight(11))

        val input = EditText(this).apply {
            isSingleLine = true
            setText(searchText)
            setSelection(text.length)
            hint = "Música ou artista..."
            setHintTextColor(secondary)
            setTextColor(ink)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            background = rounded(
                if (dark) Color.argb(110, 15, 24, 34) else Color.rgb(239, 244, 249),
                16
            )
            setPadding(dp(16), dp(13), dp(14), dp(13))
        }
        panel.addView(input, LinearLayout.LayoutParams(-1, dp(51)))
        panel.addView(spacerHeight(10))

        statusLabel = text("", 12f, secondary).apply { setPadding(dp(2), 0, 0, dp(5)) }
        panel.addView(statusLabel)
        val scroll = ScrollView(this).apply {
            isFillViewport = false
            clipToPadding = false
            layoutParams = LinearLayout.LayoutParams(-1,
                min(dp(302), max(dp(115), (resources.displayMetrics.heightPixels * 0.36).toInt())))
        }
        val results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        resultList = results
        scroll.addView(results)
        panel.addView(scroll)
        panel.addView(spacerHeight(12))
        panel.addView(
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(labelButton("Pesquisar na web") { openWebSearch(searchText) },
                    LinearLayout.LayoutParams(0, dp(42), 1f))
                addView(spacer(9))
                addView(labelButton("Colar letra") { showManual() },
                    LinearLayout.LayoutParams(0, dp(42), 1f))
            }
        )
        val params = layoutParams(-1, -2, true)
        attach(root, params)
        renderRecent()
        if (searchText.isNotBlank()) scheduleSearch(searchText, 100)

        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchText = s?.toString().orEmpty()
                scheduleSearch(searchText, 650)
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        input.setOnEditorActionListener { _, _, _ ->
            searchText = input.text.toString()
            scheduleSearch(searchText, 0)
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
                .hideSoftInputFromWindow(input.windowToken, 0)
            input.clearFocus()
            true
        }
        if (openKeyboard) {
            input.requestFocus()
            input.postDelayed({
                (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
                    .showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
            }, 160)
        } else {
            input.clearFocus()
        }
    }

    private fun spacerHeight(height: Int): View = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(1, dp(height))
    }

    private fun renderRecent() {
        val target = resultList ?: return
        target.removeAllViews()
        val recent = store.recent().take(8)
        statusLabel?.text = if (recent.isEmpty()) "Digite uma música para começar" else "Recentes · disponíveis offline"
        recent.forEach { addSongRow(target, it) }
    }

    private fun scheduleSearch(query: String, debounceMs: Long) {
        searchJob?.cancel()
        if (query.isBlank()) {
            renderRecent()
            return
        }
        if (query.trim().length < 2) {
            statusLabel?.text = "Digite pelo menos 2 caracteres"
            resultList?.removeAllViews()
            return
        }
        statusLabel?.text = "Pesquisando..."
        searchJob = scope.launch {
            delay(debounceMs)
            try {
                val songs = repository.search(query.trim())
                if (page != Page.SEARCH || searchText != query) return@launch
                resultList?.removeAllViews()
                if (songs.isEmpty()) {
                    statusLabel?.text = "Nenhum resultado no catálogo. Tente a web ou cole a letra."
                } else {
                    statusLabel?.text = "${songs.size} resultado(s) · LRCLIB"
                    val container = resultList ?: return@launch
                    songs.forEach { addSongRow(container, it) }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (page == Page.SEARCH && searchText == query) {
                    statusLabel?.text = error.message ?: "Não foi possível pesquisar agora."
                    resultList?.removeAllViews()
                }
            }
        }
    }

    private fun addSongRow(target: LinearLayout, song: Song) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(13), dp(10), dp(13), dp(10))
            background = rounded(
                if (dark) Color.argb(75, 255, 255, 255) else Color.argb(185, 238, 244, 249),
                14
            )
            isClickable = true
            isFocusable = true
            contentDescription = "${song.title}, ${song.artist}"
            setOnClickListener { selectSong(song) }
        }
        row.addView(text(song.title, 14f, ink, true).apply {
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        })
        row.addView(text(
            song.artist + if (song.instrumental) " · instrumental" else if (song.source == "Manual") " · salva" else "",
            12f, secondary
        ).apply {
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        })
        target.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(7) })
    }

    private fun selectSong(song: Song) {
        searchJob?.cancel()
        dismissKeyboard()
        activeSong = song
        activeLyrics = song.lyrics
        showLyricsPanel()
        if (song.lyrics.isNotBlank()) {
            store.save(song)
            return
        }
        if (song.instrumental) return
        lyricsJob?.cancel()
        lyricsJob = scope.launch {
            try {
                val complete = repository.complete(song)
                if (page == Page.LYRICS && activeSong?.id == song.id && activeSong?.title == song.title) {
                    activeSong = complete
                    activeLyrics = complete.lyrics
                    store.save(complete)
                    lyricsLabel?.text = displayLyrics(complete)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (page == Page.LYRICS && activeSong?.id == song.id) {
                    lyricsLabel?.text = error.message ?: "Não foi possível carregar a letra."
                }
            }
        }
    }

    private fun displayLyrics(song: Song): String = when {
        song.instrumental -> "Esta faixa está marcada como instrumental."
        activeLyrics.isNotBlank() -> activeLyrics
        song.id > 0 -> "Nenhuma letra disponível nesta fonte. Experimente a pesquisa na web ou cole a letra."
        else -> "Cole uma letra para ler aqui."
    }

    private fun showLyricsPanel() {
        page = Page.LYRICS
        dismissKeyboard()
        val song = activeSong ?: run { showBubble(); return }
        val maxHeight = (resources.displayMetrics.heightPixels * 0.83f).toInt()
        val minHeight = min(dp(185), maxHeight)
        if (panelHeight == 0) panelHeight = (resources.displayMetrics.heightPixels * 0.51f).toInt()
        panelHeight = panelHeight.coerceIn(minHeight, maxHeight)
        val (root, panel) = outerPanel(true)

        val handle = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(-1, dp(24))
        }
        handle.addView(View(this).apply {
            background = rounded(if (dark) Color.argb(155, 220, 233, 245) else Color.argb(145, 111, 127, 145), 6)
        }, FrameLayout.LayoutParams(dp(40), dp(4), Gravity.CENTER))
        var touchY = 0f
        var startHeight = panelHeight
        handle.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    touchY = event.rawY
                    startHeight = panelHeight
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val height = (startHeight + (touchY - event.rawY)).toInt()
                    panelHeight = height.coerceIn(minHeight, maxHeight)
                    val lp = parameters
                    if (lp != null && overlay === root) {
                        lp.height = panelHeight
                        try { manager.updateViewLayout(root, lp) } catch (_: Exception) { }
                    }
                    true
                }
                else -> true
            }
        }
        panel.addView(handle)
        val decrease = iconButton(R.drawable.ic_minus, "Diminuir fonte") {
            val size = (Prefs.textSize(this) - 1).coerceAtLeast(14f)
            Prefs.setTextSize(this, size)
            lyricsLabel?.setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
        }
        val increase = iconButton(R.drawable.ic_plus, "Aumentar fonte") {
            val size = (Prefs.textSize(this) + 1).coerceAtMost(32f)
            Prefs.setTextSize(this, size)
            lyricsLabel?.setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
        }
        val minimize = iconButton(R.drawable.ic_minus, "Minimizar painel", ::showBubble)
        val close = iconButton(R.drawable.ic_close, "Encerrar", ::stopSelf)
        panel.addView(header(song.title, ::showSearch, listOf(decrease, increase, minimize, close)))
        panel.addView(text(song.artist, 12f, secondary).apply {
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(dp(2), 0, 0, dp(9))
        })
        val separator = View(this).apply {
            setBackgroundColor(if (dark) Color.argb(42, 255, 255, 255) else Color.argb(32, 35, 49, 67))
        }
        panel.addView(separator, LinearLayout.LayoutParams(-1, dp(1)))
        val scroll = ScrollView(this).apply {
            fillViewport = true
            isVerticalScrollBarEnabled = false
            clipToPadding = false
        }
        val lyric = text(displayLyrics(song), Prefs.textSize(this), ink).apply {
            gravity = Gravity.TOP or Gravity.START
            setLineSpacing(dp(7).toFloat(), 1.08f)
            setPadding(dp(4), dp(17), dp(4), dp(18))
        }
        lyricsLabel = lyric
        scroll.addView(lyric, ScrollView.LayoutParams(-1, -2))
        panel.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(labelButton("Web") { openWebSearch(song.title + " " + song.artist) },
                LinearLayout.LayoutParams(0, dp(39), 1f))
            addView(spacer(9))
            addView(labelButton("Colar letra") { showManual(song.title, song.artist) },
                LinearLayout.LayoutParams(0, dp(39), 1f))
        }
        panel.addView(actions)
        attach(root, layoutParams(-1, panelHeight, false))
    }

    private fun showManual(title: String = searchText, artist: String = "") {
        page = Page.MANUAL
        searchJob?.cancel()
        dismissKeyboard()
        val (root, panel) = outerPanel(false)
        panel.addView(header("Adicionar letra", ::showSearch, listOf(
            iconButton(R.drawable.ic_minus, "Minimizar", ::showBubble),
            iconButton(R.drawable.ic_close, "Encerrar", ::stopSelf)
        )))
        panel.addView(spacerHeight(9))
        val titleInput = manualInput("Título da música", title, 1)
        val artistInput = manualInput("Artista (opcional)", artist, 1)
        val lyricsInput = manualInput("Cole a letra aqui...", "", 7)
        panel.addView(titleInput)
        panel.addView(spacerHeight(9))
        panel.addView(artistInput)
        panel.addView(spacerHeight(9))
        panel.addView(lyricsInput, LinearLayout.LayoutParams(-1,
            min(dp(245), max(dp(128), (resources.displayMetrics.heightPixels * 0.29f).toInt()))))
        panel.addView(spacerHeight(11))
        panel.addView(labelButton("Salvar e abrir", true) {
            val body = lyricsInput.text.toString().trim()
            if (body.isBlank()) {
                lyricsInput.error = "Cole uma letra"
            } else {
                val song = Song(
                    id = 0L,
                    title = titleInput.text.toString().trim().ifBlank { "Letra manual" },
                    artist = artistInput.text.toString().trim().ifBlank { "Artista desconhecido" },
                    lyrics = body,
                    source = "Manual"
                )
                activeSong = song
                activeLyrics = body
                store.save(song)
                showLyricsPanel()
            }
        }, LinearLayout.LayoutParams(-1, dp(45)))
        attach(root, layoutParams(-1, -2, true))
    }

    private fun manualInput(hintText: String, initial: String, lines: Int): EditText =
        EditText(this).apply {
            hint = hintText
            setText(initial)
            setTextColor(ink)
            setHintTextColor(secondary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                (if (lines > 1) android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE else 0)
            isSingleLine = lines == 1
            if (lines > 1) {
                gravity = Gravity.TOP or Gravity.START
                minLines = lines
            }
            background = rounded(
                if (dark) Color.argb(110, 15, 24, 34) else Color.rgb(239, 244, 249), 14
            )
            setPadding(dp(13), dp(11), dp(13), dp(11))
        }

    private fun openWebSearch(query: String) {
        if (query.isBlank()) {
            statusLabel?.text = "Digite uma música antes de pesquisar na web."
            return
        }
        val url = "https://www.google.com/search?q=" + Uri.encode(query.trim() + " letra lyrics")
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            showBubble()
        } catch (_: Exception) {
            statusLabel?.text = "Nenhum navegador disponível."
        }
    }
}
