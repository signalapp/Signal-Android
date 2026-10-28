/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.routes

import android.os.Parcelable
import androidx.annotation.StringRes
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable
import org.signal.core.models.recipients.RecipientId
import org.signal.core.ui.compose.split.DetailNavKey
import org.signal.donations.InAppPaymentType

/**
 * Describes a route that the AppSettings screen can open. Every route listed here is displayed in
 * the PRIMARY (detail) pane of the AppSettingsScreen.
 */
@Serializable
@Parcelize
sealed interface AppSettingsRoute : Parcelable, DetailNavKey {

  /**
   * The sections opened from [AppSettingsHomeRoute] or directly by a start route. Opening one replaces whatever
   * detail is displayed, rather than stacking on top of it.
   */
  @IgnoredOnParcel
  override val isContentRoot: Boolean
    get() = when (this) {
      AccountRoute.Account,
      AccountRoute.ManageProfile,
      is AccountRoute.Username,
      UsernameLinkRoute.UsernameLink,
      LinkDeviceRoute.LinkDevice,
      is DonationsRoute.Donations,
      AppearanceRoute.Appearance,
      ChatsRoute.Chats,
      ChatFoldersRoute.ChatFolders,
      is ChatFoldersRoute.CreateChatFolders,
      is StoriesRoute.Privacy,
      NotificationsRoute.Notifications,
      NotificationsRoute.NotificationProfiles,
      PrivacyRoute.Privacy,
      is BackupsRoute.Backups,
      is BackupsRoute.Local,
      is BackupsRoute.Remote,
      DataAndStorageRoute.DataAndStorage,
      DataAndStorageRoute.Proxy,
      ChangeNumberRoute.Start,
      AppUpdates,
      Payments,
      is HelpRoute.Settings,
      Invite,
      LabsRoute.Labs,
      InternalRoute.Internal -> true
      else -> false
    }

  /**
   * Empty state, displayed when there is no current route. In this case, the "top" of our
   * scaffold navigator should be the SECONDARY (list) pane.
   */
  @Serializable
  data object Empty : AppSettingsRoute

  @Serializable
  @Parcelize
  sealed interface AccountRoute : AppSettingsRoute {
    @Serializable
    data object Account : AccountRoute

    @Serializable
    data object ManageProfile : AccountRoute

    @Serializable
    data object ProfileName : AccountRoute

    @Serializable
    data object About : AccountRoute

    @Serializable
    data object AvatarPicker : AccountRoute

    @Serializable
    data object AdvancedPinSettings : AccountRoute

    @Serializable
    data object DeleteAccount : AccountRoute

    @Serializable
    data object ExportAccountData : AccountRoute

    @Serializable
    data object OldDeviceTransfer : AccountRoute

    @Serializable
    data class Username(val mode: UsernameEditMode = UsernameEditMode.NORMAL) : AccountRoute
  }

  @Serializable
  data object Payments : AppSettingsRoute

  @Serializable
  data object Invite : AppSettingsRoute

  @Serializable
  data object AppUpdates : AppSettingsRoute

  @Serializable
  @Parcelize
  sealed interface StoriesRoute : AppSettingsRoute {
    @Serializable
    data class Privacy(@StringRes val titleId: Int) : StoriesRoute

    @Serializable
    data object MyStory : StoriesRoute

    /** [distributionListId] is a serialized `DistributionListId`. */
    @Serializable
    data class PrivateStory(val distributionListId: Long) : StoriesRoute

    /** [groupId] is a serialized `GroupId`. */
    @Serializable
    data class GroupStory(val groupId: String) : StoriesRoute

    @Serializable
    data object OnlyShareWith : StoriesRoute

    @Serializable
    data object AllExcept : StoriesRoute

    @Serializable
    data object SignalConnections : StoriesRoute

    /** [distributionListId] is a serialized `DistributionListId`. */
    @Serializable
    data class EditName(val distributionListId: Long, val name: String) : StoriesRoute

