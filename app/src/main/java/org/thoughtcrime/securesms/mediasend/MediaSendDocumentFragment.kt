package org.thoughtcrime.securesms.mediasend

import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import org.signal.core.util.getParcelableCompat
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.mms.PartAuthority
import org.thoughtcrime.securesms.util.MediaUtil
import org.thoughtcrime.securesms.util.Util
import java.io.IOException
import java.util.Optional

/**
 * Fragment to show full screen document attachments
 */
class MediaSendDocumentFragment : Fragment(R.layout.mediasend_document_fragment), MediaSendPageFragment {

  companion object {
    private val TAG = Log.tag(MediaSendDocumentFragment::class.java)

    private const val KEY_MEDIA = "media"

    fun newInstance(media: Media): MediaSendDocumentFragment {
      val args = Bundle()
      args.putParcelable(KEY_MEDIA, media)

      val fragment = MediaSendDocumentFragment()
      fragment.arguments = args
      fragment.uri = media.uri
      return fragment
    }
  }

  private lateinit var uri: Uri
  private lateinit var media: Media

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    val name: TextView = view.findViewById(R.id.name)
    val size: TextView = view.findViewById(R.id.size)
    val extension: TextView = view.findViewById(R.id.extension)

    this.media = requireNotNull(requireArguments().getParcelableCompat(KEY_MEDIA, Media::class.java))

    val fileInfo: Pair<String?, Long>? = getFileInfo()
    if (fileInfo != null) {
      media.setFileName(fileInfo.first)
      name.text = fileInfo.first ?: getString(R.string.DocumentView_unnamed_file)
      size.text = Util.getPrettyFileSize(fileInfo.second)

      val extensionText: String = MediaUtil.getFileType(requireContext(), Optional.ofNullable(fileInfo.first), media.uri).orElse("")
      if (extensionText.length <= 3) {
        extension.text = extensionText
        extension.setTextAppearance(requireContext(), R.style.Signal_Text_BodySmall)
      } else if (extensionText.length == 4) {
        extension.text = extensionText
        extension.setTextAppearance(requireContext(), R.style.Signal_Text_Caption)
      }
    } else {
      Toast.makeText(requireContext(), R.string.ConversationActivity_sorry_there_was_an_error_setting_your_attachment, Toast.LENGTH_SHORT).show()
      requireActivity().finishAfterTransition()
    }
  }

  override fun getUri(): Uri {
    return uri
  }

  override fun setUri(uri: Uri) {
    this.uri = uri
  }

  override fun saveState(): Any = Unit

  override fun restoreState(state: Any) = Unit

  override fun notifyHidden() = Unit

  private fun getFileInfo(): Pair<String?, Long>? {
    val fileInfo: Pair<String?, Long>
    try {
      if (PartAuthority.isLocalUri(uri)) {
        fileInfo = getManuallyCalculatedFileInfo(uri)
      } else {
        val result = getContentResolverFileInfo(uri)
        fileInfo = if ((result == null)) getManuallyCalculatedFileInfo(uri) else result
      }
    } catch (e: IOException) {
      Log.w(TAG, e)
      return null
    }

    return fileInfo
  }

  @Throws(IOException::class)
  private fun getManuallyCalculatedFileInfo(uri: Uri): Pair<String?, Long> {
    var fileName: String? = null
    var fileSize: Long? = null

    if (PartAuthority.isLocalUri(uri)) {
      fileSize = PartAuthority.getAttachmentSize(requireContext(), uri)
      fileName = PartAuthority.getAttachmentFileName(requireContext(), uri)
    }
    if (fileSize == null) {
      fileSize = MediaUtil.getMediaSize(context, uri)
    }

    return Pair(fileName, fileSize)
  }

  private fun getContentResolverFileInfo(uri: Uri): Pair<String, Long>? {
    var cursor: Cursor? = null

    try {
      cursor = requireContext().contentResolver.query(uri, null, null, null, null)

      if (cursor != null && cursor.moveToFirst()) {
        val fileName = cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
        val fileSize = cursor.getLong(cursor.getColumnIndexOrThrow(OpenableColumns.SIZE))
        media.setFileName(fileName)

        return Pair(fileName, fileSize)
      }
    } finally {
      cursor?.close()
    }

    return null
  }
}
