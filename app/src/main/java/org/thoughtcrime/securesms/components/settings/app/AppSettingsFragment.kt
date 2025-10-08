package org.thoughtcrime.securesms.components.settings.app

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Dividers
import org.signal.core.ui.compose.IconButtons
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.Rows
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.horizontalGutters
import org.signal.core.ui.compose.theme.SignalTheme
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.avatar.AvatarImage
import org.thoughtcrime.securesms.backup.v2.BackupRepository
import org.thoughtcrime.securesms.banner.Banner
import org.thoughtcrime.securesms.banner.BannerManager
import org.thoughtcrime.securesms.banner.banners.DeprecatedBuildBanner
import org.thoughtcrime.securesms.banner.banners.UnauthorizedBanner
import org.thoughtcrime.securesms.banner.ui.compose.Action
import org.thoughtcrime.securesms.banner.ui.compose.DefaultBanner
import org.thoughtcrime.securesms.banner.ui.compose.Importance
import org.thoughtcrime.securesms.components.compose.TextWithBetaLabel
import org.thoughtcrime.securesms.components.emoji.Emojifier
import org.thoughtcrime.securesms.components.settings.app.routes.AppSettingsRoute
import org.thoughtcrime.securesms.components.settings.app.routes.AppSettingsRouter
import org.thoughtcrime.securesms.components.settings.app.subscription.BadgeImageMedium
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppPaymentsRepository
import org.thoughtcrime.securesms.components.settings.app.subscription.completed.InAppPaymentsBottomSheetDelegate
import org.thoughtcrime.securesms.compose.ComposeFragment
import org.thoughtcrime.securesms.compose.rememberStatusBarColorNestedScrollModifier
import org.thoughtcrime.securesms.database.model.InAppPaymentSubscriberRecord
import org.thoughtcrime.securesms.profiles.ProfileName
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.CommunicationActions
import org.thoughtcrime.securesms.util.SignalE164Util
import org.thoughtcrime.securesms.util.Util
import org.thoughtcrime.securesms.util.navigation.safeNavigate

class AppSettingsFragment : ComposeFragment(), Callbacks {

  private val viewModel: AppSettingsViewModel by viewModels()
  private val appSettingsRouter by viewModels<AppSettingsRouter>()

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    viewLifecycleOwner.lifecycle.addObserver(InAppPaymentsBottomSheetDelegate(childFragmentManager, viewLifecycleOwner))

