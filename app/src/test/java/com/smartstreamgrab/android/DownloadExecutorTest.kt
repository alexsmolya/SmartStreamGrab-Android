package com.smartstreamgrab.android

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadExecutorTest {
    private val request = DownloadRequest(
        sourceUrl = "https://example.test/video",
        media = MediaIdentity("Example video", "Example"),
        format = MediaFormat("137", "mp4", 1920, 1080, "avc1", "mp4a", null, null),
    )

    @Test
    fun commandBuilderPropagatesSelectedFormatAndOutput() {
        val output = File("/tmp/smartstream/example.mp4")
        val command = YtDlpCommandBuilder.build(
            BackendDownloadSpec(request.sourceUrl, request.format.formatId, output),
        ).buildCommand()

        assertTrue(command.contains("-f"))
        assertEquals("137", command[command.indexOf("-f") + 1])
        assertTrue(command.contains("-o"))
        assertEquals(output.absolutePath, command[command.indexOf("-o") + 1])
        assertTrue(command.contains(request.sourceUrl))
    }

    @Test
    fun validRequestProducesTypedOutputAndProgress() {
        val backend = FakeBackend { spec, progress ->
            spec.outputFile.parentFile?.mkdirs()
            spec.outputFile.writeText("media")
            progress(DownloadProgress(1f, "1MiB/s"))
            BackendDownloadResult(true)
        }
        val storage = FakeStorage()
        val executor = DownloadExecutor(backend, storage)
        val progress = mutableListOf<DownloadProgress>()

        val output = executor.execute(request) { progress += it }

        assertEquals("137", backend.lastSpec?.formatId)
        assertEquals("content://downloads/1", output.location)
        assertEquals(1, progress.size)
        assertEquals(1f, progress.single().fraction)
    }

    @Test
    fun invalidRequestIsRejectedBeforeBackendInvocation() {
        val backend = FakeBackend { _, _ -> BackendDownloadResult(true) }
        val executor = DownloadExecutor(backend, FakeStorage())
        val invalid = request.copy(sourceUrl = "file:///unsafe")

        try {
            executor.execute(invalid)
        } catch (expected: InvalidDownloadRequestException) {
            assertNull(backend.lastSpec)
            return
        }
        throw AssertionError("Expected invalid request rejection")
    }

    @Test
    fun filenameSanitizationPreventsPathTraversal() {
        val unsafe = request.copy(options = DownloadOptions("../../secret/evil?.mp4"))

        val name = DownloadFileNaming.displayName(unsafe)

        assertFalse(name.contains('/'))
        assertFalse(name.contains('\\'))
        assertFalse(name.contains(".."))
        assertTrue(name.endsWith(".mp4"))
    }

    @Test
    fun backendFailureMapsToBoundedFailureState() {
        val controller = DownloadExecutionController(
            DownloadExecutor(FakeBackend { _, _ -> BackendDownloadResult(false, "remote failed") }, FakeStorage()),
        )
        assertTrue(controller.prepare(request))
        assertTrue(controller.start())

        val state = controller.executeStarted()

        assertTrue(state is DownloadExecutionState.Failed)
        assertEquals("remote failed", (state as DownloadExecutionState.Failed).message)
    }

    @Test
    fun successfulExecutionProducesTypedResultState() {
        val controller = DownloadExecutionController(
            DownloadExecutor(FakeBackend { spec, _ ->
                spec.outputFile.parentFile?.mkdirs()
                spec.outputFile.writeText("media")
                BackendDownloadResult(true)
            }, FakeStorage()),
        )
        assertTrue(controller.prepare(request))
        assertTrue(controller.start())

        val state = controller.executeStarted()

        assertTrue(state is DownloadExecutionState.Succeeded)
        assertEquals("content://downloads/1", (state as DownloadExecutionState.Succeeded).output.location)
    }

    @Test
    fun duplicateStartIsRejectedWhileRunning() {
        val controller = DownloadExecutionController(
            DownloadExecutor(FakeBackend { _, _ -> BackendDownloadResult(true) }, FakeStorage()),
        )
        assertTrue(controller.prepare(request))

        assertTrue(controller.start())
        assertFalse(controller.start())
    }

    @Test
    fun resetPreventsStaleRequestFromStarting() {
        val controller = DownloadExecutionController(
            DownloadExecutor(FakeBackend { _, _ -> BackendDownloadResult(true) }, FakeStorage()),
        )
        assertTrue(controller.prepare(request))
        controller.reset()

        assertFalse(controller.start())
        assertTrue(controller.state is DownloadExecutionState.Idle)
    }

    private class FakeBackend(
        private val action: (BackendDownloadSpec, (DownloadProgress) -> Unit) -> BackendDownloadResult,
    ) : DownloadBackend {
        var lastSpec: BackendDownloadSpec? = null

        override fun execute(
            spec: BackendDownloadSpec,
            onProgress: (DownloadProgress) -> Unit,
        ): BackendDownloadResult {
            lastSpec = spec
            return action(spec, onProgress)
        }
    }

    private class FakeStorage : DownloadStorage {
        private val root = Files.createTempDirectory("smartstream-test-").toFile()
        private val target = DownloadTarget(File(root, "example.mp4"), "example.mp4", "video/mp4")

        override fun createTarget(request: DownloadRequest): DownloadTarget = target

        override fun publish(target: DownloadTarget): DownloadOutput {
            assertNotNull(target.file)
            return DownloadOutput("content://downloads/1", target.displayName, target.file.length())
        }

        override fun discard(target: DownloadTarget) {
            target.file.delete()
        }
    }
}
