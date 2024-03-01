package org.thoughtcrime.securesms.keyvalue;

import android.preference.PreferenceManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.components.settings.app.usernamelinks.UsernameQrCodeColorScheme;
import org.thoughtcrime.securesms.database.model.databaseprotos.PendingChangeNumberMetadata;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobmanager.impl.ChangeNumberConstraintObserver;

import java.util.Collections;
import java.util.List;

public final class MiscellaneousValues extends SignalStoreValues {

  private static final String LAST_PREKEY_REFRESH_TIME       = "last_prekey_refresh_time";
  private static final String MESSAGE_REQUEST_ENABLE_TIME    = "message_request_enable_time";
  private static final String LAST_PROFILE_REFRESH_TIME      = "misc.last_profile_refresh_time";
  private static final String USERNAME_SHOW_REMINDER         = "username.show.reminder";
  private static final String CLIENT_DEPRECATED              = "misc.client_deprecated";
  private static final String OLD_DEVICE_TRANSFER_LOCKED     = "misc.old_device.transfer.locked";
  private static final String HAS_EVER_HAD_AN_AVATAR         = "misc.has.ever.had.an.avatar";
  private static final String CHANGE_NUMBER_LOCK             = "misc.change_number.lock";
  private static final String PENDING_CHANGE_NUMBER_METADATA = "misc.pending_change_number.metadata";
  private static final String CENSORSHIP_LAST_CHECK_TIME     = "misc.censorship.last_check_time";
  private static final String CENSORSHIP_SERVICE_REACHABLE   = "misc.censorship.service_reachable";
  private static final String LAST_GV2_PROFILE_CHECK_TIME    = "misc.last_gv2_profile_check_time";
  private static final String CDS_TOKEN                      = "misc.cds_token";
  private static final String CDS_BLOCKED_UNTIL              = "misc.cds_blocked_until";
  private static final String LAST_FOREGROUND_TIME           = "misc.last_foreground_time";
  private static final String PNI_INITIALIZED_DEVICES        = "misc.pni_initialized_devices";
  private static final String LINKED_DEVICES_REMINDER        = "misc.linked_devices_reminder";
  private static final String HAS_LINKED_DEVICES             = "misc.linked_devices_present";
  private static final String USERNAME_QR_CODE_COLOR         = "mis.username_qr_color_scheme";
  private static final String KEYBOARD_LANDSCAPE_HEIGHT      = "misc.keyboard.landscape_height";
  private static final String KEYBOARD_PORTRAIT_HEIGHT       = "misc.keyboard.protrait_height";
  private static final String LAST_CONSISTENCY_CHECK_TIME    = "misc.last_consistency_check_time";
  private static final String SERVER_TIME_OFFSET             = "misc.server_time_offset";
  private static final String LAST_SERVER_TIME_OFFSET_UPDATE = "misc.last_server_time_offset_update";
  private static final String NEEDS_USERNAME_RESTORE         = "misc.needs_username_restore";
  private static final String LAST_FORCED_PREKEY_REFRESH     = "misc.last_forced_prekey_refresh";
  private static final String LAST_CDS_FOREGROUND_SYNC       = "misc.last_cds_foreground_sync";

  MiscellaneousValues(@NonNull KeyValueStore store) {
    super(store);
  }

  @Override
  void onFirstEverAppLaunch() {
    putLong(MESSAGE_REQUEST_ENABLE_TIME, 0);
    putBoolean(NEEDS_USERNAME_RESTORE, true);
  }

  @Override
  @NonNull List<String> getKeysToIncludeInBackup() {
    return Collections.emptyList();
  }

  /**
   * Represents the last time a _full_ prekey refreshed finished. That means signed+one-time prekeys for both ACI and PNI.
   */
  public long getLastFullPrekeyRefreshTime() {
    return getLong(LAST_PREKEY_REFRESH_TIME, 0);
  }

  public void setLastFullPrekeyRefreshTime(long time) {
    putLong(LAST_PREKEY_REFRESH_TIME, time);
  }

  public long getMessageRequestEnableTime() {
    return getLong(MESSAGE_REQUEST_ENABLE_TIME, 0);
  }

  public long getLastProfileRefreshTime() {
    return getLong(LAST_PROFILE_REFRESH_TIME, 0);
  }

  public void setLastProfileRefreshTime(long time) {
    putLong(LAST_PROFILE_REFRESH_TIME, time);
  }

  public void hideUsernameReminder() {
    putBoolean(USERNAME_SHOW_REMINDER, false);
  }

  public boolean shouldShowUsernameReminder() {
    return getBoolean(USERNAME_SHOW_REMINDER, true);
  }

  public boolean isClientDeprecated() {
    return getBoolean(CLIENT_DEPRECATED, false);
  }

  public void markClientDeprecated() {
    putBoolean(CLIENT_DEPRECATED, true);
  }

