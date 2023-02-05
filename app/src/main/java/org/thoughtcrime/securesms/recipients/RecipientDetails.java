package org.thoughtcrime.securesms.recipients;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.libsignal.zkgroup.profiles.ExpiringProfileKeyCredential;
import org.thoughtcrime.securesms.badges.models.Badge;
import org.thoughtcrime.securesms.conversation.colors.AvatarColor;
import org.thoughtcrime.securesms.conversation.colors.ChatColors;
import org.thoughtcrime.securesms.database.RecipientTable.InsightsBannerTier;
import org.thoughtcrime.securesms.database.RecipientTable.MentionSetting;
import org.thoughtcrime.securesms.database.RecipientTable.RegisteredState;
import org.thoughtcrime.securesms.database.RecipientTable.UnidentifiedAccessMode;
import org.thoughtcrime.securesms.database.RecipientTable.VibrateState;
import org.thoughtcrime.securesms.database.model.DistributionListId;
import org.thoughtcrime.securesms.database.model.ProfileAvatarFileDetails;
import org.thoughtcrime.securesms.database.model.RecipientRecord;
import org.thoughtcrime.securesms.groups.GroupId;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.profiles.ProfileName;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.wallpaper.ChatWallpaper;
import org.whispersystems.signalservice.api.push.PNI;
import org.whispersystems.signalservice.api.push.ServiceId;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

public class RecipientDetails {

  final ServiceId                    serviceId;
  final PNI                          pni;
  final String                       username;
  final String                       e164;
  final String                       email;
  final GroupId                      groupId;
  final DistributionListId           distributionListId;
  final String                       groupName;
  final String                       systemContactName;
  final String                       customLabel;
  final Uri                          systemContactPhoto;
  final Uri                          contactUri;
  final Optional<Long>               groupAvatarId;
  final Uri                          messageRingtone;
  final Uri                          callRingtone;
  final long                         mutedUntil;
  final VibrateState                 messageVibrateState;
  final VibrateState                 callVibrateState;
  final boolean                      blocked;
  final int                          expireMessages;
  final List<RecipientId>            participantIds;
  final ProfileName                  profileName;
  final Optional<Integer>            defaultSubscriptionId;
  final RegisteredState              registered;
  final byte[]                       profileKey;
  final ExpiringProfileKeyCredential expiringProfileKeyCredential;
  final String                       profileAvatar;
  final ProfileAvatarFileDetails     profileAvatarFileDetails;
  final boolean                      profileSharing;
  final long                         lastProfileFetch;
  final boolean                      systemContact;
  final boolean                      isSelf;
  final String                       notificationChannel;
  final UnidentifiedAccessMode       unidentifiedAccessMode;
  final boolean                      forceSmsSelection;
  final RecipientRecord.Capabilities capabilities;
  final InsightsBannerTier           insightsBannerTier;
  final byte[]                       storageId;
  final MentionSetting               mentionSetting;
  final ChatWallpaper                wallpaper;
  final ChatColors                   chatColors;
  final AvatarColor                  avatarColor;
  final String                       about;
  final String                       aboutEmoji;
  final ProfileName                  systemProfileName;
  final Optional<Recipient.Extras>   extras;
  final boolean                      hasGroupsInCommon;
  final List<Badge>                  badges;
  final boolean                      isReleaseChannel;
  final boolean                      needsPniSignature;

  public RecipientDetails(@Nullable String groupName,
                          @Nullable String systemContactName,
                          @NonNull Optional<Long> groupAvatarId,
                          boolean systemContact,
                          boolean isSelf,
                          @NonNull RegisteredState registeredState,
                          @NonNull RecipientRecord record,
                          @Nullable List<RecipientId> participantIds,
                          boolean isReleaseChannel)
  {
    this.groupAvatarId                = groupAvatarId;
    this.systemContactPhoto           = Util.uri(record.getSystemContactPhotoUri());
    this.customLabel                  = record.getSystemPhoneLabel();
    this.contactUri                   = Util.uri(record.getSystemContactUri());
    this.serviceId                    = record.getServiceId();
    this.pni                          = record.getPni();
    this.username                     = record.getUsername();
    this.e164                         = record.getE164();
    this.email                        = record.getEmail();
    this.groupId                      = record.getGroupId();
    this.distributionListId           = record.getDistributionListId();
    this.messageRingtone              = record.getMessageRingtone();
    this.callRingtone                 = record.getCallRingtone();
    this.mutedUntil                   = record.getMuteUntil();
    this.messageVibrateState          = record.getMessageVibrateState();
    this.callVibrateState             = record.getCallVibrateState();
    this.blocked                      = record.isBlocked();
    this.expireMessages               = record.getExpireMessages();
    this.participantIds               = participantIds == null ? new LinkedList<>() : participantIds;
    this.profileName                  = record.getProfileName();
    this.defaultSubscriptionId        = record.getDefaultSubscriptionId();
    this.registered                   = registeredState;
    this.profileKey                   = record.getProfileKey();
    this.expiringProfileKeyCredential = record.getExpiringProfileKeyCredential();
    this.profileAvatar                = record.getProfileAvatar();
    this.profileAvatarFileDetails     = record.getProfileAvatarFileDetails();
    this.profileSharing               = record.isProfileSharing();
    this.lastProfileFetch             = record.getLastProfileFetch();
    this.systemContact                = systemContact;
    this.isSelf                       = isSelf;
    this.notificationChannel          = record.getNotificationChannel();
    this.unidentifiedAccessMode       = record.getUnidentifiedAccessMode();
    this.forceSmsSelection            = record.isForceSmsSelection();
    this.capabilities                 = record.getCapabilities();
    this.insightsBannerTier           = record.getInsightsBannerTier();
    this.storageId                    = record.getStorageId();
    this.mentionSetting               = record.getMentionSetting();
    this.wallpaper                    = record.getWallpaper();
    this.chatColors                   = record.getChatColors();
    this.avatarColor                  = record.getAvatarColor();
    this.about                        = record.getAbout();
    this.aboutEmoji                   = record.getAboutEmoji();
    this.systemProfileName            = record.getSystemProfileName();
    this.groupName                    = groupName;
    this.systemContactName            = systemContactName;
    this.extras                       = Optional.ofNullable(record.getExtras());
    this.hasGroupsInCommon            = record.hasGroupsInCommon();
    this.badges                       = record.getBadges();
    this.isReleaseChannel             = isReleaseChannel;
    this.needsPniSignature            = record.needsPniSignature();
  }

