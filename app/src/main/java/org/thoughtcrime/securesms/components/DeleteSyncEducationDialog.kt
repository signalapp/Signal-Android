/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components

import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentManager
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.subjects.CompletableSubject
import org.signal.core.ui.Buttons
import org.signal.core.ui.Previews
import org.signal.core.ui.SignalPreview
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.compose.ComposeBottomSheetDialogFragment
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.TextSecurePreferences

/**
 * Show educational info about delete syncing to linked devices. This dialog uses a subject to convey when
 * it completes and will dismiss itself if that subject is null aka dialog is recreated by OS instead of being
 * shown by our code.
 */
class DeleteSyncEducationDialog : ComposeBottomSheetDialogFragment() {

  companion object {

    @JvmStatic
    fun shouldShow(): Boolean {
      return TextSecurePreferences.isMultiDevice(AppDependencies.application) &&
        !SignalStore.uiHints.hasSeenDeleteSyncEducationSheet &&
        Recipient.self().deleteSyncCapability.isSupported
    }

    @JvmStatic
    fun show(fragmentManager: FragmentManager): Completable {
      val dialog = DeleteSyncEducationDialog()

      dialog.show(fragmentManager, null)
      SignalStore.uiHints.hasSeenDeleteSyncEducationSheet = true

      val subject = CompletableSubject.create()
      dialog.subject = subject

      return subject
        .onErrorComplete()
        .observeOn(AndroidSchedulers.mainThread())
    }
  }

  override val peekHeightPercentage: Float = 1f

  private var subject: CompletableSubject? = null

  @Composable
  override fun SheetContent() {
    Sheet(dismiss = this::dismissAllowingStateLoss)
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    if (subject == null || savedInstanceState != null) {
      dismissAllowingStateLoss()
    }
  }

  override fun onDismiss(dialog: DialogInterface) {
    super.onDismiss(dialog)
    subject?.onComplete()
  }
}

@Composable
private fun Sheet(
  dismiss: () -> Unit = {}
) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = Modifier
      .padding(24.dp)
  ) {
    Image(
      painter = painterResource(id = R.drawable.delete_sync),
      contentDescription = null,
      modifier = Modifier
        .padding(top = 48.dp)
    )

    Text(
      text = stringResource(id = R.string.DeleteSyncEducation_title),
      style = MaterialTheme.typography.titleLarge,
      textAlign = TextAlign.Center,
      modifier = Modifier
        .padding(top = 24.dp, bottom = 12.dp)
    )

    Text(
      text = stringResource(id = R.string.DeleteSyncEducation_message),
      style = MaterialTheme.typography.bodyLarge,
      textAlign = TextAlign.Center,
      color = MaterialTheme.colorScheme.secondary
    )

    Buttons.LargeTonal(
      onClick = dismiss,
      modifier = Modifier
        .padding(top = 64.dp)
        .defaultMinSize(minWidth = 132.dp)
    ) {
      Text(text = stringResource(id = R.string.DeleteSyncEducation_acknowledge_button))
    }
  }
}

@SignalPreview
@Composable
private fun SheetPreview() {
  Previews.Preview {
    Sheet()
  }
}