  public void clearClientDeprecated() {
    putBoolean(CLIENT_DEPRECATED, false);
  }

  public boolean isOldDeviceTransferLocked() {
    return getBoolean(OLD_DEVICE_TRANSFER_LOCKED, false);
  }

  public void markOldDeviceTransferLocked() {
    putBoolean(OLD_DEVICE_TRANSFER_LOCKED, true);
  }

  public void clearOldDeviceTransferLocked() {
    putBoolean(OLD_DEVICE_TRANSFER_LOCKED, false);
  }

  public boolean hasEverHadAnAvatar() {
    return getBoolean(HAS_EVER_HAD_AN_AVATAR, false);
  }

  public void markHasEverHadAnAvatar() {
    putBoolean(HAS_EVER_HAD_AN_AVATAR, true);
  }

  public boolean isChangeNumberLocked() {
    return getBoolean(CHANGE_NUMBER_LOCK, false);
  }

  public void lockChangeNumber() {
    putBoolean(CHANGE_NUMBER_LOCK, true);
    ChangeNumberConstraintObserver.INSTANCE.onChange();
  }

  public void unlockChangeNumber() {
    putBoolean(CHANGE_NUMBER_LOCK, false);
    ChangeNumberConstraintObserver.INSTANCE.onChange();
  }

  public @Nullable PendingChangeNumberMetadata getPendingChangeNumberMetadata() {
    return getObject(PENDING_CHANGE_NUMBER_METADATA, null, PendingChangeNumberMetadataSerializer.INSTANCE);
  }

  /** Store pending new PNI data to be applied after successful change number */
  public void setPendingChangeNumberMetadata(@NonNull PendingChangeNumberMetadata metadata) {
    putObject(PENDING_CHANGE_NUMBER_METADATA, metadata, PendingChangeNumberMetadataSerializer.INSTANCE);
  }

  /** Clear pending new PNI data after confirmed successful or failed change number */
  public void clearPendingChangeNumberMetadata() {
    remove(PENDING_CHANGE_NUMBER_METADATA);
  }

  public long getLastCensorshipServiceReachabilityCheckTime() {
    return getLong(CENSORSHIP_LAST_CHECK_TIME, 0);
  }

  public void setLastCensorshipServiceReachabilityCheckTime(long value) {
    putLong(CENSORSHIP_LAST_CHECK_TIME, value);
  }

  public boolean isServiceReachableWithoutCircumvention() {
    return getBoolean(CENSORSHIP_SERVICE_REACHABLE, false);
  }

  public void setServiceReachableWithoutCircumvention(boolean value) {
    putBoolean(CENSORSHIP_SERVICE_REACHABLE, value);
  }

  public long getLastGv2ProfileCheckTime() {
    return getLong(LAST_GV2_PROFILE_CHECK_TIME, 0);
  }

  public void setLastGv2ProfileCheckTime(long value) {
    putLong(LAST_GV2_PROFILE_CHECK_TIME, value);
  }

  public @Nullable byte[] getCdsToken() {
    return getBlob(CDS_TOKEN, null);
  }

  public void setCdsToken(@Nullable byte[] token) {
    getStore().beginWrite()
              .putBlob(CDS_TOKEN, token)
              .commit();
  }

  /**
   * Marks the time at which we think the next CDS request will succeed. This should be taken from the service response.
   */
  public void setCdsBlockedUtil(long time) {
    putLong(CDS_BLOCKED_UNTIL, time);
  }

  /**
   * Indicates that a CDS request will never succeed at the current contact count.
   */
  public void markCdsPermanentlyBlocked() {
    putLong(CDS_BLOCKED_UNTIL, Long.MAX_VALUE);
  }

  /**
   * Clears any rate limiting state related to CDS.
   */
  public void clearCdsBlocked() {
    setCdsBlockedUtil(0);
  }

  /**
   * Whether or not we expect the next CDS request to succeed.
   */
  public boolean isCdsBlocked() {
    return getCdsBlockedUtil() > 0;
  }

  /**
   * This represents the next time we think we'll be able to make a successful CDS request. If it is before this time, we expect the request will fail
   * (assuming the user still has the same number of new E164s).
   */
  public long getCdsBlockedUtil() {
    return getLong(CDS_BLOCKED_UNTIL, 0);
  }

  public long getLastForegroundTime() {
    return getLong(LAST_FOREGROUND_TIME, 0);
  }

  public void setLastForegroundTime(long time) {
    putLong(LAST_FOREGROUND_TIME, time);
  }

  public boolean hasPniInitializedDevices() {
    return getBoolean(PNI_INITIALIZED_DEVICES, false);
  }

  public void setPniInitializedDevices(boolean value) {
    putBoolean(PNI_INITIALIZED_DEVICES, value);
  }

  public @NonNull SmsExportPhase getSmsExportPhase() {
    return SmsExportPhase.getCurrentPhase();
  }

