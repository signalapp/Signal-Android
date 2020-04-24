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
import org.thoughtcrime.securesms.database.IdentityDatabase.VerifiedStatus;
import org.thoughtcrime.securesms.database.RecipientDatabase;
import org.thoughtcrime.securesms.database.RecipientDatabase.RegisteredState;
import org.thoughtcrime.securesms.database.RecipientDatabase.UnidentifiedAccessMode;
import org.thoughtcrime.securesms.database.RecipientDatabase.VibrateState;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.groups.GroupId;
import org.thoughtcrime.securesms.jobs.DirectoryRefreshJob;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.notifications.NotificationChannels;
import org.thoughtcrime.securesms.phonenumbers.NumberUtil;
import org.thoughtcrime.securesms.phonenumbers.PhoneNumberFormatter;
import org.thoughtcrime.securesms.profiles.ProfileName;
import org.thoughtcrime.securesms.util.FeatureFlags;
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

  private static final FallbackPhotoProvider DEFAULT_FALLBACK_PHOTO_PROVIDER = new FallbackPhotoProvider();
  private static final String                TAG = Log.tag(Recipient.class);

  private final RecipientId            id;
  private final boolean                resolving;
  private final UUID                   uuid;
  private final String                 username;
  private final String                 e164;
  private final String                 email;
  private final GroupId                groupId;
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
  private final byte[]                 profileKeyCredential;
  private final String                 name;
  private final Uri                    systemContactPhoto;
  private final String                 customLabel;
  private final Uri                    contactUri;
  private final ProfileName            profileName;
  private final String                 profileAvatar;
  private final boolean                hasProfileImage;
  private final boolean                profileSharing;
  private final String                 notificationChannel;
  private final UnidentifiedAccessMode unidentifiedAccessMode;
  private final boolean                forceSmsSelection;
  private final Capability             uuidCapability;
  private final Capability             groupsV2Capability;
  private final InsightsBannerTier     insightsBannerTier;
  private final byte[]                 storageId;
  private final byte[]                 identityKey;
  private final VerifiedStatus         identityStatus;


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
   * Returns a fully-populated {@link Recipient} and associates it with the provided username.
   */
  @WorkerThread
  public static @NonNull Recipient externalUsername(@NonNull Context context, @NonNull UUID uuid, @NonNull String username) {
    Recipient recipient = externalPush(context, uuid, null);
    DatabaseFactory.getRecipientDatabase(context).setUsername(recipient.getId(), username);
    return recipient;
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

        if (FeatureFlags.uuids()) {
          Log.i(TAG, "No UUID! Scheduling a fetch.");
          ApplicationDependencies.getJobManager().add(new DirectoryRefreshJob(recipient, false));
        }
      }

      return resolved(recipient.getId());
    } else if (uuid != null) {
      if (FeatureFlags.uuids() || e164 != null) {
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

        if (FeatureFlags.uuids()) {
          Log.i(TAG, "No UUID! Scheduling a fetch.");
          ApplicationDependencies.getJobManager().add(new DirectoryRefreshJob(recipient, false));
        }
      }

      return resolved(recipient.getId());
    } else {
      throw new AssertionError("You must provide either a UUID or phone number!");
    }
  }

  /**
   * A safety wrapper around {@link #external(Context, String)} for when you know you're using an
   * identifier for a system contact, and therefore always want to prevent interpreting it as a
   * UUID. This will crash if given a UUID.
   *
   * (This may seem strange, but apparently some devices are returning valid UUIDs for contacts)
   */
  @WorkerThread
  public static @NonNull Recipient externalContact(@NonNull Context context, @NonNull String identifier) {
    RecipientDatabase db = DatabaseFactory.getRecipientDatabase(context);
    RecipientId       id = null;

    if (UuidUtil.isUuid(identifier)) {
      throw new UuidRecipientError();
    } else if (NumberUtil.isValidEmail(identifier)) {
      id = db.getOrInsertFromEmail(identifier);
    } else {
      id = db.getOrInsertFromE164(identifier);
    }

    return Recipient.resolved(id);
  }

  /**
   * A version of {@link #external(Context, String)} that should be used when you know the
   * identifier is a groupId.
   */
  @WorkerThread
  public static @NonNull Recipient externalGroup(@NonNull Context context, @NonNull GroupId groupId) {
    return Recipient.resolved(DatabaseFactory.getRecipientDatabase(context).getOrInsertFromGroupId(groupId));
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

      if (FeatureFlags.uuids()) {
        id = db.getOrInsertFromUuid(uuid);
      } else {
        Optional<RecipientId> possibleId = db.getByUuid(uuid);

        if (possibleId.isPresent()) {
          id = possibleId.get();
        } else {
          throw new UuidRecipientError();
        }
      }
    } else if (GroupId.isEncodedGroup(identifier)) {
      id = db.getOrInsertFromGroupId(GroupId.parseOrThrow(identifier));
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
    this.username               = null;
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
    this.profileKeyCredential   = null;
    this.name                   = null;
    this.systemContactPhoto     = null;
    this.customLabel            = null;
    this.contactUri             = null;
    this.profileName            = ProfileName.EMPTY;
    this.profileAvatar          = null;
    this.hasProfileImage        = false;
    this.profileSharing         = false;
    this.notificationChannel    = null;
    this.unidentifiedAccessMode = UnidentifiedAccessMode.DISABLED;
    this.forceSmsSelection      = false;
    this.uuidCapability         = Capability.UNKNOWN;
    this.groupsV2Capability     = Capability.UNKNOWN;
    this.storageId              = null;
    this.identityKey            = null;
    this.identityStatus         = VerifiedStatus.DEFAULT;
  }

  Recipient(@NonNull RecipientId id, @NonNull RecipientDetails details) {
    this.id                     = id;
    this.resolving              = false;
    this.uuid                   = details.uuid;
    this.username               = details.username;
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
    this.profileKeyCredential   = details.profileKeyCredential;
    this.name                   = details.name;
    this.systemContactPhoto     = details.systemContactPhoto;
    this.customLabel            = details.customLabel;
    this.contactUri             = details.contactUri;
    this.profileName            = details.profileName;
    this.profileAvatar          = details.profileAvatar;
    this.hasProfileImage        = details.hasProfileImage;
    this.profileSharing         = details.profileSharing;
    this.notificationChannel    = details.notificationChannel;
    this.unidentifiedAccessMode = details.unidentifiedAccessMode;
    this.forceSmsSelection      = details.forceSmsSelection;
    this.uuidCapability         = details.uuidCapability;
    this.groupsV2Capability     = details.groupsV2Capability;
    this.storageId              = details.storageId;
    this.identityKey            = details.identityKey;
    this.identityStatus         = details.identityStatus;
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
    if (this.name == null && groupId != null && groupId.isMms()) {
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
    if (FeatureFlags.profileDisplay()) return getDisplayName(context);
    else                               return Optional.fromNullable(getName(context)).or(getSmsAddress()).or("");
  }

  public @NonNull String getDisplayName(@NonNull Context context) {
    return Util.getFirstNonEmpty(getName(context),
                                 getProfileName().toString(),
                                 getDisplayUsername(),
                                 e164,
                                 email,
                                 context.getString(R.string.Recipient_unknown));
  }

  public @NonNull MaterialColor getColor() {
    if (isGroupInternal()) {
      return MaterialColor.GROUP;
    } else if (color != null) {
      return color;
     } else if (name != null) {
      Log.i(TAG, "Saving color for " + id);
      MaterialColor color = ContactColors.generateFor(name);
      DatabaseFactory.getRecipientDatabase(ApplicationDependencies.getApplication()).setColor(id, color);
      return color;
    } else {
      return ContactColors.UNKNOWN_COLOR;
    }
  }

  public @NonNull Optional<UUID> getUuid() {
    return Optional.fromNullable(uuid);
  }

  public @NonNull Optional<String> getUsername() {
    if (FeatureFlags.usernames()) {
      return Optional.fromNullable(username);
    } else {
      return Optional.absent();
    }
  }

  public @NonNull Optional<String> getE164() {
    return Optional.fromNullable(e164);
  }

  public @NonNull Optional<String> getEmail() {
    return Optional.fromNullable(email);
  }

  public @NonNull Optional<GroupId> getGroupId() {
    return Optional.fromNullable(groupId);
  }

  public @NonNull Optional<String> getSmsAddress() {
    return Optional.fromNullable(e164).or(Optional.fromNullable(email));
  }

  public @NonNull UUID requireUuid() {
    UUID resolved = resolving ? resolve().uuid : uuid;

    if (resolved == null) {
      throw new MissingAddressError();
    }

    return resolved;
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

  public @NonNull GroupId requireGroupId() {
    GroupId resolved = resolving ? resolve().groupId : groupId;

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
      return resolved.requireGroupId().toString();
    } else if (resolved.getUuid().isPresent()) {
      return resolved.getUuid().get().toString();
    }

    return requireSmsAddress();
  }

  public Optional<Integer> getDefaultSubscriptionId() {
    return defaultSubscriptionId;
  }

  public @NonNull ProfileName getProfileName() {
    return profileName;
  }

  public @Nullable String getCustomLabel() {
    if (FeatureFlags.profileDisplay()) throw new AssertionError("This method should never be called if PROFILE_DISPLAY is enabled.");
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
    GroupId groupId = resolve().groupId;
    return groupId != null && groupId.isMms();
  }

  public boolean isPushGroup() {
    GroupId groupId = resolve().groupId;
    return groupId != null && groupId.isPush();
  }

  public boolean isPushV2Group() {
    GroupId groupId = resolve().groupId;
    return groupId != null && groupId.isV2();
  }

  public @NonNull List<Recipient> getParticipants() {
    return new ArrayList<>(participants);
  }

  public @NonNull Drawable getFallbackContactPhotoDrawable(Context context, boolean inverted) {
    return getFallbackContactPhotoDrawable(context, inverted, DEFAULT_FALLBACK_PHOTO_PROVIDER);
  }

  public @NonNull Drawable getFallbackContactPhotoDrawable(Context context, boolean inverted, @Nullable FallbackPhotoProvider fallbackPhotoProvider) {
    return getFallbackContactPhoto(Util.firstNonNull(fallbackPhotoProvider, DEFAULT_FALLBACK_PHOTO_PROVIDER)).asDrawable(context, getColor().toAvatarColor(context), inverted);
  }

  public @NonNull Drawable getSmallFallbackContactPhotoDrawable(Context context, boolean inverted, @Nullable FallbackPhotoProvider fallbackPhotoProvider) {
    return getFallbackContactPhoto(Util.firstNonNull(fallbackPhotoProvider, DEFAULT_FALLBACK_PHOTO_PROVIDER)).asSmallDrawable(context, getColor().toAvatarColor(context), inverted);
  }

  public @NonNull FallbackContactPhoto getFallbackContactPhoto() {
    return getFallbackContactPhoto(DEFAULT_FALLBACK_PHOTO_PROVIDER);
  }

  public @NonNull FallbackContactPhoto getFallbackContactPhoto(@NonNull FallbackPhotoProvider fallbackPhotoProvider) {
    if      (localNumber)              return fallbackPhotoProvider.getPhotoForLocalNumber();
    if      (isResolving())            return fallbackPhotoProvider.getPhotoForResolvingRecipient();
    else if (isGroupInternal())        return fallbackPhotoProvider.getPhotoForGroup();
    else if (isGroup())                return fallbackPhotoProvider.getPhotoForGroup();
    else if (!TextUtils.isEmpty(name)) return fallbackPhotoProvider.getPhotoForRecipientWithName(name);
    else                               return fallbackPhotoProvider.getPhotoForRecipientWithoutName();
  }

  public @Nullable ContactPhoto getContactPhoto() {
    if      (localNumber)                                    return null;
    else if (isGroupInternal() && groupAvatarId.isPresent()) return new GroupRecordContactPhoto(groupId, groupAvatarId.get());
    else if (systemContactPhoto != null)                     return new SystemContactPhoto(id, systemContactPhoto, 0);
    else if (profileAvatar != null && hasProfileImage)       return new ProfileContactPhoto(this, profileAvatar);
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
    if (FeatureFlags.usernames()) {
      return true;
    } else {
      return FeatureFlags.uuids() && uuidCapability == Capability.SUPPORTED;
    }
  }

  public Capability getGroupsV2Capability() {
    return groupsV2Capability;
  }

  public @Nullable byte[] getProfileKey() {
    return profileKey;
  }

  public @Nullable byte[] getProfileKeyCredential() {
    return profileKeyCredential;
  }

  public boolean hasProfileKeyCredential() {
    return profileKeyCredential != null;
  }

  public @Nullable byte[] getStorageServiceId() {
    return storageId;
  }

  public @NonNull VerifiedStatus getIdentityVerifiedStatus() {
    return identityStatus;
  }

  public @Nullable byte[] getIdentityKey() {
    return identityKey;
  }

  public @NonNull UnidentifiedAccessMode getUnidentifiedAccessMode() {
    return unidentifiedAccessMode;
  }

  public boolean isSystemContact() {
    return contactUri != null;
  }

  /**
   * If this recipient is missing crucial data, this will return a populated copy. Otherwise it
   * returns itself.
   */
  public @NonNull Recipient resolve() {
    if (resolving) {
      return live().resolve();
    } else {
      return this;
    }
  }

  public boolean isResolving() {
    return resolving;
  }

  /**
   * Forces retrieving a fresh copy of the recipient, regardless of its state.
   */
  public @NonNull Recipient fresh() {
    return live().resolve();
  }

  public @NonNull LiveRecipient live() {
    return ApplicationDependencies.getRecipientCache().getLive(id);
  }

  private @Nullable String getDisplayUsername() {
    if (!TextUtils.isEmpty(username)) {
      return "@" + username;
    } else {
      return null;
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Recipient recipient = (Recipient) o;
    return id.equals(recipient.id);
  }

  public enum Capability {
    UNKNOWN(0),
    SUPPORTED(1),
    NOT_SUPPORTED(-1);

    private final int value;

    Capability(int value) {
      this.value = value;
    }

    public int serialize() {
      return value;
    }

    public static Capability deserialize(int value) {
      switch (value) {
        case  1 : return SUPPORTED;
        case -1 : return NOT_SUPPORTED;
        default : return UNKNOWN;
      }
    }

    public static Capability fromBoolean(boolean supported) {
      return supported ? SUPPORTED : NOT_SUPPORTED;
    }
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }

  public static class FallbackPhotoProvider {
    public @NonNull FallbackContactPhoto getPhotoForLocalNumber() {
      return new ResourceContactPhoto(R.drawable.ic_note_34, R.drawable.ic_note_24);
    }

    public @NonNull FallbackContactPhoto getPhotoForResolvingRecipient() {
      return new TransparentContactPhoto();
    }

    public @NonNull FallbackContactPhoto getPhotoForGroup() {
      return new ResourceContactPhoto(R.drawable.ic_group_outline_34, R.drawable.ic_group_outline_20, R.drawable.ic_group_outline_48);
    }

    public @NonNull FallbackContactPhoto getPhotoForRecipientWithName(String name) {
      return new GeneratedContactPhoto(name, R.drawable.ic_profile_outline_40);
    }

    public @NonNull FallbackContactPhoto getPhotoForRecipientWithoutName() {
      return new ResourceContactPhoto(R.drawable.ic_profile_outline_40, R.drawable.ic_profile_outline_20, R.drawable.ic_profile_outline_48);
    }

  }

  private static class MissingAddressError extends AssertionError {
  }

  private static class UuidRecipientError extends AssertionError {
  }
}
