package org.thoughtcrime.securesms.groups;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.annimon.stream.Stream;

import org.signal.core.util.logging.Log;
import org.signal.storageservice.protos.groups.local.DecryptedGroup;
import org.signal.zkgroup.groups.GroupMasterKey;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.GroupDatabase;
import org.thoughtcrime.securesms.database.RecipientDatabase;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.mms.MmsException;
import org.thoughtcrime.securesms.mms.OutgoingMediaMessage;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.recipients.RecipientUtil;
import org.thoughtcrime.securesms.transport.RetryLaterException;
import org.thoughtcrime.securesms.util.FeatureFlags;
import org.thoughtcrime.securesms.util.GroupUtil;

import java.io.Closeable;
import java.io.IOException;
import java.util.List;

import static org.thoughtcrime.securesms.groups.v2.processing.GroupsV2StateProcessor.LATEST;

public final class GroupsV1MigrationUtil {

  private static final String TAG = Log.tag(GroupsV1MigrationUtil.class);

  private GroupsV1MigrationUtil() {}

  public static void migrate(@NonNull Context context, @NonNull RecipientId recipientId, boolean forced)
      throws IOException, RetryLaterException, GroupChangeBusyException, InvalidMigrationStateException
  {
    Recipient     groupRecipient = Recipient.resolved(recipientId);
    Long          threadId       = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(recipientId);
    GroupDatabase groupDatabase  = DatabaseFactory.getGroupDatabase(context);

    if (threadId == null) {
      Log.w(TAG, "No thread found!");
      throw new InvalidMigrationStateException();
    }

    if (!groupRecipient.isPushV1Group()) {
      Log.w(TAG, "Not a V1 group!");
      throw new InvalidMigrationStateException();
    }

    if (groupRecipient.getParticipants().size() > FeatureFlags.groupLimits().getHardLimit()) {
      Log.w(TAG, "Too many members! Size: " + groupRecipient.getParticipants().size());
      throw new InvalidMigrationStateException();
    }

    GroupId.V1     gv1Id        = groupRecipient.requireGroupId().requireV1();
    GroupId.V2     gv2Id        = gv1Id.deriveV2MigrationGroupId();
    GroupMasterKey gv2MasterKey = gv1Id.deriveV2MigrationMasterKey();
    boolean        newlyCreated = false;

    if (groupDatabase.groupExists(gv2Id)) {
      Log.w(TAG, "We already have a V2 group for this V1 group! Must have been added before we were migration-capable.");
      throw new InvalidMigrationStateException();
    }

    if (!groupRecipient.isActiveGroup()) {
      Log.w(TAG, "Group is inactive! Can't migrate.");
      throw new InvalidMigrationStateException();
    }

    switch (GroupManager.v2GroupStatus(context, gv2MasterKey)) {
      case DOES_NOT_EXIST:
        Log.i(TAG, "Group does not exist on the service.");

        if (!groupRecipient.isProfileSharing()) {
          Log.w(TAG, "Profile sharing is disabled! Can't migrate.");
          throw new InvalidMigrationStateException();
        }

        if (!forced && SignalStore.internalValues().disableGv1AutoMigrateInitiation()) {
          Log.w(TAG, "Auto migration initiation has been disabled! Skipping.");
          throw new InvalidMigrationStateException();
        }

        if (forced && !FeatureFlags.groupsV1ManualMigration()) {
          Log.w(TAG, "Manual migration is not enabled! Skipping.");
          throw new InvalidMigrationStateException();
        }

        List<Recipient> registeredMembers = RecipientUtil.getEligibleForSending(groupRecipient.getParticipants());

        if (RecipientUtil.ensureUuidsAreAvailable(context, registeredMembers)) {
          Log.i(TAG, "Newly-discovered UUIDs. Getting fresh recipients.");
          registeredMembers = Stream.of(registeredMembers).map(Recipient::fresh).toList();
        }

        List<Recipient> possibleMembers = forced ? getMigratableManualMigrationMembers(registeredMembers)
                                                 : getMigratableAutoMigrationMembers(registeredMembers);

        if (!forced && possibleMembers.size() != registeredMembers.size()) {
          Log.w(TAG, "Not allowed to invite or leave registered users behind in an auto-migration! Skipping.");
          throw new InvalidMigrationStateException();
        }

        Log.i(TAG, "Attempting to create group.");

        try {
          GroupManager.migrateGroupToServer(context, gv1Id, possibleMembers);
          newlyCreated = true;
          Log.i(TAG, "Successfully created!");
        } catch (GroupChangeFailedException e) {
          Log.w(TAG, "Failed to migrate group. Retrying.", e);
          throw new RetryLaterException();
        } catch (MembershipNotSuitableForV2Exception e) {
          Log.w(TAG, "Failed to migrate job due to the membership not yet being suitable for GV2. Aborting.", e);
          return;
        } catch (GroupAlreadyExistsException e) {
          Log.w(TAG, "Someone else created the group while we were trying to do the same! It exists now. Continuing on.", e);
        }
        break;
      case NOT_A_MEMBER:
        Log.w(TAG, "The migrated group already exists, but we are not a member. Doing a local leave.");
        handleLeftBehind(context, gv1Id, groupRecipient, threadId);
        return;
      case FULL_OR_PENDING_MEMBER:
        Log.w(TAG, "The migrated group already exists, and we're in it. Continuing on.");
        break;
      default: throw new AssertionError();
    }

    Log.i(TAG, "Migrating local group " + gv1Id + " to " + gv2Id);

    DecryptedGroup decryptedGroup = performLocalMigration(context, gv1Id, threadId, groupRecipient);

    if (newlyCreated && decryptedGroup != null && !SignalStore.internalValues().disableGv1AutoMigrateNotification()) {
      Log.i(TAG, "Sending no-op update to notify others.");
      GroupManager.sendNoopUpdate(context, gv2MasterKey, decryptedGroup);
    }
  }

