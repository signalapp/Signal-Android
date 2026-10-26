/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.home

import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import org.signal.appsettings.AppSettingsLazyColumn
import org.signal.appsettings.R
import org.signal.appsettings.appSettingsScreen
import org.signal.appsettings.routes.AppSettingsRoute
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Dividers
import org.signal.core.ui.compose.IconButtons
import org.signal.core.ui.compose.Rows
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.compose.SignalPreviewWrapper
import org.signal.core.ui.compose.Snackbars
import org.signal.core.ui.compose.horizontalGutters
import org.signal.core.ui.compose.rememberIsSplitPaneSettings
import org.signal.core.ui.compose.selectionHighlight
import org.signal.core.ui.compose.theme.SignalTheme
import org.signal.emoji.Emojifier
import org.signal.core.ui.R as CoreUiR

@VisibleForTesting
object AppSettingsTestTags {
  const val SCROLLER = "scroller"
  const val ROW_PROFILE = "row-profile"
  const val BUTTON_USERNAME_LINK = "button-username-link"
  const val TEXT_PROFILE_NAME = "text-profile-name"
  const val TEXT_USERNAME = "text-username"
  const val TEXT_ABOUT = "text-about"
  const val ROW_BACKUP_WARNING = "row-backup-warning"
  const val ROW_ACCOUNT = "row-account"
  const val ROW_LINKED_DEVICES = "row-linked-devices"
  const val ROW_DONATE = "row-donate"
  const val ROW_APPEARANCE = "row-appearance"
  const val ROW_CHATS = "row-chats"
  const val ROW_STORIES = "row-stories"
  const val ROW_NOTIFICATIONS = "row-notifications"
  const val ROW_PRIVACY = "row-privacy"
  const val ROW_BACKUPS = "row-backups"
  const val ROW_DATA_AND_STORAGE = "row-data-and-storage"
  const val ROW_APP_UPDATES = "row-app-updates"
  const val ROW_PAYMENTS = "row-payments"
  const val ROW_HELP = "row-help"
  const val ROW_INVITE = "row-invite"
  const val ROW_LABS = "row-labs"
  const val ROW_INTERNAL = "row-internal"
}

/**
 * The top level of app settings: the user's profile, a row for each settings section, and any backup warnings.
 *
 * @param selectedRoute The section open beside this screen, whose row is highlighted. Null when nothing is beside it.
 * @param avatar The user's avatar and featured badge, drawn into an 80dp box beside their profile.
 * @param banner Shown above the list, for app-wide problems such as being unregistered.
 */
