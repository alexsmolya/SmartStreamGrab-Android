package com.smartstreamgrab.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
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
            downloadExecutionController.reset()
            downloadState.value = downloadExecutionController.state
            return
        }
        loading.value = true
        error.value = null
        handoffController.beginExtraction(url)
        session.value = handoffController.state
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

    private fun download(formatId: String) {
        if (!handoffController.selectFormat(formatId)) return
        session.value = handoffController.state
        val request = handoffController.createDownloadRequest() ?: return
        if (!downloadExecutionController.prepare(request) || !downloadExecutionController.start()) return
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
        val currentDownloadState by downloadState
        val isDownloading = currentDownloadState is DownloadExecutionState.Running

        MaterialTheme {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item { Text("SmartStreamGrab", style = MaterialTheme.typography.headlineSmall) }
                item {
                    OutlinedTextField(
                        value = input,
                        onValueChange = ::updateInput,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Video link") },
                        singleLine = true,
                    )
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = ::extract, enabled = !isLoading) { Text("Extract") }
                        if (isLoading) CircularProgressIndicator()
                    }
                }
                if (currentError != null) {
                    item {
                        Text(currentError!!, color = MaterialTheme.colorScheme.error)
                    }
                }
                if (currentPreview != null) {
                    val media = currentPreview!!
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                if (!media.thumbnail.isNullOrBlank()) {
                                    AsyncImage(
                                        model = media.thumbnail,
                                        contentDescription = "${media.title} preview",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(220.dp)
                                            .clip(RoundedCornerShape(12.dp)),
                                        contentScale = ContentScale.Crop,
                                    )
                                }
                                Text(media.title, style = MaterialTheme.typography.titleLarge)
                                Text(media.extractor, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    item {
                        Text("Available downloads", style = MaterialTheme.typography.titleMedium)
                    }
                    items(downloadableFormats(media.formats)) { format ->
                        Button(
                            onClick = { download(format.formatId) },
                            enabled = !isLoading && !isDownloading,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Download ${format.displayLabel()}")
                        }
                    }
                    when (val state = currentDownloadState) {
                        is DownloadExecutionState.Running -> item {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                CircularProgressIndicator(progress = { state.progress.fraction })
                                Text("Downloading ${(state.progress.fraction * 100).toInt().coerceIn(0, 100)}%")
                            }
                        }
                        is DownloadExecutionState.Succeeded -> item {
                            Text("Saved to Downloads.")
                        }
                        is DownloadExecutionState.Failed -> item {
                            Text(
                                "Download failed: ${state.message}",
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        else -> Unit
                    }
                }
            }
        }
    }
}
