/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.profiles.username

import android.os.Bundle
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.signal.core.ui.Buttons
import org.signal.core.ui.Previews
import org.signal.core.ui.Scaffolds
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.compose.ComposeDialogFragment
import org.thoughtcrime.securesms.profiles.manage.EditProfileActivity

/**
 * Displays an explanation page about usernames and gives the user
 * the opportunity to set one up now.
 */
class NewWaysToConnectDialogFragment : ComposeDialogFragment() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    setStyle(STYLE_NO_FRAME, R.style.Signal_DayNight_Dialog_FullScreen)
  }

  @Composable
  override fun DialogContent() {
    NewWaysToConnectDialogContent(
      onSetUpUsernameClick = {
        startActivity(EditProfileActivity.getIntentForUsernameEdit(requireContext()))
        dismissAllowingStateLoss()
      },
      onNotNowClick = { dismissAllowingStateLoss() }
    )
  }
}

@Preview
@Composable
private fun PreviewNewWaysToConnectDialogContent() {
  Previews.Preview {
    NewWaysToConnectDialogContent(
      onSetUpUsernameClick = {},
      onNotNowClick = {}
    )
  }
}

@Composable
private fun NewWaysToConnectDialogContent(
  onSetUpUsernameClick: () -> Unit,
  onNotNowClick: () -> Unit
) {
  Scaffolds.Settings(
    title = "",
    onNavigationClick = onNotNowClick,
    navigationIconPainter = painterResource(id = R.drawable.symbol_x_24)
  ) {
    Column(modifier = Modifier.padding(it)) {
      LazyColumn(modifier = Modifier.weight(1f)) {
        item {
          Text(
            text = stringResource(id = R.string.NewWaysToConnectDialogFragment__new_ways_to_connect),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = dimensionResource(id = R.dimen.core_ui__gutter))
              .padding(top = 4.dp, bottom = 36.dp)
          )
        }

        item {
          NewWaysToConnectRowItem(
            title = stringResource(id = R.string.NewWaysToConnectDialogFragment__phone_number_privacy),
            description = stringResource(id = R.string.NewWaysToConnectDialogFragment__your_phone_number_is_no_longer_shared),
            image = painterResource(id = R.drawable.phone_48_color)
          )
        }

        item {
          NewWaysToConnectRowItem(
            title = stringResource(id = R.string.NewWaysToConnectDialogFragment__usernames),
            description = stringResource(id = R.string.NewWaysToConnectDialogFragment__people_can_now_message_you_using_your_optional_username),
            image = painterResource(id = R.drawable.usernames_48_color)
          )
        }

        item {
          NewWaysToConnectRowItem(
            title = stringResource(id = R.string.NewWaysToConnectDialogFragment__qr_codes_and_links),
            description = stringResource(id = R.string.NewWaysToConnectDialogFragment__usernames_have_a_unique_qr_code),
            image = painterResource(id = R.drawable.qr_codes_48_color)
          )
        }
      }

      Buttons.LargeTonal(
        onClick = onSetUpUsernameClick,
        modifier = Modifier
          .padding(horizontal = dimensionResource(id = R.dimen.core_ui__gutter))
          .padding(top = 16.dp)
          .defaultMinSize(minWidth = 221.dp)
          .align(alignment = Alignment.CenterHorizontally)
      ) {
        Text(
          text = stringResource(id = R.string.NewWaysToConnectDialogFragment__set_up_your_username)
        )
      }

      TextButton(
        onClick = onNotNowClick,
        modifier = Modifier
          .padding(
            start = dimensionResource(id = R.dimen.core_ui__gutter),
            end = dimensionResource(id = R.dimen.core_ui__gutter),
            top = 8.dp,
            bottom = 16.dp
          )
          .defaultMinSize(minWidth = 221.dp)
          .align(alignment = Alignment.CenterHorizontally)
      ) {
        Text(text = stringResource(id = R.string.NewWaysToConnectDialogFragment__not_now))
      }
    }
  }
}

@Preview
@Composable
private fun PreviewNewWaysToConnectRowItem() {
  Previews.Preview {
    NewWaysToConnectRowItem(
      title = "Example Item",
      description = "Sample text for the subtitle of the example",
      image = painterResource(id = R.drawable.symbol_album_tilt_24)
    )
  }
}

@Composable
private fun NewWaysToConnectRowItem(
  title: String,
  description: String,
  image: Painter,
  modifier: Modifier = Modifier
) {
  Row(
    modifier = modifier
      .padding(
        horizontal = dimensionResource(id = R.dimen.core_ui__gutter)
      )
      .padding(
        bottom = 40.dp
      )
  ) {
    Image(
      painter = image,
      contentDescription = null,
      modifier = Modifier
        .padding(
          start = 12.dp,
          top = 4.dp,
          end = 24.dp
        )
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
