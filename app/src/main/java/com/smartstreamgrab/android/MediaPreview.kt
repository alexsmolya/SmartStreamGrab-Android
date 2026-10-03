package com.smartstreamgrab.android

/** Typed, UI-facing representation of the metadata needed by the preview screen. */
data class MediaPreview(
    val url: String,
    val title: String,
    val thumbnail: String?,
    val extractor: String,
    val formats: List<MediaFormat>,
)

data class MediaFormat(
    val formatId: String,
    val extension: String?,
    val width: Int?,
    val height: Int?,
    val videoCodec: String?,
    val audioCodec: String?,
    val filesize: Long?,
    val playableUrl: String?,
    val fallbackLabel: String? = null,
) {
    /** A short label suitable for a download button; backend IDs and codec internals stay hidden. */
    fun displayLabel(): String {
        val type = extension
            ?.trim()
            ?.removePrefix(".")
            ?.takeIf { it.isNotBlank() }
            ?.uppercase()
        val dimensions = if (width != null && width > 0 && height != null && height > 0) {
            "${width}×${height}"
        } else {
            null
        }
        return when {
            type != null && dimensions != null -> "$type · $dimensions"
            type != null -> "$type · Best quality"
            dimensions != null -> "Video · $dimensions"
            else -> "Best available"
        }
    }
}

private fun String.isMissingCodec(): Boolean = equals("none", ignoreCase = true)

/**
 * A format is useful to this app only when it is a selectable, combined video
 * stream. Video-only and audio-only formats need a muxing step (FFmpeg) before
 * they can be opened as a normal downloaded video, which the current app does
 * not perform.
 */
internal fun MediaFormat.isPlayableVideoCandidate(): Boolean =
    isDownloadSelectable() &&
        videoCodec?.isMissingCodec() != true &&
        audioCodec?.isMissingCodec() != true

/** Collapses backend variants that have the same useful label for a person choosing a download. */
fun downloadableFormats(formats: List<MediaFormat>): List<MediaFormat> =
    formats
        .filter(MediaFormat::isPlayableVideoCandidate)
        .distinctBy(MediaFormat::displayLabel)

internal fun Any.readNoArg(vararg names: String): Any? = names.firstNotNullOfOrNull { name ->
    runCatching {
        javaClass.methods.firstOrNull { it.name == name && it.parameterCount == 0 }?.invoke(this)
    }.getOrNull()
}

internal fun Any.readString(vararg names: String): String? = readNoArg(*names)?.toString()?.takeIf { it.isNotBlank() }

internal fun Any.readInt(vararg names: String): Int? = when (val value = readNoArg(*names)) {
    is Number -> value.toInt()
    else -> value?.toString()?.toIntOrNull()
}

internal fun Any.readLong(vararg names: String): Long? = when (val value = readNoArg(*names)) {
    is Number -> value.toLong()
    else -> value?.toString()?.toLongOrNull()
}

internal fun Any.toMediaFormat(): MediaFormat = MediaFormat(
    formatId = readString("getFormatId", "getId") ?: "unknown",
    extension = readString("getExt", "getExtension"),
    width = readInt("getWidth"),
    height = readInt("getHeight", "getResolution"),
    videoCodec = readString("getVcodec", "getVideoCodec"),
    audioCodec = readString("getAcodec", "getAudioCodec"),
    filesize = readLong("getFilesize", "getFilesizeApprox"),
    playableUrl = readString("getUrl", "getPlayableUrl"),
)