  public void setHasLinkedDevices(boolean value) {
    putBoolean(HAS_LINKED_DEVICES, value);
  }

  public boolean getHasLinkedDevices() {
    return getBoolean(HAS_LINKED_DEVICES, false);
  }

  public void setShouldShowLinkedDevicesReminder(boolean value) {
    putBoolean(LINKED_DEVICES_REMINDER, value);
  }

  public boolean getShouldShowLinkedDevicesReminder() {
    return getBoolean(LINKED_DEVICES_REMINDER, false);
  }

  /** The color the user saved for rendering their shareable username QR code. */
  public @NonNull UsernameQrCodeColorScheme getUsernameQrCodeColorScheme() {
    String serialized = getString(USERNAME_QR_CODE_COLOR, null);
    return UsernameQrCodeColorScheme.deserialize(serialized);
  }

  public void setUsernameQrCodeColorScheme(@NonNull UsernameQrCodeColorScheme color) {
    putString(USERNAME_QR_CODE_COLOR, color.serialize());
  }

  public int getKeyboardLandscapeHeight() {
    int height = (int) getLong(KEYBOARD_LANDSCAPE_HEIGHT, 0);
    if (height == 0) {
      //noinspection deprecation
      height = PreferenceManager.getDefaultSharedPreferences(ApplicationDependencies.getApplication())
                                .getInt("keyboard_height_landscape", 0);

      if (height > 0) {
        setKeyboardLandscapeHeight(height);
      }
    }
    return height;
  }

  public void setKeyboardLandscapeHeight(int height) {
    putLong(KEYBOARD_LANDSCAPE_HEIGHT, height);
  }

  public int getKeyboardPortraitHeight() {
    int height = (int) getInteger(KEYBOARD_PORTRAIT_HEIGHT, 0);
    if (height == 0) {
      //noinspection deprecation
      height = PreferenceManager.getDefaultSharedPreferences(ApplicationDependencies.getApplication())
                                .getInt("keyboard_height_portrait", 0);

      if (height > 0) {
        setKeyboardPortraitHeight(height);
      }
    }
    return height;
  }

  public void setKeyboardPortraitHeight(int height) {
    putInteger(KEYBOARD_PORTRAIT_HEIGHT, height);
  }

  public long getLastConsistencyCheckTime() {
    return getLong(LAST_CONSISTENCY_CHECK_TIME, 0);
  }

  public void setLastConsistencyCheckTime(long time) {
    putLong(LAST_CONSISTENCY_CHECK_TIME, time);
  }

  /**
   * Sets the last-known server time.
   */
  public void setLastKnownServerTime(long serverTime, long currentTime) {
    getStore()
        .beginWrite()
        .putLong(SERVER_TIME_OFFSET, currentTime - serverTime)
        .putLong(LAST_SERVER_TIME_OFFSET_UPDATE, System.currentTimeMillis())
        .apply();
  }

  /**
   * The last-known offset between our local clock and the server. To get an estimate of the server time, take your current time and subtract this offset. e.g.
   *
   * estimatedServerTime = System.currentTimeMillis() - SignalStore.misc().getLastKnownServerTimeOffset()
   */
  public long getLastKnownServerTimeOffset() {
    return getLong(SERVER_TIME_OFFSET, 0);
  }

  /**
   * The last time (using our local clock) we updated the server time offset returned by {@link #getLastKnownServerTimeOffset()}}.
   */
  public long getLastKnownServerTimeOffsetUpdateTime() {
    return getLong(LAST_SERVER_TIME_OFFSET_UPDATE, 0);
  }

  /**
   * Whether or not we should attempt to restore the user's username and link.
   */
  public boolean needsUsernameRestore() {
    return getBoolean(NEEDS_USERNAME_RESTORE, false);
  }

  public void setNeedsUsernameRestore(boolean value) {
    putBoolean(NEEDS_USERNAME_RESTORE, value);
  }

  /**
   * Set the last time we successfully completed a forced prekey refresh.
   */
  public void setLastForcedPreKeyRefresh(long time) {
    putLong(LAST_FORCED_PREKEY_REFRESH, time);
  }

  /**
   * Get the last time we successfully completed a forced prekey refresh.
   */
  public long getLastForcedPreKeyRefresh() {
    return getLong(LAST_FORCED_PREKEY_REFRESH, 0);
  }

  /**
   * How long it's been since the last foreground CDS sync, which we do in response to new threads being created.
   */
  public long getLastCdsForegroundSyncTime() {
    return getLong(LAST_CDS_FOREGROUND_SYNC, 0);
  }

  /**
   * Set the last time we did a foreground CDS sync.
   */
  public void setLastCdsForegroundSyncTime(long time) {
    putLong(LAST_CDS_FOREGROUND_SYNC, time);
  }
}
