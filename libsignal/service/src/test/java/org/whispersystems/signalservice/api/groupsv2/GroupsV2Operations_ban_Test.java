package org.whispersystems.signalservice.api.groupsv2;

import com.google.protobuf.ByteString;

import org.junit.Before;
import org.junit.Test;
import org.signal.libsignal.zkgroup.InvalidInputException;
import org.signal.libsignal.zkgroup.groups.GroupMasterKey;
import org.signal.libsignal.zkgroup.groups.GroupSecretParams;
import org.signal.storageservice.protos.groups.BannedMember;
import org.signal.storageservice.protos.groups.GroupChange;
import org.signal.storageservice.protos.groups.GroupChange.Actions.AddBannedMemberAction;
import org.signal.storageservice.protos.groups.GroupChange.Actions.DeleteBannedMemberAction;
import org.signal.storageservice.protos.groups.local.DecryptedBannedMember;
import org.whispersystems.signalservice.api.push.ServiceId;
import org.whispersystems.signalservice.api.push.ServiceId.ACI;
import org.whispersystems.signalservice.api.util.UuidUtil;
import org.whispersystems.signalservice.internal.util.Util;
import org.whispersystems.signalservice.testutil.LibSignalLibraryUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.whispersystems.signalservice.api.groupsv2.ProtoTestUtils.bannedMember;

public final class GroupsV2Operations_ban_Test {

  private GroupsV2Operations.GroupOperations groupOperations;

  @Before
  public void setup() throws InvalidInputException {
    LibSignalLibraryUtil.assumeLibSignalSupportedOnOS();

    TestZkGroupServer  server             = new TestZkGroupServer();
    GroupSecretParams  groupSecretParams  = GroupSecretParams.deriveFromMasterKey(new GroupMasterKey(Util.getSecretBytes(32)));
    ClientZkOperations clientZkOperations = new ClientZkOperations(server.getServerPublicParams());

    groupOperations = new GroupsV2Operations(clientZkOperations, 10).forGroup(groupSecretParams);
  }

  @Test
  public void addBanToEmptyList() {
    UUID ban = UUID.randomUUID();

    GroupChange.Actions.Builder banUuidsChange = groupOperations.createBanUuidsChange(Collections.singleton(ban),
                                                                                      false,
                                                                                      Collections.emptyList());

    assertThat(banUuidsChange.getAddBannedMembersCount(), is(1));
    assertThat(banUuidsChange.getAddBannedMembers(0).getAdded().getUserId(), is(groupOperations.encryptServiceId(ACI.from(ban))));
  }

  @Test
  public void addBanToPartialFullList() {
    UUID                        toBan         = UUID.randomUUID();
    List<DecryptedBannedMember> alreadyBanned = new ArrayList<>(5);

    for (int i = 0; i < 5; i++) {
      alreadyBanned.add(bannedMember(UUID.randomUUID()));
    }

    GroupChange.Actions.Builder banUuidsChange = groupOperations.createBanUuidsChange(Collections.singleton(toBan),
                                                                                      false,
                                                                                      alreadyBanned);

    assertThat(banUuidsChange.getAddBannedMembersCount(), is(1));
    assertThat(banUuidsChange.getAddBannedMembers(0).getAdded().getUserId(), is(groupOperations.encryptServiceId(ACI.from(toBan))));
  }

  @Test
  public void addBanToFullList() {
    UUID                        toBan         = UUID.randomUUID();
    List<DecryptedBannedMember> alreadyBanned = new ArrayList<>(10);
    DecryptedBannedMember       oldest        = null;

    for (int i = 0; i < 10; i++) {
      DecryptedBannedMember member = bannedMember(UUID.randomUUID()).toBuilder().setTimestamp(100 + i).build();
      if (oldest == null) {
        oldest = member;
      }
      alreadyBanned.add(member);
    }

    Collections.shuffle(alreadyBanned);

    GroupChange.Actions.Builder banUuidsChange = groupOperations.createBanUuidsChange(Collections.singleton(toBan),
                                                                                      false,
                                                                                      alreadyBanned);

    assertThat(banUuidsChange.getDeleteBannedMembersCount(), is(1));
    assertThat(banUuidsChange.getDeleteBannedMembers(0).getDeletedUserId(), is(groupOperations.encryptServiceId(ServiceId.parseOrThrow(oldest.getServiceIdBinary()))));


    assertThat(banUuidsChange.getAddBannedMembersCount(), is(1));
    assertThat(banUuidsChange.getAddBannedMembers(0).getAdded().getUserId(), is(groupOperations.encryptServiceId(ACI.from(toBan))));
  }

  @Test
  public void addMultipleBanToFullList() {
    List<UUID> toBan = new ArrayList<>();
    toBan.add(UUID.randomUUID());
    toBan.add(UUID.randomUUID());

    List<DecryptedBannedMember> alreadyBanned = new ArrayList<>(10);
    for (int i = 0; i < 10; i++) {
      alreadyBanned.add(bannedMember(UUID.randomUUID()).toBuilder().setTimestamp(100 + i).build());
    }

    List<ByteString> oldest = new ArrayList<>(2);
    oldest.add(groupOperations.encryptServiceId(ServiceId.parseOrThrow(alreadyBanned.get(0).getServiceIdBinary())));
    oldest.add(groupOperations.encryptServiceId(ServiceId.parseOrThrow(alreadyBanned.get(1).getServiceIdBinary())));

    Collections.shuffle(alreadyBanned);

    GroupChange.Actions.Builder banUuidsChange = groupOperations.createBanUuidsChange(new HashSet<>(toBan),
                                                                                      false,
                                                                                      alreadyBanned);

    assertThat(banUuidsChange.getDeleteBannedMembersCount(), is(2));
    assertThat(banUuidsChange.getDeleteBannedMembersList()
                             .stream()
                             .map(DeleteBannedMemberAction::getDeletedUserId)
                             .collect(Collectors.toList()),
               hasItems(oldest.get(0), oldest.get(1)));


    assertThat(banUuidsChange.getAddBannedMembersCount(), is(2));
    assertThat(banUuidsChange.getAddBannedMembersList()
                             .stream()
                             .map(AddBannedMemberAction::getAdded)
                             .map(BannedMember::getUserId)
                             .collect(Collectors.toList()),
               hasItems(groupOperations.encryptServiceId(ACI.from(toBan.get(0))),
                        groupOperations.encryptServiceId(ACI.from(toBan.get(1)))));
  }
}
