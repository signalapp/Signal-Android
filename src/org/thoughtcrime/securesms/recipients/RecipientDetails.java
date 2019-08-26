package org.thoughtcrime.securesms.recipients;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.color.MaterialColor;
import org.thoughtcrime.securesms.database.Address;
import org.thoughtcrime.securesms.database.RecipientDatabase.RecipientSettings;
import org.thoughtcrime.securesms.database.RecipientDatabase.RegisteredState;
import org.thoughtcrime.securesms.database.RecipientDatabase.UnidentifiedAccessMode;
import org.thoughtcrime.securesms.database.RecipientDatabase.VibrateState;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.libsignal.util.guava.Optional;

import java.util.LinkedList;
import java.util.List;

public class RecipientDetails {

  final Address                address;
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
  final boolean                seenInviteReminder;
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
    this.address                         = settings.getAddress();
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
    this.seenInviteReminder              = settings.hasSeenInviteReminder();
    this.defaultSubscriptionId           = settings.getDefaultSubscriptionId();
    this.registered                      = settings.getRegistered();
    this.profileKey                      = settings.getProfileKey();
    this.profileAvatar                   = settings.getProfileAvatar();
    this.profileSharing                  = settings.isProfileSharing();
    this.systemContact                   = systemContact;
    this.isLocalNumber                   = isLocalNumber;
    this.notificationChannel             = settings.getNotificationChannel();
    this.unidentifiedAccessMode          = settings.getUnidentifiedAccessMode();
    this.forceSmsSelection               = settings.isForceSmsSelection();

    if (name == null) this.name = settings.getSystemDisplayName();
    else              this.name = name;
  }
}
