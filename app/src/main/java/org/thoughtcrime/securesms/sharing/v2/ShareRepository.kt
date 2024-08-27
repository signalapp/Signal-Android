package org.thoughtcrime.securesms.sharing.v2

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.annotation.NonNull
import androidx.annotation.WorkerThread
import androidx.core.util.toKotlinPair
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.mediasend.Media
import org.thoughtcrime.securesms.providers.BlobProvider
import org.thoughtcrime.securesms.util.MediaUtil
import org.thoughtcrime.securesms.util.RemoteConfig
import org.thoughtcrime.securesms.util.UriUtil
import java.io.IOException
import java.io.InputStream
import java.util.Optional

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
  @Throws(IOException::class)
  private fun resolve(multiShareExternal: UnresolvedShareData.ExternalSingleShare): ResolvedShareData {
    if (!UriUtil.isValidExternalUri(appContext, multiShareExternal.uri)) {
      return ResolvedShareData.Failure
    }

    val uri = multiShareExternal.uri
    val size = getSize(appContext, uri)
    val name = getFileName(appContext, uri)
    val mimeType = getMimeType(appContext, uri, multiShareExternal.mimeType, name?.substringAfterLast('.', ""))

    val stream: InputStream = try {
      appContext.contentResolver.openInputStream(uri)
    } catch (e: SecurityException) {
      Log.w(TAG, "Failed to read stream!", e)
      null
    } ?: return ResolvedShareData.Failure

    val blobUri: Uri = try {
      BlobProvider.getInstance()
        .forData(stream, size)
        .withMimeType(mimeType)
        .withFileName(name)
        .createForSingleSessionOnDisk(appContext)
    } catch (e: IOException) {
      Log.e(TAG, "Failed to get blob uri")
      return ResolvedShareData.Failure
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
    val mimeTypes: Map<Uri, String> = externalMultiShare.uris
      .associateWith { uri -> getMimeType(appContext, uri, null) }
      .filterValues {
        MediaUtil.isImageType(it) || MediaUtil.isVideoType(it)
      }

    if (mimeTypes.isEmpty()) {
      return ResolvedShareData.Failure
    }

    val media: List<Media> = mimeTypes.toList()
      .take(RemoteConfig.maxAttachmentCount)
      .map { (uri, mimeType) ->
        val stream: InputStream = try {
          appContext.contentResolver.openInputStream(uri)
        } catch (e: IOException) {
          Log.w(TAG, "Failed to open: $uri")
          null
        } ?: return@map null

        val size = getSize(appContext, uri)
        val dimens: Pair<Int, Int> = MediaUtil.getDimensions(appContext, mimeType, uri).toKotlinPair()
        val duration = 0L
        val blobUri = try {
          BlobProvider.getInstance()
            .forData(stream, size)
            .withMimeType(mimeType)
            .createForSingleSessionOnDisk(appContext)
        } catch (e: IOException) {
          Log.w(TAG, "Failed create blob uri")
          return@map null
        }

        Media(
          blobUri,
          mimeType,
          System.currentTimeMillis(),
          dimens.first,
          dimens.second,
          size,
          duration,
          false,
          false,
          Optional.of(Media.ALL_MEDIA_BUCKET_ID),
          Optional.empty(),
          Optional.empty(),
          Optional.empty()
        )
      }.filterNotNull()

    return if (media.isNotEmpty()) {
      ResolvedShareData.Media(media)
    } else {
      ResolvedShareData.Failure
    }
  }

  companion object {
    private val TAG = Log.tag(ShareRepository::class.java)

    private fun getMimeType(context: Context, uri: Uri, mimeType: String?, fileExtension: String? = null): String {
      var updatedMimeType = MediaUtil.getMimeType(context, uri, fileExtension)
      if (updatedMimeType == null) {
        updatedMimeType = MediaUtil.getCorrectedMimeType(mimeType)
      }
      return updatedMimeType ?: MediaUtil.UNKNOWN
    }

    @Throws(IOException::class)
    private fun getSize(context: Context, uri: Uri): Long {
      var size: Long = 0

      context.contentResolver.query(uri, null, null, null, null).use { cursor ->
        if (cursor != null && cursor.moveToFirst() && cursor.getColumnIndex(OpenableColumns.SIZE) >= 0) {
          size = cursor.getLong(cursor.getColumnIndexOrThrow(OpenableColumns.SIZE))
        }
      }

      if (size <= 0) {
        size = MediaUtil.getMediaSize(context, uri)
      }

      return size
    }

    private fun getFileName(context: Context, uri: Uri): String? {
      if (uri.scheme.equals("file", ignoreCase = true)) {
        return uri.lastPathSegment
      }

      context.contentResolver.query(uri, null, null, null, null).use { cursor ->
        if (cursor != null && cursor.moveToFirst() && cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME) >= 0) {
          return cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
        }
      }
      return null
    }
  }
}
