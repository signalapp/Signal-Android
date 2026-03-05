/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.groups.memberlabel

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.signal.core.ui.compose.AllDevicePreviews
import org.signal.core.ui.compose.BottomSheets
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.ComposeBottomSheetDialogFragment
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.util.requireParcelableCompat
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.groups.GroupId
import org.thoughtcrime.securesms.util.viewModel

/**
 * Explains what member labels are and provides options to edit the current user's label.
 */
class MemberLabelEducationSheet : ComposeBottomSheetDialogFragment() {
  companion object {
    const val RESULT_EDIT_MEMBER_LABEL = "edit_member_label"
    const val KEY_GROUP_ID = "group_id"

    private const val FRAGMENT_TAG = "MemberLabelEducationSheet"
    private const val ARGS_GROUP_ID = "group_id"

    fun show(fragmentManager: FragmentManager, groupId: GroupId.V2) {
      val fragment = MemberLabelEducationSheet().apply {
        arguments = bundleOf(ARGS_GROUP_ID to groupId)
      }
      fragment.show(fragmentManager, FRAGMENT_TAG)
    }
  }

  private val groupId: GroupId.V2 by lazy {
    requireArguments().requireParcelableCompat(ARGS_GROUP_ID, GroupId.V2::class.java)
  }

  private val viewModel: MemberLabelEducationViewModel by viewModel {
    MemberLabelEducationViewModel(groupId)
  }

  override val peekHeightPercentage: Float = 1f

  @Composable
  override fun SheetContent() {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val callbacks = remember {
      object : MemberLabelEducationUiCallbacks {
        override fun onSetLabelClicked() {
          setFragmentResult(RESULT_EDIT_MEMBER_LABEL, bundleOf(KEY_GROUP_ID to groupId))
          dismiss()
        }

        override fun onDismiss() = dismiss()
      }
    }

    MemberLabelEducationSheetContent(
      state = state,
      callbacks = callbacks
    )
  }
}

@Composable
private fun MemberLabelEducationSheetContent(
  state: MemberLabelEducationViewModel.UiState,
  callbacks: MemberLabelEducationUiCallbacks = MemberLabelEducationUiCallbacks.Empty
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
      text = stringResource(R.string.MemberLabelsEducation__title),
      style = MaterialTheme.typography.titleLarge,
      color = MaterialTheme.colorScheme.onSurface,
      modifier = Modifier.padding(top = 16.dp)
    )

    Text(
      text = stringResource(R.string.MemberLabelsEducation__body),
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
      modifier = Modifier.padding(top = 12.dp)
    )

    if (state.selfCanSetLabel) {
      TextButton(
        onClick = callbacks::onSetLabelClicked,
        modifier = Modifier.padding(top = 56.dp)
      ) {
        Text(
          text = stringResource(
            if (state.selfHasLabel) R.string.MemberLabelsEducation__edit_label
            else R.string.MemberLabelsEducation__set_label
          )
        )
      }
    } else {
      Spacer(modifier = Modifier.height(56.dp))
    }

    Buttons.LargeTonal(
      onClick = callbacks::onDismiss,
      modifier = Modifier
        .padding(top = 16.dp)
        .defaultMinSize(minWidth = 220.dp)
    ) {
      Text(text = stringResource(android.R.string.ok))
    }
  }
}

private interface MemberLabelEducationUiCallbacks {
  fun onSetLabelClicked()
  fun onDismiss()

  object Empty : MemberLabelEducationUiCallbacks {
    override fun onSetLabelClicked() = Unit
    override fun onDismiss() = Unit
  }
}

@AllDevicePreviews
@Composable
private fun MemberLabelEducationSheetPreviewCanSetNoLabel() = Previews.Preview {
  MemberLabelEducationSheetContent(
    state = MemberLabelEducationViewModel.UiState(
      selfHasLabel = false,
      selfCanSetLabel = true
    )
  )
}

@DayNightPreviews
@Composable
private fun MemberLabelEducationSheetPreviewCanSetHasLabel() = Previews.Preview {
  MemberLabelEducationSheetContent(
    state = MemberLabelEducationViewModel.UiState(
      selfHasLabel = true,
      selfCanSetLabel = true
    )
  )
}

@DayNightPreviews
@Composable
private fun MemberLabelEducationSheetPreviewCannotSet() = Previews.Preview {
  MemberLabelEducationSheetContent(
    state = MemberLabelEducationViewModel.UiState(
      selfHasLabel = false,
      selfCanSetLabel = false
    )
  )
}
