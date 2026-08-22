package com.smartstreamgrab.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaPreviewTest {
    private class FakeFormat {
        fun getFormatId() = "137"
        fun getExt() = "mp4"
        fun getWidth() = 1920
        fun getHeight() = 1080
        fun getVcodec() = "avc1"
        fun getAcodec() = "none"
        fun getFilesize() = 1234L
        fun getUrl() = "https://media.example/137"
    }

    @Test
    fun mapsBackendFormatToTypedPreviewModel() {
        val format = FakeFormat().toMediaFormat()

        assertEquals("137", format.formatId)
        assertEquals("mp4", format.extension)
        assertEquals(1920, format.width)
        assertEquals(1080, format.height)
        assertEquals(1234L, format.filesize)
        assertTrue(format.displayLabel().contains("1920x1080"))
    }

    @Test
    fun missingFormatFieldsRemainSafe() {
        val format = Any().toMediaFormat()

        assertEquals("unknown", format.formatId)
        assertTrue(format.displayLabel().contains("unknown"))
    }
}