@Composable
fun AppSettingsScreen(
  state: AppSettingsState,
  onEvent: (AppSettingsEvent) -> Unit,
  modifier: Modifier = Modifier,
  containerColor: Color = if (rememberIsSplitPaneSettings()) SignalTheme.colors.colorSurface4 else MaterialTheme.colorScheme.background,
  contentColor: Color = MaterialTheme.colorScheme.contentColorFor(containerColor).takeOrElse { MaterialTheme.colorScheme.onSurface },
  selectedRoute: AppSettingsRoute? = null,
  avatar: @Composable () -> Unit = {},
  banner: @Composable () -> Unit = {}
) {
  LifecycleResumeEffect(Unit) {
    onEvent(AppSettingsEvent.ScreenResumed)
    onPauseOrDispose {}
  }

  Scaffolds.Settings(
    title = stringResource(R.string.AppSettingsScreen__settings),
    navigationContentDescription = stringResource(R.string.AppSettingsScreen__go_back),
    navigationIcon = SignalIcons.ArrowStart.imageVector,
    onNavigationClick = { onEvent(AppSettingsEvent.NavigateBackClicked) },
    snackbarHost = {
      Snackbars.Host(
        message = state.snackbar.message(),
        onDismissed = { onEvent(AppSettingsEvent.SnackbarDismissed) }
      )
    },
    containerColor = containerColor,
    contentColor = contentColor,
    collapseTopBarInSplitPane = true,
    modifier = modifier
  ) { contentPadding ->
    Column(
      modifier = Modifier.padding(contentPadding)
    ) {
      Box(modifier = Modifier.appSettingsScreen()) {
        banner()
      }

      AppSettingsLazyColumn(
        modifier = Modifier.testTag(AppSettingsTestTags.SCROLLER)
      ) {
        if (state.profile != null) {
          item {
            ProfileRow(
              profile = state.profile,
              avatar = avatar,
              onEvent = onEvent,
              modifier = Modifier.selectionHighlight(selectedRoute.isProfile())
            )
          }
        }

        if (state.backupFailureState != BackupFailureState.NONE) {
          item {
            Dividers.Default()

            BackupWarningRow(
              backupFailureState = state.backupFailureState,
              onClick = { onEvent(AppSettingsEvent.BackupWarningClicked) }
            )

            Dividers.Default()
          }
        }

        item {
          Rows.TextRow(
            text = stringResource(R.string.AccountSettingsFragment__account),
            icon = SignalIcons.PersonCircle.painter,
            onClick = { onEvent(AppSettingsEvent.AccountClicked) },
            modifier = Modifier
              .selectionHighlight(selectedRoute.isAccount())
              .testTag(AppSettingsTestTags.ROW_ACCOUNT)
          )
        }

        if (state.isPrimaryDevice) {
          item {
            Rows.TextRow(
              text = stringResource(R.string.AppSettingsScreen__linked_devices),
              icon = SignalIcons.Devices.painter,
              onClick = { onEvent(AppSettingsEvent.LinkedDevicesClicked) },
              enabled = state.isRegisteredAndUpToDate,
              modifier = Modifier
                .selectionHighlight(selectedRoute is AppSettingsRoute.LinkDeviceRoute)
                .testTag(AppSettingsTestTags.ROW_LINKED_DEVICES)
            )
          }
        }

        item {
          Rows.TextRow(
            text = {
              Text(
                text = stringResource(R.string.AppSettingsScreen__donate_to_signal),
                modifier = Modifier.weight(1f)
              )

              if (state.hasExpiredGiftBadge) {
                Icon(
                  painter = painterResource(CoreUiR.drawable.symbol_info_fill_24),
                  tint = colorResource(CoreUiR.color.signal_accent_primary),
                  contentDescription = null
                )
              }
            },
            icon = {
              Icon(
                painter = SignalIcons.Heart.painter,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface
              )
            },
            onClick = { onEvent(AppSettingsEvent.DonateClicked) },
            onLongClick = { onEvent(AppSettingsEvent.DonateLongClicked) },
            modifier = Modifier
              .selectionHighlight(selectedRoute is AppSettingsRoute.DonationsRoute)
              .testTag(AppSettingsTestTags.ROW_DONATE)
          )
        }

        item {
          Dividers.Default()
        }

        item {
          Rows.TextRow(
            text = stringResource(R.string.preferences__appearance),
            icon = SignalIcons.Appearance.painter,
            onClick = { onEvent(AppSettingsEvent.AppearanceClicked) },
            modifier = Modifier
              .selectionHighlight(selectedRoute is AppSettingsRoute.AppearanceRoute)
              .testTag(AppSettingsTestTags.ROW_APPEARANCE)
          )
        }

        item {
          Rows.TextRow(
            text = stringResource(R.string.preferences_chats__chats),
            icon = SignalIcons.Chat.painter,
            onClick = { onEvent(AppSettingsEvent.ChatsClicked) },
            enabled = state.isRegisteredAndUpToDate,
            modifier = Modifier
              .selectionHighlight(selectedRoute is AppSettingsRoute.ChatsRoute || selectedRoute is AppSettingsRoute.ChatFoldersRoute)
              .testTag(AppSettingsTestTags.ROW_CHATS)
          )
        }

        item {
          Rows.TextRow(
            text = stringResource(R.string.AppSettingsScreen__stories),
            icon = SignalIcons.Stories.painter,
            onClick = { onEvent(AppSettingsEvent.StoriesClicked) },
            enabled = state.isRegisteredAndUpToDate,
            modifier = Modifier
              .selectionHighlight(selectedRoute is AppSettingsRoute.StoriesRoute)
              .testTag(AppSettingsTestTags.ROW_STORIES)
          )
        }

        item {
          Rows.TextRow(
            text = stringResource(R.string.AppSettingsScreen__notifications),
            icon = SignalIcons.Bell.painter,
            onClick = { onEvent(AppSettingsEvent.NotificationsClicked) },
            enabled = state.isRegisteredAndUpToDate,
            modifier = Modifier
              .selectionHighlight(selectedRoute is AppSettingsRoute.NotificationsRoute)
              .testTag(AppSettingsTestTags.ROW_NOTIFICATIONS)
          )
        }

        item {
          Rows.TextRow(
            text = stringResource(R.string.preferences__privacy),
            icon = SignalIcons.Lock.painter,
            onClick = { onEvent(AppSettingsEvent.PrivacyClicked) },
            enabled = state.isRegisteredAndUpToDate,
            modifier = Modifier
              .selectionHighlight(selectedRoute is AppSettingsRoute.PrivacyRoute)
              .testTag(AppSettingsTestTags.ROW_PRIVACY)
          )
        }

        item {
          Rows.TextRow(
            icon = SignalIcons.Backup.imageVector,
            text = stringResource(R.string.AppSettingsScreen__backups),
            onClick = { onEvent(AppSettingsEvent.BackupsClicked) },
            onLongClick = { onEvent(AppSettingsEvent.BackupsLongClicked) },
            enabled = state.isRegisteredAndUpToDate,
            modifier = Modifier
              .selectionHighlight(selectedRoute is AppSettingsRoute.BackupsRoute)
              .testTag(AppSettingsTestTags.ROW_BACKUPS)
          )
        }

        item {
          Rows.TextRow(
            text = stringResource(R.string.preferences__data_and_storage),
            icon = SignalIcons.Data.painter,
            onClick = { onEvent(AppSettingsEvent.DataAndStorageClicked) },
            modifier = Modifier
              .selectionHighlight(selectedRoute is AppSettingsRoute.DataAndStorageRoute)
              .testTag(AppSettingsTestTags.ROW_DATA_AND_STORAGE)
          )
        }

        if (state.showAppUpdates) {
          item {
            Rows.TextRow(
              text = "App updates",
              icon = SignalIcons.Calendar.painter,
              onClick = { onEvent(AppSettingsEvent.AppUpdatesClicked) },
              modifier = Modifier
                .selectionHighlight(selectedRoute == AppSettingsRoute.AppUpdates)
                .testTag(AppSettingsTestTags.ROW_APP_UPDATES)
            )
          }
        }

        if (state.isPrimaryDevice && state.showPayments) {
          item {
            Dividers.Default()
          }

          item {
            PaymentsRow(
              unreadPaymentsCount = state.unreadPaymentsCount,
              onClick = { onEvent(AppSettingsEvent.PaymentsClicked) },
              modifier = Modifier.selectionHighlight(selectedRoute == AppSettingsRoute.Payments)
            )
          }
        }

        item {
          Dividers.Default()
        }

        item {
          Rows.TextRow(
            text = stringResource(R.string.preferences__help),
            icon = SignalIcons.Help.painter,
            onClick = { onEvent(AppSettingsEvent.HelpClicked) },
            modifier = Modifier
              .selectionHighlight(selectedRoute is AppSettingsRoute.HelpRoute)
              .testTag(AppSettingsTestTags.ROW_HELP)
          )
        }

        item {
          Rows.TextRow(
            text = stringResource(R.string.AppSettingsFragment__invite_your_friends),
            icon = SignalIcons.Invite.painter,
            onClick = { onEvent(AppSettingsEvent.InviteClicked) },
            modifier = Modifier
              .selectionHighlight(selectedRoute == AppSettingsRoute.Invite)
              .testTag(AppSettingsTestTags.ROW_INVITE)
          )
        }

        if (state.showInternalPreferences) {
          item {
            Dividers.Default()
          }

          item {
            Rows.TextRow(
              text = "Labs",
              icon = SignalIcons.FlashOn.painter,
              onClick = { onEvent(AppSettingsEvent.LabsClicked) },
              modifier = Modifier
                .selectionHighlight(selectedRoute is AppSettingsRoute.LabsRoute)
                .testTag(AppSettingsTestTags.ROW_LABS)
            )
          }

          item {
            Rows.TextRow(
              text = stringResource(R.string.AppSettingsScreen__internal_preferences),
              icon = SignalIcons.Key.painter,
              onClick = { onEvent(AppSettingsEvent.InternalClicked) },
              modifier = Modifier
                .selectionHighlight(selectedRoute is AppSettingsRoute.InternalRoute)
                .testTag(AppSettingsTestTags.ROW_INTERNAL)
            )
          }
        }
      }
    }
  }
}

