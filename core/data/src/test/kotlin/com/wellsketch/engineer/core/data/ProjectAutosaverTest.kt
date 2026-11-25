package com.wellsketch.engineer.core.data

import com.wellsketch.engineer.core.data.demo.SyntheticDemoProjects
import com.wellsketch.engineer.core.data.repository.InMemoryWellProjectRepository
import com.wellsketch.engineer.core.data.repository.ProjectAutosaver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class ProjectAutosaverTest {

    @Test
    fun latestSubmissionPerProjectWins() = runTest {
        val repo = InMemoryWellProjectRepository()
        val saverScope = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())
        val saver = ProjectAutosaver(saverScope, repo)
        val a = SyntheticDemoProjects.vertical()
        val b = SyntheticDemoProjects.jShaped()
        saver.submit(a.copy(name = "a1"))
        saver.submit(b.copy(name = "b1"))
        saver.submit(a.copy(name = "a2"))
        advanceUntilIdle()
        assertEquals("a2", repo.getProject(a.id)?.name)
        assertEquals("b1", repo.getProject(b.id)?.name)
        saverScope.cancel()
    }
}
