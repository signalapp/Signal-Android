package org.whispersystems.signalservice.api.groupsv2;

import org.junit.Before;
import org.junit.Test;
import org.signal.libsignal.zkgroup.InvalidInputException;
import org.signal.libsignal.zkgroup.groups.GroupMasterKey;
import org.signal.libsignal.zkgroup.groups.GroupSecretParams;
import org.signal.storageservice.protos.groups.GroupChange;
import org.signal.storageservice.protos.groups.local.DecryptedBannedMember;
import org.whispersystems.signalservice.api.push.ServiceId;
import org.whispersystems.signalservice.api.push.ServiceId.ACI;
import org.whispersystems.signalservice.internal.util.Util;
import org.whispersystems.signalservice.testutil.LibSignalLibraryUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import okio.ByteString;

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
    ACI ban = ACI.from(UUID.randomUUID());

    GroupChange.Actions.Builder banUuidsChange = groupOperations.createBanServiceIdsChange(Collections.singleton(ban),
                                                                                           false,
                                                                                           Collections.emptyList());

    assertThat(banUuidsChange.addBannedMembers.size(), is(1));
    assertThat(banUuidsChange.addBannedMembers.get(0).added.userId, is(groupOperations.encryptServiceId(ban)));
  }

  @Test
  public void addBanToPartialFullList() {
    ACI                         toBan         = ACI.from(UUID.randomUUID());
    List<DecryptedBannedMember> alreadyBanned = new ArrayList<>(5);

    for (int i = 0; i < 5; i++) {
      alreadyBanned.add(bannedMember(UUID.randomUUID()));
    }

    GroupChange.Actions.Builder banUuidsChange = groupOperations.createBanServiceIdsChange(Collections.singleton(toBan),
                                                                                           false,
                                                                                           alreadyBanned);

    assertThat(banUuidsChange.addBannedMembers.size(), is(1));
    assertThat(banUuidsChange.addBannedMembers.get(0).added.userId, is(groupOperations.encryptServiceId(toBan)));
  }

  @Test
  public void addBanToFullList() {
    ACI                         toBan         = ACI.from(UUID.randomUUID());
    List<DecryptedBannedMember> alreadyBanned = new ArrayList<>(10);
    DecryptedBannedMember       oldest        = null;

    for (int i = 0; i < 10; i++) {
      DecryptedBannedMember member = bannedMember(UUID.randomUUID()).newBuilder().timestamp(100 + i).build();
      if (oldest == null) {
        oldest = member;
      }
      alreadyBanned.add(member);
    }

    Collections.shuffle(alreadyBanned);

    GroupChange.Actions.Builder banUuidsChange = groupOperations.createBanServiceIdsChange(Collections.singleton(toBan),
                                                                                           false,
                                                                                           alreadyBanned);

    assertThat(banUuidsChange.deleteBannedMembers.size(), is(1));
    assertThat(banUuidsChange.deleteBannedMembers.get(0).deletedUserId, is(groupOperations.encryptServiceId(ServiceId.parseOrThrow(oldest.serviceIdBytes))));


    assertThat(banUuidsChange.addBannedMembers.size(), is(1));
    assertThat(banUuidsChange.addBannedMembers.get(0).added.userId, is(groupOperations.encryptServiceId(toBan)));
  }

  @Test
  public void addMultipleBanToFullList() {
    List<ACI> toBan = new ArrayList<>();
    toBan.add(ACI.from(UUID.randomUUID()));
    toBan.add(ACI.from(UUID.randomUUID()));

    List<DecryptedBannedMember> alreadyBanned = new ArrayList<>(10);
    for (int i = 0; i < 10; i++) {
      alreadyBanned.add(bannedMember(UUID.randomUUID()).newBuilder().timestamp(100 + i).build());
    }

    List<ByteString> oldest = new ArrayList<>(2);
    oldest.add(groupOperations.encryptServiceId(ServiceId.parseOrThrow(alreadyBanned.get(0).serviceIdBytes)));
    oldest.add(groupOperations.encryptServiceId(ServiceId.parseOrThrow(alreadyBanned.get(1).serviceIdBytes)));

    Collections.shuffle(alreadyBanned);

    GroupChange.Actions.Builder banUuidsChange = groupOperations.createBanServiceIdsChange(new HashSet<>(toBan),
                                                                                           false,
                                                                                           alreadyBanned);

    assertThat(banUuidsChange.deleteBannedMembers.size(), is(2));
    assertThat(banUuidsChange.deleteBannedMembers
                             .stream()
                             .map(a -> a.deletedUserId)
                             .collect(Collectors.toList()),
               hasItems(oldest.get(0), oldest.get(1)));


    assertThat(banUuidsChange.addBannedMembers.size(), is(2));
    assertThat(banUuidsChange.addBannedMembers
                             .stream()
                             .map(a -> a.added)
                             .map(b -> b.userId)
                             .collect(Collectors.toList()),
               hasItems(groupOperations.encryptServiceId(toBan.get(0)),
                        groupOperations.encryptServiceId(toBan.get(1))));
  }
}