  private RecipientDetails() {
    this.groupAvatarId                = null;
    this.systemContactPhoto           = null;
    this.customLabel                  = null;
    this.contactUri                   = null;
    this.serviceId                    = null;
    this.pni                          = null;
    this.username                     = null;
    this.e164                         = null;
    this.email                        = null;
    this.groupId                      = null;
    this.distributionListId           = null;
    this.messageRingtone              = null;
    this.callRingtone                 = null;
    this.mutedUntil                   = 0;
    this.messageVibrateState          = VibrateState.DEFAULT;
    this.callVibrateState             = VibrateState.DEFAULT;
    this.blocked                      = false;
    this.expireMessages               = 0;
    this.participantIds               = new LinkedList<>();
    this.profileName                  = ProfileName.EMPTY;
    this.insightsBannerTier           = InsightsBannerTier.TIER_TWO;
    this.defaultSubscriptionId        = Optional.empty();
    this.registered                   = RegisteredState.UNKNOWN;
    this.profileKey                   = null;
    this.expiringProfileKeyCredential = null;
    this.profileAvatar                = null;
    this.profileAvatarFileDetails     = ProfileAvatarFileDetails.NO_DETAILS;
    this.profileSharing               = false;
    this.lastProfileFetch             = 0;
    this.systemContact                = true;
    this.isSelf                       = false;
    this.notificationChannel          = null;
    this.unidentifiedAccessMode       = UnidentifiedAccessMode.UNKNOWN;
    this.forceSmsSelection            = false;
    this.groupName                    = null;
    this.capabilities                 = RecipientRecord.Capabilities.UNKNOWN;
    this.storageId                    = null;
    this.mentionSetting               = MentionSetting.ALWAYS_NOTIFY;
    this.wallpaper                    = null;
    this.chatColors                   = null;
    this.avatarColor                  = AvatarColor.UNKNOWN;
    this.about                        = null;
    this.aboutEmoji                   = null;
    this.systemProfileName            = ProfileName.EMPTY;
    this.systemContactName            = null;
    this.extras                       = Optional.empty();
    this.hasGroupsInCommon            = false;
    this.badges                       = Collections.emptyList();
    this.isReleaseChannel             = false;
    this.needsPniSignature            = false;
  }

  public static @NonNull RecipientDetails forIndividual(@NonNull Context context, @NonNull RecipientRecord settings) {
    boolean systemContact    = !settings.getSystemProfileName().isEmpty();
    boolean isSelf           = (settings.getE164() != null && settings.getE164().equals(SignalStore.account().getE164())) ||
                               (settings.getServiceId() != null && settings.getServiceId().equals(SignalStore.account().getAci()));
    boolean isReleaseChannel = settings.getId().equals(SignalStore.releaseChannelValues().getReleaseChannelRecipientId());

    RegisteredState registeredState = settings.getRegistered();

    if (isSelf) {
      if (SignalStore.account().isRegistered() && !TextSecurePreferences.isUnauthorizedReceived(context)) {
        registeredState = RegisteredState.REGISTERED;
      } else {
        registeredState = RegisteredState.NOT_REGISTERED;
      }
    }

    return new RecipientDetails(null, settings.getSystemDisplayName(), Optional.empty(), systemContact, isSelf, registeredState, settings, null, isReleaseChannel);
  }

  public static @NonNull RecipientDetails forDistributionList(String title, @Nullable List<RecipientId> members, @NonNull RecipientRecord record) {
    return new RecipientDetails(title, null, Optional.empty(), false, false, record.getRegistered(), record, members, false);
  }

  public static @NonNull RecipientDetails forUnknown() {
    return new RecipientDetails();
  }
}
