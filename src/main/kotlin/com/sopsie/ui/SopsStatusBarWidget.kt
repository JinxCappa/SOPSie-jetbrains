package com.sopsie.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.impl.status.EditorBasedWidget
import com.intellij.util.Alarm
import com.intellij.util.Consumer
import com.sopsie.config.SopsConfigManager
import com.sopsie.detection.SopsDetector
import com.sopsie.execution.SopsOperations
import com.sopsie.model.FileEncryptionState
import com.sopsie.services.FileStateTracker
import com.sopsie.services.SopsSettingsService
import com.sopsie.util.SopsIcons
import java.awt.event.MouseEvent
import javax.swing.Icon

class SopsStatusBarWidget(project: Project) : EditorBasedWidget(project), StatusBarWidget.IconPresentation {

    companion object {
        const val ID = "SopsieStatusBar"
        private const val STARTUP_UPDATE_DELAY_MS = 1000
    }

    private var currentState: FileEncryptionState = FileEncryptionState.UNKNOWN
    private var hasMatchingRule: Boolean = false
    private val updateAlarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, this)

    override fun ID(): String = ID

    override fun getPresentation(): StatusBarWidget.WidgetPresentation = this

    override fun getIcon(): Icon? {
        if (!SopsSettingsService.getInstance().shouldShowStatusBar) return null
        if (!hasMatchingRule) return null

        return when (currentState) {
            FileEncryptionState.ENCRYPTED -> SopsIcons.Locked
            FileEncryptionState.DECRYPTED -> SopsIcons.Unlocked
            FileEncryptionState.PLAINTEXT -> SopsIcons.Info
            FileEncryptionState.UNKNOWN -> null
        }
    }

    override fun getTooltipText(): String? {
        if (!hasMatchingRule) return null

        return when (currentState) {
            FileEncryptionState.ENCRYPTED -> "SOPS: Encrypted - Click to decrypt"
            FileEncryptionState.DECRYPTED -> "SOPS: Decrypted - Click to encrypt"
            FileEncryptionState.PLAINTEXT -> "SOPS: Not encrypted - Click to encrypt"
            FileEncryptionState.UNKNOWN -> null
        }
    }

    override fun getClickConsumer(): Consumer<MouseEvent>? {
        return Consumer { _ ->
            val file = getCurrentFile() ?: return@Consumer

            when (currentState) {
                FileEncryptionState.ENCRYPTED -> SopsOperations.decrypt(project, file)
                FileEncryptionState.DECRYPTED, FileEncryptionState.PLAINTEXT -> SopsOperations.encrypt(project, file)
                FileEncryptionState.UNKNOWN -> return@Consumer
            }
        }
    }

    fun update() {
        ApplicationManager.getApplication().invokeLater {
            val file = getCurrentFile()
            updateForFile(file)
            myStatusBar?.updateWidget(ID())
        }
    }

    private fun updateForFile(file: VirtualFile?) {
        if (file == null || file.isDirectory) {
            currentState = FileEncryptionState.UNKNOWN
            hasMatchingRule = false
            return
        }

        val configManager = SopsConfigManager.getInstance(project)
        hasMatchingRule = configManager.hasMatchingRule(file)

        if (!hasMatchingRule) {
            currentState = FileEncryptionState.UNKNOWN
            return
        }

        val detector = SopsDetector.getInstance()
        val fileStateTracker = FileStateTracker.getInstance(project)

        currentState = when {
            detector.isEncrypted(file) -> FileEncryptionState.ENCRYPTED
            fileStateTracker.isMarkedDecrypted(file) -> FileEncryptionState.DECRYPTED
            else -> FileEncryptionState.PLAINTEXT
        }
    }

    private fun getCurrentFile(): VirtualFile? {
        return FileEditorManager.getInstance(project).selectedFiles.firstOrNull()
    }

    override fun install(statusBar: StatusBar) {
        super.install(statusBar)
        update()
        updateAlarm.addRequest({ update() }, STARTUP_UPDATE_DELAY_MS)
    }

    override fun dispose() {
        updateAlarm.cancelAllRequests()
        super.dispose()
    }
}
