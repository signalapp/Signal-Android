package org.thoughtcrime.securesms.mediasend

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.signal.core.models.media.Media
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.mms.PartAuthority
import org.thoughtcrime.securesms.util.MediaUtil
import java.io.IOException
import java.util.Optional

class MediaSendDocumentViewModel(private val media: Media) : ViewModel() {

  private val internalDocumentInfo = MutableLiveData<Optional<DocumentInfo>>()
  val documentInfo: LiveData<Optional<DocumentInfo>> = internalDocumentInfo

  fun loadDocumentInfo() {
    viewModelScope.launch {
      internalDocumentInfo.value = withContext(Dispatchers.IO) {
        Optional.ofNullable(computeDocumentInfo())
      }
    }
  }

  private fun computeDocumentInfo(): DocumentInfo? {
    val context = AppDependencies.application
    val fileInfo: Pair<String?, Long> = getFileInfo(context) ?: return null

    val extensionText: String = MediaUtil.getFileType(context, Optional.ofNullable(fileInfo.first), media.uri).orElse("")

    return DocumentInfo(fileInfo.first, fileInfo.second, extensionText)
  }

  private fun getFileInfo(context: Context): Pair<String?, Long>? {
    val uri = media.uri
    return try {
      if (PartAuthority.isLocalUri(uri)) {
        getManuallyCalculatedFileInfo(context, uri)
      } else {
        getContentResolverFileInfo(context, uri) ?: getManuallyCalculatedFileInfo(context, uri)
      }
    } catch (e: IOException) {
      Log.w(TAG, e)
      null
    }
  }

  @Throws(IOException::class)
  private fun getManuallyCalculatedFileInfo(context: Context, uri: Uri): Pair<String?, Long> {
    var fileName: String? = null
    var fileSize: Long? = null

    if (PartAuthority.isLocalUri(uri)) {
      fileSize = PartAuthority.getAttachmentSize(context, uri)
      fileName = PartAuthority.getAttachmentFileName(context, uri)
    }
    if (fileSize == null) {
      fileSize = MediaUtil.getMediaSize(context, uri)
    }

    return Pair(fileName, fileSize)
  }

  private fun getContentResolverFileInfo(context: Context, uri: Uri): Pair<String, Long>? {
    var cursor: Cursor? = null

    try {
      cursor = context.contentResolver.query(uri, null, null, null, null)

      if (cursor != null && cursor.moveToFirst()) {
        val fileName = cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
        val fileSize = cursor.getLong(cursor.getColumnIndexOrThrow(OpenableColumns.SIZE))

        return Pair(fileName, fileSize)
      }
    } finally {
      cursor?.close()
    }

    return null
  }

  data class DocumentInfo(val fileName: String?, val fileSize: Long, val extension: String)

  class Factory(private val media: Media) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return requireNotNull(modelClass.cast(MediaSendDocumentViewModel(media)))
    }
  }

  companion object {
    private val TAG = Log.tag(MediaSendDocumentViewModel::class.java)
  }
}
