package com.smartstreamgrab.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaPreviewTest {
    private class FakeFormat(
        private val id: String = "137",
        private val ext: String = "mp4",
        private val width: Int = 1920,
        private val height: Int = 1080,
        private val videoCodec: String = "avc1",
        private val audioCodec: String = "mp4a",
    ) {
        fun getFormatId() = id
        fun getExt() = ext
        fun getWidth() = width
        fun getHeight() = height
        fun getVcodec() = videoCodec
        fun getAcodec() = audioCodec
        fun getFilesize() = 1234L
        fun getUrl() = "https://media.example/$id"
    }

    @Test
    fun mapsBackendFormatToTypedPreviewModel() {
        val format = FakeFormat().toMediaFormat()

        assertEquals("137", format.formatId)
        assertEquals("mp4", format.extension)
        assertEquals(1920, format.width)
        assertEquals(1080, format.height)
        assertEquals(1234L, format.filesize)
        assertEquals("MP4 · 1920×1080", format.displayLabel())
    }

    @Test
    fun missingFormatFieldsRemainSafeAndReadable() {
        val format = Any().toMediaFormat()

        assertEquals("unknown", format.formatId)
        assertEquals("Best available", format.displayLabel())
        assertTrue(format.displayLabel().isNotBlank())
    }

    @Test
    fun userFacingFormatListCollapsesDuplicateLabelsButKeepsDifferentResolutions() {
        val formats = listOf(
            FakeFormat(id = "format-1").toMediaFormat(),
            FakeFormat(id = "format-2").toMediaFormat(),
            FakeFormat(id = "format-3", width = 576, height = 1024).toMediaFormat(),
        )

        val visibleFormats = downloadableFormats(formats)

        assertEquals(2, visibleFormats.size)
        assertEquals("format-1", visibleFormats[0].formatId)
        assertEquals("MP4 · 576×1024", visibleFormats[1].displayLabel())
    }

    @Test
    fun userFacingFormatListExcludesSeparateAudioAndVideoStreams() {
        val formats = listOf(
            FakeFormat(id = "combined").toMediaFormat(),
            FakeFormat(id = "video-only", audioCodec = "none").toMediaFormat(),
            FakeFormat(id = "audio-only", videoCodec = "none", width = 0, height = 0).toMediaFormat(),
        )

        val visibleFormats = downloadableFormats(formats)

        assertEquals(listOf("combined"), visibleFormats.map(MediaFormat::formatId))
    }
}
