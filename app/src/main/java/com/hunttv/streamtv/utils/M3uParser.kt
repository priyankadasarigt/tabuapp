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
 * M3U parser rewritten to match the working "Network Stream Video Player" app (oa3.java).
 *
 * CRITICAL DIFFERENCES vs old parser:
 *
 * 1. #EXTHTTP  → raw JSON stored separately (NOT merged into stream headers)
 *    Working app passes it as "extHttpJson" → PlayerActivity sends it as DRM key request headers
 *    This is how the cookie reaches the clearkey license server!
 *
 * 2. #KODIPROP license_key URL → stored AS-IS, not decoded
 *    PlayerActivity detects it's a URL → uses HttpMediaDrmCallback to FETCH live
 *    (this is the core fix — your old app tried to extract key from URL params instead)
 *
 * 3. Multiple clearkey pairs (comma-separated "kid1:key1,kid2:key2") are supported
 */
object M3uParser {
    private const val TAG = "M3uParser"

    suspend fun parseFromUrl(playlistUrl: String): List<M3uChannel> = withContext(Dispatchers.IO) {
        try {
            val conn = URL(playlistUrl).openConnection() as HttpURLConnection
            conn.apply {
                requestMethod = "GET"
                setRequestProperty("User-Agent", "VLC/3.0.18 LibVLC/3.0.18")
                connectTimeout = 15000
                readTimeout    = 30000
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

            // ── #EXTINF line ──────────────────────────────────────────────────
            val name    = line.substringAfterLast(",").trim()
            val logo    = extractAttr(line, "tvg-logo")
            val group   = extractAttr(line, "group-title")
            val tvgId   = extractAttr(line, "tvg-id")
            val tvgName = extractAttr(line, "tvg-name")

            // Per-channel metadata (mirrors ContentBean in the working app)
            var userAgent   = ""
            var cookie      = ""   // from #EXTVLCOPT:http-cookie
            var referer     = ""
            var origin      = ""
            var drmScheme   = ""   // "clearkey" / "widevine" / "playready"
            var drmLicense  = ""   // raw: URL or "hex:hex" or "hex:hex,hex:hex" pairs
            var extHttpJson = ""   // raw JSON from #EXTHTTP, e.g. {"cookie":"__hdnea__=..."}

            var j = i + 1

            // ── Read metadata lines until stream URL ──────────────────────────
            while (j < lines.size) {
                val next = lines[j].trim()
                when {
                    next.isEmpty() -> { j++; continue }

                    next.startsWith("#EXTVLCOPT") -> {
                        // Matches oa3.java exactly
                        if (next.contains("user-agent", ignoreCase = true))
                            userAgent = regexFirst(next, ".*http-user-agent=(.+?)$") ?: userAgent
                        if (next.contains("referrer", ignoreCase = true))
                            referer   = regexFirst(next, ".*http-referrer=(.+?)$") ?: referer
                        else if (next.contains("referer", ignoreCase = true))
                            referer   = regexFirst(next, ".*http-referer=(.+?)$") ?: referer
                        if (next.contains("cookie", ignoreCase = true))
                            cookie    = regexFirst(next, """.*http-cookie="?(.+?)"?$""") ?: cookie
                        if (next.contains("origin", ignoreCase = true))
                            origin    = regexFirst(next, ".*http-origin=(.+?)$") ?: origin
                        if (next.contains("drm-scheme", ignoreCase = true))
                            drmScheme = normalizeDrmScheme(regexFirst(next, ".*http-drm-scheme=(.+?)$"))
                        if (next.contains("drm-license", ignoreCase = true))
                            drmLicense = regexFirst(next, ".*http-drm-license=(.+?)$") ?: drmLicense
                        j++
                    }

                    next.startsWith("#KODIPROP") -> {
                        if (next.contains("license_type", ignoreCase = true))
                            drmScheme = normalizeDrmScheme(regexFirst(next, ".*license_type=(.+?)$"))
                        if (next.contains("license_key", ignoreCase = true)) {
                            // !!CRITICAL!! Store the RAW value — URL or "kid:key" pairs
                            // Do NOT try to decode/parse here.
                            // PlayerActivity checks: if it starts with "http" → HttpMediaDrmCallback (live fetch)
                            //                        else → LocalMediaDrmCallback (inline keys)
                            drmLicense = regexFirst(next, ".*license_key=([^|]+)") ?: drmLicense
                        }
                        j++
                    }

                    next.startsWith("#EXTHTTP") -> {
                        // Store raw JSON as-is — matching what oa3.java does:
                        //   builder.header(sd4.c0(sd4.Y(str5, "#EXTHTTP:", str5)).toString())
                        // This JSON (e.g. {"cookie":"__hdnea__=..."}) gets passed to PlayerActivity
                        // which uses fw4.m5911() to parse it into a HashMap for DRM key request headers
                        extHttpJson = next.substringAfter("#EXTHTTP:").trim()
                        j++
                    }

                    next.startsWith("#") -> { j++; continue }
                    else                 -> break  // stream URL
                }
            }

            // ── Stream URL line ───────────────────────────────────────────────
            if (j >= lines.size) { i++; continue }
            val urlLine = lines[j].trim()
            if (urlLine.startsWith("#") || urlLine.isEmpty()) { i++; continue }

            // Strip pipe suffix to get clean base URL (matching oa3.java: tt.f(str5, "(.+?)(\\|.*)?")
            var streamUrl = if (urlLine.contains("|")) urlLine.substringBefore("|") else urlLine
            // Remove stray trailing '?' left by ?| format
            streamUrl = streamUrl.trimEnd('?')

            // Also pick up pipe-format UA/referer/origin if not already set from metadata
            if (urlLine.contains("|")) {
                if (userAgent.isEmpty())
                    userAgent = regexFirst(urlLine, """.*\|user-agent=(.+?)(\|.*)?""") ?: ""
                if (referer.isEmpty())
                    referer   = regexFirst(urlLine, """.*\|referer=(.+?)(\|.*)?""") ?: ""
                if (origin.isEmpty())
                    origin    = regexFirst(urlLine, """.*\|origin=(.+?)(\|.*)?""") ?: ""
            }

            i = j + 1
            if (streamUrl.isEmpty()) continue

            // ── Encode all metadata as pipe params ────────────────────────────
            // PlayerActivity.initializePlayer() decodes this in StreamParser
            val parts = mutableListOf<String>()
            if (userAgent.isNotEmpty())    parts.add("User-Agent=$userAgent")
            if (cookie.isNotEmpty())       parts.add("Cookie=$cookie")
            if (referer.isNotEmpty())      parts.add("Referer=$referer")
            if (origin.isNotEmpty())       parts.add("Origin=$origin")
            // extHttpJson goes as its own key so PlayerActivity can parse the JSON
            // and add those headers to BOTH the DRM license request AND stream request
            if (extHttpJson.isNotEmpty())  parts.add("extHttpJson=$extHttpJson")
            if (drmScheme.isNotEmpty() && drmLicense.isNotEmpty()) {
                parts.add("drmScheme=$drmScheme")
                parts.add("drmLicense=$drmLicense")
            }

            val finalUrl = if (parts.isNotEmpty()) "$streamUrl|${parts.joinToString("&")}" else streamUrl

            Log.d(TAG, "[$name] scheme=$drmScheme licenseIsUrl=${drmLicense.startsWith("http")} hasExtHttp=${extHttpJson.isNotEmpty()}")

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

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun regexFirst(input: String, pattern: String): String? =
        Regex(pattern).find(input)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }

    private fun extractAttr(line: String, attr: String): String? =
        Regex("""$attr="([^"]*?)"""", RegexOption.IGNORE_CASE)
            .find(line)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }

    /** Matches working app's tt.B() */
    private fun normalizeDrmScheme(value: String?): String = when {
        value == null                                    -> ""
        value.contains("widevine",  ignoreCase = true)  -> "widevine"
        value.contains("playready", ignoreCase = true)  -> "playready"
        value.contains("clearkey",  ignoreCase = true)  -> "clearkey"
        else                                             -> ""
    }

    // ── Public utilities used by PlayerActivity ───────────────────────────────

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