private fun AppSettingsRoute?.isProfile(): Boolean {
  return this == AppSettingsRoute.AccountRoute.ManageProfile || this is AppSettingsRoute.AccountRoute.Username || this is AppSettingsRoute.UsernameLinkRoute
}

private fun AppSettingsRoute?.isAccount(): Boolean {
  return (this is AppSettingsRoute.AccountRoute && !isProfile()) || this is AppSettingsRoute.ChangeNumberRoute
}

@Composable
private fun BackupWarningRow(
  backupFailureState: BackupFailureState,
  onClick: () -> Unit
) {
  val modifier = Modifier.testTag(AppSettingsTestTags.ROW_BACKUP_WARNING)

  if (backupFailureState == BackupFailureState.OUT_OF_STORAGE_SPACE) {
    Rows.TextRow(
      text = stringResource(R.string.AppSettingsFragment__backup_storage_limit_reached),
      icon = SignalIcons.ErrorCircle.imageVector,
      iconTint = MaterialTheme.colorScheme.error,
      onClick = onClick,
      modifier = modifier
    )
    return
  }

  val text = when (backupFailureState) {
    BackupFailureState.SUBSCRIPTION_STATE_MISMATCH -> stringResource(R.string.AppSettingsFragment__renew_your_signal_backups_subscription)
    BackupFailureState.BACKUP_FAILED, BackupFailureState.COULD_NOT_COMPLETE_BACKUP -> stringResource(R.string.AppSettingsFragment__couldnt_complete_backup)
    BackupFailureState.ALREADY_REDEEMED -> stringResource(R.string.AppSettingsScreen__couldnt_redeem_your_backups_subscription)
    BackupFailureState.OUT_OF_STORAGE_SPACE, BackupFailureState.NONE -> error("No warning row for $backupFailureState")
  }

  Rows.TextRow(
    text = {
      Text(text = text)
    },
    icon = {
      Box {
        Icon(
          imageVector = SignalIcons.Backup.imageVector,
          tint = MaterialTheme.colorScheme.onSurface,
          contentDescription = null
        )

        Box(
          modifier = Modifier
            .absoluteOffset(3.dp, (-2).dp)
            .background(color = Color(0xFFFFCC00), shape = CircleShape)
            .size(12.dp)
            .align(Alignment.TopEnd)
        )
      }
    },
    onClick = onClick,
    modifier = modifier
  )
}

