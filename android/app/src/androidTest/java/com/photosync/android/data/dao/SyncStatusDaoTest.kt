package com.photosync.android.data.dao

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.photosync.android.data.AppDatabase
import com.photosync.android.data.entity.SyncStatusEntity
import com.photosync.android.data.entity.SyncStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*

@RunWith(AndroidJUnit4::class)
class SyncStatusDaoTest {
    
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()
    
    private lateinit var database: AppDatabase
    private lateinit var syncStatusDao: SyncStatusDao
    
    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        
        syncStatusDao = database.syncStatusDao()
    }
    
    @After
    fun tearDown() {
        database.close()
    }
    
    @Test
    fun insertAndRetrieveSyncStatus() = runBlocking {
        val entity = SyncStatusEntity(
            mediaId = "test123",
            hash = "abc123",
            syncStatus = SyncStatus.PENDING,
            lastUpdated = System.currentTimeMillis(),
            retryCount = 0,
            lastAttemptTimestamp = 0L,
            failureReason = null
        )
        
        syncStatusDao.insertSyncStatus(entity)
        val retrieved = syncStatusDao.getSyncStatus("test123")
        
        assertNotNull(retrieved)
        assertEquals("test123", retrieved?.mediaId)
        assertEquals(SyncStatus.PENDING, retrieved?.syncStatus)
    }
    
    @Test
    fun incrementRetryError_incrementsCountAndSetsReason() = runBlocking {
        val entity = SyncStatusEntity(
            mediaId = "test123",
            hash = "abc123",
            syncStatus = SyncStatus.PENDING,
            lastUpdated = System.currentTimeMillis(),
            retryCount = 0,
            lastAttemptTimestamp = 0L,
            failureReason = null
        )
        
        syncStatusDao.insertSyncStatus(entity)
        val timestamp = System.currentTimeMillis()
        syncStatusDao.incrementRetryError("test123", timestamp, "Network timeout")
        
        val updated = syncStatusDao.getSyncStatus("test123")
        
        assertEquals(1, updated?.retryCount)
        assertEquals("Network timeout", updated?.failureReason)
        assertEquals(SyncStatus.ERROR, updated?.syncStatus)
        assertTrue(updated?.lastAttemptTimestamp ?: 0L > 0L)
    }
    
    @Test
    fun incrementRetryError_multipleIncrements() = runBlocking {
        val entity = SyncStatusEntity(
            mediaId = "test123",
            hash = "abc123",
            syncStatus = SyncStatus.PENDING,
            lastUpdated = System.currentTimeMillis(),
            retryCount = 0,
            lastAttemptTimestamp = 0L,
            failureReason = null
        )
        
        syncStatusDao.insertSyncStatus(entity)
        val timestamp = System.currentTimeMillis()
        syncStatusDao.incrementRetryError("test123", timestamp, "Error 1")
        syncStatusDao.incrementRetryError("test123", timestamp + 1000, "Error 2")
        syncStatusDao.incrementRetryError("test123", timestamp + 2000, "Error 3")
        
        val updated = syncStatusDao.getSyncStatus("test123")
        
        assertEquals(3, updated?.retryCount)
        assertEquals("Error 3", updated?.failureReason)
    }
    
    @Test
    fun resetRetryStatus_resetsAllFields() = runBlocking {
        val entity = SyncStatusEntity(
            mediaId = "test123",
            hash = "abc123",
            syncStatus = SyncStatus.ERROR,
            lastUpdated = System.currentTimeMillis(),
            retryCount = 5,
            lastAttemptTimestamp = System.currentTimeMillis(),
            failureReason = "Some error"
        )
        
        syncStatusDao.insertSyncStatus(entity)
        val timestamp = System.currentTimeMillis()
        syncStatusDao.resetRetryStatus("test123", timestamp, SyncStatus.SYNCED)
        
        val updated = syncStatusDao.getSyncStatus("test123")
        
        assertEquals(0, updated?.retryCount)
        assertEquals(timestamp, updated?.lastAttemptTimestamp)
        assertEquals(SyncStatus.SYNCED, updated?.syncStatus)
    }
    
    @Test
    fun getFailedCount_returnsCorrectCount() = runBlocking {
        // Insert multiple items with different statuses
        syncStatusDao.insertSyncStatus(
            SyncStatusEntity("id1", "hash1", SyncStatus.ERROR, System.currentTimeMillis(), 1, 0L, "Error")
        )
        syncStatusDao.insertSyncStatus(
            SyncStatusEntity("id2", "hash2", SyncStatus.ERROR, System.currentTimeMillis(), 2, 0L, "Error")
        )
        syncStatusDao.insertSyncStatus(
            SyncStatusEntity("id3", "hash3", SyncStatus.SYNCED, System.currentTimeMillis(), 0, 0L, null)
        )
        syncStatusDao.insertSyncStatus(
            SyncStatusEntity("id4", "hash4", SyncStatus.PENDING, System.currentTimeMillis(), 0, 0L, null)
        )
        
        val failedCount = syncStatusDao.getFailedCount().first()
        
        assertEquals(2, failedCount)
    }
    
    @Test
    fun getFailedCount_returnsZeroWhenNoErrors() = runBlocking {
        syncStatusDao.insertSyncStatus(
            SyncStatusEntity("id1", "hash1", SyncStatus.SYNCED, System.currentTimeMillis(), 0, 0L, null)
        )
        syncStatusDao.insertSyncStatus(
            SyncStatusEntity("id2", "hash2", SyncStatus.PENDING, System.currentTimeMillis(), 0, 0L, null)
        )
        
        val failedCount = syncStatusDao.getFailedCount().first()
        
        assertEquals(0, failedCount)
    }
    
    @Test
    fun getPendingCount_returnsCorrectCount() = runBlocking {
        syncStatusDao.insertSyncStatus(
            SyncStatusEntity("id1", "hash1", SyncStatus.PENDING, System.currentTimeMillis(), 0, 0L, null)
        )
        syncStatusDao.insertSyncStatus(
            SyncStatusEntity("id2", "hash2", SyncStatus.PENDING, System.currentTimeMillis(), 0, 0L, null)
        )
        syncStatusDao.insertSyncStatus(
            SyncStatusEntity("id3", "hash3", SyncStatus.SYNCED, System.currentTimeMillis(), 0, 0L, null)
        )
        
        val pendingCount = syncStatusDao.getPendingCount().first()
        
        assertEquals(2, pendingCount)
    }
}
