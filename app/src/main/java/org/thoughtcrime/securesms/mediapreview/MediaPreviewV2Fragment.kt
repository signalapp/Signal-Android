package org.thoughtcrime.securesms.mediapreview

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.animation.DepthPageTransformer
import org.thoughtcrime.securesms.components.ViewBinderDelegate
import org.thoughtcrime.securesms.database.MediaDatabase
import org.thoughtcrime.securesms.databinding.FragmentMediaPreviewV2Binding
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.DateUtils
import org.thoughtcrime.securesms.util.FullscreenHelper
import org.thoughtcrime.securesms.util.LifecycleDisposable
import java.util.Locale

class MediaPreviewV2Fragment : Fragment(R.layout.fragment_media_preview_v2), MediaPreviewFragment.Events {
  private val TAG = Log.tag(MediaPreviewV2Fragment::class.java)

  private val lifecycleDisposable = LifecycleDisposable()
  private val binding by ViewBinderDelegate(FragmentMediaPreviewV2Binding::bind)
  private val viewModel: MediaPreviewV2ViewModel by viewModels()

  private lateinit var fullscreenHelper: FullscreenHelper

  override fun onAttach(context: Context) {
    super.onAttach(context)
    fullscreenHelper = FullscreenHelper(requireActivity())
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    initializeViewModel()
    binding.mediaPager.offscreenPageLimit = 1
    binding.mediaPager.setPageTransformer(DepthPageTransformer())
    val adapter = MediaPreviewV2Adapter(this)
    binding.mediaPager.adapter = adapter
    binding.mediaPager.registerOnPageChangeCallback(object : OnPageChangeCallback() {
      override fun onPageSelected(position: Int) {
        super.onPageSelected(position)
        viewModel.setCurrentPage(position)
      }
    })
    initializeFullScreenUi()
    lifecycleDisposable += viewModel.state.distinctUntilChanged().observeOn(AndroidSchedulers.mainThread()).subscribe {
      bindCurrentState(it)
    }
  }

  private fun initializeFullScreenUi() {
    fullscreenHelper.configureToolbarLayout(binding.toolbarCutoutSpacer, binding.toolbar)
    fullscreenHelper.hideSystemUI()
  }

  private fun initializeViewModel() {
    val args = MediaIntentFactory.requireArguments(requireArguments())
    viewModel.setShowThread(args.showThread)
    val sorting = MediaDatabase.Sorting.values()[args.sorting]
    viewModel.fetchAttachments(args.initialMediaUri, args.threadId, sorting)
  }

  private fun bindCurrentState(currentState: MediaPreviewV2State) {
    when (currentState.loadState) {
      MediaPreviewV2State.LoadState.READY -> bindReadyState(currentState)
      // INIT, else -> no-op
    }
  }

  private fun bindReadyState(currentState: MediaPreviewV2State) {
    (binding.mediaPager.adapter as MediaPreviewV2Adapter).updateBackingItems(currentState.mediaRecords.mapNotNull { it.attachment })
    val currentItem: MediaDatabase.MediaRecord = currentState.mediaRecords[currentState.position]
    binding.toolbar.title = getTitleText(currentItem, currentState.showThread)
    binding.toolbar.subtitle = getSubTitleText(currentItem)
  }

  private fun getTitleText(mediaRecord: MediaDatabase.MediaRecord, showThread: Boolean): String {
    val recipient: Recipient = Recipient.live(mediaRecord.recipientId).get()
    val defaultFromString: String = if (mediaRecord.isOutgoing) {
      getString(R.string.MediaPreviewActivity_you)
    } else {
      recipient.getDisplayName(requireContext())
    }
    if (!showThread) {
      return defaultFromString
    }

    val threadRecipient = Recipient.live(mediaRecord.threadRecipientId).get()
    return if (mediaRecord.isOutgoing) {
      if (threadRecipient.isSelf) {
        getString(R.string.note_to_self)
      } else {
        getString(R.string.MediaPreviewActivity_you_to_s, threadRecipient.getDisplayName(requireContext()))
      }
    } else {
      if (threadRecipient.isGroup) {
        getString(R.string.MediaPreviewActivity_s_to_s, defaultFromString, threadRecipient.getDisplayName(requireContext()))
      } else {
        getString(R.string.MediaPreviewActivity_s_to_you, defaultFromString)
      }
    }
  }

  private fun getSubTitleText(mediaRecord: MediaDatabase.MediaRecord): String =
    if (mediaRecord.date > 0) {
      DateUtils.getExtendedRelativeTimeSpanString(requireContext(), Locale.getDefault(), mediaRecord.date)
    } else {
      getString(R.string.MediaPreviewActivity_draft)
    }

  override fun singleTapOnMedia(): Boolean {
    Log.d(TAG, "singleTapOnMedia()")
    return true
  }

  override fun mediaNotAvailable() {
    Log.d(TAG, "mediaNotAvailable()")
  }

  override fun onMediaReady() {
    Log.d(TAG, "onMediaReady()")
  }

  companion object {
    val ARGS_KEY: String = "args"
  }
}
