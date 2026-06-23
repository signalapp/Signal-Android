package org.thoughtcrime.securesms.mediasend

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import org.signal.core.models.media.Media
import org.signal.core.util.bytes
import org.signal.core.util.getParcelableCompat
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.mediasend.MediaSendDocumentViewModel.DocumentInfo
import org.signal.core.ui.R as CoreUiR

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

  private val viewModel: MediaSendDocumentViewModel by viewModels {
    MediaSendDocumentViewModel.Factory(media)
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    val name: TextView = view.findViewById(R.id.name)
    val size: TextView = view.findViewById(R.id.size)
    val extension: TextView = view.findViewById(R.id.extension)

    this.media = requireNotNull(requireArguments().getParcelableCompat(KEY_MEDIA, Media::class.java))

    viewModel.documentInfo.observe(viewLifecycleOwner) { documentInfoOptional ->
      val documentInfo: DocumentInfo? = documentInfoOptional.orElse(null)
      if (documentInfo != null) {
        media.fileName = documentInfo.fileName

        name.text = documentInfo.fileName ?: getString(R.string.DocumentView_unnamed_file)
        size.text = documentInfo.fileSize.bytes.toUnitString()

        if (documentInfo.extension.length <= 3) {
          extension.text = documentInfo.extension
          extension.setTextAppearance(requireContext(), CoreUiR.style.Signal_Text_BodySmall)
        } else if (documentInfo.extension.length == 4) {
          extension.text = documentInfo.extension
          extension.setTextAppearance(requireContext(), CoreUiR.style.Signal_Text_Caption)
        }
      } else {
        Toast.makeText(requireContext(), R.string.ConversationActivity_sorry_there_was_an_error_setting_your_attachment, Toast.LENGTH_SHORT).show()
        requireActivity().finishAfterTransition()
      }
    }

    viewModel.loadDocumentInfo()
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
}
