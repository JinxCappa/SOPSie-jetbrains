package com.sopsie.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.sopsie.detection.SopsDetector
import com.sopsie.execution.SopsOperations

/**
 * Action to encrypt the current file in-place using SOPS.
 * Uses the matching creation rule from .sops.yaml.
 */
class EncryptAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        val project = e.project

        // Enable only for local files that are not already encrypted
        val enabled = project != null &&
                file != null &&
                file.isInLocalFileSystem &&
                !file.isDirectory &&
                !SopsDetector.getInstance().isEncrypted(file)

        e.presentation.isEnabledAndVisible = enabled
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        SopsOperations.encrypt(project, file)
    }
}
