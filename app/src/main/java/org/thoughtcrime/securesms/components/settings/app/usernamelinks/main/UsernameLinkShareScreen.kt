package org.thoughtcrime.securesms.components.settings.app.usernamelinks.main

import android.content.res.Configuration
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import org.signal.core.ui.Dialogs
import org.signal.core.ui.theme.SignalTheme
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.app.usernamelinks.QrCodeBadge
import org.thoughtcrime.securesms.components.settings.app.usernamelinks.QrCodeData
import org.thoughtcrime.securesms.components.settings.app.usernamelinks.QrCodeState
import org.thoughtcrime.securesms.components.settings.app.usernamelinks.UsernameQrCodeColorScheme
import org.thoughtcrime.securesms.components.settings.app.usernamelinks.main.UsernameLinkSettingsState.ActiveTab
import org.thoughtcrime.securesms.util.Util
import org.thoughtcrime.securesms.util.navigation.safeNavigate
import org.whispersystems.signalservice.api.push.UsernameLinkComponents
import java.util.UUID

/**
 * A screen that shows all the data around your username link and how to share it, including a QR code.
 */
@Composable
fun UsernameLinkShareScreen(
  state: UsernameLinkSettingsState,
  onLinkResultHandled: () -> Unit,
  snackbarHostState: SnackbarHostState,
  scope: CoroutineScope,
  navController: NavController?,
  onShareBadge: () -> Unit,
  modifier: Modifier = Modifier,
  onResetClicked: () -> Unit
) {
  val context = LocalContext.current

  when (state.usernameLinkResetResult) {
    UsernameLinkResetResult.NetworkUnavailable -> {
      ResetLinkResultDialog(stringResource(R.string.UsernameLinkSettings_reset_link_result_network_unavailable), onDismiss = onLinkResultHandled)
    }
    UsernameLinkResetResult.NetworkError -> {
      ResetLinkResultDialog(stringResource(R.string.UsernameLinkSettings_reset_link_result_network_error), onDismiss = onLinkResultHandled)
    }
    UsernameLinkResetResult.UnexpectedError -> {
      ResetLinkResultDialog(stringResource(R.string.UsernameLinkSettings_reset_link_result_unknown_error), onDismiss = onLinkResultHandled)
    }
    is UsernameLinkResetResult.Success -> {
      ResetLinkResultDialog(stringResource(R.string.UsernameLinkSettings_reset_link_result_success), onDismiss = onLinkResultHandled)
    }
    else -> {}
  }

  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = modifier
      .verticalScroll(rememberScrollState())
  ) {
    val usernameCopiedString = stringResource(id = R.string.UsernameLinkSettings_username_copied_toast)
    QrCodeBadge(
      data = state.qrCodeState,
      colorScheme = state.qrCodeColorScheme,
      username = state.username,
      usernameCopyable = true,
      modifier = Modifier.padding(horizontal = 58.dp, vertical = 24.dp),
      onClick = { username ->
        Util.copyToClipboard(context, username)
        scope.launch {
          snackbarHostState.showSnackbar(usernameCopiedString)
        }
      }
    )

    ButtonBar(
      onShareClicked = onShareBadge,
      onColorClicked = { navController?.safeNavigate(UsernameLinkSettingsFragmentDirections.actionUsernameLinkSettingsFragmentToUsernameLinkQrColorPickerFragment()) },
      onLinkClicked = {
        navController?.safeNavigate(UsernameLinkSettingsFragmentDirections.actionUsernameLinkSettingsFragmentToUsernameLinkShareBottomSheet())
      },
      linkState = state.usernameLinkState
    )

    Text(
      text = stringResource(id = R.string.UsernameLinkSettings_qr_description),
      textAlign = TextAlign.Center,
      style = MaterialTheme.typography.bodyMedium,
      modifier = Modifier.padding(top = 42.dp, bottom = 19.dp, start = 43.dp, end = 43.dp),
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(bottom = 24.dp),
      horizontalArrangement = Arrangement.Center
    ) {
      Buttons.Small(onClick = onResetClicked) {
        Text(
          text = stringResource(id = R.string.UsernameLinkSettings_reset_button_label)
        )
      }
    }
  }
}

@Composable
private fun ButtonBar(
  linkState: UsernameLinkState,
  onLinkClicked: () -> Unit,
  onShareClicked: () -> Unit,
  onColorClicked: () -> Unit
) {
  Row(
    horizontalArrangement = Arrangement.spacedBy(space = 32.dp, alignment = Alignment.CenterHorizontally),
    modifier = Modifier.fillMaxWidth()
  ) {
    Buttons.ActionButton(
      enabled = linkState is UsernameLinkState.Present,
      onClick = onLinkClicked,
      iconResId = R.drawable.symbol_link_24,
      labelResId = R.string.UsernameLinkSettings_link_button_label
    )
    Buttons.ActionButton(
      onClick = onShareClicked,
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
private fun LinkRow(linkState: UsernameLinkState, onClick: () -> Unit = {}) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .background(color = MaterialTheme.colorScheme.background)
      .padding(
        top = 32.dp,
        bottom = 24.dp,
        start = 24.dp,
        end = 24.dp
      )
      .border(
        width = 1.dp,
        color = MaterialTheme.colorScheme.outline,
        shape = RoundedCornerShape(12.dp)
      )
      .clickable(enabled = linkState is UsernameLinkState.Present) {
        onClick()
      }
      .padding(horizontal = 26.dp, vertical = 16.dp)
      .alpha(if (linkState is UsernameLinkState.Present) 1.0f else 0.6f)
  ) {
    Image(
      painter = painterResource(id = R.drawable.symbol_link_24),
      contentDescription = null,
      colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onBackground)
    )

    Text(
      text = when (linkState) {
        is UsernameLinkState.Present -> linkState.link
        is UsernameLinkState.NotSet -> stringResource(id = R.string.UsernameLinkSettings_link_not_set_label)
        is UsernameLinkState.Resetting -> stringResource(id = R.string.UsernameLinkSettings_resetting_link_label)
      },
      modifier = Modifier.padding(start = 26.dp),
      maxLines = 1,
      overflow = TextOverflow.Ellipsis
    )
  }
}

