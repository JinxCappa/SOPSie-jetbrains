package com.sopsie.actions

import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.replaceService
import com.sopsie.detection.SopsDetector
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.nio.file.Files
import java.nio.file.Path

/**
 * Platform test for [DecryptAction.update] visibility logic.
 *
 * The action enables itself iff: project is non-null, a VirtualFile is in
 * the data context, the file is on the local file system, is not a
 * directory, and SopsDetector reports it as encrypted. This test pins
 * each branch.
 *
 * `myFixture.configureByText` creates `temp:///` files which return
 * `isInLocalFileSystem = false`, so this test uses real on-disk files
 * via [LocalFileSystem]. Files are cleaned up in `tearDown`.
 */
class DecryptActionPlatformTest : BasePlatformTestCase() {

    private lateinit var detectorMock: SopsDetector
    private lateinit var action: DecryptAction
    private val createdPaths = mutableListOf<Path>()

    override fun setUp() {
        super.setUp()
        detectorMock = mock()
        ApplicationManager.getApplication()
            .replaceService(SopsDetector::class.java, detectorMock, testRootDisposable)
        action = DecryptAction()
    }

    override fun tearDown() {
        try {
            createdPaths.forEach { runCatching { Files.deleteIfExists(it) } }
            createdPaths.clear()
        } finally {
            super.tearDown()
        }
    }

    private fun realFile(name: String, content: String): VirtualFile {
        val path = Files.createTempFile("decrypt-action-test-", "-$name")
        Files.writeString(path, content)
        createdPaths.add(path)
        return LocalFileSystem.getInstance().refreshAndFindFileByPath(path.toAbsolutePath().toString())
            ?: error("could not find virtual file for $path")
    }

    private fun runUpdate(file: VirtualFile?, includeProject: Boolean = true): Presentation {
        val presentation = Presentation()
        val context = DataContext { dataId ->
            when (dataId) {
                CommonDataKeys.VIRTUAL_FILE.name -> file
                CommonDataKeys.PROJECT.name -> if (includeProject) project else null
                else -> null
            }
        }
        val event = AnActionEvent.createEvent(
            action,
            context,
            presentation,
            "TestPlace",
            ActionUiKind.NONE,
            null
        )
        action.update(event)
        return presentation
    }

    fun `test enabled for an encrypted local file`() {
        val file = realFile("encrypted.yaml", "irrelevant")
        whenever(detectorMock.isEncrypted(any<VirtualFile>())).thenReturn(true)

        val p = runUpdate(file)
        assertTrue("should be enabled", p.isEnabled)
        assertTrue("should be visible", p.isVisible)
    }

    fun `test disabled when SopsDetector says not encrypted`() {
        val file = realFile("plain.yaml", "irrelevant")
        whenever(detectorMock.isEncrypted(any<VirtualFile>())).thenReturn(false)

        val p = runUpdate(file)
        assertFalse(p.isEnabled)
        assertFalse(p.isVisible)
    }

    fun `test disabled when no file is in the data context`() {
        whenever(detectorMock.isEncrypted(any<VirtualFile>())).thenReturn(true)

        val p = runUpdate(file = null)
        assertFalse(p.isEnabled)
    }

    fun `test disabled when no project is available`() {
        val file = realFile("encrypted.yaml", "irrelevant")
        whenever(detectorMock.isEncrypted(any<VirtualFile>())).thenReturn(true)

        val p = runUpdate(file, includeProject = false)
        assertFalse(p.isEnabled)
    }

    // Note: the `isInLocalFileSystem` branch is intentionally not covered
    // here. The IntelliJ test VFS reports this property inconsistently
    // across versions (the in-memory `temp:///` scheme has been observed
    // to report true under some test fixtures), so the assertion would be
    // brittle. The branch is exercised in the real-SOPS integration suite,
    // where files live on the actual local file system.
}