    /** [distributionListId] is a serialized `DistributionListId`. */
    @Serializable
    data class AddViewers(val distributionListId: Long) : StoriesRoute
  }

  @Serializable
  @Parcelize
  sealed interface UsernameLinkRoute : AppSettingsRoute {
    @Serializable
    data object UsernameLink : UsernameLinkRoute

    @Serializable
    data object QRColorPicker : UsernameLinkRoute

    @Serializable
    data object Share : UsernameLinkRoute
  }

  @Serializable
  @Parcelize
  sealed interface BackupsRoute : AppSettingsRoute {
    @Serializable
    data class Backups(
      val launchCheckoutFlow: Boolean = false
    ) : BackupsRoute

    @Serializable
    data class Local(val triggerUpdateFlow: Boolean = false) : BackupsRoute

    @Serializable
    data class Remote(val forQuickRestore: Boolean = false) : BackupsRoute

    @Serializable
    data object DisplayKey : BackupsRoute
  }

  @Serializable
  @Parcelize
  sealed interface NotificationsRoute : AppSettingsRoute {
    @Serializable
    data object Notifications : NotificationsRoute

    @Serializable
    data object MutedNotifications : NotificationsRoute

    @Serializable
    data object NotificationProfiles : NotificationsRoute

    @Serializable
    data class EditProfile(val profileId: Long = -1L) : NotificationsRoute

    @Serializable
    data class ProfileDetails(val profileId: Long) : NotificationsRoute

    @Serializable
    data class AddAllowedMembers(val profileId: Long) : NotificationsRoute

    @Serializable
    data class SelectRecipients(val profileId: Long, val currentSelection: Array<RecipientId>? = null) : NotificationsRoute {
      override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SelectRecipients

        if (profileId != other.profileId) return false
        if (!currentSelection.contentEquals(other.currentSelection)) return false

        return true
      }

      override fun hashCode(): Int {
        var result = profileId.hashCode()
        result = 31 * result + (currentSelection?.contentHashCode() ?: 0)
        return result
      }
    }

    @Serializable
    data class EditSchedule(val profileId: Long, val createMode: Boolean) : NotificationsRoute

