package io.ezz.launcher.core.runtime.process

import io.ezz.launcher.core.storage.path.DefaultPathProvider
import kotlinx.coroutines.runBlocking
import okio.Path.Companion.toPath
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ProcessSessionTrackerTest {

    private lateinit var tempDir: File
    private lateinit var pathProvider: DefaultPathProvider
    private lateinit var tracker: ProcessSessionTracker

    @BeforeTest
    fun setUp() {
        tempDir = File.createTempFile("ezz_proc_test", "").apply {
            delete()
            mkdirs()
        }
        pathProvider = DefaultPathProvider(tempDir.absolutePath.toPath())
        pathProvider.initializeDirectories()
        tracker = ProcessSessionTracker(pathProvider)
    }

    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun testRegisterAndUnregisterSessions() = runBlocking {
        val currentPid = ProcessHandle.current().pid()
        val startTime = System.currentTimeMillis() - 5000L

        tracker.registerSession("instance-1", currentPid, startTime)
        tracker.registerSession("instance-2", 999999999L, startTime - 10000L)

        val s1 = tracker.getActiveSession("instance-1")
        assertNotNull(s1)
        assertEquals("instance-1", s1.instanceId)
        assertEquals(currentPid, s1.processId)
        assertEquals(startTime, s1.startedAt)

        val all = tracker.getAllActiveSessions()
        assertEquals(2, all.size)

        // Unregister instance-2
        tracker.unregisterSession("instance-2")
        assertNull(tracker.getActiveSession("instance-2"))
        assertEquals(1, tracker.getAllActiveSessions().size)
    }

    @Test
    fun testRecoverActiveSessionsFiltersDeadPids() = runBlocking {
        val currentPid = ProcessHandle.current().pid()
        val now = System.currentTimeMillis()

        tracker.registerSession("alive-instance", currentPid, now)
        tracker.registerSession("dead-instance", 999999999L, now) // fake dead pid

        // Create new tracker instance to simulate launcher restart
        val freshTracker = ProcessSessionTracker(pathProvider)
        val recovered = freshTracker.recoverActiveSessions()

        // Only alive PID should be recovered
        assertEquals(1, recovered.size)
        assertEquals("alive-instance", recovered[0].instanceId)
        assertEquals(currentPid, recovered[0].processId)
    }
}
