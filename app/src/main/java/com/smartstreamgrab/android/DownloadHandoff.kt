package com.smartstreamgrab.android

/** Output choices kept explicit until a later phase owns file creation. */
data class DownloadOptions(
    val outputFileName: String? = null,
)

data class MediaIdentity(
    val title: String,
    val extractor: String,
)

/** Typed request passed to the future download executor. No file I/O is performed here. */
data class DownloadRequest(
    val sourceUrl: String,
    val media: MediaIdentity,
    val format: MediaFormat,
    val options: DownloadOptions = DownloadOptions(),
)

sealed interface PreviewDownloadState {
    data object Idle : PreviewDownloadState
    data class Draft(val url: String) : PreviewDownloadState
    data class Loading(val url: String) : PreviewDownloadState
    data class Ready(val preview: MediaPreview, val selectedFormatId: String? = null) : PreviewDownloadState
    data class Error(val url: String?, val message: String) : PreviewDownloadState
}

/**
 * Pure state boundary between metadata preview and a future download executor.
 * It deliberately creates no files and has no Android lifecycle dependency.
 */
class PreviewDownloadController {
    var state: PreviewDownloadState = PreviewDownloadState.Idle
        private set

    fun updateInput(url: String) {
        state = if (url.isBlank()) PreviewDownloadState.Idle else PreviewDownloadState.Draft(url)
    }

    fun beginExtraction(url: String) {
        state = PreviewDownloadState.Loading(url)
    }

    fun showPreview(preview: MediaPreview) {
        state = PreviewDownloadState.Ready(preview)
    }

    fun showError(url: String?, message: String) {
        state = PreviewDownloadState.Error(url, message)
    }

    fun selectFormat(formatId: String): Boolean {
        val ready = state as? PreviewDownloadState.Ready ?: return false
        val format = ready.preview.formats.firstOrNull { it.formatId == formatId }
            ?: return false
        if (!format.isDownloadSelectable()) return false
        state = ready.copy(selectedFormatId = format.formatId)
        return true
    }

    fun createDownloadRequest(options: DownloadOptions = DownloadOptions()): DownloadRequest? {
        val ready = state as? PreviewDownloadState.Ready ?: return null
        val selectedId = ready.selectedFormatId ?: return null
        val format = ready.preview.formats.firstOrNull { it.formatId == selectedId }
            ?: return null
        if (!ready.preview.url.isHttpUrl() || !format.isDownloadSelectable()) return null
        return DownloadRequest(
            sourceUrl = ready.preview.url,
            media = MediaIdentity(ready.preview.title, ready.preview.extractor),
            format = format,
            options = options,
        )
    }
}

internal fun MediaFormat.isDownloadSelectable(): Boolean =
    formatId.isNotBlank() && formatId != "unknown" && formatId != "resolved" &&
        formatId.none { it.isISOControl() || it == '/' || it == '\\' }

internal fun String.isHttpUrl(): Boolean = startsWith("http://") || startsWith("https://")
