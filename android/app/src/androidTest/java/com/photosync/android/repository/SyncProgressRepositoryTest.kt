package com.photosync.android.repository

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.photosync.android.data.AppDatabase
import com.photosync.android.data.dao.SyncStatusDao
import com.photosync.android.data.entity.SyncStatus
import com.photosync.android.data.entity.SyncStatusEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*

@RunWith(AndroidJUnit4::class)
class SyncProgressRepositoryTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var database: AppDatabase
    private lateinit var syncStatusDao: SyncStatusDao
    private lateinit var repository: SyncProgressRepository

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()

        syncStatusDao = database.syncStatusDao()
        repository = SyncProgressRepository(syncStatusDao)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testByteBasedProgressCalculation() = runBlocking {
        // Seed sync_status with:
        // 2 SYNCED (size 100 each)
        // 1 UPLOADING (size 200, offset 50)
        // 1 PENDING (size 100)
        
        syncStatusDao.insertSyncStatus(SyncStatusEntity("1", "h1", SyncStatus.SYNCED, fileSize = 100))
        syncStatusDao.insertSyncStatus(SyncStatusEntity("2", "h2", SyncStatus.SYNCED, fileSize = 100))
        syncStatusDao.insertSyncStatus(SyncStatusEntity("3", "h3", SyncStatus.UPLOADING, fileSize = 200, lastKnownOffset = 50))
        syncStatusDao.insertSyncStatus(SyncStatusEntity("4", "h4", SyncStatus.PENDING, fileSize = 100))

        val progress = repository.syncProgressFlow.first()

        // totalBytes = 100+100+200+100 = 500
        // uploadedBytes = 100+100+50+0 = 250
        // percent = 50
        
        assertEquals(500L, progress.totalBytes)
        assertEquals(250L, progress.uploadedBytes)
        assertEquals(50, progress.percent)
        assertEquals(4, progress.eligibleCount)
        assertEquals(2, progress.syncedCount)
        assertEquals(1, progress.uploadingCount)
        assertEquals(1, progress.pendingCount)
    }

    @Test
    fun testEmptyDatabase_returnsZero() = runBlocking {
        val progress = repository.syncProgressFlow.first()
        assertEquals(0, progress.percent)
        assertEquals(0, progress.eligibleCount)
    }
}