@Composable
private fun ResetLinkResultDialog(message: String, onDismiss: () -> Unit) {
  Dialogs.SimpleMessageDialog(
    message = message,
    dismiss = stringResource(id = android.R.string.ok),
    onDismiss = onDismiss
  )
}

@Preview(name = "Light Theme", group = "screen", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Dark Theme", group = "screen", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ScreenPreview() {
  SignalTheme {
    Surface {
      UsernameLinkShareScreen(
        state = previewState(),
        snackbarHostState = SnackbarHostState(),
        scope = rememberCoroutineScope(),
        navController = NavController(LocalContext.current),
        onShareBadge = {},
        onResetClicked = {},
        onLinkResultHandled = {}
      )
    }
  }
}

@Preview(name = "Light Theme", group = "screen", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Dark Theme", group = "screen", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ScreenPreviewResetSuccess() {
  SignalTheme {
    Surface {
      UsernameLinkShareScreen(
        state = previewState().copy(usernameLinkResetResult = UsernameLinkResetResult.Success(UsernameLinkComponents(Util.getSecretBytes(32), UUID.randomUUID()))),
        snackbarHostState = SnackbarHostState(),
        scope = rememberCoroutineScope(),
        navController = NavController(LocalContext.current),
        onShareBadge = {},
        onResetClicked = {},
        onLinkResultHandled = {}
      )
    }
  }
}

@Preview(name = "Light Theme", group = "screen", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Dark Theme", group = "screen", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ScreenPreviewResetNetworkError() {
  SignalTheme {
    Surface {
      UsernameLinkShareScreen(
        state = previewState().copy(usernameLinkResetResult = UsernameLinkResetResult.NetworkError),
        snackbarHostState = SnackbarHostState(),
        scope = rememberCoroutineScope(),
        navController = NavController(LocalContext.current),
        onShareBadge = {},
        onResetClicked = {},
        onLinkResultHandled = {}
      )
    }
  }
}

@Preview(name = "Light Theme", group = "screen", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Dark Theme", group = "screen", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ScreenPreviewResetNetworkUnavailable() {
  SignalTheme {
    Surface {
      UsernameLinkShareScreen(
        state = previewState().copy(usernameLinkResetResult = UsernameLinkResetResult.NetworkUnavailable),
        snackbarHostState = SnackbarHostState(),
        scope = rememberCoroutineScope(),
        navController = NavController(LocalContext.current),
        onShareBadge = {},
        onResetClicked = {},
        onLinkResultHandled = {}
      )
    }
  }
}

@Preview(name = "Light Theme", group = "screen", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Dark Theme", group = "screen", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ScreenPreviewResetUnexpectedError() {
  SignalTheme {
    Surface {
      UsernameLinkShareScreen(
        state = previewState().copy(usernameLinkResetResult = UsernameLinkResetResult.UnexpectedError),
        snackbarHostState = SnackbarHostState(),
        scope = rememberCoroutineScope(),
        navController = NavController(LocalContext.current),
        onShareBadge = {},
        onResetClicked = {},
        onLinkResultHandled = {}
      )
    }
  }
}

@Preview(name = "Light Theme", group = "LinkRow", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Dark Theme", group = "LinkRow", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun LinkRowPreview() {
  SignalTheme {
    Surface {
      Column(modifier = Modifier.padding(8.dp)) {
        LinkRow(
          linkState = UsernameLinkState.Present("https://signal.me/#eu/asdfasdfasdfasdfasdfasdfasdfasdfasdfasdf")
        )
        LinkRow(
          linkState = UsernameLinkState.NotSet
        )
        LinkRow(
          linkState = UsernameLinkState.Resetting
        )
      }
    }
  }
}

private fun previewState(): UsernameLinkSettingsState {
  val link = "https://signal.me/#eu/asdfasdfasdfasdfasdfasdfasdfasdfasdfasdf"
  return UsernameLinkSettingsState(
    activeTab = ActiveTab.Code,
    username = "parker.42",
    usernameLinkState = UsernameLinkState.Present("https://signal.me/#eu/asdfasdfasdfasdfasdfasdfasdfasdfasdfasdf"),
    qrCodeState = QrCodeState.Present(QrCodeData.forData(link, 64)),
    qrCodeColorScheme = UsernameQrCodeColorScheme.Blue
  )
}
