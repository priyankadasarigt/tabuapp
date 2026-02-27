package com.hunttv.streamtv.models

import com.google.gson.annotations.SerializedName

/**
 * A user-saved playlist entry
 */
data class PlaylistEntry(
    val id: String,
    val name: String,
    val url: String,
    val addedAt: Long = System.currentTimeMillis()
)

/**
 * A parsed channel from an M3U playlist
 */
data class M3uChannel(
    val name: String,
    val logoUrl: String?,
    val groupTitle: String?,
    val streamUrl: String,
    val tvgId: String? = null,
    val tvgName: String? = null,
    var isFavorite: Boolean = false
)
