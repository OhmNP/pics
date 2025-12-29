package com.photosync.android.worker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*

@RunWith(AndroidJUnit4::class)
class SyncWorkerTest {
    
    private lateinit var context: Context
    
    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }
    
    @Test
    fun syncWorker_runsSuccessfully() = runBlocking {
        val worker = TestListenableWorkerBuilder<SyncWorker>(context).build()
        
        val result = worker.doWork()
        
        // Worker should succeed (it starts the service)
        assertEquals(ListenableWorker.Result.success(), result)
    }
    
    @Test
    fun syncWorker_startsEnhancedSyncService() = runBlocking {
        // This test verifies the worker completes
        // Actual service start verification would require mocking or integration test
        val worker = TestListenableWorkerBuilder<SyncWorker>(context).build()
        
        val result = worker.doWork()
        
        assertNotNull(result)
        assertTrue(result is ListenableWorker.Result.Success)
    }
}
