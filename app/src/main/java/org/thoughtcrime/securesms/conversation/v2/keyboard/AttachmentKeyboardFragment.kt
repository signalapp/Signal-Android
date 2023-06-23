/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.v2.keyboard

import android.Manifest
import android.os.Bundle
import android.view.View
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
import org.thoughtcrime.securesms.conversation.v2.ConversationViewModel
import org.thoughtcrime.securesms.mediasend.Media
import org.thoughtcrime.securesms.permissions.Permissions
import org.thoughtcrime.securesms.util.ViewModelFactory

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

  private val viewModel: AttachmentKeyboardViewModel by viewModels(
    factoryProducer = ViewModelFactory.factoryProducer { AttachmentKeyboardViewModel() }
  )

  private lateinit var conversationViewModel: ConversationViewModel

  private val lifecycleDisposable = LifecycleDisposable()

  @Suppress("ReplaceGetOrSet")
  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    lifecycleDisposable.bindTo(viewLifecycleOwner)

    val attachmentKeyboardView = view.findViewById<AttachmentKeyboard>(R.id.attachment_keyboard)

    attachmentKeyboardView.setCallback(this)

    viewModel.getRecentMedia()
      .subscribeBy {
        attachmentKeyboardView.onMediaChanged(it)
      }
      .addTo(lifecycleDisposable)

    conversationViewModel = ViewModelProvider(requireParentFragment()).get(ConversationViewModel::class.java)
    conversationViewModel
      .recipient
      .observeOn(AndroidSchedulers.mainThread())
      .subscribeBy {
        attachmentKeyboardView.setWallpaperEnabled(it.hasWallpaper())
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
      .request(Manifest.permission.READ_EXTERNAL_STORAGE)
      .onAllGranted { viewModel.refreshRecentMedia() }
      .withPermanentDenialDialog(getString(R.string.AttachmentManager_signal_requires_the_external_storage_permission_in_order_to_attach_photos_videos_or_audio))
      .execute()
  }
}
