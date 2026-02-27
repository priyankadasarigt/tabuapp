package com.hunttv.streamtv.utils

import com.hunttv.streamtv.models.ParsedStreamData
import org.json.JSONObject

/**
 * Parses pipe-encoded stream URLs.
 *
 * Format: url|User-Agent=x&Cookie=x&drmScheme=x&drmLicense=x&extHttpJson={...}
 *
 * NEW: extHttpJson field — raw JSON from #EXTHTTP (e.g. {"cookie":"__hdnea__=..."})
 * This matches how the working app passes cookie/headers to the DRM license server.
 */
object StreamParser {

    fun parseStreamUrl(fullUrl: String): ParsedStreamData {

        // Decode common percent-encoded chars EXCEPT & and = (they are delimiters)
        val decodedUrl = fullUrl
            .replace("%7C", "|").replace("%7c", "|")
            .replace("%3F", "?").replace("%3f", "?")
            .replace("%3A", ":").replace("%3a", ":")
            .replace("%2F", "/").replace("%2f", "/")
            .replace("%20", " ")
            .replace("%2C", ",").replace("%2c", ",")
            .trim()

        // Split at pipe (handles both url|params and url?|params)
        var baseUrl      = decodedUrl
        var paramsString = ""

        when {
            decodedUrl.contains("?|") -> {
                val idx  = decodedUrl.indexOf("?|")
                baseUrl      = decodedUrl.substring(0, idx)
                paramsString = decodedUrl.substring(idx + 2)
            }
            decodedUrl.contains("|") -> {
                val idx  = decodedUrl.indexOf("|")
                baseUrl      = decodedUrl.substring(0, idx)
                paramsString = decodedUrl.substring(idx + 1)
            }
            else -> { /* plain URL, no params */ }
        }

        baseUrl      = baseUrl.trim()
        paramsString = paramsString.trim()

        val headers       = mutableMapOf<String, String>()
        var drmScheme     = ""
        var drmLicense    = ""   // raw value passed from M3uParser
        var extHttpJson   = ""   // raw JSON from #EXTHTTP

        if (paramsString.isNotEmpty()) {
            // Split by & but be careful: drmLicense could be a URL containing &
            // We split ALL params first, then reconstruct drmLicense if needed
            // Safe because cookie/UA values use ~ not & as separators in JioTV/Hotstar
            paramsString.split("&").forEach { param ->
                val eqIdx = param.indexOf("=")
                if (eqIdx < 0) return@forEach
                val key   = param.substring(0, eqIdx).trim()
                val value = param.substring(eqIdx + 1).trim()
                when (key.lowercase()) {
                    "cookie"          -> headers["Cookie"]      = value
                    "referer"         -> headers["Referer"]     = value
                    "origin"          -> headers["Origin"]      = value
                    "user-agent", "useragent" -> headers["User-Agent"] = value
                    "accept"          -> headers["Accept"]      = value
                    "accept-language" -> headers["Accept-Language"] = value
                    "authorization"   -> headers["Authorization"]   = value
                    "drmscheme"       -> drmScheme  = value
                    "drmlicense"      -> drmLicense = value
                    "exthttpjson"     -> extHttpJson = value     // ← NEW
                    else -> if (key.isNotEmpty() && value.isNotEmpty()) headers[key] = value
                }
            }
        }

        // Merge extHttpJson headers into both the main headers map
        // (so cookie from #EXTHTTP reaches the MPD/segment requests)
        // AND return the raw JSON so PlayerActivity can also pass it to DRM key requests
        if (extHttpJson.isNotEmpty()) {
            try {
                val json = JSONObject(extHttpJson)
                val keys = json.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val v = json.getString(k)
                    when (k.lowercase()) {
                        "cookie"     -> headers.putIfAbsent("Cookie", v)
                        "user-agent" -> headers.putIfAbsent("User-Agent", v)
                        "referer"    -> headers.putIfAbsent("Referer", v)
                        "origin"     -> headers.putIfAbsent("Origin", v)
                        else         -> headers.putIfAbsent(k, v)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("StreamParser", "extHttpJson parse error: ${e.message}")
            }
        }

        return ParsedStreamData(
            baseUrl      = baseUrl,
            headers      = headers,
            drmScheme    = drmScheme.ifEmpty { null },
            drmLicense   = drmLicense.ifEmpty { null },
            extHttpJson  = extHttpJson.ifEmpty { null }
        )
    }
}