  public static void performLocalMigration(@NonNull Context context, @NonNull GroupId.V1 gv1Id) throws IOException
  {
    Log.i(TAG, "Beginning local migration! V1 ID: " + gv1Id, new Throwable());
    try (Closeable ignored = GroupsV2ProcessingLock.acquireGroupProcessingLock()) {
      if (DatabaseFactory.getGroupDatabase(context).groupExists(gv1Id.deriveV2MigrationGroupId())) {
        Log.w(TAG, "Group was already migrated! Could have been waiting for the lock.", new Throwable());
        return;
      }

      Recipient recipient = Recipient.externalGroupExact(context, gv1Id);
      long      threadId  = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(recipient);

      performLocalMigration(context, gv1Id, threadId, recipient);
      Log.i(TAG, "Migration complete! (" + gv1Id + ", " + threadId + ", " + recipient.getId() + ")", new Throwable());
    } catch (GroupChangeBusyException e) {
      throw new IOException(e);
    }
  }

  private static @Nullable DecryptedGroup performLocalMigration(@NonNull Context context,
                                                                @NonNull GroupId.V1 gv1Id,
                                                                long threadId,
                                                                @NonNull Recipient groupRecipient)
      throws IOException, GroupChangeBusyException
  {
    Log.i(TAG, "performLocalMigration(" + gv1Id + ", " + threadId + ", " + groupRecipient.getId());

    try (Closeable ignored = GroupsV2ProcessingLock.acquireGroupProcessingLock()){
      DecryptedGroup decryptedGroup;
      try {
        decryptedGroup = GroupManager.addedGroupVersion(context, gv1Id.deriveV2MigrationMasterKey());
      } catch (GroupDoesNotExistException e) {
        throw new IOException("[Local] The group should exist already!");
      } catch (GroupNotAMemberException e) {
        Log.w(TAG, "[Local] We are not in the group. Doing a local leave.");
        handleLeftBehind(context, gv1Id, groupRecipient, threadId);
        return null;
      }

      Log.i(TAG, "[Local] Migrating group over to the version we were added to: V" + decryptedGroup.getRevision());
      DatabaseFactory.getGroupDatabase(context).migrateToV2(threadId, gv1Id, decryptedGroup);

      Log.i(TAG, "[Local] Applying all changes since V" + decryptedGroup.getRevision());
      try {
        GroupManager.updateGroupFromServer(context, gv1Id.deriveV2MigrationMasterKey(), LATEST, System.currentTimeMillis(), null);
      } catch (GroupChangeBusyException | GroupNotAMemberException e) {
        Log.w(TAG, e);
      }

      return decryptedGroup;
    }
  }

  private static void handleLeftBehind(@NonNull Context context, @NonNull GroupId.V1 gv1Id, @NonNull Recipient groupRecipient, long threadId) {
    OutgoingMediaMessage leaveMessage = GroupUtil.createGroupV1LeaveMessage(gv1Id, groupRecipient);
    try {
      long id = DatabaseFactory.getMmsDatabase(context).insertMessageOutbox(leaveMessage, threadId, false, null);
      DatabaseFactory.getMmsDatabase(context).markAsSent(id, true);
    } catch (MmsException e) {
      Log.w(TAG, "Failed to insert group leave message!", e);
    }

    DatabaseFactory.getGroupDatabase(context).setActive(gv1Id, false);
    DatabaseFactory.getGroupDatabase(context).remove(gv1Id, Recipient.self().getId());
  }

  /**
   * In addition to meeting traditional requirements, you must also have a profile key for a member
   * to consider them migratable in an auto-migration.
   */
  private static @NonNull List<Recipient> getMigratableAutoMigrationMembers(@NonNull List<Recipient> registeredMembers) {
    return Stream.of(getMigratableManualMigrationMembers(registeredMembers))
                 .filter(r -> r.getProfileKey() != null)
                 .toList();
  }

  /**
   * You can only migrate users that have the required capabilities.
   */
  private static @NonNull List<Recipient> getMigratableManualMigrationMembers(@NonNull List<Recipient> registeredMembers) {
    return Stream.of(registeredMembers)
                 .filter(r -> r.getGroupsV2Capability() == Recipient.Capability.SUPPORTED &&
                     r.getGroupsV1MigrationCapability() == Recipient.Capability.SUPPORTED)
                 .toList();
  }

  /**
   * True if the user meets all the requirements to be auto-migrated, otherwise false.
   */
  public static boolean isAutoMigratable(@NonNull Recipient recipient) {
    return recipient.hasUuid()                                                          &&
           recipient.getGroupsV2Capability() == Recipient.Capability.SUPPORTED          &&
           recipient.getGroupsV1MigrationCapability() == Recipient.Capability.SUPPORTED &&
           recipient.getRegistered() == RecipientDatabase.RegisteredState.REGISTERED    &&
           recipient.getProfileKey() != null;
  }

  public static final class InvalidMigrationStateException extends Exception {
  }
}
