package org.thoughtcrime.securesms.groups;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.signal.zkgroup.groups.GroupMasterKey;
import org.thoughtcrime.securesms.util.Hex;

import java.util.Arrays;
import java.util.Collection;

import static org.junit.Assert.assertEquals;
import static org.whispersystems.signalservice.test.LibSignalLibraryUtil.assumeLibSignalSupportedOnOS;

@RunWith(Parameterized.class)
public final class GroupId_v1_v2_migration_derivation_Test {

  @Before
  public void ensureNativeSupported() {
    assumeLibSignalSupportedOnOS();
  }

  @Parameterized.Parameter(0)
  public String inputV1GroupId;

  @Parameterized.Parameter(1)
  public String expectedMasterKey;

  @Parameterized.Parameter(2)
  public String expectedGroupId;

  @Parameterized.Parameters(name = "{0}")
  public static Collection<Object[]> data() {
    return Arrays.asList(new Object[][]{
      {"00000000000000000000000000000000", "dbde68f4ee9169081f8814eabc65523fea1359235c8cfca32b69e31dce58b039", "__signal_group__v2__!10dc4a74082c2dfe849f0f3e7be03d21150dd5333b2ab9233d40d9d7d8ff74f3"},
      {"000102030405060708090a0b0c0d0e0f", "70884f78f07a94480ee36b67a4b5e975e92e4a774561e3df84c9076e3be4b9bf", "__signal_group__v2__!5c71c128f68b5facd4749f8796d4585c5c3d7fd1fe978122eaa3566e8759ebba"},
      {"7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f", "e69bf7c183b288b4ea5745b7c52b651a61e57769fafde683a6fdf1240f1905f2", "__signal_group__v2__!f21c4aaa4b29248cb5f80958307de2d805296551f1272cf7286de6c874e00a46"},
      {"ffffffffffffffffffffffffffffffff", "dd3a7de23d10f18b64457fbeedc76226c112a730e4b76112e62c36c4432eb37d", "__signal_group__v2__!9d552517683101828fb409c197f7ee1f81feb786f00c066ca0821dece5b29174"}
    });
  }

  @Test
  public void deriveMigrationV2MasterKey() {
    GroupId.V1     groupV1Id              = GroupId.v1orThrow(Hex.fromStringOrThrow(inputV1GroupId));
    GroupMasterKey migratedGroupMasterKey = groupV1Id.deriveV2MigrationMasterKey();

    assertEquals(expectedMasterKey, Hex.toStringCondensed(migratedGroupMasterKey.serialize()));
  }

  @Test
  public void deriveMigrationV2GroupId() {
    GroupId.V1 groupV1Id         = GroupId.v1orThrow(Hex.fromStringOrThrow(inputV1GroupId));
    GroupId.V2 migratedV2GroupId = groupV1Id.deriveV2MigrationGroupId();

    assertEquals(expectedGroupId, migratedV2GroupId.toString());
  }
}