@Composable
private fun PaymentsRow(
  unreadPaymentsCount: Int,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  Rows.TextRow(
    text = {
      Text(
        text = stringResource(R.string.AppSettingsScreen__payments),
        modifier = Modifier.weight(1f)
      )

      if (unreadPaymentsCount > 0) {
        Text(
          text = unreadPaymentsCount.toString(),
          color = MaterialTheme.colorScheme.inverseOnSurface,
          style = MaterialTheme.typography.bodyMedium,
          textAlign = TextAlign.Center,
          modifier = Modifier
            .background(
              color = MaterialTheme.colorScheme.primary,
              shape = RoundedCornerShape(50)
            )
            .defaultMinSize(minWidth = 30.dp)
            .padding(4.dp)
        )
      }
    },
    icon = {
      Icon(
        painter = SignalIcons.Payment.painter,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurface
      )
    },
    onClick = onClick,
    modifier = modifier.testTag(AppSettingsTestTags.ROW_PAYMENTS)
  )
}

@Composable
private fun ProfileRow(
  profile: AppSettingsState.Profile,
  avatar: @Composable () -> Unit,
  onEvent: (AppSettingsEvent) -> Unit,
  modifier: Modifier = Modifier
) {
  val hasUsername = profile.username.isNotBlank()

  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = modifier
      .clickable(onClick = { onEvent(AppSettingsEvent.ProfileClicked) })
      .horizontalGutters()
      .testTag(AppSettingsTestTags.ROW_PROFILE)
  ) {
    Box(
      modifier = Modifier
        .padding(vertical = 24.dp)
        .size(80.dp)
    ) {
      avatar()
    }

    Column(
      modifier = Modifier
        .weight(1f)
        .padding(start = 24.dp, end = 12.dp)
    ) {
      Emojifier(text = profile.name) { annotatedString, inlineTextContentMap ->
        Text(
          text = annotatedString,
          inlineContent = inlineTextContentMap,
          style = MaterialTheme.typography.titleLarge,
          modifier = Modifier.testTag(AppSettingsTestTags.TEXT_PROFILE_NAME)
        )
      }

      if (profile.formattedPhoneNumber.isNotBlank()) {
        Text(
          text = profile.formattedPhoneNumber,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          style = TextStyle(
            textDirection = TextDirection.ContentOrLtr
          )
        )
      }

      if (hasUsername) {
        Text(
          text = profile.username,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.testTag(AppSettingsTestTags.TEXT_USERNAME)
        )
      }

      if (profile.about != null) {
        Emojifier(
          text = profile.about
        ) { annotatedString, inlineTextContentMap ->
          Text(
            text = annotatedString,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            inlineContent = inlineTextContentMap,
            modifier = Modifier
              .padding(top = 8.dp)
              .testTag(AppSettingsTestTags.TEXT_ABOUT)
          )
        }
      }
    }

    if (hasUsername) {
      IconButtons.IconButton(
        onClick = { onEvent(AppSettingsEvent.UsernameLinkClicked) },
        size = 36.dp,
        colors = IconButtons.iconButtonColors(
          containerColor = SignalTheme.colors.colorSurface4
        ),
        modifier = Modifier.testTag(AppSettingsTestTags.BUTTON_USERNAME_LINK)
      ) {
        Icon(
          painter = SignalIcons.QrCode.painter,
          contentDescription = null,
          modifier = Modifier.size(20.dp)
        )
      }
    }
  }
}

