package com.hunttv.streamtv.activities

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
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
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.drm.DefaultDrmSessionManager
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
            "Dalvik/2.1.0 (Linux; U; Android 10; SM-G975F Build/QP1A.190711.020)",
            "VLC/3.0.18 LibVLC/3.0.18",
            "stagefright/1.2 (Linux;Android 10)",
            "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 Chrome/91.0.4472.120 Mobile Safari/537.36"
        )

        // Regex matching working app (ck1.m2679) to decide URL vs inline key
        private val URL_REGEX = Regex(
            "^(?:https?|rtmps?|rtsp)://(?:[a-zA-Z0-9.-]+|\\d{1,3}(?:\\.\\d{1,3}){3})(?::\\d+)?(?:[/?].*)?$"
        )
    }

    private var player: ExoPlayer? = null
    private lateinit var playerView:    PlayerView
    private lateinit var trackSelector: DefaultTrackSelector
    private lateinit var qualityButton: ImageButton
    private lateinit var audioButton:   ImageButton

    private var selectedVideoBitrate  = -1
    private var selectedVideoHeight   = -1
    private var selectedAudioBitrate  = -1
    private var selectedAudioChannels = -1

    private var selectedVideoTrackGroup: androidx.media3.common.Tracks.Group? = null
    private var selectedVideoTrackIndex  = -1
    private var selectedAudioTrackGroup: androidx.media3.common.Tracks.Group? = null
    private var selectedAudioTrackIndex  = -1

    private var retryIndex       = 0
    private var currentStreamUrl = ""
    private var isPlaylistMode   = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableFullscreen()
        setContentView(R.layout.activity_player)

        playerView    = findViewById(R.id.player_view)
        qualityButton = playerView.findViewById(R.id.btn_quality)
        audioButton   = playerView.findViewById(R.id.btn_audio)

        qualityButton.setOnClickListener { showVideoQualityDialog() }
        audioButton.setOnClickListener   { showAudioTracksDialog()  }

        val streamUrl  = intent.getStringExtra(EXTRA_STREAM_URL)
        isPlaylistMode = intent.getBooleanExtra(EXTRA_IS_PLAYLIST, false)

        if (streamUrl.isNullOrEmpty()) {
            Toast.makeText(this, "Invalid stream", Toast.LENGTH_SHORT).show()
            finish(); return
        }

        currentStreamUrl = streamUrl
        initializePlayer(streamUrl)
    }

    private fun enableFullscreen() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )
    }

    private fun initializePlayer(streamUrl: String) {
        try {
            val parsed = StreamParser.parseStreamUrl(streamUrl)
            Log.d(TAG, "Playing: ${parsed.baseUrl}")
            Log.d(TAG, "DRM scheme: ${parsed.drmScheme}  license: ${parsed.drmLicense?.take(80)}")

            val headers = buildRequestHeaders(parsed)

            val dataSourceFactory = DefaultHttpDataSource.Factory()
                .setUserAgent(headers["User-Agent"] ?: "HuntTV/1.0")
                .setDefaultRequestProperties(headers)
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(30_000)
                .setReadTimeoutMs(30_000)

            trackSelector = DefaultTrackSelector(this)

            val loadControl = DefaultLoadControl.Builder()
                .setBufferDurationsMs(15_000, 50_000, 2_500, 5_000)
                .build()

            val (mediaItem, drmSessionManager) = buildPlayerConfig(parsed, headers)

            val mediaSourceFactory = if (drmSessionManager != null) {
                DefaultMediaSourceFactory(dataSourceFactory)
                    .setDrmSessionManagerProvider { drmSessionManager }
            } else {
                DefaultMediaSourceFactory(dataSourceFactory)
            }

            player?.release()
            player = ExoPlayer.Builder(this)
                .setTrackSelector(trackSelector)
                .setLoadControl(loadControl)
                .setMediaSourceFactory(mediaSourceFactory)
                .build()

            playerView.player = player

            player?.addListener(object : Player.Listener {
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    val msg = error.cause?.message ?: error.message ?: "Unknown error"
                    Log.e(TAG, "Playback error: $msg  retry=$retryIndex")

                    val m3uHadUA = parsed.headers.containsKey("User-Agent")
                    if (isPlaylistMode && !m3uHadUA && retryIndex < PLAYLIST_UA_LIST.size - 1) {
                        retryIndex++
                        runOnUiThread { initializePlayer(currentStreamUrl) }
                    } else {
                        runOnUiThread {
                            Toast.makeText(this@PlayerActivity, "Error: $msg", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            })

            player?.apply {
                setMediaItem(mediaItem)
                prepare()
                playWhenReady = true
            }

        } catch (e: Exception) {
            Log.e(TAG, "Init error: ${e.message}")
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun buildPlayerConfig(
        parsed:  ParsedStreamData,
        headers: Map<String, String>
    ): Pair<MediaItem, DefaultDrmSessionManager?> {

        val baseUri = Uri.parse(parsed.baseUrl)
        val scheme  = parsed.drmScheme
        val license = parsed.drmLicense

        val plainItem = MediaItem.Builder().setUri(baseUri).build()

        if (scheme == null || license == null) {
            Log.d(TAG, "No DRM — plain stream")
            return Pair(plainItem, null)
        }

        val drmUuid = when (scheme.lowercase()) {
            "clearkey"  -> C.CLEARKEY_UUID
            "widevine"  -> C.WIDEVINE_UUID
            "playready" -> C.PLAYREADY_UUID
            else        -> return Pair(plainItem, null)
        }

        return when {

            // PATH A: HTTP license URL → MediaItem.DrmConfiguration
            URL_REGEX.matches(license) -> {
                Log.d(TAG, "DRM PATH A: HTTP license URL")
                val drmConfig = MediaItem.DrmConfiguration.Builder(drmUuid)
                    .setLicenseUri(license)
                    .setLicenseRequestHeaders(headers)
                    .build()
                val item = MediaItem.Builder()
                    .setUri(baseUri)
                    .setDrmConfiguration(drmConfig)
                    .build()
                Pair(item, null)
            }

            // PATH B: Inline keys → LocalMediaDrmCallback on MediaSourceFactory
            else -> {
                Log.d(TAG, "DRM PATH B: inline keys → LocalMediaDrmCallback")
                val clearKeyJson = buildClearKeyJson(license) ?: run {
                    Log.w(TAG, "Failed to parse license, playing without DRM")
                    return Pair(plainItem, null)
                }
                Log.d(TAG, "ClearKey JSON: $clearKeyJson")

                val callback = LocalMediaDrmCallback(clearKeyJson.toByteArray(Charsets.UTF_8))
                val drmMgr = DefaultDrmSessionManager.Builder()
                    .setUuidAndExoMediaDrmProvider(drmUuid, androidx.media3.exoplayer.drm.FrameworkMediaDrm.DEFAULT_PROVIDER)
                    .setMultiSession(false)
                    .build(callback)

                Pair(plainItem, drmMgr)
            }
        }
    }

    private fun buildClearKeyJson(license: String): String? {
        if (license.trimStart().startsWith("{")) return license

        val separator = if (license.contains("|")) "|" else ","

        val entries = license.split(separator).mapNotNull { pair ->
            val idx = pair.indexOf(":")
            if (idx < 0) return@mapNotNull null
            val kidRaw = pair.substring(0, idx).trim()
            val keyRaw = pair.substring(idx + 1).trim()
            if (kidRaw.isEmpty() || keyRaw.isEmpty()) return@mapNotNull null

            val (kidB64, keyB64) = try {
                Pair(hexToBase64Url(kidRaw), hexToBase64Url(keyRaw))
            } catch (e: Exception) {
                Log.d(TAG, "hex decode failed, using raw keys as base64url")
                Pair(kidRaw, keyRaw)
            }
            """{"kty":"oct","k":"$keyB64","kid":"$kidB64"}"""
        }
        if (entries.isEmpty()) return null
        return """{"keys":[${entries.joinToString(",")}],"type":"temporary"}"""
    }

    private fun hexToBase64Url(hex: String): String {
        require(hex.length % 2 == 0) { "Odd hex length: ${hex.length}" }
        val bytes = ByteArray(hex.length / 2) { i ->
            hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    private fun buildRequestHeaders(parsed: ParsedStreamData): MutableMap<String, String> {
        val headers = mutableMapOf<String, String>()
        headers.putAll(parsed.headers)

        parsed.extHttpJson?.let { json ->
            try {
                val obj = JSONObject(json)
                val it  = obj.keys()
                while (it.hasNext()) {
                    val k = it.next()
                    val v = obj.getString(k)
                    val key = when (k.lowercase()) {
                        "cookie"     -> "Cookie"
                        "user-agent" -> "User-Agent"
                        "referer"    -> "Referer"
                        "origin"     -> "Origin"
                        else         -> k
                    }
                    headers.putIfAbsent(key, v)
                }
            } catch (e: Exception) {
                Log.w(TAG, "extHttpJson parse error: ${e.message}")
            }
        }

        if (!headers.containsKey("User-Agent")) {
            headers["User-Agent"] = if (isPlaylistMode) PLAYLIST_UA_LIST[retryIndex]
                                    else "HuntTV/1.0.0 (Android)"
        }
        return headers
    }

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
        val pb = trackSelector.parameters.buildUpon()
        pb.clearOverrides()
        if (isVideo) {
            selectedVideoTrackGroup = tg; selectedVideoTrackIndex = idx
            if (tg != null && idx >= 0) {
                tg.getTrackFormat(idx).also { f -> selectedVideoBitrate = f.bitrate; selectedVideoHeight = f.height }
                pb.addOverride(TrackSelectionOverride(tg.mediaTrackGroup, listOf(idx)))
            } else { selectedVideoBitrate = -1; selectedVideoHeight = -1 }
            selectedAudioTrackGroup?.also { atg ->
                if (selectedAudioTrackIndex >= 0)
                    pb.addOverride(TrackSelectionOverride(atg.mediaTrackGroup, listOf(selectedAudioTrackIndex)))
            }
        } else {
            selectedAudioTrackGroup = tg; selectedAudioTrackIndex = idx
            if (tg != null && idx >= 0) {
                tg.getTrackFormat(idx).also { f -> selectedAudioBitrate = f.bitrate; selectedAudioChannels = f.channelCount }
                pb.addOverride(TrackSelectionOverride(tg.mediaTrackGroup, listOf(idx)))
            } else { selectedAudioBitrate = -1; selectedAudioChannels = -1 }
            selectedVideoTrackGroup?.also { vtg ->
                if (selectedVideoTrackIndex >= 0)
                    pb.addOverride(TrackSelectionOverride(vtg.mediaTrackGroup, listOf(selectedVideoTrackIndex)))
            }
        }
        trackSelector.parameters = pb.build()
        Toast.makeText(this, "${if (isVideo) "Video" else "Audio"}: ${if (idx == -1) "Auto" else "Selected"}", Toast.LENGTH_SHORT).show()
    }

    private fun formatVideo(f: Format): String {
        val kbps = if (f.bitrate > 0) f.bitrate / 1000 else 0
        val fps  = if (f.frameRate > 0) f.frameRate.toInt() else 0
        return when { fps > 0 && kbps > 0 -> "${f.height}p (${fps}fps, $kbps kbps)"; kbps > 0 -> "${f.height}p ($kbps kbps)"; else -> "${f.height}p" }
    }

    private fun formatAudio(f: Format): String {
        val lang = f.language ?: "und"
        val kbps = if (f.bitrate > 0) f.bitrate / 1000 else 0
        return when { kbps > 0 && f.channelCount > 0 -> "[$lang] $kbps kbps ${f.channelCount}ch"; kbps > 0 -> "[$lang] $kbps kbps"; else -> "[$lang]" }
    }

    data class TrackInfo(val type: String, val index: Int, val format: Format, val group: androidx.media3.common.Tracks.Group)
    data class QualityItem(val label: String, val trackInfo: TrackInfo? = null, val isAuto: Boolean = false, val isSelected: Boolean = false)

    inner class QualityAdapter(private val items: List<QualityItem>, private val onClick: (QualityItem) -> Unit) :
        RecyclerView.Adapter<QualityAdapter.VH>() {
        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val label: TextView = v.findViewById(R.id.tv_quality_label)
            val check: TextView = v.findViewById(R.id.tv_checkmark)
        }
        override fun onCreateViewHolder(p: ViewGroup, t: Int) =
            VH(LayoutInflater.from(p.context).inflate(R.layout.item_quality_track, p, false))
        override fun onBindViewHolder(h: VH, pos: Int) {
            val item = items[pos]
            h.label.text = item.label
            if (item.isSelected) { h.label.setTextColor(Color.parseColor("#00BCD4")); h.check.visibility = View.VISIBLE }
            else { h.label.setTextColor(Color.WHITE); h.check.visibility = View.GONE }
            h.itemView.setOnClickListener { onClick(item) }
        }
        override fun getItemCount() = items.size
    }

    override fun onResume()  { super.onResume();  player?.playWhenReady = true  }
    override fun onPause()   { super.onPause();   player?.playWhenReady = false }
    override fun onDestroy() { super.onDestroy(); player?.release(); player = null }
}
