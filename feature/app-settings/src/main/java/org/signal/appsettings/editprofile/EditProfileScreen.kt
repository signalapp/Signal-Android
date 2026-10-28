/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.editprofile

import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import org.signal.appsettings.AppSettingsLazyColumn
import org.signal.appsettings.R
import org.signal.core.ui.compose.BottomSheets
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Dialogs
import org.signal.core.ui.compose.Dividers
import org.signal.core.ui.compose.Rows
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.compose.SignalPreviewWrapper
import org.signal.core.ui.compose.Snackbars
import org.signal.core.ui.compose.Tooltips
import org.signal.core.ui.compose.horizontalGutters
import org.signal.emoji.EmojiText

@VisibleForTesting
object EditProfileTestTags {
  const val LIST = "list"
  const val AVATAR = "avatar"
  const val BUTTON_EDIT_PHOTO = "button-edit-photo"
  const val ROW_NAME = "row-name"
  const val NAME = "name"
  const val ABOUT = "about"
  const val USERNAME = "username"
  const val ROW_ABOUT = "row-about"
  const val ROW_BADGES = "row-badges"
  const val ROW_USERNAME = "row-username"
  const val ROW_USERNAME_LINK = "row-username-link"
  const val USERNAME_ERROR = "username-error"
  const val USERNAME_LINK_ERROR = "username-link-error"
  const val TOOLTIP = "tooltip"
  const val BUTTON_TOOLTIP_CLOSE = "button-tooltip-close"
  const val BECOME_A_SUSTAINER_SHEET = "become-a-sustainer-sheet"
  const val BUTTON_BECOME_A_SUSTAINER = "button-become-a-sustainer"
}

private const val DISABLED_ALPHA = 0.4f

/**
 * The user's own profile: their avatar, name, about and badges, and their username and its link, each opening the
 * screen that edits it.
 *
 * @param avatar the user's avatar, drawn in an 80dp box. It may draw beyond the box, such as a badge on its corner.
 * @param sustainerBadgePreview the user's avatar wearing [EditProfileState.sustainerBadge], drawn in an 80dp box in
 *   [EditProfileState.Dialog.BecomeASustainer].
 */
