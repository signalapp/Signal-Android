package org.thoughtcrime.securesms.recipients;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.zkgroup.profiles.ProfileKeyCredential;
import org.thoughtcrime.securesms.badges.models.Badge;
import org.thoughtcrime.securesms.conversation.colors.AvatarColor;
import org.thoughtcrime.securesms.conversation.colors.ChatColors;
import org.thoughtcrime.securesms.database.RecipientDatabase.InsightsBannerTier;
import org.thoughtcrime.securesms.database.RecipientDatabase.MentionSetting;
import org.thoughtcrime.securesms.database.model.RecipientRecord;
import org.thoughtcrime.securesms.database.RecipientDatabase.RegisteredState;
import org.thoughtcrime.securesms.database.RecipientDatabase.UnidentifiedAccessMode;
import org.thoughtcrime.securesms.database.RecipientDatabase.VibrateState;
import org.thoughtcrime.securesms.groups.GroupId;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.profiles.ProfileName;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.wallpaper.ChatWallpaper;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.push.ACI;
import org.whispersystems.signalservice.api.push.PNI;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class RecipientDetails {

  final ACI                        aci;
  final PNI                        pni;
  final String                     username;
  final String                     e164;
  final String                     email;
  final GroupId                    groupId;
  final String                     groupName;
  final String                     systemContactName;
  final String                     customLabel;
  final Uri                        systemContactPhoto;
  final Uri                        contactUri;
  final Optional<Long>             groupAvatarId;
  final Uri                        messageRingtone;
  final Uri                        callRingtone;
  final long                       mutedUntil;
  final VibrateState               messageVibrateState;
  final VibrateState               callVibrateState;
  final boolean                    blocked;
  final int                        expireMessages;
  final List<Recipient>            participants;
  final ProfileName                profileName;
  final Optional<Integer>          defaultSubscriptionId;
  final RegisteredState            registered;
  final byte[]                     profileKey;
  final ProfileKeyCredential       profileKeyCredential;
  final String                     profileAvatar;
  final boolean                    hasProfileImage;
  final boolean                    profileSharing;
  final long                       lastProfileFetch;
  final boolean                    systemContact;
  final boolean                    isSelf;
  final String                     notificationChannel;
  final UnidentifiedAccessMode     unidentifiedAccessMode;
  final boolean                    forceSmsSelection;
  final Recipient.Capability       groupsV2Capability;
  final Recipient.Capability       groupsV1MigrationCapability;
  final Recipient.Capability       senderKeyCapability;
  final Recipient.Capability       announcementGroupCapability;
  final Recipient.Capability       changeNumberCapability;
  final InsightsBannerTier         insightsBannerTier;
  final byte[]                     storageId;
  final MentionSetting             mentionSetting;
  final ChatWallpaper              wallpaper;
  final ChatColors                 chatColors;
  final AvatarColor                avatarColor;
  final String                     about;
  final String                     aboutEmoji;
  final ProfileName                systemProfileName;
  final Optional<Recipient.Extras> extras;
  final boolean                    hasGroupsInCommon;
  final List<Badge>                badges;
  final boolean                    isReleaseChannel;

  public RecipientDetails(@Nullable String groupName,
                          @Nullable String systemContactName,
                          @NonNull Optional<Long> groupAvatarId,
                          boolean systemContact,
                          boolean isSelf,
                          @NonNull RegisteredState registeredState,
                          @NonNull RecipientRecord record,
                          @Nullable List<Recipient> participants,
                          boolean isReleaseChannel)
  {
    this.groupAvatarId               = groupAvatarId;
    this.systemContactPhoto          = Util.uri(record.getSystemContactPhotoUri());
    this.customLabel                 = record.getSystemPhoneLabel();
    this.contactUri                  = Util.uri(record.getSystemContactUri());
    this.aci                         = record.getAci();
    this.pni                         = record.getPni();
    this.username                    = record.getUsername();
    this.e164                        = record.getE164();
    this.email                       = record.getEmail();
    this.groupId                     = record.getGroupId();
    this.messageRingtone             = record.getMessageRingtone();
    this.callRingtone                = record.getCallRingtone();
    this.mutedUntil                  = record.getMuteUntil();
    this.messageVibrateState         = record.getMessageVibrateState();
    this.callVibrateState            = record.getCallVibrateState();
    this.blocked                     = record.isBlocked();
    this.expireMessages              = record.getExpireMessages();
    this.participants                = participants == null ? new LinkedList<>() : participants;
    this.profileName                 = record.getProfileName();
    this.defaultSubscriptionId       = record.getDefaultSubscriptionId();
    this.registered                  = registeredState;
    this.profileKey                  = record.getProfileKey();
    this.profileKeyCredential        = record.getProfileKeyCredential();
    this.profileAvatar               = record.getProfileAvatar();
    this.hasProfileImage             = record.hasProfileImage();
    this.profileSharing              = record.isProfileSharing();
    this.lastProfileFetch            = record.getLastProfileFetch();
    this.systemContact               = systemContact;
    this.isSelf                      = isSelf;
    this.notificationChannel         = record.getNotificationChannel();
    this.unidentifiedAccessMode      = record.getUnidentifiedAccessMode();
    this.forceSmsSelection           = record.isForceSmsSelection();
    this.groupsV2Capability          = record.getGroupsV2Capability();
    this.groupsV1MigrationCapability = record.getGroupsV1MigrationCapability();
    this.senderKeyCapability         = record.getSenderKeyCapability();
    this.announcementGroupCapability = record.getAnnouncementGroupCapability();
    this.changeNumberCapability      = record.getChangeNumberCapability();
    this.insightsBannerTier          = record.getInsightsBannerTier();
    this.storageId                   = record.getStorageId();
    this.mentionSetting              = record.getMentionSetting();
    this.wallpaper                   = record.getWallpaper();
    this.chatColors                  = record.getChatColors();
    this.avatarColor                 = record.getAvatarColor();
    this.about                       = record.getAbout();
    this.aboutEmoji                  = record.getAboutEmoji();
    this.systemProfileName           = record.getSystemProfileName();
    this.groupName                   = groupName;
    this.systemContactName           = systemContactName;
    this.extras                      = Optional.fromNullable(record.getExtras());
    this.hasGroupsInCommon           = record.hasGroupsInCommon();
    this.badges                      = record.getBadges();
    this.isReleaseChannel            = isReleaseChannel;
  }

  /**
   * Only used for {@link Recipient#UNKNOWN}.
   */
  RecipientDetails() {
    this.groupAvatarId               = null;
    this.systemContactPhoto          = null;
    this.customLabel                 = null;
    this.contactUri                  = null;
    this.aci                         = null;
    this.pni                         = null;
    this.username                    = null;
    this.e164                        = null;
    this.email                       = null;
    this.groupId                     = null;
    this.messageRingtone             = null;
    this.callRingtone                = null;
    this.mutedUntil                  = 0;
    this.messageVibrateState         = VibrateState.DEFAULT;
    this.callVibrateState            = VibrateState.DEFAULT;
    this.blocked                     = false;
    this.expireMessages              = 0;
    this.participants                = new LinkedList<>();
    this.profileName                 = ProfileName.EMPTY;
    this.insightsBannerTier          = InsightsBannerTier.TIER_TWO;
    this.defaultSubscriptionId       = Optional.absent();
    this.registered                  = RegisteredState.UNKNOWN;
    this.profileKey                  = null;
    this.profileKeyCredential        = null;
    this.profileAvatar               = null;
    this.hasProfileImage             = false;
    this.profileSharing              = false;
    this.lastProfileFetch            = 0;
    this.systemContact               = true;
    this.isSelf                      = false;
    this.notificationChannel         = null;
    this.unidentifiedAccessMode      = UnidentifiedAccessMode.UNKNOWN;
    this.forceSmsSelection           = false;
    this.groupName                   = null;
    this.groupsV2Capability          = Recipient.Capability.UNKNOWN;
    this.groupsV1MigrationCapability = Recipient.Capability.UNKNOWN;
    this.senderKeyCapability         = Recipient.Capability.UNKNOWN;
    this.announcementGroupCapability = Recipient.Capability.UNKNOWN;
    this.changeNumberCapability      = Recipient.Capability.UNKNOWN;
    this.storageId                   = null;
    this.mentionSetting              = MentionSetting.ALWAYS_NOTIFY;
    this.wallpaper                   = null;
    this.chatColors                  = null;
    this.avatarColor                 = AvatarColor.UNKNOWN;
    this.about                       = null;
    this.aboutEmoji                  = null;
    this.systemProfileName           = ProfileName.EMPTY;
    this.systemContactName           = null;
    this.extras                      = Optional.absent();
    this.hasGroupsInCommon           = false;
    this.badges                      = Collections.emptyList();
    this.isReleaseChannel            = false;
  }

  public static @NonNull RecipientDetails forIndividual(@NonNull Context context, @NonNull RecipientRecord settings) {
    boolean systemContact    = !settings.getSystemProfileName().isEmpty();
    boolean isSelf           = (settings.getE164() != null && settings.getE164().equals(SignalStore.account().getE164())) ||
                               (settings.getAci() != null && settings.getAci().equals(SignalStore.account().getAci()));
    boolean isReleaseChannel = settings.getId().equals(SignalStore.releaseChannelValues().getReleaseChannelRecipientId());

    RegisteredState registeredState = settings.getRegistered();

    if (isSelf) {
      if (SignalStore.account().isRegistered() && !TextSecurePreferences.isUnauthorizedRecieved(context)) {
        registeredState = RegisteredState.REGISTERED;
      } else {
        registeredState = RegisteredState.NOT_REGISTERED;
      }
    }

    return new RecipientDetails(null, settings.getSystemDisplayName(), Optional.absent(), systemContact, isSelf, registeredState, settings, null, isReleaseChannel);
  }
}
