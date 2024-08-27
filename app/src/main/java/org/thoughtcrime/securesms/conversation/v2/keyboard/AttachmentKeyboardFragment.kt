/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.v2.keyboard

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.kotlin.subscribeBy
import org.signal.core.util.concurrent.LifecycleDisposable
import org.signal.core.util.concurrent.addTo
import org.thoughtcrime.securesms.LoggingFragment
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.conversation.AttachmentKeyboard
import org.thoughtcrime.securesms.conversation.AttachmentKeyboardButton
import org.thoughtcrime.securesms.conversation.ManageContextMenu
import org.thoughtcrime.securesms.conversation.v2.ConversationViewModel
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.mediasend.Media
import org.thoughtcrime.securesms.permissions.PermissionCompat
import org.thoughtcrime.securesms.permissions.Permissions
import org.thoughtcrime.securesms.recipients.Recipient
import java.util.function.Predicate

/**
 * Fragment wrapped version of [AttachmentKeyboard] to help encapsulate logic the view
 * needs from external sources.
 */
class AttachmentKeyboardFragment : LoggingFragment(R.layout.attachment_keyboard_fragment), AttachmentKeyboard.Callback {

  companion object {
    const val RESULT_KEY = "AttachmentKeyboardFragmentResult"
    const val MEDIA_RESULT = "Media"
    const val BUTTON_RESULT = "Button"
  }

  private val viewModel: AttachmentKeyboardViewModel by viewModels()

  private lateinit var conversationViewModel: ConversationViewModel
  private lateinit var attachmentKeyboardView: AttachmentKeyboard

  private val lifecycleDisposable = LifecycleDisposable()
  private val removePaymentFilter: Predicate<AttachmentKeyboardButton> = Predicate { button -> button != AttachmentKeyboardButton.PAYMENT }

  @Suppress("ReplaceGetOrSet")
  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    lifecycleDisposable.bindTo(viewLifecycleOwner)

    attachmentKeyboardView = view.findViewById(R.id.attachment_keyboard)
    attachmentKeyboardView.apply {
      setCallback(this@AttachmentKeyboardFragment)
      if (!SignalStore.payments.paymentsAvailability.isSendAllowed) {
        filterAttachmentKeyboardButtons(removePaymentFilter)
      }
    }

    viewModel.getRecentMedia()
      .subscribeBy {
        attachmentKeyboardView.onMediaChanged(it)
      }
      .addTo(lifecycleDisposable)

    conversationViewModel = ViewModelProvider(requireParentFragment()).get(ConversationViewModel::class.java)

    val snapshot = conversationViewModel.recipientSnapshot
    if (snapshot != null) {
      updatePaymentsAvailable(snapshot)
    }

    conversationViewModel
      .recipient
      .observeOn(AndroidSchedulers.mainThread())
      .subscribeBy {
        attachmentKeyboardView.setWallpaperEnabled(it.hasWallpaper)
        updatePaymentsAvailable(it)
      }
      .addTo(lifecycleDisposable)
  }

  override fun onAttachmentMediaClicked(media: Media) {
    setFragmentResult(RESULT_KEY, bundleOf(MEDIA_RESULT to media))
  }

  override fun onAttachmentSelectorClicked(button: AttachmentKeyboardButton) {
    setFragmentResult(RESULT_KEY, bundleOf(BUTTON_RESULT to button))
  }

  override fun onAttachmentPermissionsRequested() {
    Permissions.with(requireParentFragment())
      .request(*PermissionCompat.forImagesAndVideos())
      .ifNecessary()
      .onAnyResult { viewModel.refreshRecentMedia() }
      .withPermanentDenialDialog(getString(R.string.AttachmentManager_signal_requires_the_external_storage_permission_in_order_to_attach_photos_videos_or_audio), null, R.string.AttachmentManager_signal_allow_storage, R.string.AttachmentManager_signal_to_show_photos, true, parentFragmentManager)
      .onSomeDenied {
        val deniedPermissions = PermissionCompat.getRequiredPermissionsForDenial()
        if (it.containsAll(deniedPermissions.toList())) {
          Toast.makeText(requireContext(), R.string.AttachmentManager_signal_needs_storage_access, Toast.LENGTH_LONG).show()
        }
      }
      .execute()
  }

  override fun onDisplayMoreContextMenu(v: View, showAbove: Boolean, showAtStart: Boolean) {
    ManageContextMenu.show(
      context = requireContext(),
      anchorView = v,
      showAbove = showAbove,
      showAtStart = showAtStart,
      onSelectMore = { selectMorePhotos() },
      onSettings = { requireContext().startActivity(Permissions.getApplicationSettingsIntent(requireContext())) }
    )
  }

  private fun selectMorePhotos() {
    Permissions.with(requireParentFragment())
      .request(*PermissionCompat.forImagesAndVideos())
      .onAnyResult { viewModel.refreshRecentMedia() }
      .execute()
  }

  private fun updatePaymentsAvailable(recipient: Recipient) {
    val paymentsValues = SignalStore.payments
    if (paymentsValues.paymentsAvailability.isSendAllowed &&
      !recipient.isSelf &&
      !recipient.isGroup &&
      recipient.isRegistered
    ) {
      attachmentKeyboardView.filterAttachmentKeyboardButtons(null)
    } else {
      attachmentKeyboardView.filterAttachmentKeyboardButtons(removePaymentFilter)
    }
  }
}