@Composable
fun EditProfileScreen(
  state: EditProfileState,
  onEvent: (EditProfileEvent) -> Unit,
  modifier: Modifier = Modifier,
  avatar: @Composable () -> Unit = {},
  sustainerBadgePreview: @Composable () -> Unit = {}
) {
  LifecycleResumeEffect(Unit) {
    onEvent(EditProfileEvent.ScreenResumed)
    onPauseOrDispose {}
  }

  Scaffolds.Settings(
    title = stringResource(R.string.EditProfileScreen__profile),
    onNavigationClick = { onEvent(EditProfileEvent.NavigateBackClicked) },
    navigationIcon = SignalIcons.ArrowStart.imageVector,
    snackbarHost = {
      Snackbars.Host(
        message = state.snackbar.message(),
        onDismissed = { onEvent(EditProfileEvent.SnackbarDismissed) }
      )
    },
    modifier = modifier
  ) { contentPadding ->
    AppSettingsLazyColumn(
      scaffoldPadding = contentPadding,
      horizontalAlignment = Alignment.CenterHorizontally,
      modifier = Modifier.testTag(EditProfileTestTags.LIST)
    ) {
      item(key = "avatar") {
        Avatar(state = state, onEvent = onEvent, avatar = avatar)
      }

      item(key = "name") {
        ProfileRow(
          text = state.profileName.ifEmpty { stringResource(R.string.ManageProfileFragment_profile_name) },
          icon = { RowIcon { Icon(imageVector = SignalIcons.Person.imageVector, contentDescription = null) } },
          canEdit = state.canEdit,
          onClick = { onEvent(EditProfileEvent.NameClicked) },
          textTestTag = EditProfileTestTags.NAME,
          modifier = Modifier
            .padding(top = 20.dp)
            .testTag(EditProfileTestTags.ROW_NAME)
        )
      }

      item(key = "about") {
        ProfileRow(
          text = state.about.ifEmpty { stringResource(R.string.ManageProfileFragment_about) },
          icon = {
            RowIcon {
              if (state.aboutEmoji.isNotEmpty()) {
                EmojiText(text = state.aboutEmoji, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 20.sp))
              } else {
                Icon(imageVector = SignalIcons.Edit.imageVector, contentDescription = null)
              }
            }
          },
          canEdit = state.canEdit,
          onClick = { onEvent(EditProfileEvent.AboutClicked) },
          textTestTag = EditProfileTestTags.ABOUT,
          modifier = Modifier.testTag(EditProfileTestTags.ROW_ABOUT)
        )
      }

      item(key = "badges") {
        ProfileRow(
          text = stringResource(R.string.EditProfileScreen__badges),
          icon = { RowIcon { Icon(imageVector = SignalIcons.BadgeMulti.imageVector, contentDescription = null) } },
          canEdit = state.canEdit,
          onClick = { onEvent(EditProfileEvent.BadgesClicked) },
          modifier = Modifier.testTag(EditProfileTestTags.ROW_BADGES)
        )
      }

      item(key = "profile-footer") {
        Footer(text = stringResource(R.string.CreateProfileActivity_signal_profiles_are_end_to_end_encrypted))
      }

      item(key = "divider") {
        Dividers.Default()
      }

      item(key = "username") {
        ProfileRow(
          text = state.username ?: stringResource(R.string.ManageProfileFragment_username),
          icon = { RowIcon { Icon(imageVector = SignalIcons.At.imageVector, contentDescription = null) } },
          canEdit = state.canEdit,
          onClick = { onEvent(EditProfileEvent.UsernameClicked) },
          textTestTag = EditProfileTestTags.USERNAME,
          error = state.usernameSyncState == EditProfileState.UsernameSyncState.USERNAME_AND_LINK_CORRUPTED,
          errorTestTag = EditProfileTestTags.USERNAME_ERROR,
          modifier = Modifier.testTag(EditProfileTestTags.ROW_USERNAME)
        )
      }

      if (state.showUsernameLink) {
        item(key = "username-link") {
          ProfileRow(
            text = stringResource(R.string.ManageProfileFragment_link_setting_text),
            icon = { RowIcon { Icon(imageVector = SignalIcons.QrCode.imageVector, contentDescription = null) } },
            canEdit = state.canEdit,
            onClick = { onEvent(EditProfileEvent.UsernameLinkClicked) },
            error = state.usernameSyncState == EditProfileState.UsernameSyncState.LINK_CORRUPTED,
            errorTestTag = EditProfileTestTags.USERNAME_LINK_ERROR,
            modifier = Modifier.testTag(EditProfileTestTags.ROW_USERNAME_LINK)
          )
        }

        if (state.showUsernameLinkTooltip) {
          item(key = "username-link-tooltip") {
            UsernameLinkTooltip(onDismiss = { onEvent(EditProfileEvent.UsernameLinkTooltipDismissed) })
          }
        }
      }

      if (!(state.showUsernameLink && state.showUsernameLinkTooltip)) {
        item(key = "username-footer") {
          Footer(
            text = stringResource(if (state.showUsernameLink) R.string.ManageProfileFragment__your_username else R.string.ManageProfileFragment__username_footer_no_username),
            modifier = Modifier.padding(bottom = 24.dp)
          )
        }
      }
    }
  }

  if (state.busy) {
    Dialogs.IndeterminateProgressDialog()
  }

  EditProfileDialog(dialog = state.dialog, onEvent = onEvent, sustainerBadgePreview = sustainerBadgePreview)
}

@Composable
private fun Avatar(
  state: EditProfileState,
  onEvent: (EditProfileEvent) -> Unit,
  avatar: @Composable () -> Unit
) {
  val setAvatar = stringResource(R.string.EditProfileScreen__set_avatar)

  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Box(
      modifier = Modifier
        .padding(top = 16.dp)
        .size(80.dp)
        .alpha(if (state.canEdit) 1f else DISABLED_ALPHA)
    ) {
      avatar()

      // Clicks land on a circle laid over the avatar, so a badge drawn past its edge isn't clipped with it.
      Box(
        modifier = Modifier
          .matchParentSize()
          .clip(CircleShape)
          .clickable(role = Role.Button) { onEvent(EditProfileEvent.AvatarClicked) }
          .semantics { contentDescription = setAvatar }
          .testTag(EditProfileTestTags.AVATAR)
      )
    }

    Buttons.Small(
      onClick = { onEvent(EditProfileEvent.EditPhotoClicked) },
      enabled = state.canEdit,
      modifier = Modifier
        .padding(top = 16.dp)
        .testTag(EditProfileTestTags.BUTTON_EDIT_PHOTO)
    ) {
      Text(text = stringResource(R.string.ManageProfileFragment__edit_photo))
    }
  }
}

/**
 * A row that opens one part of the profile. While the profile can't be edited it's dimmed, but still reports a click
 * so the user can be told why.
 */
