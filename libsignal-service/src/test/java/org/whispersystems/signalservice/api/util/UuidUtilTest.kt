package org.whispersystems.signalservice.api.util

import okio.ByteString
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.signal.libsignal.protocol.util.Hex
import java.io.IOException
import java.util.UUID

class UuidUtilTest {

  @Test
  @Throws(IOException::class)
  fun toByteArray() {
    val uuid = UUID.fromString("67dfd496-ea02-4720-b13d-83a462168b1d")
    val serialized = UuidUtil.toByteArray(uuid)
    assertArrayEquals(Hex.fromStringCondensed("67dfd496ea024720b13d83a462168b1d"), serialized)
  }

  @Test
  @Throws(IOException::class)
  fun toByteArray_alternativeValues() {
    val uuid = UUID.fromString("b70df6ac-3b21-4b39-a514-613561f51e2a")
    val serialized = UuidUtil.toByteArray(uuid)
    assertArrayEquals(Hex.fromStringCondensed("b70df6ac3b214b39a514613561f51e2a"), serialized)
  }

  @Test
  @Throws(IOException::class)
  fun parseOrThrow_from_byteArray() {
    val bytes = Hex.fromStringCondensed("3dc48790568b49c19bd6ab6604a5bc32")
    val uuid = UuidUtil.parseOrThrow(bytes)
    assertEquals("3dc48790-568b-49c1-9bd6-ab6604a5bc32", uuid.toString())
  }

  @Test
  @Throws(IOException::class)
  fun parseOrThrow_from_byteArray_alternativeValues() {
    val bytes = Hex.fromStringCondensed("b83dfb0b67f141aa992e030c167cd011")
    val uuid = UuidUtil.parseOrThrow(bytes)
    assertEquals("b83dfb0b-67f1-41aa-992e-030c167cd011", uuid.toString())
  }

  @Test
  fun byte_string_round_trip() {
    val uuid = UUID.fromString("67dfd496-ea02-4720-b13d-83a462168b1d")
    val result = UuidUtil.fromByteString(ByteString.of(*UuidUtil.toByteArray(uuid)))
    assertEquals(uuid, result)
  }
}
