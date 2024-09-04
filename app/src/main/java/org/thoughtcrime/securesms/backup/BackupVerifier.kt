package org.thoughtcrime.securesms.backup

import org.greenrobot.eventbus.EventBus
import org.signal.core.util.logging.Log
import org.signal.core.util.stream.NullOutputStream
import org.thoughtcrime.securesms.backup.proto.Attachment
import org.thoughtcrime.securesms.backup.proto.Avatar
import org.thoughtcrime.securesms.backup.proto.BackupFrame
import org.thoughtcrime.securesms.backup.proto.Sticker
import java.io.IOException
import java.io.InputStream

/**
 * Given a backup file, run over it and verify it will decrypt properly when attempting to import it.
 */
object BackupVerifier {

  private val TAG = Log.tag(BackupVerifier::class.java)

  @JvmStatic
  @Throws(IOException::class, FullBackupExporter.BackupCanceledException::class)
  fun verifyFile(cipherStream: InputStream, passphrase: String, expectedCount: Long, cancellationSignal: FullBackupExporter.BackupCancellationSignal): Boolean {
    val inputStream = BackupRecordInputStream(cipherStream, passphrase)

    var count = 0L
    var frame: BackupFrame = inputStream.readFrame()

    cipherStream.use {
      while (frame.end != true && !cancellationSignal.isCanceled) {
        val verified = when {
          frame.attachment != null -> verifyAttachment(frame.attachment!!, inputStream)
          frame.sticker != null -> verifySticker(frame.sticker!!, inputStream)
          frame.avatar != null -> verifyAvatar(frame.avatar!!, inputStream)
          else -> true
        }

        if (!verified) {
          return false
        }

        EventBus.getDefault().post(BackupEvent(BackupEvent.Type.PROGRESS_VERIFYING, ++count, expectedCount))

        frame = inputStream.readFrame()
      }
      if (frame.end == true) {
        count++
      }
    }

    if (cancellationSignal.isCanceled) {
      throw FullBackupExporter.BackupCanceledException()
    }

    if (count != expectedCount) {
      Log.e(TAG, "Incorrect number of frames expected $expectedCount but only $count")
      return false
    }

    return true
  }

  private fun verifyAttachment(attachment: Attachment, inputStream: BackupRecordInputStream): Boolean {
    try {
      inputStream.readAttachmentTo(NullOutputStream, attachment.length ?: 0)
    } catch (e: IOException) {
      Log.w(TAG, "Bad attachment id: ${attachment.attachmentId} len: ${attachment.length}", e)
      return false
    }

    return true
  }

  private fun verifySticker(sticker: Sticker, inputStream: BackupRecordInputStream): Boolean {
    try {
      inputStream.readAttachmentTo(NullOutputStream, sticker.length ?: 0)
    } catch (e: IOException) {
      Log.w(TAG, "Bad sticker id: ${sticker.rowId} len: ${sticker.length}", e)
      return false
    }
    return true
  }

  private fun verifyAvatar(avatar: Avatar, inputStream: BackupRecordInputStream): Boolean {
    try {
      inputStream.readAttachmentTo(NullOutputStream, avatar.length ?: 0)
    } catch (e: IOException) {
      Log.w(TAG, "Bad avatar id: ${avatar.recipientId} len: ${avatar.length}", e)
      return false
    }
    return true
  }
}