    viewLifecycleOwner.lifecycleScope.launch {
      repeatOnLifecycle(Lifecycle.State.RESUMED) {
        appSettingsRouter.currentRoute.collect { route ->
          when (route) {
            is AppSettingsRoute.BackupsRoute.Remote -> findNavController().safeNavigate(R.id.action_appSettingsFragment_to_remoteBackupsSettingsFragment)
            is AppSettingsRoute.AccountRoute.Account -> findNavController().safeNavigate(R.id.action_appSettingsFragment_to_accountSettingsFragment)
            is AppSettingsRoute.LinkDeviceRoute.LinkDevice -> findNavController().safeNavigate(R.id.action_appSettingsFragment_to_linkDeviceFragment)
            is AppSettingsRoute.DonationsRoute.Donations -> findNavController().safeNavigate(R.id.action_appSettingsFragment_to_manageDonationsFragment)
            is AppSettingsRoute.AppearanceRoute.Appearance -> findNavController().safeNavigate(R.id.action_appSettingsFragment_to_appearanceSettingsFragment)
            is AppSettingsRoute.ChatsRoute.Chats -> findNavController().safeNavigate(R.id.action_appSettingsFragment_to_chatsSettingsFragment)
            is AppSettingsRoute.StoriesRoute.Privacy -> findNavController().safeNavigate(AppSettingsFragmentDirections.actionAppSettingsFragmentToStoryPrivacySettings(route.titleId))
            is AppSettingsRoute.NotificationsRoute.Notifications -> findNavController().safeNavigate(R.id.action_appSettingsFragment_to_notificationsSettingsFragment)
            is AppSettingsRoute.PrivacyRoute.Privacy -> findNavController().safeNavigate(R.id.action_appSettingsFragment_to_privacySettingsFragment)
            is AppSettingsRoute.BackupsRoute.Backups -> findNavController().safeNavigate(R.id.action_appSettingsFragment_to_backupsSettingsFragment)
            is AppSettingsRoute.DataAndStorageRoute.DataAndStorage -> findNavController().safeNavigate(R.id.action_appSettingsFragment_to_dataAndStorageSettingsFragment)
            is AppSettingsRoute.AppUpdates -> findNavController().safeNavigate(R.id.action_appSettingsFragment_to_appUpdatesSettingsFragment)
            is AppSettingsRoute.Payments -> findNavController().safeNavigate(R.id.action_appSettingsFragment_to_paymentsActivity)
            is AppSettingsRoute.HelpRoute.Settings -> findNavController().safeNavigate(R.id.action_appSettingsFragment_to_helpSettingsFragment)
            is AppSettingsRoute.Invite -> findNavController().safeNavigate(R.id.action_appSettingsFragment_to_inviteFragment)
            is AppSettingsRoute.InternalRoute.Internal -> findNavController().safeNavigate(R.id.action_appSettingsFragment_to_internalSettingsFragment)
            is AppSettingsRoute.AccountRoute.ManageProfile -> findNavController().safeNavigate(R.id.action_appSettingsFragment_to_manageProfileActivity)
            is AppSettingsRoute.UsernameLinkRoute.UsernameLink -> findNavController().safeNavigate(R.id.action_appSettingsFragment_to_usernameLinkSettingsFragment)
            else -> error("Unsupported route: ${route.javaClass.name}")
          }
        }
      }
    }
  }

  @Composable
  override fun FragmentContent() {
    val state by viewModel.state.observeAsState()
    val self by viewModel.self.observeAsState()

    if (state == null) return
    if (self == null) return

    val context = LocalContext.current
    val bannerManager = remember {
      BannerManager(
        banners = listOf(
          DeprecatedBuildBanner(),
          UnauthorizedBanner(context)
        )
      )
    }

    AppSettingsContent(
      self = self!!,
      state = state!!,
      bannerManager = bannerManager,
      callbacks = this
    )
  }

  override fun onNavigationClick() {
    requireActivity().finishAfterTransition()
  }

  override fun navigate(route: AppSettingsRoute) {
    appSettingsRouter.navigateTo(route)
  }

  override fun onResume() {
    super.onResume()
    viewModel.refresh()
    viewModel.refreshDeprecatedOrUnregistered()
  }

  override fun copyDonorBadgeSubscriberIdToClipboard() {
    copySubscriberIdToClipboard(
      subscriberType = InAppPaymentSubscriberRecord.Type.DONATION,
      toastSuccessStringRes = R.string.AppSettingsFragment__copied_donor_subscriber_id_to_clipboard
    )
  }

  override fun copyRemoteBackupsSubscriberIdToClipboard() {
    copySubscriberIdToClipboard(
      subscriberType = InAppPaymentSubscriberRecord.Type.BACKUP,
      toastSuccessStringRes = R.string.AppSettingsFragment__copied_backups_subscriber_id_to_clipboard
    )
  }

  private fun copySubscriberIdToClipboard(
    subscriberType: InAppPaymentSubscriberRecord.Type,
    @StringRes toastSuccessStringRes: Int
  ) {
    lifecycleScope.launch {
      val subscriber = withContext(Dispatchers.IO) {
        InAppPaymentsRepository.getSubscriber(subscriberType)
      }

      withContext(Dispatchers.Main) {
        if (subscriber != null) {
          Toast.makeText(requireContext(), toastSuccessStringRes, Toast.LENGTH_LONG).show()
          Util.copyToClipboard(requireContext(), subscriber.subscriberId.serialize())
        }
      }
    }
  }
}

