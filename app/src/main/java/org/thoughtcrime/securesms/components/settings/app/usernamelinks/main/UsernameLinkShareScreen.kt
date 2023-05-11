package org.thoughtcrime.securesms.components.settings.app.usernamelinks.main

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.signal.core.ui.Buttons
import org.signal.core.ui.theme.SignalTheme
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.app.usernamelinks.QrCodeBadge
import org.thoughtcrime.securesms.components.settings.app.usernamelinks.QrCodeData
import org.thoughtcrime.securesms.components.settings.app.usernamelinks.UsernameQrCodeColorScheme
import org.thoughtcrime.securesms.util.UsernameUtil
import org.thoughtcrime.securesms.util.Util
import org.thoughtcrime.securesms.util.navigation.safeNavigate

/**
 * A screen that shows all the data around your username link and how to share it, including a QR code.
 */
@Composable
fun UsernameLinkShareScreen(
  state: UsernameLinkSettingsState,
  snackbarHostState: SnackbarHostState,
  scope: CoroutineScope,
  navController: NavController,
  modifier: Modifier = Modifier,
  contentPadding: PaddingValues = PaddingValues(0.dp)
) {
  Column(
    modifier = modifier
      .padding(contentPadding)
      .verticalScroll(rememberScrollState())
  ) {
    QrCodeBadge(
      data = state.qrCodeData,
      colorScheme = state.qrCodeColorScheme,
      username = state.username
    )

    ButtonBar(
      onColorClicked = { navController.safeNavigate(R.id.action_usernameLinkSettingsFragment_to_usernameLinkQrColorPickerFragment) }
    )

    CopyRow(
      displayText = state.username,
      copyMessage = stringResource(R.string.UsernameLinkSettings_username_copied_toast),
      snackbarHostState = snackbarHostState,
      scope = scope
    )

    CopyRow(
      displayText = state.usernameLink,
      copyMessage = stringResource(R.string.UsernameLinkSettings_link_copied_toast),
      snackbarHostState = snackbarHostState,
      scope = scope
    )

    Text(
      text = stringResource(id = R.string.UsernameLinkSettings_qr_description),
      textAlign = TextAlign.Center,
      style = MaterialTheme.typography.bodyMedium,
      modifier = Modifier.padding(top = 24.dp, bottom = 36.dp, start = 43.dp, end = 43.dp)
    )

    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(bottom = 24.dp),
      horizontalArrangement = Arrangement.Center
    ) {
      Buttons.Small(onClick = { /*TODO*/ }) {
        Text(
          text = stringResource(id = R.string.UsernameLinkSettings_reset_button_label)
        )
      }
    }
  }
}

@Composable
private fun ButtonBar(onColorClicked: () -> Unit) {
  Row(
    horizontalArrangement = Arrangement.spacedBy(space = 32.dp, alignment = Alignment.CenterHorizontally),
    modifier = Modifier.fillMaxWidth()
  ) {
    Buttons.ActionButton(
      onClick = {},
      iconResId = R.drawable.symbol_share_android_24,
      labelResId = R.string.UsernameLinkSettings_share_button_label
    )
    Buttons.ActionButton(
      onClick = onColorClicked,
      iconResId = R.drawable.symbol_color_24,
      labelResId = R.string.UsernameLinkSettings_color_button_label
    )
  }
}

@Composable
private fun CopyRow(displayText: String, copyMessage: String, snackbarHostState: SnackbarHostState, scope: CoroutineScope) {
  val context = LocalContext.current
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .background(color = MaterialTheme.colorScheme.background)
      .clickable {
        Util.copyToClipboard(context, displayText)

        scope.launch {
          snackbarHostState.showSnackbar(copyMessage)
        }
      }
      .padding(horizontal = 26.dp, vertical = 16.dp)
  ) {
    Image(
      painter = painterResource(id = R.drawable.symbol_copy_android_24),
      contentDescription = null,
      colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onBackground)
    )

    Text(
      text = displayText,
      modifier = Modifier.padding(start = 26.dp),
      maxLines = 1,
      overflow = TextOverflow.Ellipsis
    )
  }
}

@Preview(name = "Light Theme")
@Composable
private fun ScreenPreviewLightTheme() {
  SignalTheme(isDarkMode = false) {
    Surface {
      UsernameLinkShareScreen(
        state = previewState(),
        snackbarHostState = SnackbarHostState(),
        scope = rememberCoroutineScope(),
        navController = NavController(LocalContext.current)
      )
    }
  }
}

@Preview(name = "Dark Theme")
@Composable
private fun ScreenPreviewDarkTheme() {
  SignalTheme(isDarkMode = true) {
    Surface {
      UsernameLinkShareScreen(
        state = previewState(),
        snackbarHostState = SnackbarHostState(),
        scope = rememberCoroutineScope(),
        navController = NavController(LocalContext.current)
      )
    }
  }
}

private fun previewState(): UsernameLinkSettingsState {
  val link = UsernameUtil.generateLink("maya.45")
  return UsernameLinkSettingsState(
    username = "maya.45",
    usernameLink = link,
    qrCodeData = QrCodeData.forData(link, 64),
    qrCodeColorScheme = UsernameQrCodeColorScheme.Blue
  )
}
