package com.smartstreamgrab.android

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.io.File
import java.util.UUID

data class DownloadProgress(
    val fraction: Float,
    val speed: String?,
)

data class BackendDownloadSpec(
    val sourceUrl: String,
    val formatId: String,
    val outputFile: File,
)

data class BackendDownloadResult(
    val succeeded: Boolean,
    val error: String? = null,
)

interface DownloadBackend {
    fun execute(spec: BackendDownloadSpec, onProgress: (DownloadProgress) -> Unit): BackendDownloadResult

    fun cancel(): Boolean = false
}

object YtDlpCommandBuilder {
    fun build(spec: BackendDownloadSpec): YoutubeDLRequest = YoutubeDLRequest(spec.sourceUrl)
        .addOption("-f", spec.formatId)
        .addOption("-o", spec.outputFile.absolutePath)
}

class YtDlpDownloadBackend(
    private val youtubeDL: YoutubeDL = YoutubeDL.getInstance(),
) : DownloadBackend {
    override fun execute(
        spec: BackendDownloadSpec,
        onProgress: (DownloadProgress) -> Unit,
    ): BackendDownloadResult {
        val response = youtubeDL.execute(
            YtDlpCommandBuilder.build(spec),
            spec.outputFile.parentFile?.absolutePath,
        ) { fraction, _, speed ->
            onProgress(DownloadProgress(fraction.coerceIn(0f, 1f), speed.takeIf { it.isNotBlank() }))
        }
        return if (response.exitCode == 0) {
            BackendDownloadResult(succeeded = true)
        } else {
            BackendDownloadResult(succeeded = false, error = response.err.takeIf { it.isNotBlank() })
        }
    }
}

data class DownloadTarget(
    val file: File,
    val displayName: String,
    val mimeType: String,
)

data class DownloadOutput(
    val location: String,
    val displayName: String,
    val sizeBytes: Long,
    val mimeType: String = "application/octet-stream",
    val folderLocation: String = "",
)

interface DownloadStorage {
    fun createTarget(request: DownloadRequest): DownloadTarget

    fun publish(target: DownloadTarget): DownloadOutput

    fun discard(target: DownloadTarget)
}

object DownloadFileNaming {
    fun displayName(request: DownloadRequest): String {
        val requested = request.options.outputFileName?.takeIf { it.isNotBlank() }
            ?: request.media.title
        val base = sanitize(requested.substringBeforeLast('.', requested))
        val extension = sanitize(request.format.extension ?: "bin").trim('.').ifBlank { "bin" }
        return "$base.$extension"
    }

    fun sanitize(value: String): String = value
        .replace("..", "_")
        .replace(Regex("[/\\\\]"), "_")
        .replace(Regex("[^A-Za-z0-9._-]"), "_")
        .trim()
        .trim('.')
        .take(120)
        .ifBlank { "media" }
}

class AndroidDownloadStorage(
    private val context: Context,
) : DownloadStorage {
    private val fileProviderAuthority = "${context.packageName}.fileprovider"

    override fun createTarget(request: DownloadRequest): DownloadTarget {
        val workDirectory = File(context.cacheDir, "smartstream-downloads/${UUID.randomUUID()}")
        check(workDirectory.mkdirs()) { "Could not create temporary download directory" }
        val displayName = DownloadFileNaming.displayName(request)
        return DownloadTarget(
            file = File(workDirectory, displayName),
            displayName = displayName,
            mimeType = mimeTypeFor(request.format.extension),
        )
    }

    override fun publish(target: DownloadTarget): DownloadOutput {
        check(target.file.isFile) { "Download output was not created" }
        check(target.file.length() > 0L) { "Download output is empty" }
        val output = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            publishToMediaStore(target)
        } else {
            publishToAppExternalDownloads(target)
        }
        target.file.parentFile?.deleteRecursively()
        return output
    }

    override fun discard(target: DownloadTarget) {
        target.file.parentFile?.deleteRecursively()
    }

    @android.annotation.TargetApi(Build.VERSION_CODES.Q)
    private fun publishToMediaStore(target: DownloadTarget): DownloadOutput {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, target.displayName)
            put(MediaStore.Downloads.MIME_TYPE, target.mimeType)
            put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/SmartStreamGrab")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = requireNotNull(resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values))
        try {
            resolver.openOutputStream(uri).use { output ->
                requireNotNull(output) { "Could not open Android download destination" }
                target.file.inputStream().use { input -> input.copyTo(output) }
            }
            resolver.update(uri, ContentValues().apply {
                put(MediaStore.Downloads.IS_PENDING, 0)
            }, null, null)
            resolver.openFileDescriptor(uri, "r").use { descriptor ->
                check(descriptor != null) { "Published download cannot be opened" }
            }
            return DownloadOutput(
                location = uri.toString(),
                displayName = target.displayName,
                sizeBytes = target.file.length(),
                mimeType = target.mimeType,
                folderLocation = downloadsFolderUri(includeAppFolder = true).toString(),
            )
        } catch (error: Exception) {
            resolver.delete(uri, null, null)
            throw error
        }
    }

    private fun publishToAppExternalDownloads(target: DownloadTarget): DownloadOutput {
        val directory = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir,
            "SmartStreamGrab",
        ).also { check(it.mkdirs() || it.isDirectory) }
        val destination = uniqueFile(directory, target.displayName)
        target.file.inputStream().use { input -> destination.outputStream().use { input.copyTo(it) } }
        check(destination.length() > 0L) { "Published download is empty" }
        return DownloadOutput(
            location = FileProvider.getUriForFile(context, fileProviderAuthority, destination).toString(),
            displayName = destination.name,
            sizeBytes = destination.length(),
            mimeType = target.mimeType,
            folderLocation = downloadsFolderUri(includeAppFolder = false).toString(),
        )
    }

    private fun downloadsFolderUri(includeAppFolder: Boolean): Uri {
        val path = buildString {
            append(Environment.DIRECTORY_DOWNLOADS)
            if (includeAppFolder) append("/SmartStreamGrab")
        }
        return DocumentsContract.buildDocumentUri(
            "com.android.externalstorage.documents",
            "primary:$path",
        )
    }

    private fun uniqueFile(directory: File, name: String): File {
        val initial = File(directory, name)
        if (!initial.exists()) return initial
        val stem = name.substringBeforeLast('.')
        val extension = name.substringAfterLast('.', "")
        var index = 1
        while (true) {
            val candidate = File(directory, "$stem ($index).$extension")
            if (!candidate.exists()) return candidate
            index++
        }
    }

    private fun mimeTypeFor(extension: String?): String = when (extension?.lowercase()) {
        "mp4", "m4v" -> "video/mp4"
        "webm" -> "video/webm"
        "mp3" -> "audio/mpeg"
        "m4a" -> "audio/mp4"
        "wav" -> "audio/wav"
        else -> "application/octet-stream"
    }
}

