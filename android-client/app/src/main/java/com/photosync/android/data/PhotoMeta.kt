package com.photosync.android.data

import android.net.Uri

data class PhotoMeta(
    val uri: Uri,
    val name: String,
    val size: Long,
    val modified: Long,
    var hash: String
)
