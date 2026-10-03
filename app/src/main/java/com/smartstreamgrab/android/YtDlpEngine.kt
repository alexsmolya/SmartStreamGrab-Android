package com.smartstreamgrab.android

import android.content.Context
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException

sealed interface YtDlpUpdateState {
    data object Idle : YtDlpUpdateState
    data object Updating : YtDlpUpdateState
    data object Updated : YtDlpUpdateState
    data object AlreadyUpToDate : YtDlpUpdateState
    data class Failed(val message: String) : YtDlpUpdateState
}

/** Small lifecycle-safe boundary around the bundled yt-dlp runtime. */
class YtDlpEngine(
    private val backend: YoutubeDL = YoutubeDL.getInstance(),
) {
    fun version(context: Context): String = backend.versionName(context)
        ?.takeIf { it.isNotBlank() }
        ?: "unknown"

    fun updateStable(context: Context): YtDlpUpdateState = try {
        when (backend.updateYoutubeDL(context, YoutubeDL.UpdateChannel.STABLE)) {
            YoutubeDL.UpdateStatus.DONE -> YtDlpUpdateState.Updated
            YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE -> YtDlpUpdateState.AlreadyUpToDate
            null -> YtDlpUpdateState.Failed("yt-dlp returned no update status")
        }
    } catch (error: YoutubeDLException) {
        YtDlpUpdateState.Failed(error.message ?: error.javaClass.simpleName)
    } catch (error: Exception) {
        YtDlpUpdateState.Failed(error.message ?: error.javaClass.simpleName)
    }
}
