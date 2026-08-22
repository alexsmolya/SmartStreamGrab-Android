package com.smartstreamgrab.android

import com.yausername.youtubedl_android.YoutubeDL

/** Keeps the AAR-specific metadata object shape out of Compose. */
class YtDlpMetadataExtractor(
    private val backend: YoutubeDL = YoutubeDL.getInstance(),
) {
    fun extract(url: String): MediaPreview {
        val info = backend.getInfo(url)
        val formats = (info.readNoArg("getFormats") as? Iterable<*>)
            ?.mapNotNull { it?.toMediaFormat() }
            .orEmpty()

        return MediaPreview(
            url = url,
            title = info.readString("getTitle") ?: "(untitled)",
            thumbnail = info.readString("getThumbnail", "getThumbnailUrl")?.takeIf { it.startsWith("http") },
            extractor = info.readString("getExtractorKey", "getExtractor") ?: "unknown",
            formats = formats.ifEmpty {
                listOf(MediaFormat("resolved", null, null, null, null, null, null, null,
                    "Resolved media (format list unavailable)"))
            },
        )
    }
}
