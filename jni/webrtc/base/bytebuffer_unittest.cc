/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/base/arraysize.h"
#include "webrtc/base/bytebuffer.h"
#include "webrtc/base/byteorder.h"
#include "webrtc/base/common.h"
#include "webrtc/base/gunit.h"

namespace rtc {

TEST(ByteBufferTest, TestByteOrder) {
  uint16_t n16 = 1;
  uint32_t n32 = 1;
  uint64_t n64 = 1;

  EXPECT_EQ(n16, NetworkToHost16(HostToNetwork16(n16)));
  EXPECT_EQ(n32, NetworkToHost32(HostToNetwork32(n32)));
  EXPECT_EQ(n64, NetworkToHost64(HostToNetwork64(n64)));

  if (IsHostBigEndian()) {
    // The host is the network (big) endian.
    EXPECT_EQ(n16, HostToNetwork16(n16));
    EXPECT_EQ(n32, HostToNetwork32(n32));
    EXPECT_EQ(n64, HostToNetwork64(n64));

    // GetBE converts big endian to little endian here.
    EXPECT_EQ(n16 >> 8, GetBE16(&n16));
    EXPECT_EQ(n32 >> 24, GetBE32(&n32));
    EXPECT_EQ(n64 >> 56, GetBE64(&n64));
  } else {
    // The host is little endian.
    EXPECT_NE(n16, HostToNetwork16(n16));
    EXPECT_NE(n32, HostToNetwork32(n32));
    EXPECT_NE(n64, HostToNetwork64(n64));

    // GetBE converts little endian to big endian here.
    EXPECT_EQ(GetBE16(&n16), HostToNetwork16(n16));
    EXPECT_EQ(GetBE32(&n32), HostToNetwork32(n32));
    EXPECT_EQ(GetBE64(&n64), HostToNetwork64(n64));

    // GetBE converts little endian to big endian here.
    EXPECT_EQ(n16 << 8, GetBE16(&n16));
    EXPECT_EQ(n32 << 24, GetBE32(&n32));
    EXPECT_EQ(n64 << 56, GetBE64(&n64));
  }
}

TEST(ByteBufferTest, TestBufferLength) {
  ByteBufferWriter buffer;
  size_t size = 0;
  EXPECT_EQ(size, buffer.Length());

  buffer.WriteUInt8(1);
  ++size;
  EXPECT_EQ(size, buffer.Length());

  buffer.WriteUInt16(1);
  size += 2;
  EXPECT_EQ(size, buffer.Length());

  buffer.WriteUInt24(1);
  size += 3;
  EXPECT_EQ(size, buffer.Length());

  buffer.WriteUInt32(1);
  size += 4;
  EXPECT_EQ(size, buffer.Length());

  buffer.WriteUInt64(1);
  size += 8;
  EXPECT_EQ(size, buffer.Length());
}

TEST(ByteBufferTest, TestReadWriteBuffer) {
  ByteBufferWriter::ByteOrder orders[2] = { ByteBufferWriter::ORDER_HOST,
                                            ByteBufferWriter::ORDER_NETWORK };
  for (size_t i = 0; i < arraysize(orders); i++) {
    ByteBufferWriter buffer(orders[i]);
    EXPECT_EQ(orders[i], buffer.Order());
    ByteBufferReader read_buf(nullptr, 0, orders[i]);
    EXPECT_EQ(orders[i], read_buf.Order());
    uint8_t ru8;
    EXPECT_FALSE(read_buf.ReadUInt8(&ru8));

    // Write and read uint8_t.
    uint8_t wu8 = 1;
    buffer.WriteUInt8(wu8);
    ByteBufferReader read_buf1(buffer.Data(), buffer.Length(), orders[i]);
    EXPECT_TRUE(read_buf1.ReadUInt8(&ru8));
    EXPECT_EQ(wu8, ru8);
    EXPECT_EQ(0U, read_buf1.Length());
    buffer.Clear();

    // Write and read uint16_t.
    uint16_t wu16 = (1 << 8) + 1;
    buffer.WriteUInt16(wu16);
    ByteBufferReader read_buf2(buffer.Data(), buffer.Length(), orders[i]);
    uint16_t ru16;
    EXPECT_TRUE(read_buf2.ReadUInt16(&ru16));
    EXPECT_EQ(wu16, ru16);
    EXPECT_EQ(0U, read_buf2.Length());
    buffer.Clear();

    // Write and read uint24.
    uint32_t wu24 = (3 << 16) + (2 << 8) + 1;
    buffer.WriteUInt24(wu24);
    ByteBufferReader read_buf3(buffer.Data(), buffer.Length(), orders[i]);
    uint32_t ru24;
    EXPECT_TRUE(read_buf3.ReadUInt24(&ru24));
    EXPECT_EQ(wu24, ru24);
    EXPECT_EQ(0U, read_buf3.Length());
    buffer.Clear();

    // Write and read uint32_t.
    uint32_t wu32 = (4 << 24) + (3 << 16) + (2 << 8) + 1;
    buffer.WriteUInt32(wu32);
    ByteBufferReader read_buf4(buffer.Data(), buffer.Length(), orders[i]);
    uint32_t ru32;
    EXPECT_TRUE(read_buf4.ReadUInt32(&ru32));
    EXPECT_EQ(wu32, ru32);
    EXPECT_EQ(0U, read_buf3.Length());
    buffer.Clear();

    // Write and read uint64_t.
    uint32_t another32 = (8 << 24) + (7 << 16) + (6 << 8) + 5;
    uint64_t wu64 = (static_cast<uint64_t>(another32) << 32) + wu32;
    buffer.WriteUInt64(wu64);
    ByteBufferReader read_buf5(buffer.Data(), buffer.Length(), orders[i]);
    uint64_t ru64;
    EXPECT_TRUE(read_buf5.ReadUInt64(&ru64));
    EXPECT_EQ(wu64, ru64);
    EXPECT_EQ(0U, read_buf5.Length());
    buffer.Clear();

    // Write and read string.
    std::string write_string("hello");
    buffer.WriteString(write_string);
    ByteBufferReader read_buf6(buffer.Data(), buffer.Length(), orders[i]);
    std::string read_string;
    EXPECT_TRUE(read_buf6.ReadString(&read_string, write_string.size()));
    EXPECT_EQ(write_string, read_string);
    EXPECT_EQ(0U, read_buf6.Length());
    buffer.Clear();

    // Write and read bytes
    char write_bytes[] = "foo";
    buffer.WriteBytes(write_bytes, 3);
    ByteBufferReader read_buf7(buffer.Data(), buffer.Length(), orders[i]);
    char read_bytes[3];
    EXPECT_TRUE(read_buf7.ReadBytes(read_bytes, 3));
    for (int i = 0; i < 3; ++i) {
      EXPECT_EQ(write_bytes[i], read_bytes[i]);
    }
    EXPECT_EQ(0U, read_buf7.Length());
    buffer.Clear();

    // Write and read reserved buffer space
    char* write_dst = buffer.ReserveWriteBuffer(3);
    memcpy(write_dst, write_bytes, 3);
    ByteBufferReader read_buf8(buffer.Data(), buffer.Length(), orders[i]);
    memset(read_bytes, 0, 3);
    EXPECT_TRUE(read_buf8.ReadBytes(read_bytes, 3));
    for (int i = 0; i < 3; ++i) {
      EXPECT_EQ(write_bytes[i], read_bytes[i]);
    }
    EXPECT_EQ(0U, read_buf8.Length());
    buffer.Clear();

    // Write and read in order.
    buffer.WriteUInt8(wu8);
    buffer.WriteUInt16(wu16);
    buffer.WriteUInt24(wu24);
    buffer.WriteUInt32(wu32);
    buffer.WriteUInt64(wu64);
    ByteBufferReader read_buf9(buffer.Data(), buffer.Length(), orders[i]);
    EXPECT_TRUE(read_buf9.ReadUInt8(&ru8));
    EXPECT_EQ(wu8, ru8);
    EXPECT_TRUE(read_buf9.ReadUInt16(&ru16));
    EXPECT_EQ(wu16, ru16);
    EXPECT_TRUE(read_buf9.ReadUInt24(&ru24));
    EXPECT_EQ(wu24, ru24);
    EXPECT_TRUE(read_buf9.ReadUInt32(&ru32));
    EXPECT_EQ(wu32, ru32);
    EXPECT_TRUE(read_buf9.ReadUInt64(&ru64));
    EXPECT_EQ(wu64, ru64);
    EXPECT_EQ(0U, read_buf9.Length());
    buffer.Clear();
  }
}

TEST(ByteBufferTest, TestReadWriteUVarint) {
  ByteBufferWriter::ByteOrder orders[2] = {ByteBufferWriter::ORDER_HOST,
                                           ByteBufferWriter::ORDER_NETWORK};
  for (ByteBufferWriter::ByteOrder& order : orders) {
    ByteBufferWriter write_buffer(order);
    size_t size = 0;
    EXPECT_EQ(size, write_buffer.Length());

    write_buffer.WriteUVarint(1u);
    ++size;
    EXPECT_EQ(size, write_buffer.Length());

    write_buffer.WriteUVarint(2u);
    ++size;
    EXPECT_EQ(size, write_buffer.Length());

    write_buffer.WriteUVarint(27u);
    ++size;
    EXPECT_EQ(size, write_buffer.Length());

    write_buffer.WriteUVarint(149u);
    size += 2;
    EXPECT_EQ(size, write_buffer.Length());

    write_buffer.WriteUVarint(68719476736u);
    size += 6;
    EXPECT_EQ(size, write_buffer.Length());

    ByteBufferReader read_buffer(write_buffer.Data(), write_buffer.Length(),
                                 order);
    EXPECT_EQ(size, read_buffer.Length());
    uint64_t val1, val2, val3, val4, val5;

    ASSERT_TRUE(read_buffer.ReadUVarint(&val1));
    EXPECT_EQ(1u, val1);
    --size;
    EXPECT_EQ(size, read_buffer.Length());

    ASSERT_TRUE(read_buffer.ReadUVarint(&val2));
    EXPECT_EQ(2u, val2);
    --size;
    EXPECT_EQ(size, read_buffer.Length());

    ASSERT_TRUE(read_buffer.ReadUVarint(&val3));
    EXPECT_EQ(27u, val3);
    --size;
    EXPECT_EQ(size, read_buffer.Length());

    ASSERT_TRUE(read_buffer.ReadUVarint(&val4));
    EXPECT_EQ(149u, val4);
    size -= 2;
    EXPECT_EQ(size, read_buffer.Length());

    ASSERT_TRUE(read_buffer.ReadUVarint(&val5));
    EXPECT_EQ(68719476736u, val5);
    size -= 6;
    EXPECT_EQ(size, read_buffer.Length());
  }
}

}  // namespace rtc