class DownloadExecutor(
    private val backend: DownloadBackend,
    private val storage: DownloadStorage,
) {
    fun execute(
        request: DownloadRequest,
        onProgress: (DownloadProgress) -> Unit = {},
    ): DownloadOutput {
        validate(request)?.let { throw InvalidDownloadRequestException(it) }
        val target = storage.createTarget(request)
        try {
            val result = backend.execute(
                BackendDownloadSpec(request.sourceUrl, request.format.formatId, target.file),
                onProgress,
            )
            if (!result.succeeded) throw DownloadExecutionException(result.error ?: "yt-dlp failed")
            return storage.publish(target)
        } catch (error: Exception) {
            storage.discard(target)
            throw error
        }
    }

    fun cancel(): Boolean = backend.cancel()

    private fun validate(request: DownloadRequest): String? = when {
        !request.sourceUrl.isHttpUrl() -> "Source URL must use http(s)."
        request.media.title.isBlank() -> "Media title is missing."
        !request.format.isDownloadSelectable() -> "Selected format is incomplete."
        else -> null
    }
}

class InvalidDownloadRequestException(message: String) : IllegalArgumentException(message)

class DownloadExecutionException(message: String) : IllegalStateException(message)

sealed interface DownloadExecutionState {
    data object Idle : DownloadExecutionState
    data class Prepared(val request: DownloadRequest) : DownloadExecutionState
    data class Running(val request: DownloadRequest, val progress: DownloadProgress) : DownloadExecutionState
    data class Succeeded(val request: DownloadRequest, val output: DownloadOutput) : DownloadExecutionState
    data class Failed(val request: DownloadRequest?, val message: String) : DownloadExecutionState
}

class DownloadExecutionController(
    private val executor: DownloadExecutor,
) {
    var state: DownloadExecutionState = DownloadExecutionState.Idle
        private set

    fun prepare(request: DownloadRequest): Boolean {
        if (!request.sourceUrl.isHttpUrl() || !request.format.isDownloadSelectable()) return false
        state = DownloadExecutionState.Prepared(request)
        return true
    }

    fun start(): Boolean {
        val prepared = state as? DownloadExecutionState.Prepared ?: return false
        state = DownloadExecutionState.Running(prepared.request, DownloadProgress(0f, null))
        return true
    }

    fun executeStarted(): DownloadExecutionState {
        val running = state as? DownloadExecutionState.Running
            ?: return state
        return try {
            val output = executor.execute(running.request) { progress ->
                state = DownloadExecutionState.Running(running.request, progress)
            }
            DownloadExecutionState.Succeeded(running.request, output).also { state = it }
        } catch (error: Exception) {
            DownloadExecutionState.Failed(running.request, error.message ?: error.javaClass.simpleName).also {
                state = it
            }
        }
    }

    fun cancel(): Boolean = executor.cancel()

    fun reset() {
        state = DownloadExecutionState.Idle
    }
}
