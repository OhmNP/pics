package com.photosync.android.data.dao

import androidx.room.*
import com.photosync.android.data.entity.ServerConfigEntity

@Dao
interface ServerConfigDao {
    
    @Query("SELECT * FROM server_config WHERE id = 1")
    suspend fun getServerConfig(): ServerConfigEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertServerConfig(config: ServerConfigEntity)
    
    @Update
    suspend fun updateServerConfig(config: ServerConfigEntity)
    
    @Query("DELETE FROM server_config")
    suspend fun clearServerConfig()
}
