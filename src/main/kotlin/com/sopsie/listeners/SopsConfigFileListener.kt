package com.sopsie.listeners

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.*
import com.sopsie.config.SopsConfigManager
import com.sopsie.services.SopsConfigDebouncer

/**
 * Watches for .sops.yaml configuration file changes across all projects.
 * Uses BulkFileListener on VFS_CHANGES topic for efficient file system monitoring.
 *
 * The debounce scheduler lives in [SopsConfigDebouncer] (an application
 * service) so it is disposed by the platform on plugin unload as well as
 * on IDE shutdown.
 */
class SopsConfigFileListener : BulkFileListener {

    companion object {
        private val LOG = Logger.getInstance(SopsConfigFileListener::class.java)
        private val CONFIG_NAMES = setOf(".sops.yaml", ".sops.yml")
        private const val DEBOUNCE_MS = 500L
    }

    private val debouncer: SopsConfigDebouncer
        get() = SopsConfigDebouncer.getInstance()

    override fun after(events: List<VFileEvent>) {
        for (event in events) {
            val file = event.file ?: continue

            // Only process .sops.yaml and .sops.yml files
            if (file.name !in CONFIG_NAMES) continue

            when (event) {
                is VFileContentChangeEvent -> handleContentChange(file)
                is VFileCreateEvent -> handleCreate(file)
                is VFileDeleteEvent -> handleDelete(event.path)
                is VFileMoveEvent -> {
                    handleDelete(event.oldPath)
                    handleCreate(file)
                }
                is VFileCopyEvent -> handleCreate(file)
                is VFilePropertyChangeEvent -> {
                    if (event.propertyName == VirtualFile.PROP_NAME) {
                        // File was renamed
                        val oldName = event.oldValue as? String
                        val newName = event.newValue as? String

                        if (oldName in CONFIG_NAMES && newName !in CONFIG_NAMES) {
                            // Config file was renamed to something else
                            val oldPath = file.parent?.path?.let { "$it/$oldName" }
                            if (oldPath != null) handleDelete(oldPath)
                        } else if (oldName !in CONFIG_NAMES && newName in CONFIG_NAMES) {
                            // File was renamed to config file
                            handleCreate(file)
                        } else if (oldName in CONFIG_NAMES && newName in CONFIG_NAMES) {
                            // Renamed from one config name to another (e.g., .sops.yaml -> .sops.yml)
                            val oldPath = file.parent?.path?.let { "$it/$oldName" }
                            if (oldPath != null) handleDelete(oldPath)
                            handleCreate(file)
                        }
                    }
                }
            }
        }
    }

    private fun handleContentChange(file: VirtualFile) {
        debouncer.debounce("change:${file.path}", DEBOUNCE_MS) {
            LOG.debug("SOPSie: Config file changed: ${file.path}")
            forEachRelevantProject(file) { project ->
                SopsConfigManager.getInstance(project).reloadConfig(file)
            }
        }
    }

    private fun handleCreate(file: VirtualFile) {
        debouncer.debounce("create:${file.path}", DEBOUNCE_MS) {
            LOG.debug("SOPSie: Config file created: ${file.path}")
            forEachRelevantProject(file) { project ->
                SopsConfigManager.getInstance(project).loadConfig(file)
            }
        }
    }

    private fun handleDelete(path: String) {
        debouncer.debounce("delete:$path", DEBOUNCE_MS) {
            LOG.debug("SOPSie: Config file deleted: $path")
            forEachProject { project ->
                SopsConfigManager.getInstance(project).removeConfig(path)
            }
        }
    }

    /**
     * Execute action for each open project that contains the file
     */
    private inline fun forEachRelevantProject(file: VirtualFile, action: (Project) -> Unit) {
        for (project in ProjectManager.getInstance().openProjects) {
            if (project.isDisposed) continue
            val baseDir = project.guessProjectDir() ?: continue
            if (file.path.startsWith(baseDir.path)) {
                action(project)
            }
        }
    }

    /**
     * Execute action for each open project
     */
    private inline fun forEachProject(action: (Project) -> Unit) {
        for (project in ProjectManager.getInstance().openProjects) {
            if (!project.isDisposed) {
                action(project)
            }
        }
    }
}
