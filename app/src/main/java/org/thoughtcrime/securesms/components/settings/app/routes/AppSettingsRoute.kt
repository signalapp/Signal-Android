/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.routes

import android.os.Parcelable
import androidx.annotation.StringRes
import kotlinx.parcelize.Parcelize
import org.signal.donations.InAppPaymentType
import org.thoughtcrime.securesms.database.model.DistributionListId
import org.thoughtcrime.securesms.groups.GroupId
import org.thoughtcrime.securesms.profiles.manage.UsernameEditMode
import org.thoughtcrime.securesms.recipients.RecipientId

/**
 * Describes a route that the AppSettings screen can open. Every route listed here is displayed in
 * the PRIMARY (detail) pane of the AppSettingsScreen.
 */
@Parcelize
sealed interface AppSettingsRoute : Parcelable {
  /**
   * Empty state, displayed when there is no current route. In this case, the "top" of our
   * scaffold navigator should be the SECONDARY (list) pane.
   */
  data object Empty : AppSettingsRoute

  @Parcelize
  sealed interface AccountRoute : AppSettingsRoute {
    data object Account : AccountRoute
    data object ManageProfile : AccountRoute
    data object AdvancedPinSettings : AccountRoute
    data object DeleteAccount : AccountRoute
    data object ExportAccountData : AccountRoute
    data object OldDeviceTransfer : AccountRoute
    data class Username(val mode: UsernameEditMode = UsernameEditMode.NORMAL) : AccountRoute
  }

  data object Payments : AppSettingsRoute
  data object Invite : AppSettingsRoute
  data object AppUpdates : AppSettingsRoute

  @Parcelize
  sealed interface StoriesRoute : AppSettingsRoute {
    data class Privacy(@StringRes val titleId: Int) : StoriesRoute
    data object MyStory : StoriesRoute
    data class PrivateStory(val distributionListId: DistributionListId) : StoriesRoute
    data class GroupStory(val groupId: GroupId) : StoriesRoute
    data object OnlyShareWith : StoriesRoute
    data object AllExcept : StoriesRoute
    data object SignalConnections : StoriesRoute
    data class EditName(val distributionListId: DistributionListId, val name: String) : StoriesRoute
    data class AddViewers(val distributionListId: DistributionListId) : StoriesRoute
  }

  @Parcelize
  sealed interface UsernameLinkRoute : AppSettingsRoute {
    data object UsernameLink : UsernameLinkRoute
    data object QRColorPicker : UsernameLinkRoute
    data object Share : UsernameLinkRoute
  }

  @Parcelize
  sealed interface BackupsRoute : AppSettingsRoute {
    data object Backups : BackupsRoute
    data object Local : BackupsRoute
    data class Remote(val backupLaterSelected: Boolean = false) : BackupsRoute
    data object DisplayKey : BackupsRoute
  }

  @Parcelize
  sealed interface NotificationsRoute : AppSettingsRoute {
    data object Notifications : NotificationsRoute
    data object NotificationProfiles : NotificationsRoute
    data class EditProfile(val profileId: Long = -1L) : NotificationsRoute
    data class ProfileDetails(val profileId: Long) : NotificationsRoute
    data class AddAllowedMembers(val profileId: Long) : NotificationsRoute

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

    data class EditSchedule(val profileId: Long, val createMode: Boolean) : NotificationsRoute
    data class Created(val profileId: Long) : NotificationsRoute
  }

  @Parcelize
  sealed interface DonationsRoute : AppSettingsRoute {
    data class Donations(
      val directToCheckoutType: InAppPaymentType = InAppPaymentType.UNKNOWN
    ) : DonationsRoute

    data object Badges : DonationsRoute
    data object Receipts : DonationsRoute
    data class Receipt(val id: Long) : DonationsRoute
    data object LearnMore : DonationsRoute
    data object Featured : DonationsRoute
  }

  @Parcelize
  sealed interface InternalRoute : AppSettingsRoute {
    data object Internal : InternalRoute
    data object DonorErrorConfiguration : InternalRoute
    data object StoryDialogs : InternalRoute
    data object Search : InternalRoute
    data object SvrPlayground : InternalRoute
    data object ChatSpringboard : InternalRoute
    data object OneTimeDonationConfiguration : InternalRoute
    data object TerminalDonationConfiguration : InternalRoute
    data object BackupPlayground : InternalRoute
    data object StorageServicePlayground : InternalRoute
    data object SqlitePlayground : InternalRoute
    data object ConversationTestFragment : InternalRoute
  }

  @Parcelize
  sealed interface PrivacyRoute : AppSettingsRoute {
    data object Privacy : PrivacyRoute
    data object BlockedUsers : PrivacyRoute
    data object Advanced : PrivacyRoute
    data object ExpiringMessages : PrivacyRoute
    data object PhoneNumberPrivacy : PrivacyRoute
    data object ScreenLock : PrivacyRoute
  }

  @Parcelize
  sealed interface DataAndStorageRoute : AppSettingsRoute {
    data object DataAndStorage : DataAndStorageRoute
    data object Storage : DataAndStorageRoute
    data object Proxy : DataAndStorageRoute
  }

  @Parcelize
  sealed interface HelpRoute : AppSettingsRoute {
    data class Settings(
      val startCategoryIndex: Int = 0
    ) : HelpRoute

    data object Help : HelpRoute
    data object DebugLog : HelpRoute
    data object Licenses : HelpRoute
  }

  @Parcelize
  sealed interface AppearanceRoute : AppSettingsRoute {
    data object Appearance : AppearanceRoute
    data object Wallpaper : AppearanceRoute
    data object AppIconSelection : AppearanceRoute
    data object AppIconTutorial : AppearanceRoute
  }

  @Parcelize
  sealed interface ChatsRoute : AppSettingsRoute {
    data object Chats : ChatsRoute
    data object Reactions : ChatsRoute
  }

  @Parcelize
  sealed interface ChatFoldersRoute : AppSettingsRoute {
    data object ChatFolders : ChatFoldersRoute

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

    data object Education : ChatFoldersRoute
    data object ChooseChats : ChatFoldersRoute
  }

  @Parcelize
  sealed interface LinkDeviceRoute : AppSettingsRoute {
    data object LinkDevice : LinkDeviceRoute
    data object Finished : LinkDeviceRoute
    data object LearnMore : LinkDeviceRoute
    data object Education : LinkDeviceRoute
    data object EditName : LinkDeviceRoute
    data object Add : LinkDeviceRoute
    data object Intro : LinkDeviceRoute
    data object Sync : LinkDeviceRoute
  }

  @Parcelize
  sealed interface ChangeNumberRoute : AppSettingsRoute {
    data object Start : ChangeNumberRoute
    data object EnterPhoneNumber : ChangeNumberRoute
    data object Confirm : ChangeNumberRoute
    data object CountryPicker : ChangeNumberRoute
    data object Verify : ChangeNumberRoute
    data object Captcha : ChangeNumberRoute
    data object EnterCode : ChangeNumberRoute
    data object RegistrationLock : ChangeNumberRoute
    data object AccountLocked : ChangeNumberRoute
    data object PinDiffers : ChangeNumberRoute
  }
}
