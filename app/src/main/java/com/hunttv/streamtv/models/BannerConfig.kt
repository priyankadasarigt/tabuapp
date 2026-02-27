package com.hunttv.streamtv.models

data class BannerConfig(
    val banner: BannerData
)

data class BannerData(
    val enabled: Boolean,
    val show_per_day: Int,
    val html_url: String
)
