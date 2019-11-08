package org.thoughtcrime.securesms.recipients;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.text.TextUtils;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.color.MaterialColor;
import org.thoughtcrime.securesms.contacts.avatars.ContactColors;
import org.thoughtcrime.securesms.contacts.avatars.ContactPhoto;
import org.thoughtcrime.securesms.contacts.avatars.FallbackContactPhoto;
import org.thoughtcrime.securesms.contacts.avatars.GeneratedContactPhoto;
import org.thoughtcrime.securesms.contacts.avatars.GroupRecordContactPhoto;
import org.thoughtcrime.securesms.contacts.avatars.ProfileContactPhoto;
import org.thoughtcrime.securesms.contacts.avatars.ResourceContactPhoto;
import org.thoughtcrime.securesms.contacts.avatars.SystemContactPhoto;
import org.thoughtcrime.securesms.contacts.avatars.TransparentContactPhoto;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.RecipientDatabase;
import org.thoughtcrime.securesms.database.RecipientDatabase.RegisteredState;
import org.thoughtcrime.securesms.database.RecipientDatabase.UnidentifiedAccessMode;
import org.thoughtcrime.securesms.database.RecipientDatabase.VibrateState;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobs.DirectoryRefreshJob;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.notifications.NotificationChannels;
import org.thoughtcrime.securesms.phonenumbers.NumberUtil;
import org.thoughtcrime.securesms.phonenumbers.PhoneNumberFormatter;
import org.thoughtcrime.securesms.util.FeatureFlags;
import org.thoughtcrime.securesms.util.GroupUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.libsignal.util.guava.Preconditions;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.util.UuidUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.thoughtcrime.securesms.database.RecipientDatabase.InsightsBannerTier;

public class Recipient {

  public static final Recipient UNKNOWN = new Recipient(RecipientId.UNKNOWN, new RecipientDetails());

  private static final String TAG = Log.tag(Recipient.class);

  private final RecipientId            id;
  private final boolean                resolving;
  private final UUID                   uuid;
  private final String                 e164;
  private final String                 email;
  private final String                 groupId;
  private final List<Recipient>        participants;
  private final Optional<Long>         groupAvatarId;
  private final boolean                localNumber;
  private final boolean                blocked;
  private final long                   muteUntil;
  private final VibrateState           messageVibrate;
  private final VibrateState           callVibrate;
  private final Uri                    messageRingtone;
  private final Uri                    callRingtone;
  private final MaterialColor          color;
  private final Optional<Integer>      defaultSubscriptionId;
  private final int                    expireMessages;
  private final RegisteredState        registered;
  private final byte[]                 profileKey;
  private final String                 name;
  private final Uri                    systemContactPhoto;
  private final String                 customLabel;
  private final Uri                    contactUri;
  private final String                 profileName;
  private final String                 profileAvatar;
  private final boolean                profileSharing;
  private final String                 notificationChannel;
  private final UnidentifiedAccessMode unidentifiedAccessMode;
  private final boolean                forceSmsSelection;
  private final boolean                uuidSupported;
  private final InsightsBannerTier     insightsBannerTier;


  /**
   * Returns a {@link LiveRecipient}, which contains a {@link Recipient} that may or may not be
   * populated with data. However, you can observe the value that's returned to be notified when the
   * {@link Recipient} changes.
   */
  @AnyThread
  public static @NonNull LiveRecipient live(@NonNull RecipientId id) {
    Preconditions.checkNotNull(id, "ID cannot be null.");
    return ApplicationDependencies.getRecipientCache().getLive(id);
  }

  /**
   * Returns a fully-populated {@link Recipient}. May hit the disk, and therefore should be
   * called on a background thread.
   */
  @WorkerThread
  public static @NonNull Recipient resolved(@NonNull RecipientId id) {
    Preconditions.checkNotNull(id, "ID cannot be null.");
    return live(id).resolve();
  }

  /**
   * Returns a fully-populated {@link Recipient} based off of a {@link SignalServiceAddress},
   * creating one in the database if necessary. Convenience overload of
   * {@link #externalPush(Context, UUID, String)}
   */
  @WorkerThread
  public static @NonNull Recipient externalPush(@NonNull Context context, @NonNull SignalServiceAddress signalServiceAddress) {
    return externalPush(context, signalServiceAddress.getUuid().orNull(), signalServiceAddress.getNumber().orNull());
  }

