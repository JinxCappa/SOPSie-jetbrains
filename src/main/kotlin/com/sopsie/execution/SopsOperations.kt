package com.sopsie.execution

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.WindowManager
import com.sopsie.detection.SopsDetector
import com.sopsie.model.SopsException
import com.sopsie.ui.SopsStatusBarWidget

private val LOG = logger<SopsOperations>()

object SopsOperations {

    fun decrypt(project: Project, file: VirtualFile) {
        if (!file.isInLocalFileSystem || !SopsDetector.getInstance().isEncrypted(file)) return

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Decrypting file...", false) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    ApplicationManager.getApplication().invokeAndWait {
                        FileDocumentManager.getInstance().saveAllDocuments()
                    }

                    val decrypted = SopsRunner.getInstance().decrypt(file.path)

                    ApplicationManager.getApplication().invokeAndWait {
                        WriteCommandAction.runWriteCommandAction(project) {
                            file.setBinaryContent(decrypted.toByteArray(Charsets.UTF_8))
                        }
                        file.refresh(true, false)
                    }

                    updateStatusBar(project)
                    showNotification(project, "File decrypted successfully", NotificationType.INFORMATION)
                } catch (ex: SopsException) {
                    LOG.warn("Failed to decrypt ${file.path}", ex)
                    showNotification(project, "SOPS Error: ${ex.error.message}", NotificationType.ERROR)
                } catch (ex: Exception) {
                    LOG.warn("Unexpected error decrypting ${file.path}", ex)
                    showNotification(project, "Decryption failed: ${ex.message}", NotificationType.ERROR)
                }
            }
        })
    }

    fun encrypt(project: Project, file: VirtualFile) {
        if (!file.isInLocalFileSystem || SopsDetector.getInstance().isEncrypted(file)) return

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Encrypting file...", false) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    ApplicationManager.getApplication().invokeAndWait {
                        FileDocumentManager.getInstance().saveAllDocuments()
                    }

                    val content = String(file.contentsToByteArray(), Charsets.UTF_8)
                    val encrypted = SopsRunner.getInstance().encryptContent(content, file.path)

                    ApplicationManager.getApplication().invokeAndWait {
                        WriteCommandAction.runWriteCommandAction(project) {
                            file.setBinaryContent(encrypted.toByteArray(Charsets.UTF_8))
                        }
                        file.refresh(true, false)
                    }

                    updateStatusBar(project)
                    showNotification(project, "File encrypted successfully", NotificationType.INFORMATION)
                } catch (ex: SopsException) {
                    LOG.warn("Failed to encrypt ${file.path}", ex)
                    showNotification(project, "SOPS Error: ${ex.error.message}", NotificationType.ERROR)
                } catch (ex: Exception) {
                    LOG.warn("Unexpected error encrypting ${file.path}", ex)
                    showNotification(project, "Encryption failed: ${ex.message}", NotificationType.ERROR)
                }
            }
        })
    }

    private fun updateStatusBar(project: Project) {
        ApplicationManager.getApplication().invokeLater {
            val statusBar = WindowManager.getInstance().getStatusBar(project)
            val widget = statusBar?.getWidget(SopsStatusBarWidget.ID) as? SopsStatusBarWidget
            widget?.update()
        }
    }

    private fun showNotification(project: Project, message: String, type: NotificationType) {
        ApplicationManager.getApplication().invokeLater {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("Sopsie.Notifications")
                .createNotification(message, type)
                .notify(project)
        }
    }
}
