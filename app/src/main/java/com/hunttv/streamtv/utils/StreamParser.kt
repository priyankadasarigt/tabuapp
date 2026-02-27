package com.hunttv.streamtv.utils

import com.hunttv.streamtv.models.ParsedStreamData
import org.json.JSONObject

/**
 * Parses pipe-encoded stream URLs.
 *
 * Format: url|User-Agent=x&Cookie=y&drmScheme=clearkey&drmLicense=<value>
 *
 * FIX: drmLicense values that are HTTP URLs may contain '&'
 * (e.g. https://license.example.com/key?token=abc&session=xyz).
 * Old code split ALL params on '&' which corrupted those URLs.
 * Fix: two-pass — parse known short keys first, then treat everything
 * after drmLicense= as the license value (captures & in URLs).
 */
object StreamParser {

    fun parseStreamUrl(fullUrl: String): ParsedStreamData {

        val decodedUrl = fullUrl
            .replace("%7C", "|").replace("%7c", "|")
            .replace("%3F", "?").replace("%3f", "?")
            .replace("%3A", ":").replace("%3a", ":")
            .replace("%2F", "/").replace("%2f", "/")
            .replace("%20", " ")
            .replace("%2C", ",").replace("%2c", ",")
            .trim()

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
        }

        baseUrl      = baseUrl.trim()
        paramsString = paramsString.trim()

        val headers     = mutableMapOf<String, String>()
        var drmScheme   = ""
        var drmLicense  = ""
        var extHttpJson = ""

        if (paramsString.isNotEmpty()) {
            // Two-pass: find where drmLicense= starts, split there.
            // Everything after drmLicense= is the license value (may contain &).
            val drmLicenseIdx = findDrmLicenseIndex(paramsString)

            val simplePart: String
            val licensePart: String

            if (drmLicenseIdx >= 0) {
                simplePart  = paramsString.substring(0, drmLicenseIdx)
                val afterEq = paramsString.indexOf("=", drmLicenseIdx)
                licensePart = if (afterEq >= 0) paramsString.substring(afterEq + 1) else ""
            } else {
                simplePart  = paramsString
                licensePart = ""
            }

            simplePart.split("&").forEach { param ->
                val eqIdx = param.indexOf("="); if (eqIdx < 0) return@forEach
                val key   = param.substring(0, eqIdx).trim()
                val value = param.substring(eqIdx + 1).trim()
                when (key.lowercase()) {
                    "cookie"                  -> headers["Cookie"]          = value
                    "referer"                 -> headers["Referer"]         = value
                    "origin"                  -> headers["Origin"]          = value
                    "user-agent", "useragent" -> headers["User-Agent"]      = value
                    "accept"                  -> headers["Accept"]          = value
                    "accept-language"         -> headers["Accept-Language"] = value
                    "authorization"           -> headers["Authorization"]   = value
                    "drmscheme"               -> drmScheme  = value
                    "exthttpjson"             -> extHttpJson = value
                    else -> if (key.isNotEmpty() && value.isNotEmpty()) headers[key] = value
                }
            }

            if (licensePart.isNotEmpty()) drmLicense = licensePart
        }

        // Merge extHttpJson headers into request headers
        if (extHttpJson.isNotEmpty()) {
            try {
                val json = JSONObject(extHttpJson); val keys = json.keys()
                while (keys.hasNext()) {
                    val k = keys.next(); val v = json.getString(k)
                    when (k.lowercase()) {
                        "cookie"     -> headers.putIfAbsent("Cookie",       v)
                        "user-agent" -> headers.putIfAbsent("User-Agent",   v)
                        "referer"    -> headers.putIfAbsent("Referer",      v)
                        "origin"     -> headers.putIfAbsent("Origin",       v)
                        else         -> headers.putIfAbsent(k, v)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("StreamParser", "extHttpJson parse error: ${e.message}")
            }
        }

        return ParsedStreamData(
            baseUrl     = baseUrl,
            headers     = headers,
            drmScheme   = drmScheme.ifEmpty { null },
            drmLicense  = drmLicense.ifEmpty { null },
            extHttpJson = extHttpJson.ifEmpty { null }
        )
    }

    private fun findDrmLicenseIndex(paramsString: String): Int {
        val lower = paramsString.lowercase()
        var idx = lower.indexOf("drmlicense=")
        while (idx >= 0) {
            if (idx == 0 || paramsString[idx - 1] == '&') return idx
            idx = lower.indexOf("drmlicense=", idx + 1)
        }
        return -1
    }
}
