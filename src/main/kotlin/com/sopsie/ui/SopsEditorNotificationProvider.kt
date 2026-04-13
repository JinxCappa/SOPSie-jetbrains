package com.sopsie.ui

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import com.sopsie.config.SopsConfigManager
import com.sopsie.detection.SopsDetector
import com.sopsie.execution.SopsOperations
import com.sopsie.handlers.DecryptedEditorHandler
import com.sopsie.handlers.ShowDecryptedOptions
import com.sopsie.services.FileStateTracker
import com.sopsie.util.SopsIcons
import java.util.function.Function
import javax.swing.JComponent

class SopsEditorNotificationProvider : EditorNotificationProvider, DumbAware {

    override fun collectNotificationData(
        project: Project,
        file: VirtualFile
    ): Function<in FileEditor, out JComponent?>? {
        if (file.isDirectory || !file.isInLocalFileSystem) {
            return null
        }

        val configManager = SopsConfigManager.getInstance(project)
        if (!configManager.hasMatchingRule(file)) {
            return null
        }

        val detector = SopsDetector.getInstance()
        val fileStateTracker = FileStateTracker.getInstance(project)

        val isEncrypted = detector.isEncrypted(file)
        val isMarkedDecrypted = fileStateTracker.isMarkedDecrypted(file)

        return when {
            isEncrypted -> createEncryptedPanel(project, file)
            isMarkedDecrypted -> createDecryptedPanel(project, file)
            else -> createPlaintextPanel(project, file)
        }
    }

    private fun createEncryptedPanel(project: Project, file: VirtualFile): Function<FileEditor, JComponent?> {
        return Function { _ ->
            EditorNotificationPanel(EditorNotificationPanel.Status.Info).apply {
                icon(SopsIcons.Locked)
                text = "This file is SOPS-encrypted"

                createActionLabel("Preview Decrypted") {
                    DecryptedEditorHandler.getInstance(project).openPreview(
                        file, ShowDecryptedOptions(preserveFocus = false, showInfoMessage = false)
                    )
                }

                createActionLabel("Edit In Place") {
                    DecryptedEditorHandler.getInstance(project).openEditInPlace(
                        file, ShowDecryptedOptions(preserveFocus = false, showInfoMessage = true)
                    )
                }

                createActionLabel("Decrypt") {
                    SopsOperations.decrypt(project, file)
                }
            }
        }
    }

    private fun createDecryptedPanel(project: Project, file: VirtualFile): Function<FileEditor, JComponent?> {
        return Function { _ ->
            EditorNotificationPanel(EditorNotificationPanel.Status.Warning).apply {
                icon(SopsIcons.Unlocked)
                text = "This file was decrypted and should be re-encrypted"

                createActionLabel("Encrypt") {
                    SopsOperations.encrypt(project, file)
                }
            }
        }
    }

    private fun createPlaintextPanel(project: Project, file: VirtualFile): Function<FileEditor, JComponent?> {
        return Function { _ ->
            EditorNotificationPanel(EditorNotificationPanel.Status.Info).apply {
                icon(SopsIcons.Info)
                text = "This file matches a SOPS rule and can be encrypted"

                createActionLabel("Encrypt") {
                    SopsOperations.encrypt(project, file)
                }
            }
        }
    }
}
