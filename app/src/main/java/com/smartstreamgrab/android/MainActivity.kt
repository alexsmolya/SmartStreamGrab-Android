package com.smartstreamgrab.android

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.provider.DocumentsContract
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
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
    private val engineVersion = mutableStateOf("unknown")
    private val engineUpdateState = mutableStateOf<YtDlpUpdateState>(YtDlpUpdateState.Idle)
    private val handoffController = PreviewDownloadController()
    private val ytDlpEngine = YtDlpEngine()
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
            engineVersion.value = ytDlpEngine.version(this)
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

    private fun updateExtractor() {
        if (engineUpdateState.value is YtDlpUpdateState.Updating) return
        engineUpdateState.value = YtDlpUpdateState.Updating
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                ytDlpEngine.updateStable(this@MainActivity)
            }
            engineVersion.value = ytDlpEngine.version(this@MainActivity)
            engineUpdateState.value = result
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
        val successfulOutput = (currentDownloadState as? DownloadExecutionState.Succeeded)?.output

        MaterialTheme {
            Scaffold(
                bottomBar = {
                    if (successfulOutput != null) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            tonalElevation = 3.dp,
                            shadowElevation = 4.dp,
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text("Saved to Downloads.")
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Button(
                                        onClick = { openVideo(successfulOutput) },
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Text("Open video")
                                    }
                                    Button(
                                        onClick = { openFolder(successfulOutput) },
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Text("Open folder")
                                    }
                                }
                            }
                        }
                    }
                },
            ) { contentPadding ->
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(contentPadding)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                item { Text("SmartStreamGrab", style = MaterialTheme.typography.headlineSmall) }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Extractor: yt-dlp ${engineVersion.value}")
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        ) {
                            androidx.compose.material3.OutlinedButton(
                                onClick = ::updateExtractor,
                                enabled = engineUpdateState.value !is YtDlpUpdateState.Updating,
                            ) {
                                Text(
                                    if (engineUpdateState.value is YtDlpUpdateState.Updating) {
                                        "Updating extractor..."
                                    } else {
                                        "Update extractor"
                                    },
                                )
                            }
                            when (val state = engineUpdateState.value) {
                                YtDlpUpdateState.Updated -> Text("Updated")
                                YtDlpUpdateState.AlreadyUpToDate -> Text("Already up to date")
                                is YtDlpUpdateState.Failed -> Text(
                                    "Update failed: ${state.message}",
                                    color = MaterialTheme.colorScheme.error,
                                )
                                else -> Unit
                            }
                        }
                    }
                }
                item {
                    OutlinedTextField(
                        value = input,
                        onValueChange = ::updateInput,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Video link") },
                        singleLine = true,
                        trailingIcon = {
                            if (input.isNotBlank()) {
                                IconButton(onClick = { updateInput("") }) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Clear video link",
                                    )
                                }
                            }
                        },
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
                                            .height(145.dp)
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
                    val playableFormats = downloadableFormats(media.formats)
                    if (playableFormats.isEmpty()) {
                        item {
                            Text(
                                "No combined video formats were found. Update the extractor and try again.",
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    } else {
                        items(playableFormats) { format ->
                            Button(
                                onClick = { download(format.formatId) },
                                enabled = !isLoading && !isDownloading,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Download ${format.displayLabel()}")
                            }
                        }
                    }
                    when (val state = currentDownloadState) {
                        is DownloadExecutionState.Running -> item {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                CircularProgressIndicator(progress = { state.progress.fraction })
                                Text("Downloading ${(state.progress.fraction * 100).toInt().coerceIn(0, 100)}%")
                            }
                        }
                        is DownloadExecutionState.Succeeded -> Unit
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

    private fun openVideo(output: DownloadOutput) {
        val uri = Uri.parse(output.location)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, output.mimeType)
            clipData = ClipData.newRawUri("SmartStreamGrab video", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            if (intent.resolveActivity(packageManager) == null) {
                error.value = "No installed app can open ${output.displayName}."
                return
            }
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            error.value = "No installed app can open ${output.displayName}."
        } catch (e: SecurityException) {
            error.value = "The video cannot be opened: ${e.message ?: "permission denied"}"
        }
    }

    private fun openFolder(output: DownloadOutput) {
        val folderUri = output.folderLocation
            .takeIf { it.isNotBlank() }
            ?.let(Uri::parse)
            ?: DocumentsContract.buildDocumentUri(
                "com.android.externalstorage.documents",
                "primary:Download",
            )
        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            data = folderUri
            type = "vnd.android.document/directory"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(viewIntent)
        } catch (_: ActivityNotFoundException) {
            openFolderPicker(folderUri)
        } catch (_: SecurityException) {
            openFolderPicker(folderUri)
        }
    }

    private fun openFolderPicker(folderUri: Uri) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                putExtra(DocumentsContract.EXTRA_INITIAL_URI, folderUri)
            }
        }
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            error.value = "No installed file manager can open the Downloads folder."
        } catch (e: SecurityException) {
            error.value = "The Downloads folder cannot be opened: ${e.message ?: "permission denied"}"
        }
    }
}
