/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.verificationrequested

import android.os.Bundle
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentManager
import org.signal.core.ui.BottomSheetUtil
import org.signal.core.ui.compose.BottomSheets
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.ComposeBottomSheetDialogFragment
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.util.DateUtils
import java.util.Locale

/**
 * Sheet shown when the server has pushed a notification telling us a verification code was
 * requested for the user's account.
 */
class VerificationCodeRequestedBottomSheet : ComposeBottomSheetDialogFragment() {

  override val peekHeightPercentage: Float = 1f

  companion object {
    private const val ARG_REQUESTED_AT = "requested_at"

    @JvmStatic
    fun show(fragmentManager: FragmentManager, requestedAtMs: Long) {
      VerificationCodeRequestedBottomSheet().apply {
        arguments = Bundle().apply { putLong(ARG_REQUESTED_AT, requestedAtMs) }
      }.show(fragmentManager, BottomSheetUtil.STANDARD_BOTTOM_SHEET_FRAGMENT_TAG)
    }
  }

  @Composable
  override fun SheetContent() {
    val context = LocalContext.current
    val resources = LocalResources.current
    val requestedAt = requireArguments().getLong(ARG_REQUESTED_AT)
    val formattedTime = remember(requestedAt) {
      val time = DateUtils.getOnlyTimeString(context, requestedAt)
      val day = DateUtils.getDayPrecisionTimeString(context, Locale.getDefault(), requestedAt)
      resources.getString(R.string.VerificationCodeRequestedBottomSheet__time_with_day, time, day)
    }
    val nestedScrollInterop = rememberNestedScrollInteropConnection()
    val scrollModifier = Modifier.nestedScroll(nestedScrollInterop)

    VerificationCodeRequestedContent(
      formattedTime = formattedTime,
      onSafetyTipsClicked = {
        val fragmentManager = parentFragmentManager
        dismissAllowingStateLoss()
        VerificationCodeRequestedSafetyTipsBottomSheet.show(fragmentManager)
      },
      onOkClicked = { dismissAllowingStateLoss() },
      modifier = scrollModifier
    )
  }
}

@Composable
private fun VerificationCodeRequestedContent(
  formattedTime: String,
  onSafetyTipsClicked: () -> Unit,
  onOkClicked: () -> Unit,
  modifier: Modifier = Modifier
) {
  val scrollState = rememberScrollState()

  Column(
    modifier = Modifier.fillMaxWidth()
  ) {
    Box(
      contentAlignment = Alignment.Center,
      modifier = Modifier.fillMaxWidth()
    ) {
      BottomSheets.Handle()
    }

    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      modifier = modifier
        .weight(weight = 1f, fill = false)
        .verticalScroll(state = scrollState)
        .padding(horizontal = 36.dp)
        .padding(bottom = 36.dp)
    ) {
      Spacer(modifier = Modifier.height(26.dp))

      Image(
        painter = painterResource(id = R.drawable.verificationcode_alert_96),
        contentDescription = null,
        modifier = Modifier.size(96.dp)
      )

      Spacer(modifier = Modifier.height(12.dp))

      Text(
        text = stringResource(id = R.string.VerificationCodeRequestedBottomSheet__title),
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center
      )

      Spacer(modifier = Modifier.height(4.dp))

      Text(
        text = formattedTime,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
      )

      Spacer(modifier = Modifier.height(20.dp))

      Text(
        text = stringResource(id = R.string.VerificationCodeRequestedBottomSheet__body_1),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
      )

      Spacer(modifier = Modifier.height(12.dp))

      Text(
        text = stringResource(id = R.string.VerificationCodeRequestedBottomSheet__body_2),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
      )

      Spacer(modifier = Modifier.height(32.dp))

      Buttons.LargeTonal(
        onClick = onSafetyTipsClicked,
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 8.dp)
      ) {
        Text(text = stringResource(id = R.string.VerificationCodeRequestedBottomSheet__safety_tips))
      }

      Spacer(modifier = Modifier.height(8.dp))

      Buttons.LargeTonal(
        onClick = onOkClicked,
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 8.dp)
      ) {
        Text(text = stringResource(id = R.string.VerificationCodeRequestedBottomSheet__ok))
      }
    }
  }
}

@DayNightPreviews
@Composable
private fun VerificationCodeRequestedContentPreview() {
  Previews.BottomSheetContentPreview {
    VerificationCodeRequestedContent(
      formattedTime = "3:25 PM Today",
      onSafetyTipsClicked = {},
      onOkClicked = {}
    )
  }
}
