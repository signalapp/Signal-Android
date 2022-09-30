package org.thoughtcrime.securesms.mediapreview

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.animation.DepthPageTransformer
import org.thoughtcrime.securesms.components.ViewBinderDelegate
import org.thoughtcrime.securesms.database.MediaDatabase
import org.thoughtcrime.securesms.databinding.FragmentMediaPreviewV2Binding
import org.thoughtcrime.securesms.util.LifecycleDisposable

class MediaPreviewV2Fragment : Fragment(R.layout.fragment_media_preview_v2), MediaPreviewFragment.Events {
  private val TAG = Log.tag(MediaPreviewV2Fragment::class.java)

  private val lifecycleDisposable = LifecycleDisposable()
  private val binding by ViewBinderDelegate(FragmentMediaPreviewV2Binding::bind)
  private val viewModel: MediaPreviewV2ViewModel by viewModels()

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    binding.mediaPager.offscreenPageLimit = 1
    binding.mediaPager.setPageTransformer(DepthPageTransformer())
    lifecycleDisposable += viewModel.state.distinctUntilChanged().observeOn(AndroidSchedulers.mainThread()).subscribe {
      if (it.loadState == MediaPreviewV2State.LoadState.READY) {
        binding.mediaPager.adapter = PreviewMediaAdapter(this, it.attachments)
      }
    }
    initializeViewModel()
  }

  private fun initializeViewModel() {
    val args = MediaIntentFactory.requireArguments(requireArguments())
    val sorting = MediaDatabase.Sorting.values()[args.sorting]
    viewModel.fetchAttachments(args.initialMediaUri, args.threadId, sorting)
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
