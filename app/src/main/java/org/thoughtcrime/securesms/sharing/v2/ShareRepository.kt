package org.thoughtcrime.securesms.sharing.v2

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.OpenableColumns
import androidx.annotation.NonNull
import androidx.annotation.WorkerThread
import androidx.core.content.ContextCompat
import androidx.core.util.toKotlinPair
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.attachments.Attachment
import org.thoughtcrime.securesms.attachments.UriAttachment
import org.thoughtcrime.securesms.conversation.MessageSendType
import org.thoughtcrime.securesms.mediasend.Media
import org.thoughtcrime.securesms.mediasend.MediaSendConstants
import org.thoughtcrime.securesms.mms.MediaConstraints
import org.thoughtcrime.securesms.providers.BlobProvider
import org.thoughtcrime.securesms.util.MediaUtil
import org.thoughtcrime.securesms.util.UriUtil
import org.thoughtcrime.securesms.util.Util
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
    val mimeType = getMimeType(appContext, uri, multiShareExternal.mimeType)

    val stream: InputStream = try {
      appContext.contentResolver.openInputStream(uri)
    } catch (e: SecurityException) {
      Log.w(TAG, "Failed to read stream!", e)
      null
    } ?: return ResolvedShareData.Failure

    val size = getSize(appContext, uri)
    val name = getFileName(appContext, uri)

    val blobUri = BlobProvider.getInstance()
      .forData(stream, size)
      .withMimeType(mimeType)
      .withFileName(name)
      .createForSingleSessionOnDisk(appContext)

    return ResolvedShareData.ExternalUri(
      uri = blobUri,
      mimeType = mimeType,
      isMmsOrSmsSupported = isMmsSupported(appContext, asUriAttachment(blobUri, mimeType, size))
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
      .take(MediaSendConstants.MAX_PUSH)
      .map { (uri, mimeType) ->
        val stream: InputStream = try {
          appContext.contentResolver.openInputStream(uri)
        } catch (e: IOException) {
          Log.w(TAG, "Failed to open: $uri")
          return@map null
        } ?: return ResolvedShareData.Failure

        val size = getSize(appContext, uri)
        val dimens: Pair<Int, Int> = MediaUtil.getDimensions(appContext, mimeType, uri).toKotlinPair()
        val duration = 0L
        val blobUri = BlobProvider.getInstance()
          .forData(stream, size)
          .withMimeType(mimeType)
          .createForSingleSessionOnDisk(appContext)

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
          Optional.empty()
        )
      }.filterNotNull()

    return if (media.isNotEmpty()) {
      val isMmsSupported = media.all { isMmsSupported(appContext, asUriAttachment(it.uri, it.mimeType, it.size)) }

      ResolvedShareData.Media(media, isMmsSupported)
    } else {
      ResolvedShareData.Failure
    }
  }

  companion object {
    private val TAG = Log.tag(ShareRepository::class.java)

    private fun getMimeType(context: Context, uri: Uri, mimeType: String?): String {
      var updatedMimeType = MediaUtil.getMimeType(context, uri)
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

    private fun asUriAttachment(uri: Uri, mimeType: String, size: Long): UriAttachment {
      return UriAttachment(uri, mimeType, -1, size, null, false, false, false, false, null, null, null, null, null)
    }

    private fun isMmsSupported(context: Context, attachment: Attachment): Boolean {
      val canReadPhoneState = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED

      if (!Util.isDefaultSmsProvider(context) || !canReadPhoneState || !Util.isMmsCapable(context)) {
        return false
      }

      val sendType: MessageSendType = MessageSendType.getFirstForTransport(context, true, MessageSendType.TransportType.SMS)
      val mmsConstraints = MediaConstraints.getMmsMediaConstraints(sendType.simSubscriptionId ?: -1)
      return mmsConstraints.isSatisfied(context, attachment) || mmsConstraints.canResize(attachment)
    }
  }
}
