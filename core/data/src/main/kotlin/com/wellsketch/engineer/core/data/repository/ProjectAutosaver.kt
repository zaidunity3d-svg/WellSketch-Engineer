package com.wellsketch.engineer.core.data.repository

import com.wellsketch.engineer.core.model.project.WellProject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * Persists edited projects in submission order on an application-wide scope, so a save
 * is not lost when a screen's ViewModel is cleared and older snapshots can never
 * overwrite newer ones. Bursts of edits are conflated to the latest version per project.
 */
class ProjectAutosaver(
    scope: CoroutineScope,
    private val repository: WellProjectRepository,
    private val onError: (Throwable) -> Unit = {},
) {
    private val queue = Channel<WellProject>(Channel.UNLIMITED)

    init {
        scope.launch {
            for (first in queue) {
                val latest = LinkedHashMap<String, WellProject>()
                latest[first.id] = first
                while (true) {
                    val next = queue.tryReceive().getOrNull() ?: break
                    latest[next.id] = next
                }
                for (project in latest.values) {
                    runCatching { repository.saveProject(project) }.onFailure(onError)
                }
            }
        }
    }

    fun submit(project: WellProject) {
        queue.trySend(project)
    }
}