@Composable
private fun ProfileRow(
  text: String,
  icon: @Composable () -> Unit,
  canEdit: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  textTestTag: String = "",
  error: Boolean = false,
  errorTestTag: String = ""
) {
  Rows.TextRow(
    text = {
      EmojiText(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = if (canEdit) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = DISABLED_ALPHA),
        modifier = Modifier
          .weight(1f)
          .testTag(textTestTag)
      )

      if (error) {
        Icon(
          imageVector = SignalIcons.ErrorCircleOutline.imageVector,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.error,
          modifier = Modifier
            .padding(start = 16.dp)
            .testTag(errorTestTag)
        )
      }
    },
    icon = {
      Box(modifier = Modifier.alpha(if (canEdit) 1f else DISABLED_ALPHA)) {
        icon()
      }
    },
    onClick = onClick,
    modifier = modifier
  )
}

@Composable
private fun RowIcon(content: @Composable () -> Unit) {
  Box(contentAlignment = Alignment.Center, modifier = Modifier.size(24.dp)) {
    content()
  }
}

@Composable
private fun Footer(text: String, modifier: Modifier = Modifier) {
  Text(
    text = text,
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = modifier
      .fillMaxWidth()
      .horizontalGutters()
      .padding(top = 8.dp, bottom = 16.dp)
  )
}