@Composable
private fun AppSettingsContent(
  self: BioRecipientState,
  state: AppSettingsState,
  bannerManager: BannerManager,
  callbacks: Callbacks
) {
  val isRegisteredAndUpToDate by rememberUpdatedState(state.isRegisteredAndUpToDate())

  Scaffolds.Settings(
    title = stringResource(R.string.text_secure_normal__menu_settings),
    navigationContentDescription = stringResource(R.string.CallScreenTopBar__go_back),
    navigationIcon = ImageVector.vectorResource(R.drawable.symbol_arrow_start_24),
    onNavigationClick = callbacks::onNavigationClick
  ) { contentPadding ->
    Column(
      modifier = Modifier.padding(contentPadding)
    ) {
      bannerManager.Banner()

      LazyColumn(
        modifier = rememberStatusBarColorNestedScrollModifier()
      ) {
        item {
          BioRow(
            self = self,
            callbacks = callbacks
          )
        }

        when (state.backupFailureState) {
          BackupFailureState.SUBSCRIPTION_STATE_MISMATCH -> {
            item {
              Dividers.Default()

              BackupsWarningRow(
                text = stringResource(R.string.AppSettingsFragment__renew_your_signal_backups_subscription),
                onClick = {
                  callbacks.navigate(AppSettingsRoute.BackupsRoute.Remote())
                }
              )

              Dividers.Default()
            }
          }

          BackupFailureState.BACKUP_FAILED, BackupFailureState.COULD_NOT_COMPLETE_BACKUP -> {
            item {
              Dividers.Default()

              BackupsWarningRow(
                text = stringResource(R.string.AppSettingsFragment__couldnt_complete_backup),
                onClick = {
                  BackupRepository.markBackupFailedIndicatorClicked()
                  callbacks.navigate(AppSettingsRoute.BackupsRoute.Remote())
                }
              )

              Dividers.Default()
            }
          }

          BackupFailureState.ALREADY_REDEEMED -> {
            item {
              Dividers.Default()

              BackupsWarningRow(
                text = stringResource(R.string.AppSettingsFragment__couldnt_redeem_your_backups_subscription),
                onClick = {
                  BackupRepository.markBackupAlreadyRedeemedIndicatorClicked()
                  callbacks.navigate(AppSettingsRoute.BackupsRoute.Remote())
                }
              )

              Dividers.Default()
            }
          }

          BackupFailureState.OUT_OF_STORAGE_SPACE -> {
            item {
              Dividers.Default()

              Rows.TextRow(
                text = stringResource(R.string.AppSettingsFragment__backup_storage_limit_reached),
                icon = ImageVector.vectorResource(R.drawable.symbol_error_circle_fill_24),
                iconTint = MaterialTheme.colorScheme.error,
                onClick = {
                  callbacks.navigate(AppSettingsRoute.BackupsRoute.Remote())
                }
              )

              Dividers.Default()
            }
          }

          BackupFailureState.NONE -> Unit
        }

        if (state.isPrimaryDevice) {
          item {
            Rows.TextRow(
              text = stringResource(R.string.AccountSettingsFragment__account),
              icon = painterResource(R.drawable.symbol_person_circle_24),
              onClick = {
                callbacks.navigate(AppSettingsRoute.AccountRoute.Account)
              }
            )
          }

          item {
            Rows.TextRow(
              text = stringResource(R.string.preferences__linked_devices),
              icon = painterResource(R.drawable.symbol_devices_24),
              onClick = {
                callbacks.navigate(AppSettingsRoute.LinkDeviceRoute.LinkDevice)
              },
              enabled = isRegisteredAndUpToDate
            )
          }

          item {
            val context = LocalContext.current
            val donateUrl = stringResource(R.string.donate_url)

            Rows.TextRow(
              text = {
                Text(
                  text = stringResource(R.string.preferences__donate_to_signal),
                  modifier = Modifier.weight(1f)
                )

                if (state.hasExpiredGiftBadge) {
                  Icon(
                    painter = painterResource(R.drawable.symbol_info_fill_24),
                    tint = colorResource(R.color.signal_accent_primary),
                    contentDescription = null
                  )
                }
              },
              icon = {
                Icon(
                  painter = painterResource(R.drawable.symbol_heart_24),
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.onSurface
                )
              },
              onClick = {
                if (state.allowUserToGoToDonationManagementScreen) {
                  callbacks.navigate(AppSettingsRoute.DonationsRoute.Donations())
                } else {
                  CommunicationActions.openBrowserLink(context, donateUrl)
                }
              },
              onLongClick = {
                callbacks.copyDonorBadgeSubscriberIdToClipboard()
              }
            )
          }

          item {
            Dividers.Default()
          }
        }

        item {
          Rows.TextRow(
            text = stringResource(R.string.preferences__appearance),
            icon = painterResource(R.drawable.symbol_appearance_24),
            onClick = {
              callbacks.navigate(AppSettingsRoute.AppearanceRoute.Appearance)
            }
          )
        }

        item {
          Rows.TextRow(
            text = stringResource(R.string.preferences_chats__chats),
            icon = painterResource(R.drawable.symbol_chat_24),
            onClick = {
              callbacks.navigate(AppSettingsRoute.ChatsRoute.Chats)
            },
            enabled = isRegisteredAndUpToDate
          )
        }

        item {
          Rows.TextRow(
            text = stringResource(R.string.preferences__stories),
            icon = painterResource(R.drawable.symbol_stories_24),
            onClick = {
              callbacks.navigate(AppSettingsRoute.StoriesRoute.Privacy(titleId = R.string.preferences__stories))
            },
            enabled = isRegisteredAndUpToDate
          )
        }

        item {
          Rows.TextRow(
            text = stringResource(R.string.preferences__notifications),
            icon = painterResource(R.drawable.symbol_bell_24),
            onClick = {
              callbacks.navigate(AppSettingsRoute.NotificationsRoute.Notifications)
            },
            enabled = isRegisteredAndUpToDate
          )
        }

        item {
          Rows.TextRow(
            text = stringResource(R.string.preferences__privacy),
            icon = painterResource(R.drawable.symbol_lock_24),
            onClick = {
              callbacks.navigate(AppSettingsRoute.PrivacyRoute.Privacy)
            },
            enabled = isRegisteredAndUpToDate
          )
        }

        if (state.isPrimaryDevice) {
          item {
            Rows.TextRow(
              text = {
                TextWithBetaLabel(
                  text = stringResource(R.string.preferences_chats__backups),
                  textStyle = MaterialTheme.typography.bodyLarge
                )
              },
              icon = {
                Icon(
                  imageVector = ImageVector.vectorResource(R.drawable.symbol_backup_24),
                  contentDescription = stringResource(R.string.preferences_chats__backups),
                  tint = MaterialTheme.colorScheme.onSurface
                )
              },
              onClick = {
                callbacks.navigate(AppSettingsRoute.BackupsRoute.Backups)
              },
              onLongClick = {
                callbacks.copyRemoteBackupsSubscriberIdToClipboard()
              },
              enabled = isRegisteredAndUpToDate
            )
          }
        }

        item {
          Rows.TextRow(
            text = stringResource(R.string.preferences__data_and_storage),
            icon = painterResource(R.drawable.symbol_data_24),
            onClick = {
              callbacks.navigate(AppSettingsRoute.DataAndStorageRoute.DataAndStorage)
            }
          )
        }

        if (state.showAppUpdates) {
          item {
            Rows.TextRow(
              text = "App updates",
              icon = painterResource(R.drawable.symbol_calendar_24),
              onClick = {
                callbacks.navigate(AppSettingsRoute.AppUpdates)
              }
            )
          }
        }

        if (state.isPrimaryDevice && state.showPayments) {
          item {
            Dividers.Default()
          }

          item {
            Rows.TextRow(
              text = {
                Text(
                  text = stringResource(R.string.preferences__payments),
                  modifier = Modifier.weight(1f)
                )

                if (state.unreadPaymentsCount > 0) {
                  Text(
                    text = state.unreadPaymentsCount.toString(),
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
                  painter = painterResource(R.drawable.symbol_payment_24),
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.onSurface
                )
              },
              onClick = {
                callbacks.navigate(AppSettingsRoute.Payments)
              }
            )
          }
        }

        item {
          Dividers.Default()
        }

        item {
          Rows.TextRow(
            text = stringResource(R.string.preferences__help),
            icon = painterResource(R.drawable.symbol_help_24),
            onClick = {
              callbacks.navigate(AppSettingsRoute.HelpRoute.Settings())
            }
          )
        }

        item {
          Rows.TextRow(
            text = stringResource(R.string.AppSettingsFragment__invite_your_friends),
            icon = painterResource(R.drawable.symbol_invite_24),
            onClick = {
              callbacks.navigate(AppSettingsRoute.Invite)
            }
          )
        }

        if (state.showInternalPreferences) {
          item {
            Dividers.Default()
          }

          item {
            Rows.TextRow(
              text = stringResource(R.string.preferences__internal_preferences),
              onClick = {
                callbacks.navigate(AppSettingsRoute.InternalRoute.Internal)
              }
            )
          }
        }
      }
    }
  }
}

@Composable
private fun BackupsWarningRow(
  text: String,
  onClick: () -> Unit
) {
  Rows.TextRow(
    text = {
      Text(text = text)
    },
    icon = {
      Box {
        Icon(
          imageVector = ImageVector.vectorResource(R.drawable.symbol_backup_24),
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
    onClick = onClick
  )
}

@Composable
private fun BioRow(
  self: BioRecipientState,
  callbacks: Callbacks
) {
  val hasUsername by rememberUpdatedState(self.username.isNotBlank())

  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier
      .clickable(
        onClick = {
          callbacks.navigate(AppSettingsRoute.AccountRoute.ManageProfile)
        }
      )
      .horizontalGutters()
  ) {
    Box {
      AvatarImage(
        recipient = self.recipient,
        modifier = Modifier
          .padding(vertical = 24.dp)
          .size(80.dp)
      )

      if (self.featuredBadge != null) {
        BadgeImageMedium(
          badge = self.featuredBadge,
          modifier = Modifier
            .padding(bottom = 24.dp)
            .size(24.dp)
            .align(Alignment.BottomEnd)
        )
      }
    }

    Column(
      modifier = Modifier
        .weight(1f)
        .padding(start = 24.dp, end = 12.dp)
    ) {
      Emojifier(text = self.profileName.toString()) { annotatedString, inlineTextContentMap ->
        Text(
          text = annotatedString,
          inlineContent = inlineTextContentMap,
          style = MaterialTheme.typography.titleLarge
        )
      }

      val prettyPhoneNumber = if (LocalInspectionMode.current) {
        self.e164
      } else {
        remember(self.e164) {
          SignalE164Util.prettyPrint(self.e164)
        }
      }

      Text(
        text = prettyPhoneNumber,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )

      if (hasUsername) {
        Text(
          text = self.username,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }

      if (self.combinedAboutAndEmoji != null) {
        Emojifier(
          text = self.combinedAboutAndEmoji
        ) { annotatedString, inlineTextContentMap ->
          Text(
            text = annotatedString,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            inlineContent = inlineTextContentMap,
            modifier = Modifier.padding(top = 8.dp)
          )
        }
      }
    }

    if (hasUsername) {
      IconButtons.IconButton(
        onClick = {
          callbacks.navigate(AppSettingsRoute.UsernameLinkRoute.UsernameLink)
        },
        size = 36.dp,
        colors = IconButtons.iconButtonColors(
          containerColor = SignalTheme.colors.colorSurface4
        )
      ) {
        Icon(
          painter = painterResource(R.drawable.symbol_qrcode_24),
          contentDescription = null,
          modifier = Modifier.size(20.dp)
        )
      }
    }
  }
}

@DayNightPreviews
@Composable
private fun AppSettingsContentPreview() {
  Previews.Preview {
    AppSettingsContent(
      self = BioRecipientState(
        Recipient(
          systemContactName = "Miles Morales",
          profileName = ProfileName.fromParts("Miles", "Morales ❤\uFE0F"),
          isSelf = true,
          e164Value = "+15555555555",
          usernameValue = "miles.98",
          aboutEmoji = "❤\uFE0F",
          about = "About",
          isResolving = false
        )
      ),
      state = AppSettingsState(
        isPrimaryDevice = true,
        unreadPaymentsCount = 5,
        hasExpiredGiftBadge = true,
        allowUserToGoToDonationManagementScreen = true,
        userUnregistered = false,
        clientDeprecated = false,
        showInternalPreferences = true,
        showPayments = true,
        showAppUpdates = true,
        backupFailureState = BackupFailureState.OUT_OF_STORAGE_SPACE
      ),
      bannerManager = BannerManager(
        banners = listOf(TestBanner())
      ),
      callbacks = EmptyCallbacks
    )
  }
}

@DayNightPreviews
@Composable
private fun BioRowPreview() {
  Previews.Preview {
    BioRow(
      self = BioRecipientState(
        Recipient(
          systemContactName = "Miles Morales",
          profileName = ProfileName.fromParts("Miles", "Morales ❤\uFE0F"),
          isSelf = true,
          e164Value = "+15555555555",
          usernameValue = "miles.98",
          aboutEmoji = "❤\uFE0F",
          about = "About",
          isResolving = false
        )
      ),
      callbacks = EmptyCallbacks
    )
  }
}

private interface Callbacks {
  fun onNavigationClick(): Unit = error("Not implemented.")
  fun navigate(route: AppSettingsRoute): Unit = error("Not implemented")
  fun copyDonorBadgeSubscriberIdToClipboard(): Unit = error("Not implemented")
  fun copyRemoteBackupsSubscriberIdToClipboard(): Unit = error("Not implemented")
}

private object EmptyCallbacks : Callbacks

private class TestBanner : Banner<Unit>() {
  override val enabled: Boolean = true
  override val dataFlow: Flow<Unit> = flowOf(Unit)

  @Composable
  override fun DisplayBanner(model: Unit, contentPadding: PaddingValues) {
    DefaultBanner(
      title = "Test Title",
      body = "This is a test body",
      importance = Importance.ERROR,
      actions = listOf(
        Action(android.R.string.ok) {}
      ),
      paddingValues = contentPadding
    )
  }
}