  /**
   * Returns a fully-populated {@link Recipient} based off of a UUID and phone number, creating one
   * in the database if necessary. We want both piece of information so we're able to associate them
   * both together, depending on which are available.
   *
   * In particular, while we'll eventually get the UUID of a user created via a phone number
   * (through a directory sync), the only way we can store the phone number is by retrieving it from
   * sent messages and whatnot. So we should store it when available.
   */
  @WorkerThread
  public static @NonNull Recipient externalPush(@NonNull Context context, @Nullable UUID uuid, @Nullable String e164) {
    RecipientDatabase     db       = DatabaseFactory.getRecipientDatabase(context);
    Optional<RecipientId> uuidUser = uuid != null ? db.getByUuid(uuid) : Optional.absent();
    Optional<RecipientId> e164User = e164 != null ? db.getByE164(e164) : Optional.absent();

    if (uuidUser.isPresent()) {
      Recipient recipient = resolved(uuidUser.get());

      if (e164 != null && !recipient.getE164().isPresent() && !e164User.isPresent()) {
        db.setPhoneNumber(recipient.getId(), e164);
      }

      return resolved(recipient.getId());
    } else if (e164User.isPresent()) {
      Recipient recipient = resolved(e164User.get());

      if (uuid != null && !recipient.getUuid().isPresent()) {
        db.markRegistered(recipient.getId(), uuid);
      } else if (!recipient.isRegistered()) {
        db.markRegistered(recipient.getId());

        Log.i(TAG, "No UUID! Scheduling a fetch.");
        ApplicationDependencies.getJobManager().add(new DirectoryRefreshJob(recipient, false));
      }

      return resolved(recipient.getId());
    } else if (uuid != null) {
      if (FeatureFlags.UUIDS || e164 != null) {
        RecipientId id = db.getOrInsertFromUuid(uuid);
        db.markRegistered(id, uuid);

        if (e164 != null) {
          db.setPhoneNumber(id, e164);
        }

        return resolved(id);
      } else {
        throw new UuidRecipientError();
      }
    } else if (e164 != null) {
      Recipient recipient = resolved(db.getOrInsertFromE164(e164));

      if (!recipient.isRegistered()) {
        db.markRegistered(recipient.getId());

        Log.i(TAG, "No UUID! Scheduling a fetch.");
        ApplicationDependencies.getJobManager().add(new DirectoryRefreshJob(recipient, false));
      }

      return resolved(recipient.getId());
    } else {
      throw new AssertionError("You must provide either a UUID or phone number!");
    }
  }

  /**
   * Returns a fully-populated {@link Recipient} based off of a string identifier, creating one in
   * the database if necessary. The identifier may be a uuid, phone number, email,
   * or serialized groupId.
   *
   * If the identifier is a UUID of a Signal user, prefer using
   * {@link #externalPush(Context, UUID, String)} or its overload, as this will let us associate
   * the phone number with the recipient.
   */
  @WorkerThread
  public static @NonNull Recipient external(@NonNull Context context, @NonNull String identifier) {
    Preconditions.checkNotNull(identifier, "Identifier cannot be null!");

    RecipientDatabase db = DatabaseFactory.getRecipientDatabase(context);
    RecipientId       id = null;

    if (UuidUtil.isUuid(identifier)) {
      UUID uuid = UuidUtil.parseOrThrow(identifier);

      if (FeatureFlags.UUIDS) {
        id = db.getOrInsertFromUuid(uuid);
      } else {
        Optional<RecipientId> possibleId = db.getByUuid(uuid);

        if (possibleId.isPresent()) {
          id = possibleId.get();
        } else {
          throw new UuidRecipientError();
        }
      }
    } else if (GroupUtil.isEncodedGroup(identifier)) {
      id = db.getOrInsertFromGroupId(identifier);
    } else if (NumberUtil.isValidEmail(identifier)) {
      id = db.getOrInsertFromEmail(identifier);
    } else {
      String e164 = PhoneNumberFormatter.get(context).format(identifier);
      id = db.getOrInsertFromE164(e164);
    }

    return Recipient.resolved(id);
  }

