package com.smartstreamgrab.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewDownloadControllerTest {
    private val preview = MediaPreview(
        url = "https://example.test/video",
        title = "Example video",
        thumbnail = null,
        extractor = "Example",
        formats = listOf(
            MediaFormat("video-1080", "mp4", 1920, 1080, "avc1", "mp4a", 100L, null),
            MediaFormat("unknown", null, null, null, null, null, null, null),
        ),
    )

    @Test
    fun previewSelectionProducesTypedDownloadRequest() {
        val controller = PreviewDownloadController()
        controller.showPreview(preview)

        assertTrue(controller.selectFormat("video-1080"))
        val request = controller.createDownloadRequest(DownloadOptions("example.mp4"))

        assertEquals("https://example.test/video", request?.sourceUrl)
        assertEquals("Example video", request?.media?.title)
        assertEquals("video-1080", request?.format?.formatId)
        assertEquals("example.mp4", request?.options?.outputFileName)
    }

    @Test
    fun incompleteFormatCannotCreateMalformedHandoff() {
        val controller = PreviewDownloadController()
        controller.showPreview(preview)

        assertTrue(!controller.selectFormat("unknown"))
        assertNull(controller.createDownloadRequest())
    }

    @Test
    fun newInputReplacesPreviousSelection() {
        val controller = PreviewDownloadController()
        controller.showPreview(preview)
        assertTrue(controller.selectFormat("video-1080"))

        controller.updateInput("https://example.test/other")

        assertTrue(controller.state is PreviewDownloadState.Draft)
        assertNull(controller.createDownloadRequest())
    }

    @Test
    fun extractionFailureCannotReusePreviousSuccessfulSelection() {
        val controller = PreviewDownloadController()
        controller.showPreview(preview)
        assertTrue(controller.selectFormat("video-1080"))

        controller.showError("https://example.test/bad", "unsupported")

        assertTrue(controller.state is PreviewDownloadState.Error)
        assertNull(controller.createDownloadRequest())
    }
}
