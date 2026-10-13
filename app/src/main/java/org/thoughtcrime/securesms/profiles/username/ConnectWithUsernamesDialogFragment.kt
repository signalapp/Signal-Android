/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.profiles.username

import android.app.Dialog
import android.os.Bundle
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.ComposeDialogFragment
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.enableEdgeToEdge
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.profiles.manage.EditProfileActivity
import org.signal.core.ui.R as CoreUiR

/**
 * Explains usernames to accounts registered without a phone number and gives them the
 * opportunity to set one up now.
 */
class ConnectWithUsernamesDialogFragment : ComposeDialogFragment() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    setStyle(STYLE_NO_FRAME, R.style.Signal_DayNight_Dialog_FullScreen)
  }

  override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
    return super.onCreateDialog(savedInstanceState).apply {
      window?.enableEdgeToEdge()
    }
  }

  @Composable
  override fun DialogContent() {
    ConnectWithUsernamesDialogContent(
      onSetUpUsernameClick = {
        startActivity(EditProfileActivity.getIntentForUsernameEdit(requireContext()))
        dismissAllowingStateLoss()
      },
      onNotNowClick = { dismissAllowingStateLoss() }
    )
  }
}

@Composable
private fun ConnectWithUsernamesDialogContent(
  onSetUpUsernameClick: () -> Unit,
  onNotNowClick: () -> Unit
) {
  Scaffolds.Settings(
    title = "",
    onNavigationClick = onNotNowClick,
    navigationIcon = SignalIcons.X.imageVector
  ) {
    Column(modifier = Modifier.padding(it)) {
      Column(
        modifier = Modifier
          .weight(1f)
          .verticalScroll(rememberScrollState())
      ) {
        Text(
          text = stringResource(R.string.ConnectWithUsernamesDialogFragment__connect_with_usernames),
          style = MaterialTheme.typography.headlineMedium,
          textAlign = TextAlign.Center,
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = dimensionResource(CoreUiR.dimen.gutter))
            .padding(top = 4.dp, bottom = 36.dp)
        )

        ConnectWithUsernamesRowItem(
          title = stringResource(R.string.ConnectWithUsernamesDialogFragment__usernames),
          description = stringResource(R.string.ConnectWithUsernamesDialogFragment__people_can_message_you_using_your_optional_username),
          image = painterResource(R.drawable.usernames_48_color)
        )

        ConnectWithUsernamesRowItem(
          title = stringResource(R.string.ConnectWithUsernamesDialogFragment__qr_codes_and_links),
          description = stringResource(R.string.ConnectWithUsernamesDialogFragment__usernames_have_a_unique_qr_code),
          image = painterResource(R.drawable.qr_codes_48_color)
        )

        ConnectWithUsernamesRowItem(
          title = stringResource(R.string.ConnectWithUsernamesDialogFragment__stay_discoverable),
          description = stringResource(R.string.ConnectWithUsernamesDialogFragment__without_a_phone_number_usernames_and_links),
          image = painterResource(R.drawable.discoverable_48_color)
        )
      }

      Buttons.LargeTonal(
        onClick = onSetUpUsernameClick,
        modifier = Modifier
          .padding(horizontal = dimensionResource(CoreUiR.dimen.gutter))
          .padding(top = 16.dp)
          .defaultMinSize(minWidth = 221.dp)
          .align(Alignment.CenterHorizontally)
      ) {
        Text(text = stringResource(R.string.ConnectWithUsernamesDialogFragment__set_up_username))
      }

      TextButton(
        onClick = onNotNowClick,
        modifier = Modifier
          .padding(
            start = dimensionResource(CoreUiR.dimen.gutter),
            end = dimensionResource(CoreUiR.dimen.gutter),
            top = 8.dp,
            bottom = 16.dp
          )
          .defaultMinSize(minWidth = 221.dp)
          .align(Alignment.CenterHorizontally)
      ) {
        Text(text = stringResource(R.string.ConnectWithUsernamesDialogFragment__not_now))
      }
    }
  }
}

@Composable
private fun ConnectWithUsernamesRowItem(
  title: String,
  description: String,
  image: Painter,
  modifier: Modifier = Modifier
) {
  Row(
    modifier = modifier
      .padding(horizontal = dimensionResource(CoreUiR.dimen.gutter))
      .padding(bottom = 40.dp)
  ) {
    Image(
      painter = image,
      contentDescription = null,
      modifier = Modifier
        .padding(start = 12.dp, top = 4.dp, end = 24.dp)
        .size(48.dp)
    )

    Column {
      Text(
        text = title,
        style = MaterialTheme.typography.titleMedium
      )

      Text(
        text = description,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 2.dp, end = 8.dp)
      )
    }
  }
}

@DayNightPreviews
@Composable
private fun ConnectWithUsernamesDialogContentPreview() {
  Previews.Preview {
    ConnectWithUsernamesDialogContent(
      onSetUpUsernameClick = {},
      onNotNowClick = {}
    )
  }
}
