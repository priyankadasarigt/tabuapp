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
 * M3U / M3U8 parser.
 *
 * Supports:
 *  • #EXTVLCOPT  user-agent / referrer / cookie / origin / drm-scheme / drm-license
 *  • #KODIPROP   license_type / license_key
 *  • #EXTHTTP    {"cookie":"..."} JSON
 *  • Pipe-suffix params on the URL line:
 *      url|User-Agent=x&Referer=y
 *      url|user-agent=x|referer=y   (VLC pipe style)
 *      url?|...
 *  • Xtream-style direct .m3u8 / .ts URLs (no extra params)
 *  • Multi-pair ClearKey: "kid1:key1,kid2:key2"
 *
 * BUG FIXES vs original:
 *  1. Pipe-suffix regex was case-sensitive and missed variants like "User-Agent"
 *  2. ?| stripping was broken for some URL shapes
 *  3. drmLicense in pipe suffix was not extracted
 *  4. VLC multi-pipe style (url|k=v|k=v) was not supported
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
            if (!line.startsWith("#EXTINF")) { i++; continue }

            // ── #EXTINF ──────────────────────────────────────────────────────
            val name    = line.substringAfterLast(",").trim()
            val logo    = extractAttr(line, "tvg-logo")
            val group   = extractAttr(line, "group-title")
            val tvgId   = extractAttr(line, "tvg-id")
            val tvgName = extractAttr(line, "tvg-name")

            var userAgent   = ""
            var cookie      = ""
            var referer     = ""
            var origin      = ""
            var drmScheme   = ""
            var drmLicense  = ""
            var extHttpJson = ""

            var j = i + 1

            // ── Read metadata lines until the stream URL ─────────────────────
            while (j < lines.size) {
                val next = lines[j].trim()
                when {
                    next.isEmpty() -> { j++; continue }

                    next.startsWith("#EXTVLCOPT") -> {
                        val lower = next.lowercase()
                        if (lower.contains("http-user-agent"))
                            userAgent  = regexFirst(next, "(?i).*http-user-agent=(.+?)$") ?: userAgent
                        if (lower.contains("http-referrer") || lower.contains("http-referer"))
                            referer    = regexFirst(next, "(?i).*http-refer+er=(.+?)$") ?: referer
                        if (lower.contains("http-cookie"))
                            cookie     = regexFirst(next, """(?i).*http-cookie="?(.+?)"?$""") ?: cookie
                        if (lower.contains("http-origin"))
                            origin     = regexFirst(next, "(?i).*http-origin=(.+?)$") ?: origin
                        if (lower.contains("drm-scheme"))
                            drmScheme  = normalizeDrm(regexFirst(next, "(?i).*http-drm-scheme=(.+?)$"))
                        if (lower.contains("drm-license"))
                            drmLicense = regexFirst(next, "(?i).*http-drm-license=(.+?)$") ?: drmLicense
                        j++
                    }

                    next.startsWith("#KODIPROP") -> {
                        val lower = next.lowercase()
                        if (lower.contains("license_type"))
                            drmScheme  = normalizeDrm(regexFirst(next, "(?i).*license_type=(.+?)$"))
                        if (lower.contains("license_key"))
                        // Store RAW — PlayerActivity decides URL vs inline keys
                            drmLicense = regexFirst(next, "(?i).*license_key=(.+?)$") ?: drmLicense
                        j++
                    }

                    next.startsWith("#EXTHTTP") -> {
                        extHttpJson = next.substringAfter("#EXTHTTP:").trim()
                        j++
                    }

                    next.startsWith("#") -> { j++; continue }
                    else                 -> break  // stream URL line
                }
            }

            if (j >= lines.size) { i++; continue }
            val urlLine = lines[j].trim()
            if (urlLine.startsWith("#") || urlLine.isEmpty()) { i++; continue }

            i = j + 1

            // ── Parse URL line ───────────────────────────────────────────────
            // Supports both:
            //   url|User-Agent=x&Cookie=y  (ampersand style)
            //   url|user-agent=x|referer=y (VLC multi-pipe style)
            val (cleanUrl, pipeParts) = splitUrlAndPipe(urlLine)
            if (cleanUrl.isEmpty()) continue

            // Extract headers from pipe suffix if not already set
            if (pipeParts.isNotEmpty()) {
                val pipeUA      = pipeParam(pipeParts, "user-agent", "useragent")
                val pipeRef     = pipeParam(pipeParts, "referer", "referrer")
                val pipeOrigin  = pipeParam(pipeParts, "origin")
                val pipeCookie  = pipeParam(pipeParts, "cookie")
                val pipeDrmS    = pipeParam(pipeParts, "drmscheme", "drm-scheme", "drm_scheme")
                val pipeDrmL    = pipeParam(pipeParts, "drmlicense", "drm-license", "drm_license", "license_key")

                if (userAgent.isEmpty()  && pipeUA     != null) userAgent  = pipeUA
                if (referer.isEmpty()    && pipeRef    != null) referer    = pipeRef
                if (origin.isEmpty()     && pipeOrigin != null) origin     = pipeOrigin
                if (cookie.isEmpty()     && pipeCookie != null) cookie     = pipeCookie
                if (drmScheme.isEmpty()  && pipeDrmS   != null) drmScheme  = normalizeDrm(pipeDrmS)
                if (drmLicense.isEmpty() && pipeDrmL   != null) drmLicense = pipeDrmL
            }

            // ── Build final encoded URL ──────────────────────────────────────
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

            Log.d(TAG, "[$name] url=${cleanUrl.take(60)} scheme=$drmScheme hasExtHttp=${extHttpJson.isNotEmpty()}")

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

        Log.d(TAG, "Total channels parsed: ${channels.size}")
        return channels
    }

    // ── URL / pipe splitting ──────────────────────────────────────────────────

    /**
     * Splits a URL line into (cleanBaseUrl, pipeParams map).
     *
     * Handles:
     *   http://x.com/stream.m3u8|User-Agent=VLC&Cookie=abc
     *   http://x.com/stream.m3u8?foo=bar|User-Agent=VLC
     *   http://x.com/stream.m3u8?|User-Agent=VLC         ← stray ?
     *   http://x.com/stream.m3u8|user-agent=VLC|referer=http://x.com
     */
    private fun splitUrlAndPipe(urlLine: String): Pair<String, Map<String, String>> {
        val pipeIdx = urlLine.indexOf("|")
        if (pipeIdx < 0) return Pair(urlLine.trimEnd('?'), emptyMap())

        var base   = urlLine.substring(0, pipeIdx).trimEnd('?').trim()
        val suffix = urlLine.substring(pipeIdx + 1).trim()

        if (base.isEmpty()) return Pair("", emptyMap())

        val params = mutableMapOf<String, String>()

        // Detect style: ampersand (k=v&k=v) or multi-pipe (k=v|k=v)
        val isAmpStyle = suffix.contains("=") &&
                (suffix.contains("&") || !suffix.contains("|"))

        val segments = if (isAmpStyle) {
            // Split on & but be careful: drmLicense value might itself have &
            // For the pipe-suffix case we use simple split since these are rarely long URLs
            suffix.split("&")
        } else {
            // VLC multi-pipe style: user-agent=VLC|referer=http://...
            suffix.split("|")
        }

        segments.forEach { seg ->
            val eqIdx = seg.indexOf("="); if (eqIdx < 0) return@forEach
            val k = seg.substring(0, eqIdx).trim().lowercase()
            val v = seg.substring(eqIdx + 1).trim()
            if (k.isNotEmpty() && v.isNotEmpty()) params[k] = v
        }

        return Pair(base, params)
    }

    /** Look up a value from pipe params by any of the given key aliases (all lowercase). */
    private fun pipeParam(params: Map<String, String>, vararg keys: String): String? {
        for (k in keys) { val v = params[k]; if (!v.isNullOrEmpty()) return v }
        return null
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

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

    // ── Public byte utilities (used by PlayerActivity) ────────────────────────

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