  public static @NonNull Recipient self() {
    return ApplicationDependencies.getRecipientCache().getSelf();
  }

  Recipient(@NonNull RecipientId id) {
    this.id                     = id;
    this.resolving              = true;
    this.uuid                   = null;
    this.e164                   = null;
    this.email                  = null;
    this.groupId                = null;
    this.participants           = Collections.emptyList();
    this.groupAvatarId          = Optional.absent();
    this.localNumber            = false;
    this.blocked                = false;
    this.muteUntil              = 0;
    this.messageVibrate         = VibrateState.DEFAULT;
    this.callVibrate            = VibrateState.DEFAULT;
    this.messageRingtone        = null;
    this.callRingtone           = null;
    this.color                  = null;
    this.insightsBannerTier     = InsightsBannerTier.TIER_TWO;
    this.defaultSubscriptionId  = Optional.absent();
    this.expireMessages         = 0;
    this.registered             = RegisteredState.UNKNOWN;
    this.profileKey             = null;
    this.name                   = null;
    this.systemContactPhoto     = null;
    this.customLabel            = null;
    this.contactUri             = null;
    this.profileName            = null;
    this.profileAvatar          = null;
    this.profileSharing         = false;
    this.notificationChannel    = null;
    this.unidentifiedAccessMode = UnidentifiedAccessMode.DISABLED;
    this.forceSmsSelection      = false;
    this.uuidSupported          = false;
  }

  Recipient(@NonNull RecipientId id, @NonNull RecipientDetails details) {
    this.id                     = id;
    this.resolving              = false;
    this.uuid                   = details.uuid;
    this.e164                   = details.e164;
    this.email                  = details.email;
    this.groupId                = details.groupId;
    this.participants           = details.participants;
    this.groupAvatarId          = details.groupAvatarId;
    this.localNumber            = details.isLocalNumber;
    this.blocked                = details.blocked;
    this.muteUntil              = details.mutedUntil;
    this.messageVibrate         = details.messageVibrateState;
    this.callVibrate            = details.callVibrateState;
    this.messageRingtone        = details.messageRingtone;
    this.callRingtone           = details.callRingtone;
    this.color                  = details.color;
    this.insightsBannerTier     = details.insightsBannerTier;
    this.defaultSubscriptionId  = details.defaultSubscriptionId;
    this.expireMessages         = details.expireMessages;
    this.registered             = details.registered;
    this.profileKey             = details.profileKey;
    this.name                   = details.name;
    this.systemContactPhoto     = details.systemContactPhoto;
    this.customLabel            = details.customLabel;
    this.contactUri             = details.contactUri;
    this.profileName            = details.profileName;
    this.profileAvatar          = details.profileAvatar;
    this.profileSharing         = details.profileSharing;
    this.notificationChannel    = details.notificationChannel;
    this.unidentifiedAccessMode = details.unidentifiedAccessMode;
    this.forceSmsSelection      = details.forceSmsSelection;
    this.uuidSupported          = details.uuidSuported;
  }

  public @NonNull RecipientId getId() {
    return id;
  }

  public boolean isLocalNumber() {
    return localNumber;
  }

  public @Nullable Uri getContactUri() {
    return contactUri;
  }

  public @Nullable String getName(@NonNull Context context) {
    if (this.name == null && groupId != null && GroupUtil.isMmsGroup(groupId)) {
      List<String> names = new LinkedList<>();

      for (Recipient recipient : participants) {
        names.add(recipient.toShortString(context));
      }

      return Util.join(names, ", ");
    }

    return this.name;
  }

  /**
   * TODO [UUID] -- Remove once UUID Feature Flag is removed
   */
  @Deprecated
  public @NonNull String toShortString(@NonNull Context context) {
    if (FeatureFlags.PROFILE_DISPLAY) return getDisplayName(context);
    else                    return Optional.fromNullable(getName(context)).or(getSmsAddress()).or("");
  }

  public @NonNull String getDisplayName(@NonNull Context context) {
    return Util.getFirstNonEmpty(getName(context),
                                 getProfileName(),
                                 getUsername(),
                                 e164,
                                 email,
                                 context.getString(R.string.Recipient_unknown));
  }

  private @NonNull String getUsername() {
    if (FeatureFlags.USERNAMES) {
      // TODO [greyson] Replace with actual username
      return "@caycepollard";
    }
    return "";
  }

