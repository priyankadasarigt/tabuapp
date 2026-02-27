package com.hunttv.streamtv.models

import com.google.gson.annotations.SerializedName

/**
 * Response model for the streams API
 */
data class StreamResponse(
    @SerializedName("powered_by")
    val poweredBy: String = "Powered By @HuntTV",
    
    @SerializedName("streams")
    val streams: List<StreamItem> = emptyList()
)

/**
 * Individual stream item
 */
data class StreamItem(
    @SerializedName("id")
    val id: Int,
    
    @SerializedName("title")
    val title: String,
    
    @SerializedName("stream_url")
    val streamUrl: String,
    
    @SerializedName("is_hidden")
    val isHidden: Boolean = false
)

/**
 * Parsed stream data after processing the URL.
 *
 * drmScheme   - "clearkey" / "widevine" / "playready" or null
 * drmLicense  - raw value: either a URL (https://...) or inline "kid:key" pairs (comma-separated)
 *               PlayerActivity decides which path based on whether it starts with "http"
 * extHttpJson - raw JSON string from #EXTHTTP (e.g. {"cookie":"__hdnea__=..."})
 *               Used to send cookies to BOTH the stream requests and DRM license requests
 */
data class ParsedStreamData(
    val baseUrl:     String,
    val headers:     Map<String, String>,
    val drmScheme:   String?  = null,
    val drmLicense:  String?  = null,   // replaces drmLicenseKey + drmLicenseValue
    val extHttpJson: String?  = null    // NEW: raw #EXTHTTP JSON
)