@Composable
private fun UsernameLinkTooltip(onDismiss: () -> Unit) {
  Tooltips.Inline(
    modifier = Modifier
      .fillMaxWidth()
      .padding(start = 24.dp, end = 24.dp, bottom = 24.dp)
      .testTag(EditProfileTestTags.TOOLTIP)
  ) {
    Row(verticalAlignment = Alignment.Top) {
      Icon(
        imageVector = SignalIcons.Share.imageVector,
        contentDescription = null,
        modifier = Modifier.padding(start = 16.dp, top = 21.dp)
      )

      Column(
        modifier = Modifier
          .weight(1f)
          .padding(start = 16.dp, top = 16.dp, end = 34.dp, bottom = 14.dp)
      ) {
        Text(
          text = stringResource(R.string.ManageProfileFragment__link_tooltip_title),
          style = MaterialTheme.typography.bodyLarge,
          fontWeight = FontWeight.Medium
        )
        Text(
          text = stringResource(R.string.ManageProfileFragment__link_tooltip_body),
          style = MaterialTheme.typography.bodyMedium,
          lineHeight = 20.sp,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }

    IconButton(
      onClick = onDismiss,
      modifier = Modifier
        .align(Alignment.TopEnd)
        .padding(top = 2.dp, end = 2.dp)
        .testTag(EditProfileTestTags.BUTTON_TOOLTIP_CLOSE)
    ) {
      Icon(
        imageVector = SignalIcons.X.imageVector,
        contentDescription = stringResource(R.string.EditProfileScreen__close_tip),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(20.dp)
      )
    }
  }
}

@Composable
private fun EditProfileDialog(
  dialog: EditProfileState.Dialog,
  onEvent: (EditProfileEvent) -> Unit,
  sustainerBadgePreview: @Composable () -> Unit
) {
  when (dialog) {
    EditProfileState.Dialog.None -> Unit

    EditProfileState.Dialog.UsernameOptions -> {
      Dialogs.ListDialog(
        options = listOf(
          stringResource(R.string.UsernameEditDialog__edit_username),
          stringResource(R.string.UsernameEditDialog__delete_username)
        ),
        onOptionClick = { index ->
          onEvent(if (index == 0) EditProfileEvent.EditUsernameClicked else EditProfileEvent.DeleteUsernameClicked)
        },
        onDismissRequest = { onEvent(EditProfileEvent.DialogDismissed) }
      )
    }

    is EditProfileState.Dialog.ConfirmDeleteUsername -> {
      Dialogs.SimpleAlertDialog(
        title = stringResource(R.string.ManageProfileFragment__delete_username_dialog_title),
        body = stringResource(R.string.ManageProfileFragment__delete_username_dialog_body, dialog.username),
        confirm = stringResource(R.string.EditProfileScreen__delete),
        dismiss = stringResource(android.R.string.cancel),
        onConfirm = { onEvent(EditProfileEvent.DeleteUsernameConfirmed) },
        onDeny = { onEvent(EditProfileEvent.DialogDismissed) },
        onDismissRequest = { onEvent(EditProfileEvent.DialogDismissed) }
      )
    }

    EditProfileState.Dialog.Unregistered -> {
      Dialogs.SimpleAlertDialog(
        title = stringResource(R.string.EditProfileFragment_unregistered_dialog_title),
        body = stringResource(R.string.EditProfileFragment_unregistered_dialog_body),
        confirm = stringResource(R.string.EditProfileFragment_unregistered_dialog_reregister_button),
        dismiss = stringResource(android.R.string.cancel),
        onConfirm = { onEvent(EditProfileEvent.ReregisterClicked) },
        onDeny = { onEvent(EditProfileEvent.DialogDismissed) },
        onDismissRequest = { onEvent(EditProfileEvent.DialogDismissed) }
      )
    }

    EditProfileState.Dialog.BecomeASustainer -> {
      BecomeASustainerSheet(onEvent = onEvent, badgePreview = sustainerBadgePreview)
    }

    EditProfileState.Dialog.Deprecated -> {
      Dialogs.SimpleAlertDialog(
        title = stringResource(R.string.EditProfileFragment_deprecated_dialog_title),
        body = stringResource(R.string.EditProfileFragment_deprecated_dialog_body),
        confirm = stringResource(R.string.EditProfileFragment_deprecated_dialog_update_button),
        dismiss = stringResource(android.R.string.cancel),
        onConfirm = { onEvent(EditProfileEvent.UpdateAppClicked) },
        onDeny = { onEvent(EditProfileEvent.DialogDismissed) },
        onDismissRequest = { onEvent(EditProfileEvent.DialogDismissed) }
      )
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BecomeASustainerSheet(
  onEvent: (EditProfileEvent) -> Unit,
  badgePreview: @Composable () -> Unit
) {
  BottomSheets.BottomSheet(
    onDismissRequest = { onEvent(EditProfileEvent.DialogDismissed) },
    dragHandle = { BottomSheets.Handle() }
  ) {
    BecomeASustainerSheetContent(onEvent = onEvent, badgePreview = badgePreview)
  }
}

/** The content of [BecomeASustainerSheet], apart from the sheet around it so it can be tested on its own. */
@VisibleForTesting
@Composable
internal fun BecomeASustainerSheetContent(
  onEvent: (EditProfileEvent) -> Unit,
  badgePreview: @Composable () -> Unit = {}
) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = Modifier
      .fillMaxWidth()
      .verticalScroll(rememberScrollState())
      .horizontalGutters()
      .padding(bottom = 32.dp)
      .testTag(EditProfileTestTags.BECOME_A_SUSTAINER_SHEET)
  ) {
    Box(
      modifier = Modifier
        .padding(top = 16.dp)
        .size(80.dp)
    ) {
      badgePreview()
    }

    Text(
      text = stringResource(R.string.BecomeASustainerFragment__get_badges),
      style = MaterialTheme.typography.titleLarge,
      textAlign = TextAlign.Center,
      modifier = Modifier.padding(top = 16.dp)
    )

    Text(
      text = stringResource(R.string.BecomeASustainerFragment__signal_is_a_non_profit),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
      modifier = Modifier.padding(top = 8.dp)
    )

    Buttons.LargeTonal(
      onClick = { onEvent(EditProfileEvent.BecomeASustainerClicked) },
      modifier = Modifier
        .padding(top = 32.dp)
        .testTag(EditProfileTestTags.BUTTON_BECOME_A_SUSTAINER)
    ) {
      Text(text = stringResource(R.string.EditProfileScreen__become_a_sustainer))
    }
  }
}

@Composable
private fun EditProfileState.Snackbar.message(): String? {
  return when (this) {
    EditProfileState.Snackbar.None -> null
    EditProfileState.Snackbar.UsernameCreated -> stringResource(R.string.ManageProfileFragment__username_created)
    EditProfileState.Snackbar.UsernameDeleted -> stringResource(R.string.ManageProfileFragment__username_deleted)
    EditProfileState.Snackbar.UsernameDeleteFailed -> stringResource(R.string.ManageProfileFragment__couldnt_delete_username)
    EditProfileState.Snackbar.AvatarReadFailed -> stringResource(R.string.ManageProfileFragment_failed_to_set_avatar)
    EditProfileState.Snackbar.AvatarNetworkFailure -> stringResource(R.string.EditProfileNameFragment_failed_to_save_due_to_network_issues_try_again_later)
  }
}

private val previewState = EditProfileState(
  profileName = "Alex Hart",
  about = "Speak freely",
  aboutEmoji = "👋",
  username = "spider.42"
)

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun EditProfileScreenPreview() {
  EditProfileScreen(state = previewState, onEvent = {})
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun EditProfileScreenEmptyPreview() {
  EditProfileScreen(state = EditProfileState(), onEvent = {})
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun EditProfileScreenTooltipPreview() {
  EditProfileScreen(state = previewState.copy(showUsernameLinkTooltip = true, usernameSyncState = EditProfileState.UsernameSyncState.LINK_CORRUPTED), onEvent = {})
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun EditProfileScreenCantEditPreview() {
  EditProfileScreen(state = previewState.copy(canEdit = false), onEvent = {})
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun EditProfileScreenBecomeASustainerPreview() {
  EditProfileScreen(state = previewState.copy(dialog = EditProfileState.Dialog.BecomeASustainer), onEvent = {})
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun EditProfileScreenUsernameOptionsPreview() {
  EditProfileScreen(state = previewState.copy(dialog = EditProfileState.Dialog.UsernameOptions), onEvent = {})
}
