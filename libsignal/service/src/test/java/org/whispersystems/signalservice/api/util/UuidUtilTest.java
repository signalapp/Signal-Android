package org.whispersystems.signalservice.api.util;

import com.google.protobuf.ByteString;

import org.junit.Test;
import org.signal.zkgroup.util.UUIDUtil;
import org.whispersystems.libsignal.util.Hex;

import java.io.IOException;
import java.util.UUID;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public final class UuidUtilTest {

  @Test
  public void toByteArray() throws IOException {
    UUID uuid = UUID.fromString("67dfd496-ea02-4720-b13d-83a462168b1d");

    byte[] serialized = UuidUtil.toByteArray(uuid);

    assertArrayEquals(Hex.fromStringCondensed("67dfd496ea024720b13d83a462168b1d"), serialized);
  }

  @Test
  public void toByteArray_alternativeValues() throws IOException {
    UUID uuid = UUID.fromString("b70df6ac-3b21-4b39-a514-613561f51e2a");

    byte[] serialized = UuidUtil.toByteArray(uuid);

    assertArrayEquals(Hex.fromStringCondensed("b70df6ac3b214b39a514613561f51e2a"), serialized);
  }

  @Test
  public void parseOrThrow_from_byteArray() throws IOException {
    byte[] bytes = Hex.fromStringCondensed("3dc48790568b49c19bd6ab6604a5bc32");

    UUID uuid = UuidUtil.parseOrThrow(bytes);

    assertEquals("3dc48790-568b-49c1-9bd6-ab6604a5bc32", uuid.toString());
  }

  @Test
  public void parseOrThrow_from_byteArray_alternativeValues() throws IOException {
    byte[] bytes = Hex.fromStringCondensed("b83dfb0b67f141aa992e030c167cd011");

    UUID uuid = UuidUtil.parseOrThrow(bytes);

    assertEquals("b83dfb0b-67f1-41aa-992e-030c167cd011", uuid.toString());
  }

  @Test
  public void byte_array_compatibility_with_zk_group_uuid_util() {
    UUID uuid = UUID.fromString("67dfd496-ea02-4720-b13d-83a462168b1d");

    UUID result = UUIDUtil.deserialize(UuidUtil.toByteArray(uuid));

    assertEquals(uuid, result);
  }

  @Test
  public void byte_string_compatibility_with_zk_group_uuid_util() {
    UUID uuid = UUID.fromString("67dfd496-ea02-4720-b13d-83a462168b1d");

    UUID result = UuidUtil.fromByteString(ByteString.copyFrom(UUIDUtil.serialize(uuid)));

    assertEquals(uuid, result);
  }

  @Test
  public void byte_string_round_trip() {
    UUID uuid = UUID.fromString("67dfd496-ea02-4720-b13d-83a462168b1d");

    UUID result = UuidUtil.fromByteString(ByteString.copyFrom(UuidUtil.toByteArray(uuid)));

    assertEquals(uuid, result);
  }
}
