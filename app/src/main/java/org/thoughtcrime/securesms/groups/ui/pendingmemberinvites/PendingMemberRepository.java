package org.thoughtcrime.securesms.groups.ui.pendingmemberinvites;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.core.util.Consumer;

import com.annimon.stream.Stream;
import com.google.protobuf.ByteString;

import org.signal.storageservice.protos.groups.local.DecryptedGroup;
import org.signal.storageservice.protos.groups.local.DecryptedPendingMember;
import org.signal.zkgroup.InvalidInputException;
import org.signal.zkgroup.VerificationFailedException;
import org.signal.zkgroup.groups.UuidCiphertext;
import org.signal.zkgroup.util.UUIDUtil;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.GroupDatabase;
import org.thoughtcrime.securesms.groups.GroupId;
import org.thoughtcrime.securesms.groups.GroupManager;
import org.thoughtcrime.securesms.groups.GroupProtoUtil;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.concurrent.SignalExecutors;
import org.whispersystems.signalservice.api.groupsv2.InvalidGroupStateException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * Repository for modifying the pending members on a single group.
 */
final class PendingMemberRepository {

  private static final String TAG = Log.tag(PendingMemberRepository.class);

  private final Context    context;
  private final GroupId.V2 groupId;
  private final Executor   executor;

  PendingMemberRepository(@NonNull Context context, @NonNull GroupId.V2 groupId) {
    this.context  = context.getApplicationContext();
    this.executor = SignalExecutors.BOUNDED;
    this.groupId  = groupId;
  }

  public void getInvitees(@NonNull Consumer<InviteeResult> onInviteesLoaded) {
    executor.execute(() -> {
      GroupDatabase                                groupDatabase      = DatabaseFactory.getGroupDatabase(context);
      GroupDatabase.V2GroupProperties              v2GroupProperties  = groupDatabase.getGroup(groupId).get().requireV2GroupProperties();
      DecryptedGroup                               decryptedGroup     = v2GroupProperties.getDecryptedGroup();
      List<DecryptedPendingMember>                 pendingMembersList = decryptedGroup.getPendingMembersList();
      List<SinglePendingMemberInvitedByYou>        byMe               = new ArrayList<>(pendingMembersList.size());
      List<MultiplePendingMembersInvitedByAnother> byOthers           = new ArrayList<>(pendingMembersList.size());
      ByteString                                   self               = ByteString.copyFrom(UUIDUtil.serialize(Recipient.self().getUuid().get()));
      boolean                                      selfIsAdmin        = v2GroupProperties.isAdmin(Recipient.self());

      Stream.of(pendingMembersList)
            .groupBy(DecryptedPendingMember::getAddedByUuid)
            .forEach(g ->
              {
                ByteString                   inviterUuid    = g.getKey();
                List<DecryptedPendingMember> invitedMembers = g.getValue();

                if (self.equals(inviterUuid)) {
                  for (DecryptedPendingMember pendingMember : invitedMembers) {
                    try {
                      Recipient      invitee        = GroupProtoUtil.pendingMemberToRecipient(context, pendingMember);
                      UuidCiphertext uuidCipherText = new UuidCiphertext(pendingMember.getUuidCipherText().toByteArray());

                      byMe.add(new SinglePendingMemberInvitedByYou(invitee, uuidCipherText));
                    } catch (InvalidInputException e) {
                      Log.w(TAG, e);
                    }
                  }
                } else {
                  Recipient                 inviter         = GroupProtoUtil.uuidByteStringToRecipient(context, inviterUuid);
                  ArrayList<UuidCiphertext> uuidCipherTexts = new ArrayList<>(invitedMembers.size());

                  for (DecryptedPendingMember pendingMember : invitedMembers) {
                    try {
                      uuidCipherTexts.add(new UuidCiphertext(pendingMember.getUuidCipherText().toByteArray()));
                    } catch (InvalidInputException e) {
                      Log.w(TAG, e);
                    }
                  }

                  byOthers.add(new MultiplePendingMembersInvitedByAnother(inviter, uuidCipherTexts));
                }
              }
            );

      onInviteesLoaded.accept(new InviteeResult(byMe, byOthers, selfIsAdmin));
    });
  }

  @WorkerThread
  boolean cancelInvites(@NonNull Collection<UuidCiphertext> uuidCipherTexts) {
    try {
      GroupManager.cancelInvites(context, groupId, uuidCipherTexts);
      return true;
    } catch (InvalidGroupStateException | VerificationFailedException | IOException e) {
      Log.w(TAG, e);
      return false;
    }
  }

  public static final class InviteeResult {
    private final List<SinglePendingMemberInvitedByYou>        byMe;
    private final List<MultiplePendingMembersInvitedByAnother> byOthers;
    private final boolean                                      canCancelOthersInvites;

    private InviteeResult(List<SinglePendingMemberInvitedByYou> byMe,
                          List<MultiplePendingMembersInvitedByAnother> byOthers,
                          boolean canCancelOthersInvites)
    {
      this.byMe                   = byMe;
      this.byOthers               = byOthers;
      this.canCancelOthersInvites = canCancelOthersInvites;
    }

    public List<SinglePendingMemberInvitedByYou> getByMe() {
      return byMe;
    }

    public List<MultiplePendingMembersInvitedByAnother> getByOthers() {
      return byOthers;
    }

    public boolean isCanCancelOthersInvites() {
      return canCancelOthersInvites;
    }
  }

  public final static class SinglePendingMemberInvitedByYou {
    private final Recipient      invitee;
    private final UuidCiphertext inviteeCipherText;

    private SinglePendingMemberInvitedByYou(@NonNull Recipient invitee, @NonNull UuidCiphertext inviteeCipherText) {
      this.invitee           = invitee;
      this.inviteeCipherText = inviteeCipherText;
    }

    public Recipient getInvitee() {
      return invitee;
    }

    public UuidCiphertext getInviteeCipherText() {
      return inviteeCipherText;
    }
  }

  public final static class MultiplePendingMembersInvitedByAnother {
    private final Recipient                  inviter;
    private final Collection<UuidCiphertext> uuidCipherTexts;

    private MultiplePendingMembersInvitedByAnother(@NonNull Recipient inviter, @NonNull Collection<UuidCiphertext> uuidCipherTexts) {
      this.inviter         = inviter;
      this.uuidCipherTexts = uuidCipherTexts;
    }

    public Recipient getInviter() {
      return inviter;
    }

    public Collection<UuidCiphertext> getUuidCipherTexts() {
      return uuidCipherTexts;
    }
  }
}