package org.thoughtcrime.securesms.mediapreview

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.MarginLayoutParams
import android.widget.Toast
import androidx.core.app.ShareCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import org.signal.core.util.concurrent.SignalExecutors
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.animation.DepthPageTransformer
import org.thoughtcrime.securesms.attachments.DatabaseAttachment
import org.thoughtcrime.securesms.components.ViewBinderDelegate
import org.thoughtcrime.securesms.conversation.mutiselect.forward.MultiselectForwardFragment
import org.thoughtcrime.securesms.conversation.mutiselect.forward.MultiselectForwardFragmentArgs
import org.thoughtcrime.securesms.database.MediaDatabase
import org.thoughtcrime.securesms.databinding.FragmentMediaPreviewV2Binding
import org.thoughtcrime.securesms.mediaoverview.MediaOverviewActivity
import org.thoughtcrime.securesms.mediasend.Media
import org.thoughtcrime.securesms.mms.GlideApp
import org.thoughtcrime.securesms.mms.PartAuthority
import org.thoughtcrime.securesms.permissions.Permissions
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.DateUtils
import org.thoughtcrime.securesms.util.FullscreenHelper
import org.thoughtcrime.securesms.util.LifecycleDisposable
import org.thoughtcrime.securesms.util.MediaUtil
import org.thoughtcrime.securesms.util.SaveAttachmentTask
import org.thoughtcrime.securesms.util.StorageUtil
import java.util.Locale
import java.util.Optional

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

    val args = MediaIntentFactory.requireArguments(requireArguments())

    initializeViewModel(args)
    initializeToolbar(binding.toolbar, args)
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
    initializeAlbumRail()
    anchorMarginsToBottomInsets(binding.mediaPreviewDetailsContainer)
    lifecycleDisposable += viewModel.state.distinctUntilChanged().observeOn(AndroidSchedulers.mainThread()).subscribe {
      bindCurrentState(it)
    }
  }

  private fun initializeToolbar(toolbar: MaterialToolbar, args: MediaIntentFactory.MediaPreviewArgs) {
    toolbar.setNavigationOnClickListener {
      requireActivity().onBackPressed()
    }

    binding.toolbar.inflateMenu(R.menu.media_preview)

    // Restricted to API26 because of MemoryFileUtil not supporting lower API levels well
    binding.toolbar.menu.findItem(R.id.media_preview__share).isVisible = Build.VERSION.SDK_INT >= 26

    if (args.hideAllMedia) {
      binding.toolbar.menu.findItem(R.id.media_preview__overview).isVisible = false
    }
  }

  private fun initializeAlbumRail() {
    binding.mediaPreviewAlbumRail.itemAnimator = null // Or can crash when set to INVISIBLE while animating by FullscreenHelper https://issuetracker.google.com/issues/148720682
    binding.mediaPreviewAlbumRail.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
    binding.mediaPreviewAlbumRail.adapter = MediaRailAdapter(
      GlideApp.with(this),
      object : MediaRailAdapter.RailItemListener {
        override fun onRailItemClicked(distanceFromActive: Int) {
          binding.mediaPager.currentItem += distanceFromActive
        }

        override fun onRailItemDeleteClicked(distanceFromActive: Int) {
          throw UnsupportedOperationException("Callback unsupported.")
        }
      },
      false
    )
  }

  private fun initializeFullScreenUi() {
    fullscreenHelper.configureToolbarLayout(binding.toolbarCutoutSpacer, binding.toolbar)
    fullscreenHelper.showAndHideWithSystemUI(requireActivity().window, binding.toolbarLayout, binding.mediaPreviewDetailsContainer)
  }

  private fun initializeViewModel(args: MediaIntentFactory.MediaPreviewArgs) {
    if (!MediaUtil.isImageType(args.initialMediaType) && !MediaUtil.isVideoType(args.initialMediaType)) {
      Log.w(TAG, "Unsupported media type sent to MediaPreviewV2Fragment, finishing.")
      Snackbar.make(binding.root, R.string.MediaPreviewActivity_unssuported_media_type, Snackbar.LENGTH_LONG)
        .setAction(R.string.MediaPreviewActivity_dismiss_due_to_error) {
          requireActivity().finish()
        }.show()
    }
    viewModel.setShowThread(args.showThread)
    val sorting = MediaDatabase.Sorting.values()[args.sorting]
    viewModel.fetchAttachments(args.initialMediaUri, args.threadId, sorting)
  }

  private fun bindCurrentState(currentState: MediaPreviewV2State) {
    when (currentState.loadState) {
      MediaPreviewV2State.LoadState.READY -> bindReadyState(currentState)
      MediaPreviewV2State.LoadState.LOADED -> {
        bindReadyState(currentState)
        bindLoadedState(currentState)
      }
      else -> null
    }
  }

  private fun bindReadyState(currentState: MediaPreviewV2State) {
    (binding.mediaPager.adapter as MediaPreviewV2Adapter).updateBackingItems(currentState.mediaRecords.mapNotNull { it.attachment })
    if (binding.mediaPager.currentItem != currentState.position) {
      binding.mediaPager.currentItem = currentState.position
    }
    val currentItem: MediaDatabase.MediaRecord = currentState.mediaRecords[currentState.position]
    binding.toolbar.title = getTitleText(currentItem, currentState.showThread)
    binding.toolbar.subtitle = getSubTitleText(currentItem)

    val menu: Menu = binding.toolbar.menu
    if (currentItem.threadId == MediaIntentFactory.NOT_IN_A_THREAD.toLong()) {
      menu.findItem(R.id.media_preview__overview).isVisible = false
      menu.findItem(R.id.delete).isVisible = false
    }

    binding.toolbar.setOnMenuItemClickListener {
      when (it.itemId) {
        R.id.media_preview__overview -> showOverview(currentItem.threadId)
        R.id.media_preview__forward -> forward(currentItem)
        R.id.media_preview__share -> share(currentItem)
        R.id.save -> saveToDisk(currentItem)
        R.id.delete -> deleteMedia(currentItem)
        android.R.id.home -> requireActivity().finish()
        else -> return@setOnMenuItemClickListener false
      }
      return@setOnMenuItemClickListener true
    }
  }

  /**
   * These are binding steps that need a reference to the actual fragment within the pager.
   * This is not available until after a page has been chosen by the ViewPager, and we receive the
   * {@link OnPageChangeCallback}.
   */
  private fun bindLoadedState(currentState: MediaPreviewV2State) {
    val currentItem: MediaDatabase.MediaRecord = currentState.mediaRecords[currentState.position]
    val currentFragment: Fragment? = childFragmentManager.findFragmentByTag("f${currentState.position}")
    val playbackControls = (currentFragment as? MediaPreviewFragment)?.playbackControls
    val albumThumbnailMedia = currentState.mediaRecords.map { it.toMedia() }
    val caption = currentItem.attachment?.caption
    if (albumThumbnailMedia.isEmpty() && caption == null && playbackControls == null) {
      binding.mediaPreviewDetailsContainer.visibility = View.GONE
    } else {
      binding.mediaPreviewDetailsContainer.visibility = View.VISIBLE
    }
    binding.mediaPreviewAlbumRail.visibility = if (albumThumbnailMedia.isEmpty()) View.GONE else View.VISIBLE
    (binding.mediaPreviewAlbumRail.adapter as MediaRailAdapter).setMedia(albumThumbnailMedia, currentState.position)
    binding.mediaPreviewAlbumRail.smoothScrollToPosition(currentState.position)

    binding.mediaPreviewCaptionContainer.visibility = if (caption == null) View.GONE else View.VISIBLE
    binding.mediaPreviewCaption.text = caption

    if (playbackControls != null) {
      val params = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
      playbackControls.layoutParams = params
      binding.mediaPreviewPlaybackControlsContainer.removeAllViews()
      binding.mediaPreviewPlaybackControlsContainer.addView(playbackControls)
    } else {
      binding.mediaPreviewPlaybackControlsContainer.removeAllViews()
    }
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

  private fun anchorMarginsToBottomInsets(viewToAnchor: View) {
    ViewCompat.setOnApplyWindowInsetsListener(viewToAnchor) { view: View, windowInsetsCompat: WindowInsetsCompat ->
      val layoutParams = view.layoutParams as MarginLayoutParams
      val systemBarInsets = windowInsetsCompat.getInsets(WindowInsetsCompat.Type.systemBars())
      layoutParams.setMargins(
        systemBarInsets.left,
        layoutParams.topMargin,
        systemBarInsets.right,
        systemBarInsets.bottom
      )
      view.layoutParams = layoutParams
      windowInsetsCompat
    }
  }

  private fun MediaDatabase.MediaRecord.toMedia(): Media? {
    val attachment = this.attachment
    val uri = attachment?.uri
    if (attachment == null || uri == null) {
      return null
    }

    return Media(
      uri,
      this.contentType,
      this.date,
      attachment.width,
      attachment.height,
      attachment.size,
      0,
      attachment.isBorderless,
      attachment.isVideoGif,
      Optional.empty(),
      Optional.ofNullable(attachment.caption),
      Optional.empty()
    )
  }

  override fun singleTapOnMedia(): Boolean {
    fullscreenHelper.toggleUiVisibility()
    return true
  }

  override fun mediaNotAvailable() {
    Snackbar.make(binding.root, R.string.MediaPreviewActivity_media_no_longer_available, Snackbar.LENGTH_LONG)
      .setAction(R.string.MediaPreviewActivity_dismiss_due_to_error) {
        requireActivity().finish()
      }.show()
  }

  override fun onMediaReady() {
    Log.d(TAG, "onMediaReady()")
  }

  private fun showOverview(threadId: Long) {
    val context = requireContext()
    context.startActivity(MediaOverviewActivity.forThread(context, threadId))
  }

  private fun forward(mediaItem: MediaDatabase.MediaRecord) {
    val attachment = mediaItem.attachment
    val uri = attachment?.uri
    if (attachment != null && uri != null) {
      MultiselectForwardFragmentArgs.create(
        requireContext(),
        mediaItem.threadId,
        uri,
        attachment.contentType
      ) { args: MultiselectForwardFragmentArgs ->
        MultiselectForwardFragment.showBottomSheet(childFragmentManager, args)
      }
    }
  }

  private fun share(mediaItem: MediaDatabase.MediaRecord) {
    val attachment = mediaItem.attachment
    val uri = attachment?.uri
    if (attachment != null && uri != null) {
      val publicUri = PartAuthority.getAttachmentPublicUri(uri)
      val mimeType = Intent.normalizeMimeType(attachment.contentType)
      val shareIntent = ShareCompat.IntentBuilder(requireActivity())
        .setStream(publicUri)
        .setType(mimeType)
        .createChooserIntent()
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

      try {
        startActivity(shareIntent)
      } catch (e: ActivityNotFoundException) {
        Log.w(TAG, "No activity existed to share the media.", e)
        Toast.makeText(requireContext(), R.string.MediaPreviewActivity_cant_find_an_app_able_to_share_this_media, Toast.LENGTH_LONG).show()
      }
    }
  }

  private fun saveToDisk(mediaItem: MediaDatabase.MediaRecord) {
    SaveAttachmentTask.showWarningDialog(requireContext()) { _: DialogInterface?, _: Int ->
      if (StorageUtil.canWriteToMediaStore()) {
        performSaveToDisk(mediaItem)
        return@showWarningDialog
      }
      Permissions.with(this)
        .request(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        .ifNecessary()
        .withPermanentDenialDialog(getString(R.string.MediaPreviewActivity_signal_needs_the_storage_permission_in_order_to_write_to_external_storage_but_it_has_been_permanently_denied))
        .onAnyDenied { Toast.makeText(requireContext(), R.string.MediaPreviewActivity_unable_to_write_to_external_storage_without_permission, Toast.LENGTH_LONG).show() }
        .onAllGranted { performSaveToDisk(mediaItem) }
        .execute()
    }
  }

  fun performSaveToDisk(mediaItem: MediaDatabase.MediaRecord) {
    val saveTask = SaveAttachmentTask(requireContext())
    val saveDate = if (mediaItem.date > 0) mediaItem.date else System.currentTimeMillis()
    val attachment = mediaItem.attachment
    val uri = attachment?.uri
    if (attachment != null && uri != null) {
      saveTask.executeOnExecutor(SignalExecutors.BOUNDED_IO, SaveAttachmentTask.Attachment(uri, attachment.contentType, saveDate, null))
    }
  }

  private fun deleteMedia(mediaItem: MediaDatabase.MediaRecord) {
    val attachment: DatabaseAttachment = mediaItem.attachment ?: return

    MaterialAlertDialogBuilder(requireContext())
      .setIcon(R.drawable.ic_warning)
      .setTitle(R.string.MediaPreviewActivity_media_delete_confirmation_title)
      .setMessage(R.string.MediaPreviewActivity_media_delete_confirmation_message)
      .setCancelable(true)
      .setPositiveButton(R.string.delete) { _, _ ->
        viewModel.deleteItem(requireContext(), attachment, onSuccess = {
          requireActivity().finish()
        }, onError = {
          Log.e(TAG, "Delete failed!", it)
          requireActivity().finish()
        })
      }
      .setNegativeButton(android.R.string.cancel, null)
      .show()
  }

  companion object {
    const val ARGS_KEY: String = "args"
  }
}
