package com.smartstreamgrab.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import coil.compose.AsyncImage
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val sharedUrl = mutableStateOf("")
    private val preview = mutableStateOf<MediaPreview?>(null)
    private val error = mutableStateOf<String?>(null)
    private val loading = mutableStateOf(false)
    private val session = mutableStateOf<PreviewDownloadState>(PreviewDownloadState.Idle)
    private val downloadHandoff = mutableStateOf<DownloadRequest?>(null)
    private val downloadState = mutableStateOf<DownloadExecutionState>(DownloadExecutionState.Idle)
    private val handoffController = PreviewDownloadController()
    private val extractor = YtDlpMetadataExtractor()
    private val downloadExecutionController by lazy {
        DownloadExecutionController(
            DownloadExecutor(YtDlpDownloadBackend(), AndroidDownloadStorage(this)),
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            YoutubeDL.getInstance().init(this)
        } catch (e: YoutubeDLException) {
            error.value = "yt-dlp initialization failed: ${e.message ?: e.javaClass.simpleName}"
        }
        acceptIntent(intent)
        setContent { PreviewScreen() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        acceptIntent(intent)
    }

    private fun acceptIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
        if (text.isNotEmpty()) updateInput(text)
    }

    private fun updateInput(value: String) {
        sharedUrl.value = value
        handoffController.updateInput(value)
        session.value = handoffController.state
        preview.value = null
        error.value = null
        downloadHandoff.value = null
        downloadExecutionController.reset()
        downloadState.value = downloadExecutionController.state
    }

    private fun extract() {
        val url = sharedUrl.value.trim()
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            error.value = "Enter an http(s) URL."
            preview.value = null
            handoffController.showError(url, error.value!!)
            session.value = handoffController.state
            downloadHandoff.value = null
            downloadExecutionController.reset()
            downloadState.value = downloadExecutionController.state
            return
        }
        loading.value = true
        error.value = null
        handoffController.beginExtraction(url)
        session.value = handoffController.state
        downloadHandoff.value = null
        downloadExecutionController.reset()
        downloadState.value = downloadExecutionController.state
        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    extractor.extract(url)
                }
                preview.value = result
                handoffController.showPreview(result)
                session.value = handoffController.state
                downloadState.value = DownloadExecutionState.Idle
            } catch (e: Exception) {
                preview.value = null
                error.value = "Could not extract media: ${e.message ?: e.javaClass.simpleName}"
                handoffController.showError(url, error.value!!)
                session.value = handoffController.state
                downloadExecutionController.reset()
                downloadState.value = downloadExecutionController.state
            } finally {
                loading.value = false
            }
        }
    }

    private fun selectFormat(formatId: String) {
        if (handoffController.selectFormat(formatId)) {
            session.value = handoffController.state
            downloadHandoff.value = null
        }
    }

    private fun startDownload() {
        val request = handoffController.createDownloadRequest() ?: return
        if (!downloadExecutionController.prepare(request) || !downloadExecutionController.start()) return
        downloadHandoff.value = request
        downloadState.value = downloadExecutionController.state
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                downloadExecutionController.executeStarted()
            }
            downloadState.value = result
        }
    }

    @androidx.compose.runtime.Composable
    private fun PreviewScreen() {
        var input by sharedUrl
        val currentPreview by preview
        val currentError by error
        val isLoading by loading
        val currentSession by session
        val currentHandoff by downloadHandoff
        val currentDownloadState by downloadState
        MaterialTheme {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { Text("SmartStreamGrab", style = MaterialTheme.typography.headlineSmall) }
                item { Text("Metadata preview and download handoff") }
                item {
                    OutlinedTextField(input, ::updateInput, Modifier.fillMaxWidth(), label = { Text("URL") })
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = ::extract, enabled = !isLoading) { Text("Extract") }
                        if (isLoading) CircularProgressIndicator()
                    }
                }
                if (currentError != null) item { Text(currentError!!, color = MaterialTheme.colorScheme.error) }
                if (currentPreview != null) {
                    val item = currentPreview!!
                    item { HorizontalDivider() }
                    item { Text("Original URL: ${item.url}") }
                    item { Text("Title: ${item.title}", style = MaterialTheme.typography.titleMedium) }
                    item { Text("Extractor/site: ${item.extractor}") }
                    item { AsyncImage(model = item.thumbnail, contentDescription = "Thumbnail", modifier = Modifier.fillMaxWidth().height(190.dp)) }
                    item { Text("Resolved media/formats (${item.formats.size})", style = MaterialTheme.typography.titleMedium) }
                    items(item.formats) { format -> Text(format.displayLabel()) }
                    items(item.formats) { format ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(if ((currentSession as? PreviewDownloadState.Ready)?.selectedFormatId == format.formatId) "Selected" else "")
                            Button(onClick = { selectFormat(format.formatId) }) { Text("Select ${format.formatId}") }
                        }
                    }
                    item {
                        Button(
                            onClick = ::startDownload,
                            enabled = (currentSession as? PreviewDownloadState.Ready)?.selectedFormatId != null &&
                                currentDownloadState !is DownloadExecutionState.Running,
                        ) { Text("Download") }
                    }
                    if (currentHandoff != null && currentDownloadState is DownloadExecutionState.Prepared) item {
                        Text("Download prepared for ${currentHandoff!!.format.formatId}.")
                    }
                    if (currentDownloadState is DownloadExecutionState.Running) item {
                        Text("Downloading: ${(currentDownloadState as DownloadExecutionState.Running).progress.fraction * 100}%")
                    }
                    if (currentDownloadState is DownloadExecutionState.Succeeded) item {
                        Text("Downloaded to ${(currentDownloadState as DownloadExecutionState.Succeeded).output.location}")
                    }
                    if (currentDownloadState is DownloadExecutionState.Failed) item {
                        Text(
                            "Download failed: ${(currentDownloadState as DownloadExecutionState.Failed).message}",
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                item { Spacer(Modifier.height(24.dp)); Text("Downloads use the Android Downloads provider when available.") }
            }
        }
    }
}
