package org.thoughtcrime.securesms.sharing.v2

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.annotation.NonNull
import androidx.annotation.WorkerThread
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import org.signal.core.models.media.Media
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.util.MediaUtil
import org.thoughtcrime.securesms.util.RemoteConfig
import org.thoughtcrime.securesms.util.UriUtil
import java.io.IOException
import java.io.InputStream

class ShareRepository(context: Context) {

  private val appContext = context.applicationContext

  fun resolve(unresolvedShareData: UnresolvedShareData): Single<out ResolvedShareData> {
    return when (unresolvedShareData) {
      is UnresolvedShareData.ExternalMultiShare -> Single.fromCallable { resolve(unresolvedShareData) }
      is UnresolvedShareData.ExternalSingleShare -> Single.fromCallable { resolve(unresolvedShareData) }
      is UnresolvedShareData.ExternalPrimitiveShare -> Single.just(ResolvedShareData.Primitive(unresolvedShareData.text))
    }.subscribeOn(Schedulers.io())
  }

  @NonNull
  @WorkerThread
  private fun resolve(multiShareExternal: UnresolvedShareData.ExternalSingleShare): ResolvedShareData {
    if (!multiShareExternal.isInternalShare && !UriUtil.isValidExternalUri(appContext, multiShareExternal.uri)) {
      return ResolvedShareData.Failure(ShareError.UNKNOWN)
    }

    val uri = multiShareExternal.uri
    val size = getSize(appContext, uri)
    val name = getFileName(appContext, uri)
    val mimeType = getMimeType(appContext, uri, multiShareExternal.mimeType, name?.substringAfterLast('.', "")).mimeType

    val stream: InputStream = try {
      appContext.contentResolver.openInputStream(uri)
    } catch (e: SecurityException) {
      Log.w(TAG, "Failed to read stream: $uri", e)
      return ResolvedShareData.Failure(ShareError.ACCESS_DENIED)
    } catch (e: IOException) {
      Log.w(TAG, "Failed to read stream: $uri", e)
      return ResolvedShareData.Failure(ShareError.UNKNOWN)
    } ?: return ResolvedShareData.Failure(ShareError.UNKNOWN)

    val blobUri: Uri = try {
      AppDependencies.blobs
        .forData(stream, size)
        .withMimeType(mimeType)
        .withFileName(name)
        .createForSingleSessionOnDisk(appContext)
    } catch (e: IOException) {
      Log.e(TAG, "Failed to get blob uri")
      return ResolvedShareData.Failure(ShareError.UNKNOWN)
    }

    return ResolvedShareData.ExternalUri(
      uri = blobUri,
      mimeType = mimeType,
      text = multiShareExternal.text
    )
  }

  @NonNull
  @WorkerThread
  private fun resolve(externalMultiShare: UnresolvedShareData.ExternalMultiShare): ResolvedShareData {
    val mimeTypeResolutions: List<Pair<Uri, MimeTypeResolution>> = externalMultiShare.uris
      .filter { externalMultiShare.isInternalShare || UriUtil.isValidExternalUri(appContext, it) }
      .map { uri -> uri to getMimeType(appContext, uri, null) }

    // If any URI's provider denied the metadata query, the share cannot be completed: fail loudly
    // rather than silently dropping the user's selection.
    if (mimeTypeResolutions.any { it.second.accessDenied }) {
      return ResolvedShareData.Failure(ShareError.ACCESS_DENIED)
    }

    val mimeTypes: Map<Uri, String> = mimeTypeResolutions
      .filter {
        MediaUtil.isImageType(it.second.mimeType) || MediaUtil.isVideoType(it.second.mimeType)
      }
      .take(RemoteConfig.maxAttachmentCount)
      .associate { it.first to it.second.mimeType }

    if (mimeTypes.isEmpty()) {
      return ResolvedShareData.Failure(ShareError.UNKNOWN)
    }

    val mediaResults: List<MediaResult> = mimeTypes.toList()
      .map { (uri, mimeType) -> resolveMedia(uri, mimeType) }

    // Same as above: if URI access was denied for any item, fail the whole share so the user
    // learns why instead of silently sharing a subset of their selection.
    if (mediaResults.any { it is MediaResult.AccessDenied }) {
      return ResolvedShareData.Failure(ShareError.ACCESS_DENIED)
    }

    val media: List<Media> = mediaResults.filterIsInstance<MediaResult.Success>().map { it.media }

    return if (media.isNotEmpty()) {
      ResolvedShareData.Media(media, externalMultiShare.text)
    } else {
      ResolvedShareData.Failure(ShareError.UNKNOWN)
    }
  }

