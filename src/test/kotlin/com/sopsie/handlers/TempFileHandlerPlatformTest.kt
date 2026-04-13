package com.sopsie.handlers

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.replaceService
import com.sopsie.execution.SopsRunner
import com.sopsie.model.SopsError
import com.sopsie.model.SopsException
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Platform-level integration test for [TempFileHandler].
 *
 * Boots a headless IntelliJ environment, replaces the application-level
 * SopsRunner service with a mock, then drives the handler through real
 * VirtualFile / Document / messagebus APIs. Catches integration bugs the
 * pure-JVM tests cannot — listener wiring, writeAction lifecycle,
 * FileDocumentManager.saveDocument round-trips.
 *
 * SopsRunner is final, so we mock it with mockito-inline (default in
 * Mockito 5) rather than subclassing.
 */
class TempFileHandlerPlatformTest : BasePlatformTestCase() {

    private lateinit var runnerMock: SopsRunner
    private lateinit var handler: TempFileHandler

    override fun setUp() {
        super.setUp()
        runnerMock = mock()
        // Default: encrypt returns a sentinel ciphertext.
        whenever(runnerMock.encryptContent(any(), any())).thenReturn("RE-ENCRYPTED")
        // Swap the app-level service; auto-restored via testRootDisposable.
        ApplicationManager.getApplication()
            .replaceService(SopsRunner::class.java, runnerMock, testRootDisposable)
        handler = TempFileHandler.getInstance(project)
    }

    fun `test createTempFile registers the file and writes content`() {
        val original = myFixture.configureByText("secret.yaml", "ENC[ciphertext]").virtualFile

        val tempFile = handler.createTempFile(original, "plaintext-content")

        assertNotNull("temp file should be created", tempFile)
        assertTrue(handler.isTempFile(tempFile!!))
        assertEquals(original.path, handler.getOriginalPath(tempFile))
        assertEquals("plaintext-content", String(tempFile.contentsToByteArray()))
        assertTrue("filename tagged with .sops-edit", tempFile.name.contains(".sops-edit"))
    }

    fun `test saving the temp document triggers re-encryption with correct payload`() {
        val original = myFixture.configureByText("save.yaml", "ENC[old]").virtualFile
        val tempFile = handler.createTempFile(original, "plaintext")!!

        // Edit the temp document and force a save — the handler's
        // beforeDocumentSaving listener should fire and call encryptContent.
        val tempDoc = FileDocumentManager.getInstance().getDocument(tempFile)!!
        WriteCommandAction.runWriteCommandAction(project) {
            tempDoc.setText("edited-plaintext")
        }
        WriteAction.runAndWait<Throwable> {
            FileDocumentManager.getInstance().saveDocument(tempDoc)
        }

        val contentCaptor = argumentCaptor<String>()
        val pathCaptor = argumentCaptor<String>()
        verify(runnerMock, times(1)).encryptContent(contentCaptor.capture(), pathCaptor.capture())
        assertEquals("edited-plaintext", contentCaptor.firstValue)
        assertEquals(original.path, pathCaptor.firstValue)

        // Note: this test asserts the encrypt invocation, not the writeback to
        // the original file. The handler writes back via
        // LocalFileSystem.findFileByPath, which does not resolve files in the
        // platform-test in-memory `temp:///` VFS. End-to-end writeback is
        // covered by the real-SOPS integration suite that uses on-disk files.
    }

    fun `test save flow swallows encrypt failures without throwing`() {
        whenever(runnerMock.encryptContent(any(), any()))
            .thenThrow(SopsException(SopsError.encryptionFailed("boom")))

        val original = myFixture.configureByText("fail.yaml", "ENC[original]").virtualFile
        val tempFile = handler.createTempFile(original, "plaintext")!!

        val tempDoc = FileDocumentManager.getInstance().getDocument(tempFile)!!
        WriteCommandAction.runWriteCommandAction(project) {
            tempDoc.setText("edited")
        }
        // The save listener catches the SopsException internally; this call
        // must not propagate the exception out to the test.
        WriteAction.runAndWait<Throwable> {
            FileDocumentManager.getInstance().saveDocument(tempDoc)
        }

        verify(runnerMock, times(1)).encryptContent(any(), any())
    }

    fun `test save flow ignores documents that are not managed temp files`() {
        val unrelated = myFixture.configureByText("unrelated.yaml", "key: value").virtualFile
        val doc = FileDocumentManager.getInstance().getDocument(unrelated)!!

        WriteCommandAction.runWriteCommandAction(project) {
            doc.setText("modified: true")
        }
        WriteAction.runAndWait<Throwable> {
            FileDocumentManager.getInstance().saveDocument(doc)
        }

        verify(runnerMock, never()).encryptContent(any(), any())
    }

    fun `test isTempFile is false for unrelated files`() {
        val original = myFixture.configureByText("not-a-temp.yaml", "x: 1").virtualFile
        assertFalse(handler.isTempFile(original))
    }
}
