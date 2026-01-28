/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.camera.hud

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import org.signal.glide.compose.GlideImage
import org.signal.glide.compose.GlideImageScaleType
import kotlinx.coroutines.withContext

/**
 * A button that displays a thumbnail of the most recent image or video from the gallery.
 * Shows a circular thumbnail with a white border that opens the gallery when clicked.
 * 
 * @param modifier Modifier to apply to the button
 * @param onClick Callback when the button is clicked
 */
@Composable
fun GalleryThumbnailButton(
  modifier: Modifier = Modifier,
  onClick: () -> Unit
) {
  val context = LocalContext.current
  var thumbnailUri by remember { mutableStateOf<Uri?>(null) }
  
  // Load the most recent media item
  LaunchedEffect(Unit) {
    thumbnailUri = getLatestMediaUri(context)
  }
  
  Box(
    modifier = modifier
      .size(52.dp)
      .clip(CircleShape)
      .border(2.dp, Color.White, CircleShape)
      .background(Color.Black.copy(alpha = 0.3f), CircleShape)
      .clickable(onClick = onClick),
    contentAlignment = Alignment.Center
  ) {
    if (thumbnailUri != null) {
      GlideImage(
        model = thumbnailUri,
        scaleType = GlideImageScaleType.CENTER_CROP,
        modifier = Modifier
          .size(52.dp)
          .clip(CircleShape)
      )
    } else {
      // Fallback to a simple icon if no media found
      Box(
        modifier = Modifier
          .size(52.dp)
          .background(Color.Gray.copy(alpha = 0.5f), CircleShape)
      )
    }
  }
}

/**
 * Queries MediaStore to get the URI of the most recent image or video.
 * Checks both images and videos and returns whichever is more recent.
 */
private suspend fun getLatestMediaUri(context: Context): Uri? = withContext(Dispatchers.IO) {
  try {
    val imageUri = getLatestImageUri(context)
    val videoUri = getLatestVideoUri(context)
    
    // Compare timestamps if both exist, otherwise return whichever is available
    when {
      imageUri != null && videoUri != null -> {
        val imageTime = getMediaTimestamp(context, imageUri) ?: 0L
        val videoTime = getMediaTimestamp(context, videoUri) ?: 0L
        if (imageTime >= videoTime) imageUri else videoUri
      }
      imageUri != null -> imageUri
      videoUri != null -> videoUri
      else -> null
    }
  } catch (e: SecurityException) {
    // Permission denied - return null
    null
  } catch (e: Exception) {
    // Other error - return null
    null
  }
}

/**
 * Gets the most recent image URI from MediaStore.
 */
private fun getLatestImageUri(context: Context): Uri? {
  val projection = arrayOf(
    MediaStore.Images.Media._ID,
    MediaStore.Images.Media.DATE_ADDED
  )
  
  val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
  
  context.contentResolver.query(
    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
    projection,
    null,
    null,
    sortOrder
  )?.use { cursor ->
    if (cursor.moveToFirst()) {
      val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
      val id = cursor.getLong(idColumn)
      return ContentUris.withAppendedId(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        id
      )
    }
  }
  
  return null
}

/**
 * Gets the most recent video URI from MediaStore.
 */
private fun getLatestVideoUri(context: Context): Uri? {
  val projection = arrayOf(
    MediaStore.Video.Media._ID,
    MediaStore.Video.Media.DATE_ADDED
  )
  
  val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"
  
  context.contentResolver.query(
    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
    projection,
    null,
    null,
    sortOrder
  )?.use { cursor ->
    if (cursor.moveToFirst()) {
      val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
      val id = cursor.getLong(idColumn)
      return ContentUris.withAppendedId(
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
        id
      )
    }
  }
  
  return null
}

/**
 * Gets the timestamp of a media item.
 */
private fun getMediaTimestamp(context: Context, uri: Uri): Long? {
  val projection = arrayOf(MediaStore.MediaColumns.DATE_ADDED)
  
  context.contentResolver.query(
    uri,
    projection,
    null,
    null,
    null
  )?.use { cursor ->
    if (cursor.moveToFirst()) {
      val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
      return cursor.getLong(dateColumn)
    }
  }
  
  return null
}

/**
 * Checks if the app has permission to read media files.
 * For Android 13+ (API 33+), we need READ_MEDIA_IMAGES and READ_MEDIA_VIDEO.
 * For older versions, we need READ_EXTERNAL_STORAGE.
 */
fun hasMediaPermissions(context: Context): Boolean {
  return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    // Android 13+
    context.checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) ==
      PackageManager.PERMISSION_GRANTED ||
    context.checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO) ==
      PackageManager.PERMISSION_GRANTED
  } else {
    // Older Android versions
    context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) ==
      PackageManager.PERMISSION_GRANTED
  }
}

/**
 * Returns the list of permissions needed to read media files based on the Android version.
 */
fun getMediaPermissions(): Array<String> {
  return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    arrayOf(
      Manifest.permission.READ_MEDIA_IMAGES,
      Manifest.permission.READ_MEDIA_VIDEO
    )
  } else {
    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
  }
}
