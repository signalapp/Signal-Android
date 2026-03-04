/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.groups.memberlabel

import android.content.DialogInterface
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.os.bundleOf
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.setFragmentResult
import org.signal.core.ui.compose.AllDevicePreviews
import org.signal.core.ui.compose.BottomSheets
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.ComposeBottomSheetDialogFragment
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.thoughtcrime.securesms.R

/**
 * Informs the user that their member label will be displayed in place of their About text in this group.
 */
class MemberLabelAboutOverrideSheet : ComposeBottomSheetDialogFragment() {
  companion object {
    const val RESULT_KEY = "member_label_about_override_result"
    const val KEY_DONT_SHOW_AGAIN = "dont_show_again"

    private const val FRAGMENT_TAG = "MemberLabelAboutOverrideSheet"

    fun show(fragmentManager: FragmentManager) {
      MemberLabelAboutOverrideSheet().show(fragmentManager, FRAGMENT_TAG)
    }
  }

  override val peekHeightPercentage: Float = 1f

  @Composable
  override fun SheetContent() {
    val callbacks = remember {
      object : MemberLabelAboutOverrideUiCallbacks {
        override fun onOkClicked() {
          setFragmentResult(RESULT_KEY, bundleOf(KEY_DONT_SHOW_AGAIN to false))
          dismiss()
        }

        override fun onDontShowAgainClicked() {
          setFragmentResult(RESULT_KEY, bundleOf(KEY_DONT_SHOW_AGAIN to true))
          dismiss()
        }
      }
    }

    MemberLabelAboutOverrideSheetContent(callbacks = callbacks)
  }

  override fun onCancel(dialog: DialogInterface) {
    setFragmentResult(RESULT_KEY, bundleOf(KEY_DONT_SHOW_AGAIN to false))
    super.onCancel(dialog)
  }
}

@Composable
private fun MemberLabelAboutOverrideSheetContent(
  callbacks: MemberLabelAboutOverrideUiCallbacks = MemberLabelAboutOverrideUiCallbacks.Empty
) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = Modifier
      .padding(top = 4.dp, bottom = 28.dp, start = 28.dp, end = 28.dp)
      .verticalScroll(rememberScrollState())
  ) {
    BottomSheets.Handle()

    Image(
      painter = painterResource(R.drawable.symbol_tag_filled_64),
      contentDescription = null,
      modifier = Modifier
        .padding(top = 24.dp)
        .size(64.dp)
    )

    Text(
      text = stringResource(R.string.MemberLabelsAboutOverride__title),
      style = MaterialTheme.typography.titleLarge,
      color = MaterialTheme.colorScheme.onSurface,
      modifier = Modifier.padding(top = 16.dp)
    )

    Text(
      text = stringResource(R.string.MemberLabelsAboutOverride__body),
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
      modifier = Modifier.padding(top = 12.dp)
    )

    Buttons.LargeTonal(
      onClick = callbacks::onOkClicked,
      modifier = Modifier
        .padding(top = 64.dp)
        .defaultMinSize(minWidth = 220.dp)
    ) {
      Text(text = stringResource(android.R.string.ok))
    }

    TextButton(
      onClick = callbacks::onDontShowAgainClicked,
      modifier = Modifier.padding(top = 16.dp)
    ) {
      Text(text = stringResource(R.string.ConversationFragment_dont_show_again))
    }
  }
}

private interface MemberLabelAboutOverrideUiCallbacks {
  fun onOkClicked()
  fun onDontShowAgainClicked()

  object Empty : MemberLabelAboutOverrideUiCallbacks {
    override fun onOkClicked() = Unit
    override fun onDontShowAgainClicked() = Unit
  }
}

@AllDevicePreviews
@Composable
private fun MemberLabelAboutOverrideSheetPreview() = Previews.Preview {
  MemberLabelAboutOverrideSheetContent()
}

@DayNightPreviews
@Composable
private fun MemberLabelAboutOverrideSheetDarkPreview() = Previews.Preview {
  MemberLabelAboutOverrideSheetContent()
}
