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
    fun displayLabel(): String = listOfNotNull(
        formatId,
        extension,
        if (width != null && height != null) "${width}x$height" else null,
        listOfNotNull(videoCodec, audioCodec).joinToString("/").ifBlank { null },
        fallbackLabel,
    ).joinToString(" · ")
}

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
    fallbackLabel = toString().takeIf { it.isNotBlank() },
)
