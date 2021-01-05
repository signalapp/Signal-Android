package org.thoughtcrime.securesms.recipients;

import android.content.Context;
import android.net.Uri;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.zkgroup.profiles.ProfileKeyCredential;
import org.thoughtcrime.securesms.color.MaterialColor;
import org.thoughtcrime.securesms.database.RecipientDatabase.InsightsBannerTier;
import org.thoughtcrime.securesms.database.RecipientDatabase.MentionSetting;
import org.thoughtcrime.securesms.database.RecipientDatabase.RecipientSettings;
import org.thoughtcrime.securesms.database.RecipientDatabase.RegisteredState;
import org.thoughtcrime.securesms.database.RecipientDatabase.UnidentifiedAccessMode;
import org.thoughtcrime.securesms.database.RecipientDatabase.VibrateState;
import org.thoughtcrime.securesms.groups.GroupId;
import org.thoughtcrime.securesms.profiles.ProfileName;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.libsignal.util.guava.Optional;

import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

public class RecipientDetails {

  final UUID                   uuid;
  final String                 username;
  final String                 e164;
  final String                 email;
  final GroupId                groupId;
  final String                 name;
  final String                 customLabel;
  final Uri                    systemContactPhoto;
  final Uri                    contactUri;
  final Optional<Long>         groupAvatarId;
  final MaterialColor          color;
  final Uri                    messageRingtone;
  final Uri                    callRingtone;
  final long                   mutedUntil;
  final VibrateState           messageVibrateState;
  final VibrateState           callVibrateState;
  final boolean                blocked;
  final int                    expireMessages;
  final List<Recipient>        participants;
  final ProfileName            profileName;
  final Optional<Integer>      defaultSubscriptionId;
  final RegisteredState        registered;
  final byte[]                 profileKey;
  final ProfileKeyCredential   profileKeyCredential;
  final String                 profileAvatar;
  final boolean                hasProfileImage;
  final boolean                profileSharing;
  final long                   lastProfileFetch;
  final boolean                systemContact;
  final boolean                isSelf;
  final String                 notificationChannel;
  final UnidentifiedAccessMode unidentifiedAccessMode;
  final boolean                forceSmsSelection;
  final Recipient.Capability   groupsV2Capability;
  final Recipient.Capability   groupsV1MigrationCapability;
  final InsightsBannerTier     insightsBannerTier;
  final byte[]                 storageId;
  final MentionSetting         mentionSetting;

  public RecipientDetails(@Nullable String name,
                          @NonNull Optional<Long> groupAvatarId,
                          boolean systemContact,
                          boolean isSelf,
                          @NonNull RecipientSettings settings,
                          @Nullable List<Recipient> participants)
  {
    this.groupAvatarId               = groupAvatarId;
    this.systemContactPhoto          = Util.uri(settings.getSystemContactPhotoUri());
    this.customLabel                 = settings.getSystemPhoneLabel();
    this.contactUri                  = Util.uri(settings.getSystemContactUri());
    this.uuid                        = settings.getUuid();
    this.username                    = settings.getUsername();
    this.e164                        = settings.getE164();
    this.email                       = settings.getEmail();
    this.groupId                     = settings.getGroupId();
    this.color                       = settings.getColor();
    this.messageRingtone             = settings.getMessageRingtone();
    this.callRingtone                = settings.getCallRingtone();
    this.mutedUntil                  = settings.getMuteUntil();
    this.messageVibrateState         = settings.getMessageVibrateState();
    this.callVibrateState            = settings.getCallVibrateState();
    this.blocked                     = settings.isBlocked();
    this.expireMessages              = settings.getExpireMessages();
    this.participants                = participants == null ? new LinkedList<>() : participants;
    this.profileName                 = settings.getProfileName();
    this.defaultSubscriptionId       = settings.getDefaultSubscriptionId();
    this.registered                  = settings.getRegistered();
    this.profileKey                  = settings.getProfileKey();
    this.profileKeyCredential        = settings.getProfileKeyCredential();
    this.profileAvatar               = settings.getProfileAvatar();
    this.hasProfileImage             = settings.hasProfileImage();
    this.profileSharing              = settings.isProfileSharing();
    this.lastProfileFetch            = settings.getLastProfileFetch();
    this.systemContact               = systemContact;
    this.isSelf                      = isSelf;
    this.notificationChannel         = settings.getNotificationChannel();
    this.unidentifiedAccessMode      = settings.getUnidentifiedAccessMode();
    this.forceSmsSelection           = settings.isForceSmsSelection();
    this.groupsV2Capability          = settings.getGroupsV2Capability();
    this.groupsV1MigrationCapability = settings.getGroupsV1MigrationCapability();
    this.insightsBannerTier          = settings.getInsightsBannerTier();
    this.storageId                   = settings.getStorageId();
    this.mentionSetting              = settings.getMentionSetting();

    if (name == null) this.name = settings.getSystemDisplayName();
    else              this.name = name;
  }

  /**
   * Only used for {@link Recipient#UNKNOWN}.
   */
  RecipientDetails() {
    this.groupAvatarId               = null;
    this.systemContactPhoto          = null;
    this.customLabel                 = null;
    this.contactUri                  = null;
    this.uuid                        = null;
    this.username                    = null;
    this.e164                        = null;
    this.email                       = null;
    this.groupId                     = null;
    this.color                       = null;
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
    this.name                        = null;
    this.groupsV2Capability          = Recipient.Capability.UNKNOWN;
    this.groupsV1MigrationCapability = Recipient.Capability.UNKNOWN;
    this.storageId                   = null;
    this.mentionSetting              = MentionSetting.ALWAYS_NOTIFY;
  }

  public static @NonNull RecipientDetails forIndividual(@NonNull Context context, @NonNull RecipientSettings settings) {
    boolean systemContact = !TextUtils.isEmpty(settings.getSystemDisplayName());
    boolean isSelf        = (settings.getE164() != null && settings.getE164().equals(TextSecurePreferences.getLocalNumber(context))) ||
                            (settings.getUuid() != null && settings.getUuid().equals(TextSecurePreferences.getLocalUuid(context)));

    return new RecipientDetails(null, Optional.absent(), systemContact, isSelf, settings, null);
  }
}
