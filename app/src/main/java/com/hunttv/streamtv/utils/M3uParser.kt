package com.hunttv.streamtv.utils

import android.util.Base64
import android.util.Log
import com.hunttv.streamtv.models.M3uChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Handles BOTH tag orderings found in real playlists:
 *
 * ORDER A — tags AFTER #EXTINF (standard):
 *   #EXTINF:-1 ...,Name
 *   #KODIPROP:inputstream.adaptive.license_type=clearkey
 *   #KODIPROP:inputstream.adaptive.license_key={...}
 *   https://stream.url
 *
 * ORDER B — tags BEFORE #EXTINF (JioTV / ZEE5 / Watcho style):
 *   #KODIPROP:inputstream.adaptive.license_type=clearkey
 *   #KODIPROP:inputstream.adaptive.license_key={...}
 *   #EXTINF:-1 ...,Name
 *   https://stream.url
 *
 * The old parser only handled Order A, so all #KODIPROP lines in
 * Order B playlists were silently dropped → stream played encrypted
 * → black video + audio only.
 */
object M3uParser {
    private const val TAG = "M3uParser"

    suspend fun parseFromUrl(playlistUrl: String): List<M3uChannel> = withContext(Dispatchers.IO) {
        try {
            val conn = URL(playlistUrl).openConnection() as HttpURLConnection
            conn.apply {
                requestMethod = "GET"
                setRequestProperty("User-Agent", "VLC/3.0.18 LibVLC/3.0.18")
                connectTimeout = 15_000
                readTimeout    = 30_000
                instanceFollowRedirects = true
            }
            val text = BufferedReader(InputStreamReader(conn.inputStream)).readText()
            conn.disconnect()
            parseContent(text)
        } catch (e: Exception) {
            Log.e(TAG, "Fetch error: ${e.message}")
            emptyList()
        }
    }

