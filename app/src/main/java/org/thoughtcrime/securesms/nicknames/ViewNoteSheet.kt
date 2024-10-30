/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.nicknames

import android.os.Bundle
import android.text.util.Linkify
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.os.bundleOf
import androidx.core.text.method.LinkMovementMethodCompat
import androidx.core.text.util.LinkifyCompat
import org.signal.core.ui.BottomSheets
import org.signal.core.ui.Previews
import org.signal.core.util.getParcelableCompat
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.emoji.EmojiTextView
import org.thoughtcrime.securesms.compose.ComposeBottomSheetDialogFragment
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.util.viewModel
import org.signal.core.ui.R as CoreUiR

/**
 * Allows user to view the full note for a given recipient.
 */
class ViewNoteSheet : ComposeBottomSheetDialogFragment() {

  companion object {

    private const val RECIPIENT_ID = "recipient_id"

    @JvmStatic
    fun create(recipientId: RecipientId): ViewNoteSheet {
      return ViewNoteSheet().apply {
        arguments = bundleOf(
          RECIPIENT_ID to recipientId
        )
      }
    }
  }

  private val recipientId: RecipientId by lazy {
    requireArguments().getParcelableCompat(RECIPIENT_ID, RecipientId::class.java)!!
  }

  private val viewModel: ViewNoteSheetViewModel by viewModel {
    ViewNoteSheetViewModel(recipientId)
  }

  private lateinit var editNoteLauncher: ActivityResultLauncher<NicknameActivity.Args>

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    editNoteLauncher = registerForActivityResult(NicknameActivity.Contract()) {}
  }

  @Composable
  override fun SheetContent() {
    val note by remember { viewModel.note }

    ViewNoteBottomSheetContent(
      onEditNoteClick = this::onEditNoteClick,
      note = note
    )
  }

  private fun onEditNoteClick() {
    editNoteLauncher.launch(
      NicknameActivity.Args(
        recipientId = recipientId,
        focusNoteFirst = true
      )
    )

    dismissAllowingStateLoss()
  }
}

@Preview
@Composable
private fun ViewNoteBottomSheetContentPreview() {
  Previews.Preview {
    ViewNoteBottomSheetContent(
      onEditNoteClick = {},
      note = "Lorem ipsum dolor sit amet\n\nWebsite: https://example.com"
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ViewNoteBottomSheetContent(
  onEditNoteClick: () -> Unit,
  note: String
) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = Modifier.padding(horizontal = dimensionResource(id = CoreUiR.dimen.gutter))
  ) {
    BottomSheets.Handle()

    CenterAlignedTopAppBar(
      title = {
        Text(
          text = stringResource(id = R.string.ViewNoteSheet__note)
        )
      },
      actions = {
        IconButton(onClick = onEditNoteClick) {
          Icon(
            painter = painterResource(id = R.drawable.symbol_edit_24),
            contentDescription = stringResource(id = R.string.ViewNoteSheet__edit_note)
          )
        }
      },
      colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
        containerColor = Color.Transparent,
        scrolledContainerColor = Color.Transparent
      )
    )

    val mask = if (LocalInspectionMode.current) {
      Linkify.WEB_URLS
    } else {
      Linkify.WEB_URLS or Linkify.EMAIL_ADDRESSES or Linkify.PHONE_NUMBERS
    }

    AndroidView(
      factory = { context ->
        val view = EmojiTextView(context)

        view.setTextAppearance(context, R.style.Signal_Text_BodyLarge)
        view.movementMethod = LinkMovementMethodCompat.getInstance()

        view
      },
      modifier = Modifier
        .fillMaxWidth()
        .padding(bottom = 48.dp)
    ) {
      it.text = note

      LinkifyCompat.addLinks(it, mask)
    }
  }
}
