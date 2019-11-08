package org.thoughtcrime.securesms.recipients;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.color.MaterialColor;
import org.thoughtcrime.securesms.database.RecipientDatabase.InsightsBannerTier;
import org.thoughtcrime.securesms.crypto.ProfileKeyUtil;
import org.thoughtcrime.securesms.database.RecipientDatabase.RecipientSettings;
import org.thoughtcrime.securesms.database.RecipientDatabase.RegisteredState;
import org.thoughtcrime.securesms.database.RecipientDatabase.UnidentifiedAccessMode;
import org.thoughtcrime.securesms.database.RecipientDatabase.VibrateState;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.libsignal.util.guava.Optional;

import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

public class RecipientDetails {

  final UUID                   uuid;
  final String                 e164;
  final String                 email;
  final String                 groupId;
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
  final String                 profileName;
  final Optional<Integer>      defaultSubscriptionId;
  final RegisteredState        registered;
  final byte[]                 profileKey;
  final String                 profileAvatar;
  final boolean                profileSharing;
  final boolean                systemContact;
  final boolean                isLocalNumber;
  final String                 notificationChannel;
  final UnidentifiedAccessMode unidentifiedAccessMode;
  final boolean                forceSmsSelection;
  final boolean                uuidSuported;
  final InsightsBannerTier     insightsBannerTier;

  RecipientDetails(@NonNull Context context,
                   @Nullable String name,
                   @NonNull Optional<Long> groupAvatarId,
                   boolean systemContact,
                   boolean isLocalNumber,
                   @NonNull RecipientSettings settings,
                   @Nullable List<Recipient> participants)
  {
    this.groupAvatarId                   = groupAvatarId;
    this.systemContactPhoto              = Util.uri(settings.getSystemContactPhotoUri());
    this.customLabel                     = settings.getSystemPhoneLabel();
    this.contactUri                      = Util.uri(settings.getSystemContactUri());
    this.uuid                            = settings.getUuid();
    this.e164                            = settings.getE164();
    this.email                           = settings.getEmail();
    this.groupId                         = settings.getGroupId();
    this.color                           = settings.getColor();
    this.messageRingtone                 = settings.getMessageRingtone();
    this.callRingtone                    = settings.getCallRingtone();
    this.mutedUntil                      = settings.getMuteUntil();
    this.messageVibrateState             = settings.getMessageVibrateState();
    this.callVibrateState                = settings.getCallVibrateState();
    this.blocked                         = settings.isBlocked();
    this.expireMessages                  = settings.getExpireMessages();
    this.participants                    = participants == null ? new LinkedList<>() : participants;
    this.profileName                     = isLocalNumber ? TextSecurePreferences.getProfileName(context) : settings.getProfileName();
    this.defaultSubscriptionId           = settings.getDefaultSubscriptionId();
    this.registered                      = settings.getRegistered();
    this.profileKey                      = isLocalNumber ? ProfileKeyUtil.getProfileKey(context) : settings.getProfileKey();
    this.profileAvatar                   = settings.getProfileAvatar();
    this.profileSharing                  = settings.isProfileSharing();
    this.systemContact                   = systemContact;
    this.isLocalNumber                   = isLocalNumber;
    this.notificationChannel             = settings.getNotificationChannel();
    this.unidentifiedAccessMode          = settings.getUnidentifiedAccessMode();
    this.forceSmsSelection               = settings.isForceSmsSelection();
    this.uuidSuported                    = settings.isUuidSupported();
    this.insightsBannerTier              = settings.getInsightsBannerTier();

    if (name == null) this.name = settings.getSystemDisplayName();
    else              this.name = name;
  }

  public RecipientDetails() {
    this.groupAvatarId          = null;
    this.systemContactPhoto     = null;
    this.customLabel            = null;
    this.contactUri             = null;
    this.uuid                   = null;
    this.e164                   = null;
    this.email                  = null;
    this.groupId                = null;
    this.color                  = null;
    this.messageRingtone        = null;
    this.callRingtone           = null;
    this.mutedUntil             = 0;
    this.messageVibrateState    = VibrateState.DEFAULT;
    this.callVibrateState       = VibrateState.DEFAULT;
    this.blocked                = false;
    this.expireMessages         = 0;
    this.participants           = new LinkedList<>();
    this.profileName            = null;
    this.insightsBannerTier     = InsightsBannerTier.TIER_TWO;
    this.defaultSubscriptionId  = Optional.absent();
    this.registered             = RegisteredState.UNKNOWN;
    this.profileKey             = null;
    this.profileAvatar          = null;
    this.profileSharing         = false;
    this.systemContact          = true;
    this.isLocalNumber          = false;
    this.notificationChannel    = null;
    this.unidentifiedAccessMode = UnidentifiedAccessMode.UNKNOWN;
    this.forceSmsSelection      = false;
    this.name                   = null;
    this.uuidSuported           = false;
  }
}