    @Serializable
    data class Created(val profileId: Long) : NotificationsRoute
  }

  @Serializable
  @Parcelize
  sealed interface DonationsRoute : AppSettingsRoute {
    @Serializable
    data class Donations(
      val directToCheckoutType: InAppPaymentType = InAppPaymentType.UNKNOWN
    ) : DonationsRoute

    @Serializable
    data object Badges : DonationsRoute

    @Serializable
    data object Receipts : DonationsRoute

    @Serializable
    data class Receipt(val id: Long) : DonationsRoute

    @Serializable
    data object LearnMore : DonationsRoute

    @Serializable
    data object Featured : DonationsRoute
  }

  @Serializable
  @Parcelize
  sealed interface LabsRoute : AppSettingsRoute {
    @Serializable
    data object Labs : LabsRoute
  }

  @Serializable
  @Parcelize
  sealed interface InternalRoute : AppSettingsRoute {
    @Serializable
    data object Internal : InternalRoute

    @Serializable
    data object DonorErrorConfiguration : InternalRoute

    @Serializable
    data object StoryDialogs : InternalRoute

    @Serializable
    data object Search : InternalRoute

    @Serializable
    data object SvrPlayground : InternalRoute

    @Serializable
    data object ChatSpringboard : InternalRoute

    @Serializable
    data object OneTimeDonationConfiguration : InternalRoute

    @Serializable
    data object TerminalDonationConfiguration : InternalRoute

    @Serializable
    data object BackupPlayground : InternalRoute

    @Serializable
    data object StorageServicePlayground : InternalRoute

    @Serializable
    data object SqlitePlayground : InternalRoute

    @Serializable
    data object ConversationTestFragment : InternalRoute
  }

  @Serializable
  @Parcelize
  sealed interface PrivacyRoute : AppSettingsRoute {
    @Serializable
    data object Privacy : PrivacyRoute

    @Serializable
    data object BlockedUsers : PrivacyRoute

    @Serializable
    data object Advanced : PrivacyRoute

    @Serializable
    data object ExpiringMessages : PrivacyRoute

    @Serializable
    data object PhoneNumberPrivacy : PrivacyRoute

    @Serializable
    data object ScreenLock : PrivacyRoute
  }

  @Serializable
  @Parcelize
  sealed interface DataAndStorageRoute : AppSettingsRoute {
    @Serializable
    data object DataAndStorage : DataAndStorageRoute

    @Serializable
    data object Storage : DataAndStorageRoute

    @Serializable
    data object Proxy : DataAndStorageRoute
  }

  @Serializable
  @Parcelize
  sealed interface HelpRoute : AppSettingsRoute {
    @Serializable
    data class Settings(
      val startCategoryIndex: Int = 0
    ) : HelpRoute

    @Serializable
    data object Help : HelpRoute

    @Serializable
    data object DebugLog : HelpRoute

    @Serializable
    data object Licenses : HelpRoute
  }

  @Serializable
  @Parcelize
  sealed interface AppearanceRoute : AppSettingsRoute {
    @Serializable
    data object Appearance : AppearanceRoute

    @Serializable
    data object Wallpaper : AppearanceRoute

    @Serializable
    data object AppIconSelection : AppearanceRoute

    @Serializable
    data object AppIconTutorial : AppearanceRoute
  }

  @Serializable
  @Parcelize
  sealed interface ChatsRoute : AppSettingsRoute {
    @Serializable
    data object Chats : ChatsRoute

    @Serializable
    data object Reactions : ChatsRoute
  }

  @Serializable
  @Parcelize
  sealed interface ChatFoldersRoute : AppSettingsRoute {
    @Serializable
    data object ChatFolders : ChatFoldersRoute

    @Serializable
    data class CreateChatFolders(
      val folderId: Long,
      val threadIds: LongArray
    ) : ChatFoldersRoute {
      override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CreateChatFolders

        if (folderId != other.folderId) return false
        if (!threadIds.contentEquals(other.threadIds)) return false

        return true
      }

      override fun hashCode(): Int {
        var result = folderId.hashCode()
        result = 31 * result + threadIds.contentHashCode()
        return result
      }
    }

    @Serializable
    data object Education : ChatFoldersRoute

    @Serializable
    data object ChooseChats : ChatFoldersRoute
  }

  @Serializable
  @Parcelize
  sealed interface LinkDeviceRoute : AppSettingsRoute {
    @Serializable
    data object LinkDevice : LinkDeviceRoute

    @Serializable
    data object Finished : LinkDeviceRoute

    @Serializable
    data object LearnMore : LinkDeviceRoute

    @Serializable
    data object Education : LinkDeviceRoute

    @Serializable
    data object EditName : LinkDeviceRoute

    @Serializable
    data object Add : LinkDeviceRoute

    @Serializable
    data object Intro : LinkDeviceRoute

    @Serializable
    data object Sync : LinkDeviceRoute
  }

  @Serializable
  @Parcelize
  sealed interface ChangeNumberRoute : AppSettingsRoute {
    @Serializable
    data object Start : ChangeNumberRoute

    @Serializable
    data object EnterPhoneNumber : ChangeNumberRoute

    @Serializable
    data object Confirm : ChangeNumberRoute

    @Serializable
    data object CountryPicker : ChangeNumberRoute

    @Serializable
    data object Verify : ChangeNumberRoute

    @Serializable
    data object Captcha : ChangeNumberRoute

    @Serializable
    data object EnterCode : ChangeNumberRoute

    @Serializable
    data object RegistrationLock : ChangeNumberRoute

    @Serializable
    data object AccountLocked : ChangeNumberRoute

    @Serializable
    data object PinDiffers : ChangeNumberRoute
  }
}
