package com.hunttv.streamtv.activities

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.drm.DefaultDrmSessionManager
import androidx.media3.exoplayer.drm.FrameworkMediaDrm
import androidx.media3.exoplayer.drm.LocalMediaDrmCallback
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.hunttv.streamtv.R
import com.hunttv.streamtv.models.ParsedStreamData
import com.hunttv.streamtv.utils.StreamParser
import org.json.JSONObject

@UnstableApi
class PlayerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_STREAM_URL   = "stream_url"
        const val EXTRA_STREAM_TITLE = "stream_title"
        const val EXTRA_IS_PLAYLIST  = "is_playlist"
        private const val TAG        = "PlayerActivity"

        private val PLAYLIST_UA_LIST = listOf(
            "Mozilla/5.0 (Linux; Android 10; SM-G975F) AppleWebKit/537.36 Chrome/91.0.4472.120 Mobile Safari/537.36",
            "VLC/3.0.18 LibVLC/3.0.18",
            "Dalvik/2.1.0 (Linux; U; Android 10; SM-G975F Build/QP1A.190711.020)",
            "stagefright/1.2 (Linux;Android 10)",
            "ExoPlayer/2.19.1 (Linux; Android 10)"
        )

        private val URL_REGEX = Regex(
            "^(?:https?|rtmps?|rtsp)://(?:[a-zA-Z0-9.-]+|\\d{1,3}(?:\\.\\d{1,3}){3})(?::\\d+)?(?:[/?].*)?$"
        )
    }

    private var player: ExoPlayer? = null
    private lateinit var playerView:    PlayerView
    private lateinit var trackSelector: DefaultTrackSelector

    private var selectedVideoBitrate  = -1
    private var selectedVideoHeight   = -1
    private var selectedAudioBitrate  = -1
    private var selectedAudioChannels = -1
    private var selectedVideoTrackGroup: androidx.media3.common.Tracks.Group? = null
    private var selectedVideoTrackIndex  = -1
    private var selectedAudioTrackGroup: androidx.media3.common.Tracks.Group? = null
    private var selectedAudioTrackIndex  = -1

    private var rendererFallback = 0   // 0=HW, 1=prefer-SW, 2=force-SW
    private var retryIndex       = 0
    private var currentStreamUrl = ""
    private var isPlaylistMode   = false

    private val mainHandler = Handler(Looper.getMainLooper())

    // ─────────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableFullscreen()
        setContentView(R.layout.activity_player)

        playerView = findViewById(R.id.player_view)
        playerView.findViewById<ImageButton?>(R.id.btn_quality)?.setOnClickListener { showVideoQualityDialog() }
        playerView.findViewById<ImageButton?>(R.id.btn_audio)?.setOnClickListener   { showAudioTracksDialog()  }

        val streamUrl  = intent.getStringExtra(EXTRA_STREAM_URL)
        isPlaylistMode = intent.getBooleanExtra(EXTRA_IS_PLAYLIST, false)

        if (streamUrl.isNullOrEmpty()) {
            Toast.makeText(this, "Invalid stream URL", Toast.LENGTH_SHORT).show()
            finish(); return
        }

        currentStreamUrl = streamUrl
        initializePlayer(streamUrl)
    }

    override fun onResume()  { super.onResume();  player?.playWhenReady = true  }
    override fun onPause()   { super.onPause();   player?.playWhenReady = false }
    override fun onDestroy() {
        super.onDestroy()
        mainHandler.removeCallbacksAndMessages(null)
        player?.release()
        player = null
    }

    private fun enableFullscreen() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN      or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Player init
    // ─────────────────────────────────────────────────────────────────────────

    private fun initializePlayer(streamUrl: String) {
        try {
            val parsed  = StreamParser.parseStreamUrl(streamUrl)
            Log.d(TAG, "▶ url=${parsed.baseUrl}")
            Log.d(TAG, "  scheme=${parsed.drmScheme} license=${parsed.drmLicense?.take(60)}")
            Log.d(TAG, "  rendererFallback=$rendererFallback retryUA=$retryIndex")

            val headers = buildRequestHeaders(parsed)

            val dataSourceFactory = DefaultHttpDataSource.Factory()
                .setUserAgent(headers["User-Agent"] ?: "HuntTV/1.0")
                .setDefaultRequestProperties(headers)
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(30_000)
                .setReadTimeoutMs(30_000)

            trackSelector = DefaultTrackSelector(this)

            val loadControl = DefaultLoadControl.Builder()
                .setBufferDurationsMs(8_000, 50_000, 1_500, 4_000)
                .build()

            val renderersFactory = DefaultRenderersFactory(this).apply {
                setExtensionRendererMode(
                    when (rendererFallback) {
                        0    -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF
                        1    -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
                        else -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
                    }
                )
                setEnableDecoderFallback(true)
            }

            val (mediaItem, drmSessionManager) = buildMediaConfig(parsed, headers)

            val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory).also { factory ->
                if (drmSessionManager != null) {
                    factory.setDrmSessionManagerProvider { drmSessionManager }
                }
            }

            player?.release(); player = null

            player = ExoPlayer.Builder(this)
                .setTrackSelector(trackSelector)
                .setLoadControl(loadControl)
                .setMediaSourceFactory(mediaSourceFactory)
                .setRenderersFactory(renderersFactory)
                .build()

            // CRITICAL: attach to PlayerView FIRST, then set trackSelector params.
            // PlayerView.setPlayer() calls trackSelector.setParameters(...setViewportSize...)
            // which would constrain video tracks to screen size. We override it right after.
            playerView.player = player

            trackSelector.setParameters(
                trackSelector.buildUponParameters()
                    .clearVideoSizeConstraints()
                    .setAllowVideoMixedMimeTypeAdaptiveness(true)
                    .setAllowVideoNonSeamlessAdaptiveness(true)
                    .setAllowAudioMixedMimeTypeAdaptiveness(true)
                    .setSelectUndeterminedTextLanguage(true)
                    .setExceedVideoConstraintsIfNecessary(true)
                    .setExceedRendererCapabilitiesIfNecessary(true)
                    .build()
            )

            player?.addListener(object : Player.Listener {

                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_READY) {
                        retryIndex = 0; rendererFallback = 0
                        checkAndForceVideoTrack()
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    val msg = error.cause?.message ?: error.message ?: "Unknown"
                    Log.e(TAG, "✗ errorCode=${error.errorCode} msg=$msg")
                    when {
                        isRendererError(error) && rendererFallback < 2 -> {
                            rendererFallback++
                            Log.w(TAG, "→ SW fallback=$rendererFallback")
                            mainHandler.postDelayed({ initializePlayer(currentStreamUrl) }, 600)
                        }
                        isPlaylistMode
                                && retryIndex < PLAYLIST_UA_LIST.size - 1
                                && !parsed.headers.containsKey("User-Agent") -> {
                            retryIndex++
                            Log.w(TAG, "→ UA retry=$retryIndex")
                            mainHandler.postDelayed({ initializePlayer(currentStreamUrl) }, 600)
                        }
                        rendererFallback == 0 && retryIndex == 0 -> {
                            rendererFallback = 1
                            if (isPlaylistMode) retryIndex = 1
                            mainHandler.postDelayed({ initializePlayer(currentStreamUrl) }, 600)
                        }
                        else -> runOnUiThread {
                            Toast.makeText(this@PlayerActivity, "Playback failed: $msg", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            })

            player?.run { setMediaItem(mediaItem); prepare(); playWhenReady = true }

        } catch (e: Exception) {
            Log.e(TAG, "Init error: ${e.message}", e)
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    // If video track exists but wasn't auto-selected, force it
    private fun checkAndForceVideoTrack() {
        val p = player ?: return
        var hasVideo = false; var videoSelected = false
        var firstGroup: androidx.media3.common.Tracks.Group? = null; var firstIdx = 0
        for (g in p.currentTracks.groups) {
            if (g.type != C.TRACK_TYPE_VIDEO) continue
            hasVideo = true
            if (firstGroup == null) {
                firstGroup = g
                for (i in 0 until g.length) { if (g.isTrackSupported(i)) { firstIdx = i; break } }
            }
            if (g.isSelected) { videoSelected = true; break }
        }
        if (hasVideo && !videoSelected && firstGroup != null) {
            Log.w(TAG, "⚠ Video not selected — forcing override")
            trackSelector.setParameters(
                trackSelector.buildUponParameters()
                    .clearVideoSizeConstraints()
                    .addOverride(TrackSelectionOverride(firstGroup.mediaTrackGroup, listOf(firstIdx)))
                    .setExceedRendererCapabilitiesIfNecessary(true)
                    .build()
            )
        }
    }

    private fun isRendererError(e: PlaybackException) = e.errorCode in listOf(
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        PlaybackException.ERROR_CODE_VIDEO_FRAME_PROCESSING_FAILED
    )

    // ─────────────────────────────────────────────────────────────────────────
    // MediaItem + DRM
    //
    // PATH A: No DRM        → plain MediaItem
    // PATH B: HTTP license  → MediaItem with DrmConfiguration(uuid + licenseUri)
    // PATH C: Inline ClearKey JSON or kid:key pairs
    //         → MediaItem with DrmConfiguration(CLEARKEY_UUID, NO licenseUri)
    //           + LocalMediaDrmCallback via MediaSourceFactory.setDrmSessionManagerProvider
    //         NOTE: MediaItem MUST have DrmConfiguration so Media3 activates the DRM
    //         session. Without it Media3 plays the encrypted stream raw → black screen.
    // ─────────────────────────────────────────────────────────────────────────

    private fun buildMediaConfig(
        parsed:  ParsedStreamData,
        headers: Map<String, String>
    ): Pair<MediaItem, DefaultDrmSessionManager?> {

        val baseUri = Uri.parse(parsed.baseUrl)
        val scheme  = parsed.drmScheme
        val license = parsed.drmLicense

        // PATH A
        if (scheme == null || license == null) {
            Log.d(TAG, "PATH A: plain stream")
            return Pair(MediaItem.Builder().setUri(baseUri).build(), null)
        }

        val drmUuid = when (scheme.lowercase()) {
            "clearkey"  -> C.CLEARKEY_UUID
            "widevine"  -> C.WIDEVINE_UUID
            "playready" -> C.PLAYREADY_UUID
            else -> {
                Log.w(TAG, "Unknown DRM '$scheme' — playing without DRM")
                return Pair(MediaItem.Builder().setUri(baseUri).build(), null)
            }
        }

        // PATH B
        if (URL_REGEX.matches(license)) {
            Log.d(TAG, "PATH B: HTTP license")
            return Pair(
                MediaItem.Builder()
                    .setUri(baseUri)
                    .setDrmConfiguration(
                        MediaItem.DrmConfiguration.Builder(drmUuid)
                            .setLicenseUri(license)
                            .setLicenseRequestHeaders(headers)
                            .build()
                    ).build(),
                null
            )
        }

        // PATH C
        val clearKeyJson: String? = when {
            license.trimStart().startsWith("{") -> license
            else -> buildClearKeyJsonFromPairs(license)
        }

        if (clearKeyJson == null) {
            Log.w(TAG, "PATH C: failed to build ClearKey JSON — playing without DRM")
            return Pair(MediaItem.Builder().setUri(baseUri).build(), null)
        }

        Log.d(TAG, "PATH C: inline ClearKey JSON=${clearKeyJson.take(120)}")

        val mediaItem = MediaItem.Builder()
            .setUri(baseUri)
            .setDrmConfiguration(
                MediaItem.DrmConfiguration.Builder(C.CLEARKEY_UUID)
                    // No setLicenseUri() — LocalMediaDrmCallback handles key delivery
                    .build()
            ).build()

        val drmMgr = DefaultDrmSessionManager.Builder()
            .setUuidAndExoMediaDrmProvider(C.CLEARKEY_UUID, FrameworkMediaDrm.DEFAULT_PROVIDER)
            .setMultiSession(false)
            .build(LocalMediaDrmCallback(clearKeyJson.toByteArray(Charsets.UTF_8)))

        return Pair(mediaItem, drmMgr)
    }

    private fun buildClearKeyJsonFromPairs(license: String): String? {
        val sep = if (license.contains("|")) "|" else ","
        val entries = license.split(sep).mapNotNull { pair ->
            val idx = pair.indexOf(":"); if (idx < 0) return@mapNotNull null
            val kid = pair.substring(0, idx).trim()
            val key = pair.substring(idx + 1).trim()
            if (kid.isEmpty() || key.isEmpty()) return@mapNotNull null
            val (kidB64, keyB64) = try {
                Pair(hexToBase64Url(kid), hexToBase64Url(key))
            } catch (e: Exception) {
                Pair(kid, key)
            }
            """{"kty":"oct","k":"$keyB64","kid":"$kidB64"}"""
        }
        return if (entries.isEmpty()) null
        else """{"keys":[${entries.joinToString(",")}],"type":"temporary"}"""
    }

    private fun hexToBase64Url(hex: String): String {
        require(hex.length % 2 == 0) { "Odd hex length: ${hex.length}" }
        val bytes = ByteArray(hex.length / 2) { i ->
            hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Headers
    // ─────────────────────────────────────────────────────────────────────────

    private fun buildRequestHeaders(parsed: ParsedStreamData): MutableMap<String, String> {
        val h = mutableMapOf<String, String>()
        h.putAll(parsed.headers)
        parsed.extHttpJson?.let { json ->
            try {
                val obj = JSONObject(json); val keys = obj.keys()
                while (keys.hasNext()) {
                    val k = keys.next(); val v = obj.getString(k)
                    val norm = when (k.lowercase()) {
                        "cookie"     -> "Cookie"
                        "user-agent" -> "User-Agent"
                        "referer"    -> "Referer"
                        "origin"     -> "Origin"
                        else         -> k
                    }
                    h.putIfAbsent(norm, v)
                }
            } catch (e: Exception) { Log.w(TAG, "extHttpJson: ${e.message}") }
        }
        if (!h.containsKey("User-Agent"))
            h["User-Agent"] = if (isPlaylistMode) PLAYLIST_UA_LIST[retryIndex] else "HuntTV/1.0.0 (Android)"
        return h
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Track selection dialogs
    // ─────────────────────────────────────────────────────────────────────────

    private fun showVideoQualityDialog() {
        val p = player ?: return
        val items = mutableListOf<QualityItem>()
        var currentLabel: String? = null
        for (tg in p.currentTracks.groups)
            if (tg.type == C.TRACK_TYPE_VIDEO && tg.isSelected)
                for (i in 0 until tg.length)
                    if (tg.isTrackSelected(i)) { currentLabel = formatVideo(tg.getTrackFormat(i)); break }
        val isAuto = selectedVideoBitrate == -1
        items.add(QualityItem(if (isAuto && currentLabel != null) "Auto ($currentLabel)" else "Auto", isAuto = true, isSelected = isAuto))
        for (tg in p.currentTracks.groups)
            if (tg.type == C.TRACK_TYPE_VIDEO)
                for (i in 0 until tg.length) {
                    val f = tg.getTrackFormat(i)
                    items.add(QualityItem(formatVideo(f), TrackInfo("video", i, f, tg),
                        isSelected = !isAuto && f.bitrate == selectedVideoBitrate && f.height == selectedVideoHeight))
                }
        showMenu(items, "VIDEO QUALITY")
    }

    private fun showAudioTracksDialog() {
        val p = player ?: return
        val items = mutableListOf<QualityItem>()
        var currentLabel: String? = null
        for (tg in p.currentTracks.groups)
            if (tg.type == C.TRACK_TYPE_AUDIO && tg.isSelected)
                for (i in 0 until tg.length)
                    if (tg.isTrackSelected(i)) { currentLabel = formatAudio(tg.getTrackFormat(i)); break }
        val isAuto = selectedAudioBitrate == -1
        items.add(QualityItem(if (isAuto && currentLabel != null) "Auto ($currentLabel)" else "Auto", isAuto = true, isSelected = isAuto))
        for (tg in p.currentTracks.groups)
            if (tg.type == C.TRACK_TYPE_AUDIO)
                for (i in 0 until tg.length) {
                    val f = tg.getTrackFormat(i)
                    items.add(QualityItem(formatAudio(f), TrackInfo("audio", i, f, tg),
                        isSelected = !isAuto && f.bitrate == selectedAudioBitrate && f.channelCount == selectedAudioChannels))
                }
        showMenu(items, "AUDIO TRACKS")
    }

    private fun showMenu(items: List<QualityItem>, title: String) {
        val dialog = Dialog(this)
        val view   = layoutInflater.inflate(R.layout.dialog_quality_selector, null)
        dialog.setContentView(view)
        view.findViewById<TextView>(R.id.tv_dialog_title)?.text = title
        val rv = view.findViewById<RecyclerView>(R.id.rv_quality)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = QualityAdapter(items) { item ->
            when {
                item.isAuto            -> applySelection(null, -1, title.contains("VIDEO"))
                item.trackInfo != null -> applySelection(item.trackInfo.group, item.trackInfo.index, item.trackInfo.type == "video")
            }
            dialog.dismiss()
        }
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setGravity(Gravity.END or Gravity.CENTER_VERTICAL)
            setLayout(resources.getDimensionPixelSize(android.R.dimen.app_icon_size) * 4, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        dialog.show()
    }

    private fun applySelection(tg: androidx.media3.common.Tracks.Group?, idx: Int, isVideo: Boolean) {
        val pb = trackSelector.parameters.buildUpon().clearOverrides().clearVideoSizeConstraints()
        if (isVideo) {
            selectedVideoTrackGroup = tg; selectedVideoTrackIndex = idx
            if (tg != null && idx >= 0) {
                tg.getTrackFormat(idx).also { f -> selectedVideoBitrate = f.bitrate; selectedVideoHeight = f.height }
                pb.addOverride(TrackSelectionOverride(tg.mediaTrackGroup, listOf(idx)))
            } else { selectedVideoBitrate = -1; selectedVideoHeight = -1 }
            selectedAudioTrackGroup?.also { if (selectedAudioTrackIndex >= 0) pb.addOverride(TrackSelectionOverride(it.mediaTrackGroup, listOf(selectedAudioTrackIndex))) }
        } else {
            selectedAudioTrackGroup = tg; selectedAudioTrackIndex = idx
            if (tg != null && idx >= 0) {
                tg.getTrackFormat(idx).also { f -> selectedAudioBitrate = f.bitrate; selectedAudioChannels = f.channelCount }
                pb.addOverride(TrackSelectionOverride(tg.mediaTrackGroup, listOf(idx)))
            } else { selectedAudioBitrate = -1; selectedAudioChannels = -1 }
            selectedVideoTrackGroup?.also { if (selectedVideoTrackIndex >= 0) pb.addOverride(TrackSelectionOverride(it.mediaTrackGroup, listOf(selectedVideoTrackIndex))) }
        }
        trackSelector.parameters = pb.build()
    }

    private fun formatVideo(f: Format): String {
        val kbps = if (f.bitrate > 0) f.bitrate / 1000 else 0
        val fps  = if (f.frameRate > 0) f.frameRate.toInt() else 0
        return when {
            fps > 0 && kbps > 0 -> "${f.height}p (${fps}fps, $kbps kbps)"
            kbps > 0            -> "${f.height}p ($kbps kbps)"
            f.height > 0        -> "${f.height}p"
            else                -> "Unknown"
        }
    }

    private fun formatAudio(f: Format): String {
        val lang = f.language ?: "und"
        val kbps = if (f.bitrate > 0) f.bitrate / 1000 else 0
        return when {
            kbps > 0 && f.channelCount > 0 -> "[$lang] $kbps kbps ${f.channelCount}ch"
            kbps > 0                       -> "[$lang] $kbps kbps"
            else                           -> "[$lang]"
        }
    }

    data class TrackInfo(val type: String, val index: Int, val format: Format, val group: androidx.media3.common.Tracks.Group)
    data class QualityItem(val label: String, val trackInfo: TrackInfo? = null, val isAuto: Boolean = false, val isSelected: Boolean = false)

    inner class QualityAdapter(
        private val items: List<QualityItem>,
        private val onClick: (QualityItem) -> Unit
    ) : RecyclerView.Adapter<QualityAdapter.VH>() {
        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val label: TextView = v.findViewById(R.id.tv_quality_label)
            val check: TextView = v.findViewById(R.id.tv_checkmark)
        }
        override fun onCreateViewHolder(p: ViewGroup, t: Int) =
            VH(LayoutInflater.from(p.context).inflate(R.layout.item_quality_track, p, false))
        override fun onBindViewHolder(h: VH, pos: Int) {
            val item = items[pos]
            h.label.text = item.label
            if (item.isSelected) {
                h.label.setTextColor(Color.parseColor("#00BCD4"))
                h.check.visibility = View.VISIBLE
            } else {
                h.label.setTextColor(Color.WHITE)
                h.check.visibility = View.GONE
            }
            h.itemView.setOnClickListener { onClick(item) }
        }
        override fun getItemCount() = items.size
    }
}