    fun parseContent(content: String): List<M3uChannel> {
        val channels = mutableListOf<M3uChannel>()
        val lines    = content.lines()
        var i        = 0

        while (i < lines.size) {
            val line = lines[i].trim()

            if (line.isEmpty() || line.startsWith("#EXTM3U")) { i++; continue }
            if (!line.startsWith("#EXTINF")) { i++; continue }

            val extinf = line

            // ── Collect meta lines BEFORE this #EXTINF (Order B support) ─────
            val metaLines = mutableListOf<String>()
            var back = i - 1
            while (back >= 0) {
                val prev = lines[back].trim()
                when {
                    prev.isEmpty()             -> break
                    prev.startsWith("#EXTINF") -> break
                    prev.startsWith("#EXTM3U") -> break
                    !prev.startsWith("#")      -> break  // URL of previous channel
                    else                       -> { metaLines.add(0, prev); back-- }
                }
            }

            // ── Collect meta lines AFTER this #EXTINF (Order A support) ──────
            var j = i + 1
            while (j < lines.size) {
                val next = lines[j].trim()
                when {
                    next.isEmpty()             -> { j++; continue }
                    next.startsWith("#EXTINF") -> break
                    next.startsWith("#")       -> { metaLines.add(next); j++ }
                    else                       -> break  // stream URL
                }
            }

            // j now points at the stream URL line
            if (j >= lines.size) { i++; continue }
            val urlLine = lines[j].trim()
            if (urlLine.isEmpty() || urlLine.startsWith("#")) { i++; continue }

            i = j + 1

            // ── Parse #EXTINF attributes ──────────────────────────────────────
            val name    = extinf.substringAfterLast(",").trim()
            val logo    = extractAttr(extinf, "tvg-logo")
            val group   = extractAttr(extinf, "group-title")
            val tvgId   = extractAttr(extinf, "tvg-id")
            val tvgName = extractAttr(extinf, "tvg-name")

            // ── Parse collected metadata ──────────────────────────────────────
            var userAgent   = ""
            var cookie      = ""
            var referer     = ""
            var origin      = ""
            var drmScheme   = ""
            var drmLicense  = ""
            var extHttpJson = ""

            for (meta in metaLines) {
                val lower = meta.lowercase()
                when {
                    meta.startsWith("#EXTVLCOPT", ignoreCase = true) -> {
                        if (lower.contains("http-user-agent"))
                            userAgent  = regexFirst(meta, "(?i).*http-user-agent=(.+)") ?: userAgent
                        if (lower.contains("http-referrer") || lower.contains("http-referer"))
                            referer    = regexFirst(meta, "(?i).*http-refer+er=(.+)") ?: referer
                        if (lower.contains("http-cookie"))
                            cookie     = regexFirst(meta, """(?i).*http-cookie="?(.+?)"?\s*$""") ?: cookie
                        if (lower.contains("http-origin"))
                            origin     = regexFirst(meta, "(?i).*http-origin=(.+)") ?: origin
                        if (lower.contains("drm-scheme"))
                            drmScheme  = normalizeDrm(regexFirst(meta, "(?i).*drm-scheme=(.+)"))
                        if (lower.contains("drm-license"))
                            drmLicense = regexFirst(meta, "(?i).*drm-license=(.+)") ?: drmLicense
                    }

                    meta.startsWith("#KODIPROP", ignoreCase = true) -> {
                        // Handles both:
                        //   #KODIPROP:license_type=clearkey
                        //   #KODIPROP:inputstream.adaptive.license_type=clearkey
                        if (lower.contains("license_type"))
                            drmScheme  = normalizeDrm(regexFirst(meta, "(?i).*license_type=(.+)"))
                        if (lower.contains("license_key"))
                            drmLicense = regexFirst(meta, "(?i).*license_key=(.+)") ?: drmLicense
                    }

                    meta.startsWith("#EXTHTTP", ignoreCase = true) ->
                        extHttpJson = meta.substringAfter(":").trim()
                }
            }

            // ── Parse pipe-suffix params on the URL line ──────────────────────
            val (cleanUrl, pipeParams) = splitUrlAndPipe(urlLine)
            if (cleanUrl.isEmpty()) continue

            if (pipeParams.isNotEmpty()) {
                if (userAgent.isEmpty())  userAgent  = pipeParam(pipeParams, "user-agent", "useragent") ?: userAgent
                if (referer.isEmpty())    referer    = pipeParam(pipeParams, "referer", "referrer")     ?: referer
                if (origin.isEmpty())     origin     = pipeParam(pipeParams, "origin")                  ?: origin
                if (cookie.isEmpty())     cookie     = pipeParam(pipeParams, "cookie")                  ?: cookie
                if (drmScheme.isEmpty())  drmScheme  = normalizeDrm(pipeParam(pipeParams, "drmscheme", "drm-scheme", "drm_scheme"))
                if (drmLicense.isEmpty()) drmLicense = pipeParam(pipeParams, "drmlicense", "drm-license", "drm_license", "license_key") ?: drmLicense
            }

            // ── Build final pipe-encoded URL ──────────────────────────────────
            val parts = mutableListOf<String>()
            if (userAgent.isNotEmpty())   parts.add("User-Agent=$userAgent")
            if (cookie.isNotEmpty())      parts.add("Cookie=$cookie")
            if (referer.isNotEmpty())     parts.add("Referer=$referer")
            if (origin.isNotEmpty())      parts.add("Origin=$origin")
            if (extHttpJson.isNotEmpty()) parts.add("extHttpJson=$extHttpJson")
            if (drmScheme.isNotEmpty() && drmLicense.isNotEmpty()) {
                parts.add("drmScheme=$drmScheme")
                parts.add("drmLicense=$drmLicense")
            }

            val finalUrl = if (parts.isNotEmpty()) "$cleanUrl|${parts.joinToString("&")}" else cleanUrl

            Log.d(TAG, "[$name] scheme=$drmScheme licenseLen=${drmLicense.length} url=${cleanUrl.take(60)}")

            channels.add(M3uChannel(
                name       = name,
                logoUrl    = logo,
                groupTitle = group,
                streamUrl  = finalUrl,
                tvgId      = tvgId,
                tvgName    = tvgName,
                isFavorite = false
            ))
        }

        Log.d(TAG, "Total parsed: ${channels.size}")
        return channels
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun splitUrlAndPipe(urlLine: String): Pair<String, Map<String, String>> {
        val pipeIdx = urlLine.indexOf("|")
        if (pipeIdx < 0) return Pair(urlLine.trimEnd('?'), emptyMap())

        val base   = urlLine.substring(0, pipeIdx).trimEnd('?').trim()
        val suffix = urlLine.substring(pipeIdx + 1).trim()
        if (base.isEmpty()) return Pair("", emptyMap())

        val params     = mutableMapOf<String, String>()
        val isAmpStyle = suffix.contains("&") || !suffix.contains("|")
        val segments   = if (isAmpStyle) suffix.split("&") else suffix.split("|")

        segments.forEach { seg ->
            val eq = seg.indexOf("="); if (eq < 0) return@forEach
            val k  = seg.substring(0, eq).trim().lowercase()
            val v  = seg.substring(eq + 1).trim()
            if (k.isNotEmpty() && v.isNotEmpty()) params[k] = v
        }
        return Pair(base, params)
    }

    private fun pipeParam(params: Map<String, String>, vararg keys: String): String? {
        for (k in keys) { val v = params[k]; if (!v.isNullOrEmpty()) return v }
        return null
    }

    private fun regexFirst(input: String, pattern: String): String? =
        Regex(pattern).find(input)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }

    private fun extractAttr(line: String, attr: String): String? =
        Regex("""$attr="([^"]*?)"""", RegexOption.IGNORE_CASE)
            .find(line)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }

    private fun normalizeDrm(value: String?): String = when {
        value == null                                   -> ""
        value.contains("widevine",  ignoreCase = true) -> "widevine"
        value.contains("playready", ignoreCase = true) -> "playready"
        value.contains("clearkey",  ignoreCase = true) -> "clearkey"
        else                                            -> ""
    }

    fun hexToBytes(hex: String): ByteArray {
        val clean = hex.trim()
        return ByteArray(clean.length / 2) { i ->
            clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }

    fun bytesToBase64Url(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

    fun base64UrlToHex(b64: String): String {
        val padded = b64.replace('-', '+').replace('_', '/')
            .let { s -> s + "=".repeat((4 - s.length % 4) % 4) }
        return Base64.decode(padded, Base64.DEFAULT).joinToString("") { "%02x".format(it) }
    }
}
