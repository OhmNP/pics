package com.photosync.android.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.security.MessageDigest

data class PhotoMeta(val uri: Uri, val name: String, val size: Long, val modified: Long, val hash: String)

class MediaScanner(private val context: Context, private val db: LocalDB) {

    suspend fun scanIncremental(): Sequence<PhotoMeta> = withContext(Dispatchers.IO) {
        val lastSync = db.getLastSync()
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        else MediaStore.Images.Media.EXTERNAL_CONTENT_URI

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATE_MODIFIED
        )
        val selection = "${MediaStore.Images.Media.DATE_MODIFIED} > ?"
        val args = arrayOf((lastSync / 1000).toString()) // seconds

        val cursor = context.contentResolver.query(collection, projection, selection, args, null)
        sequence {
            cursor?.use {
                val idCol = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameCol = it.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val sizeCol = it.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                val modCol = it.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
                while (it.moveToNext()) {
                    val id = it.getLong(idCol)
                    val name = it.getString(nameCol)
                    val size = it.getLong(sizeCol)
                    val modified = it.getLong(modCol)
                    val uri = ContentUris.withAppendedId(collection, id)
                    val hash = calculateSha256(uri)
                    yield(PhotoMeta(uri, name, size, modified, hash))
                    Log.e("PhotoScan", "scanIncremental: $name, $size", )
                }
            }
        }
    }

    private fun calculateSha256(uri: Uri): String {
        val md = MessageDigest.getInstance("SHA-256")
        context.contentResolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(8192)
            var read: Int
            while (input.read(buffer).also { read = it } > 0) {
                md.update(buffer, 0, read)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