  public @NonNull MaterialColor getColor() {
    if      (isGroupInternal()) return MaterialColor.GROUP;
    else if (color != null)     return color;
    else if (name != null)      return ContactColors.generateFor(name);
    else                        return ContactColors.UNKNOWN_COLOR;
  }

  public @NonNull Optional<UUID> getUuid() {
    return Optional.fromNullable(uuid);
  }

  public @NonNull Optional<String> getE164() {
    return Optional.fromNullable(e164);
  }

  public @NonNull Optional<String> getEmail() {
    return Optional.fromNullable(email);
  }

  public @NonNull Optional<String> getGroupId() {
    return Optional.fromNullable(groupId);
  }

  public @NonNull Optional<String> getSmsAddress() {
    return Optional.fromNullable(e164).or(Optional.fromNullable(email));
  }

  public @NonNull String requireE164() {
    String resolved = resolving ? resolve().e164 : e164;

    if (resolved == null) {
      throw new MissingAddressError();
    }

    return resolved;
  }

  public @NonNull String requireEmail() {
    String resolved = resolving ? resolve().email : email;

    if (resolved == null) {
      throw new MissingAddressError();
    }

    return resolved;
  }

  public @NonNull String requireSmsAddress() {
    Recipient recipient = resolving ? resolve() : this;

    if (recipient.getE164().isPresent()) {
      return recipient.getE164().get();
    } else if (recipient.getEmail().isPresent()) {
      return recipient.getEmail().get();
    } else {
      throw new MissingAddressError();
    }
  }

  public boolean hasSmsAddress() {
    return getE164().or(getEmail()).isPresent();
  }

  public boolean hasE164() {
    return getE164().isPresent();
  }

  public boolean hasUuid() {
    return getUuid().isPresent();
  }

  public @NonNull String requireGroupId() {
    String resolved = resolving ? resolve().groupId : groupId;

    if (resolved == null) {
      throw new MissingAddressError();
    }

    return resolved;
  }

  public boolean hasServiceIdentifier() {
    return uuid != null || e164 != null;
  }

  /**
   * @return A string identifier able to be used with the Signal service. Prefers UUID, and if not
   * available, will return an E164 number.
   */
  public @NonNull String requireServiceId() {
    Recipient resolved = resolving ? resolve() : this;

    if (resolved.getUuid().isPresent()) {
      return resolved.getUuid().get().toString();
    } else {
      return getE164().get();
    }
  }

  /**
   * @return A single string to represent the recipient, in order of precedence:
   *
   * Group ID > UUID > Phone > Email
   */
  public @NonNull String requireStringId() {
    Recipient resolved = resolving ? resolve() : this;

    if (resolved.isGroup()) {
      return resolved.requireGroupId();
    } else if (resolved.getUuid().isPresent()) {
      return resolved.getUuid().get().toString();
    }

    return requireSmsAddress();
  }

  public Optional<Integer> getDefaultSubscriptionId() {
    return defaultSubscriptionId;
  }

  public @Nullable String getProfileName() {
    return profileName;
  }

  public @Nullable String getCustomLabel() {
    if (FeatureFlags.PROFILE_DISPLAY) throw new AssertionError("This method should never be called if PROFILE_DISPLAY is enabled.");
    return customLabel;
  }

  public @Nullable String getProfileAvatar() {
    return profileAvatar;
  }

  public boolean isProfileSharing() {
    return profileSharing;
  }

  public boolean isGroup() {
    return resolve().groupId != null;
  }

  private boolean isGroupInternal() {
    return groupId != null;
  }

  public boolean isMmsGroup() {
    String groupId = resolve().groupId;
    return groupId != null && GroupUtil.isMmsGroup(groupId);
  }

  public boolean isPushGroup() {
    String groupId = resolve().groupId;
    return groupId != null && !GroupUtil.isMmsGroup(groupId);
  }

  public @NonNull List<Recipient> getParticipants() {
    return new ArrayList<>(participants);
  }

  public @NonNull Drawable getFallbackContactPhotoDrawable(Context context, boolean inverted) {
    return getFallbackContactPhoto().asDrawable(context, getColor().toAvatarColor(context), inverted);
  }

