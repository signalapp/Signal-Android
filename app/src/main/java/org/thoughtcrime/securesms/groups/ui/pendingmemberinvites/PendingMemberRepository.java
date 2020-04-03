package org.thoughtcrime.securesms.groups.ui.pendingmemberinvites;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.core.util.Consumer;

import com.annimon.stream.Stream;
import com.google.protobuf.ByteString;

import org.signal.storageservice.protos.groups.local.DecryptedGroup;
import org.signal.storageservice.protos.groups.local.DecryptedPendingMember;
import org.signal.zkgroup.util.UUIDUtil;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.GroupDatabase;
import org.thoughtcrime.securesms.groups.GroupId;
import org.thoughtcrime.securesms.groups.GroupProtoUtil;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.concurrent.SignalExecutors;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

final class PendingMemberRepository {

  private final Context context;
  private final Executor executor;

  PendingMemberRepository(@NonNull Context context) {
    this.context  = context.getApplicationContext();
    this.executor = SignalExecutors.BOUNDED;
  }

  public void getInvitees(GroupId.V2 groupId, @NonNull Consumer<InviteeResult> onInviteesLoaded) {
    executor.execute(() -> {
      GroupDatabase                                groupDatabase      = DatabaseFactory.getGroupDatabase(context);
      GroupDatabase.V2GroupProperties              v2GroupProperties  = groupDatabase.getGroup(groupId).get().requireV2GroupProperties();
      DecryptedGroup                               decryptedGroup     = v2GroupProperties.getDecryptedGroup();
      List<DecryptedPendingMember>                 pendingMembersList = decryptedGroup.getPendingMembersList();
      List<SinglePendingMemberInvitedByYou>        byMe               = new ArrayList<>(pendingMembersList.size());
      List<MultiplePendingMembersInvitedByAnother> byOthers           = new ArrayList<>(pendingMembersList.size());
      ByteString                                   self               = ByteString.copyFrom(UUIDUtil.serialize(Recipient.self().getUuid().get()));

      Stream.of(pendingMembersList)
            .groupBy(DecryptedPendingMember::getAddedByUuid)
            .forEach(g ->
              {
                ByteString                   inviterUuid    = g.getKey();
                List<DecryptedPendingMember> invitedMembers = g.getValue();

                if (self.equals(inviterUuid)) {
                  for (DecryptedPendingMember pendingMember : invitedMembers) {
                    Recipient invitee        = GroupProtoUtil.pendingMemberToRecipient(context, pendingMember);
                    byte[]    uuidCipherText = pendingMember.getUuidCipherText().toByteArray();

                    byMe.add(new SinglePendingMemberInvitedByYou(invitee, uuidCipherText));
                  }
                } else {
                  Recipient inviter = GroupProtoUtil.uuidByteStringToRecipient(context, inviterUuid);

                  ArrayList<byte[]> uuidCipherTexts = new ArrayList<>(invitedMembers.size());
                  for (DecryptedPendingMember pendingMember : invitedMembers) {
                    uuidCipherTexts.add(pendingMember.getUuidCipherText().toByteArray());
                  }

                  byOthers.add(new MultiplePendingMembersInvitedByAnother(inviter, uuidCipherTexts));
                }
              }
            );

      onInviteesLoaded.accept(new InviteeResult(byMe, byOthers));
    });
  }

  public static final class InviteeResult {
    private final List<SinglePendingMemberInvitedByYou>        byMe;
    private final List<MultiplePendingMembersInvitedByAnother> byOthers;

    private InviteeResult(List<SinglePendingMemberInvitedByYou> byMe,
                          List<MultiplePendingMembersInvitedByAnother> byOthers)
    {
      this.byMe     = byMe;
      this.byOthers = byOthers;
    }

    public List<SinglePendingMemberInvitedByYou> getByMe() {
      return byMe;
    }

    public List<MultiplePendingMembersInvitedByAnother> getByOthers() {
      return byOthers;
    }
  }

  public final static class SinglePendingMemberInvitedByYou {
    private final Recipient invitee;
    private final byte[]    inviteeCipherText;

    private SinglePendingMemberInvitedByYou(@NonNull Recipient invitee, @NonNull byte[] inviteeCipherText) {
      this.invitee           = invitee;
      this.inviteeCipherText = inviteeCipherText;
    }

    public Recipient getInvitee() {
      return invitee;
    }

    public byte[] getInviteeCipherText() {
      return inviteeCipherText;
    }
  }

  public final static class MultiplePendingMembersInvitedByAnother {
    private final Recipient         inviter;
    private final ArrayList<byte[]> uuidCipherTexts;

    private MultiplePendingMembersInvitedByAnother(@NonNull Recipient inviter, @NonNull ArrayList<byte[]> uuidCipherTexts) {
      this.inviter         = inviter;
      this.uuidCipherTexts = uuidCipherTexts;
    }

    public Recipient getInviter() {
      return inviter;
    }

    public ArrayList<byte[]> getUuidCipherTexts() {
      return uuidCipherTexts;
    }
  }
}