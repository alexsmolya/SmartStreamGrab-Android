package com.smartstreamgrab.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.remember
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

data class Preview(
    val url: String,
    val title: String,
    val thumbnail: String?,
    val extractor: String,
    val formats: List<String>,
)

class MainActivity : ComponentActivity() {
    private val sharedUrl = mutableStateOf("")
    private val preview = mutableStateOf<Preview?>(null)
    private val error = mutableStateOf<String?>(null)
    private val loading = mutableStateOf(false)

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
        if (text.isNotEmpty()) sharedUrl.value = text
    }

    private fun extract() {
        val url = sharedUrl.value.trim()
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            error.value = "Enter an http(s) URL."
            preview.value = null
            return
        }
        loading.value = true
        error.value = null
        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val info = YoutubeDL.getInstance().getInfo(url)
                    Preview(url, info.title(), info.thumbnail(), info.extractor(), info.formats())
                }
                preview.value = result
            } catch (e: Exception) {
                preview.value = null
                error.value = "Could not extract media: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                loading.value = false
            }
        }
    }

    private fun Any.call(vararg names: String): Any? = names.firstNotNullOfOrNull { name ->
        runCatching { javaClass.methods.firstOrNull { it.name == name && it.parameterCount == 0 }?.invoke(this) }.getOrNull()
    }

    private fun Any.title() = call("getTitle")?.toString()?.ifBlank { "(untitled)" } ?: "(untitled)"
    private fun Any.thumbnail() = call("getThumbnail", "getThumbnailUrl")?.toString()?.takeIf { it.startsWith("http") }
    private fun Any.extractor() = call("getExtractorKey", "getExtractor")?.toString() ?: "unknown"
    private fun Any.formats(): List<String> {
        val raw = call("getFormats") as? Iterable<*> ?: return listOf("Resolved media (format list unavailable)")
        return raw.take(12).map { it.toString() }.ifEmpty { listOf("Resolved media (no format rows)") }
    }

    @androidx.compose.runtime.Composable
    private fun PreviewScreen() {
        var input by remember { sharedUrl }
        val currentPreview by remember { preview }
        val currentError by remember { error }
        val isLoading by remember { loading }
        MaterialTheme {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { Text("SmartStreamGrab", style = MaterialTheme.typography.headlineSmall) }
                item { Text("Phase 0 media extraction preview") }
                item {
                    OutlinedTextField(input, { input = it }, Modifier.fillMaxWidth(), label = { Text("URL") })
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
                    items(item.formats) { format -> Text(format) }
                }
                item { Spacer(Modifier.height(24.dp)); Text("No download or persistence is performed in this spike.") }
            }
        }
    }
}