  @WorkerThread
  private fun resolveMedia(uri: Uri, mimeType: String): MediaResult {
    val stream: InputStream = try {
      appContext.contentResolver.openInputStream(uri)
    } catch (e: SecurityException) {
      Log.w(TAG, "Failed to open: $uri", e)
      return MediaResult.AccessDenied
    } catch (e: IOException) {
      Log.w(TAG, "Failed to open: $uri", e)
      return MediaResult.Unavailable
    } ?: return MediaResult.Unavailable

    val size = getSize(appContext, uri)
    val dimens: Pair<Int, Int> = MediaUtil.getDimensions(appContext, mimeType, uri)
    val duration = 0L
    val blobUri = try {
      AppDependencies.blobs
        .forData(stream, size)
        .withMimeType(mimeType)
        .createForSingleSessionOnDisk(appContext)
    } catch (e: IOException) {
      Log.w(TAG, "Failed create blob uri")
      return MediaResult.Unavailable
    }

    return MediaResult.Success(
      Media(
        uri = blobUri,
        contentType = mimeType,
        date = System.currentTimeMillis(),
        width = dimens.first,
        height = dimens.second,
        size = size,
        duration = duration,
        isBorderless = false,
        isVideoGif = false,
        bucketId = Media.ALL_MEDIA_BUCKET_ID,
        caption = null,
        transformProperties = null,
        fileName = null
      )
    )
  }

  companion object {
    private val TAG = Log.tag(ShareRepository::class.java)

    /**
     * Result of a mime type lookup. [accessDenied] is set when the provider rejected the query
     * with a [SecurityException], which indicates the sending app did not grant URI access.
     */
    private data class MimeTypeResolution(val mimeType: String, val accessDenied: Boolean)

    private fun getMimeType(context: Context, uri: Uri, mimeType: String?, fileExtension: String? = null): MimeTypeResolution {
      var updatedMimeType: String? = null
      var accessDenied = false
      try {
        updatedMimeType = MediaUtil.getMimeType(context, uri, fileExtension)
      } catch (e: SecurityException) {
        Log.w(TAG, "Failed to query mime type: $uri", e)
        accessDenied = true
      }
      if (updatedMimeType == null) {
        updatedMimeType = MediaUtil.getCorrectedMimeType(mimeType)
      }
      return MimeTypeResolution(updatedMimeType ?: MediaUtil.UNKNOWN, accessDenied)
    }

    private fun getSize(context: Context, uri: Uri): Long {
      var size: Long = 0

      try {
        context.contentResolver.query(uri, null, null, null, null).use { cursor ->
          if (cursor != null && cursor.moveToFirst() && cursor.getColumnIndex(OpenableColumns.SIZE) >= 0) {
            size = cursor.getLong(cursor.getColumnIndexOrThrow(OpenableColumns.SIZE))
          }
        }
      } catch (e: SecurityException) {
        Log.w(TAG, "Failed to query size: $uri", e)
        return 0
      }

      if (size <= 0) {
        try {
          size = MediaUtil.getMediaSize(context, uri)
        } catch (e: IOException) {
          Log.w(TAG, "Failed to read media size: $uri", e)
          return 0
        }
      }

      return size
    }

    private fun getFileName(context: Context, uri: Uri): String? {
      if (uri.scheme.equals("file", ignoreCase = true)) {
        return uri.lastPathSegment
      }

      try {
        context.contentResolver.query(uri, null, null, null, null).use { cursor ->
          if (cursor != null && cursor.moveToFirst() && cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME) >= 0) {
            return cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
          }
        }
      } catch (e: SecurityException) {
        Log.w(TAG, "Failed to query file name: $uri", e)
      }

      return null
    }
  }

  /**
   * Outcome of resolving a single URI into a shareable [Media] within a multi-share.
   */
  private sealed class MediaResult {
    data class Success(val media: Media) : MediaResult()
    object AccessDenied : MediaResult()
    object Unavailable : MediaResult()
  }
}