  public @NonNull Drawable getSmallFallbackContactPhotoDrawable(Context context, boolean inverted) {
    return getFallbackContactPhoto().asSmallDrawable(context, getColor().toAvatarColor(context), inverted);
  }

  public @NonNull FallbackContactPhoto getFallbackContactPhoto() {
    if      (localNumber)              return new ResourceContactPhoto(R.drawable.ic_note_to_self);
    if      (isResolving())            return new TransparentContactPhoto();
    else if (isGroupInternal())        return new ResourceContactPhoto(R.drawable.ic_group_outline_40, R.drawable.ic_group_outline_20, R.drawable.ic_group_large);
    else if (isGroup())                return new ResourceContactPhoto(R.drawable.ic_group_outline_40, R.drawable.ic_group_outline_20, R.drawable.ic_group_large);
    else if (!TextUtils.isEmpty(name)) return new GeneratedContactPhoto(name, R.drawable.ic_profile_outline_40);
    else                               return new ResourceContactPhoto(R.drawable.ic_profile_outline_40, R.drawable.ic_profile_outline_20, R.drawable.ic_person_large);
  }

  public @Nullable ContactPhoto getContactPhoto() {
    if      (localNumber)                                    return null;
    else if (isGroupInternal() && groupAvatarId.isPresent()) return new GroupRecordContactPhoto(groupId, groupAvatarId.get());
    else if (systemContactPhoto != null)                     return new SystemContactPhoto(id, systemContactPhoto, 0);
    else if (profileAvatar != null)                          return new ProfileContactPhoto(id, profileAvatar);
    else                                                     return null;
  }

  public @Nullable Uri getMessageRingtone() {
    if (messageRingtone != null && messageRingtone.getScheme() != null && messageRingtone.getScheme().startsWith("file")) {
      return null;
    }

    return messageRingtone;
  }

  public @Nullable Uri getCallRingtone() {
    if (callRingtone != null && callRingtone.getScheme() != null && callRingtone.getScheme().startsWith("file")) {
      return null;
    }

    return callRingtone;
  }

  public boolean isMuted() {
    return System.currentTimeMillis() <= muteUntil;
  }

  public boolean isBlocked() {
    return blocked;
  }

  public @NonNull VibrateState getMessageVibrate() {
    return messageVibrate;
  }

  public @NonNull VibrateState getCallVibrate() {
    return callVibrate;
  }

  public int getExpireMessages() {
    return expireMessages;
  }

  public boolean hasSeenFirstInviteReminder() {
    return insightsBannerTier.seen(InsightsBannerTier.TIER_ONE);
  }

  public boolean hasSeenSecondInviteReminder() {
    return insightsBannerTier.seen(InsightsBannerTier.TIER_TWO);
  }

  public @NonNull RegisteredState getRegistered() {
    if      (isPushGroup()) return RegisteredState.REGISTERED;
    else if (isMmsGroup())  return RegisteredState.NOT_REGISTERED;

    return registered;
  }

  public boolean isRegistered() {
    return registered == RegisteredState.REGISTERED || isPushGroup();
  }

  public @Nullable String getNotificationChannel() {
    return !NotificationChannels.supported() ? null : notificationChannel;
  }

  public boolean isForceSmsSelection() {
    return forceSmsSelection;
  }

  /**
   * @return True if this recipient can support receiving UUID-only messages, otherwise false.
   */
  public boolean isUuidSupported() {
    return FeatureFlags.UUIDS && uuidSupported;
  }

  public @Nullable byte[] getProfileKey() {
    return profileKey;
  }

  public @NonNull UnidentifiedAccessMode getUnidentifiedAccessMode() {
    return unidentifiedAccessMode;
  }

  public boolean isSystemContact() {
    return contactUri != null;
  }

  public Recipient resolve() {
    if (resolving) {
      return live().resolve();
    } else {
      return this;
    }
  }

  public boolean isResolving() {
    return resolving;
  }

  public @NonNull LiveRecipient live() {
    return ApplicationDependencies.getRecipientCache().getLive(id);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Recipient recipient = (Recipient) o;
    return id.equals(recipient.id);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }

  private static class MissingAddressError extends AssertionError {
  }

  private static class UuidRecipientError extends AssertionError {
  }
}