@Composable
private fun AppSettingsState.Snackbar.message(): String? {
  return when (this) {
    AppSettingsState.Snackbar.None -> null
    AppSettingsState.Snackbar.CopiedDonorSubscriberId -> stringResource(R.string.AppSettingsFragment__copied_donor_subscriber_id_to_clipboard)
    AppSettingsState.Snackbar.CopiedBackupsSubscriberId -> stringResource(R.string.AppSettingsFragment__copied_backups_subscriber_id_to_clipboard)
  }
}

internal val appSettingsPreviewState = AppSettingsState(
  profile = AppSettingsState.Profile(
    name = "Miles Morales ❤\uFE0F",
    formattedPhoneNumber = "+1 555-555-5555",
    username = "miles.98",
    about = "❤\uFE0F About"
  ),
  isPrimaryDevice = true,
  unreadPaymentsCount = 5,
  hasExpiredGiftBadge = true,
  allowUserToGoToDonationManagementScreen = true,
  showInternalPreferences = true,
  showPayments = true,
  showAppUpdates = true,
  backupFailureState = BackupFailureState.OUT_OF_STORAGE_SPACE
)

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun AppSettingsScreenPreview() {
  AppSettingsScreen(
    state = appSettingsPreviewState,
    onEvent = {},
    avatar = { PreviewAvatar() }
  )
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun AppSettingsScreenUnregisteredPreview() {
  AppSettingsScreen(
    state = appSettingsPreviewState.copy(userUnregistered = true),
    onEvent = {},
    avatar = { PreviewAvatar() }
  )
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun AppSettingsScreenNoPhoneNumberPreview() {
  AppSettingsScreen(
    state = appSettingsPreviewState.copy(
      profile = appSettingsPreviewState.profile?.copy(formattedPhoneNumber = ""),
      backupFailureState = BackupFailureState.COULD_NOT_COMPLETE_BACKUP
    ),
    onEvent = {},
    avatar = { PreviewAvatar() }
  )
}

@Composable
internal fun PreviewAvatar() {
  Box(
    modifier = Modifier
      .size(80.dp)
      .background(color = MaterialTheme.colorScheme.primaryContainer, shape = CircleShape)
  )
}
