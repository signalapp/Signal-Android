/*
 *  Copyright 2015 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/base/arraysize.h"
#include "webrtc/base/bitbuffer.h"
#include "webrtc/base/bytebuffer.h"
#include "webrtc/base/common.h"
#include "webrtc/base/gunit.h"

namespace rtc {

TEST(BitBufferTest, ConsumeBits) {
  const uint8_t bytes[64] = {0};
  BitBuffer buffer(bytes, 32);
  uint64_t total_bits = 32 * 8;
  EXPECT_EQ(total_bits, buffer.RemainingBitCount());
  EXPECT_TRUE(buffer.ConsumeBits(3));
  total_bits -= 3;
  EXPECT_EQ(total_bits, buffer.RemainingBitCount());
  EXPECT_TRUE(buffer.ConsumeBits(3));
  total_bits -= 3;
  EXPECT_EQ(total_bits, buffer.RemainingBitCount());
  EXPECT_TRUE(buffer.ConsumeBits(15));
  total_bits -= 15;
  EXPECT_EQ(total_bits, buffer.RemainingBitCount());
  EXPECT_TRUE(buffer.ConsumeBits(37));
  total_bits -= 37;
  EXPECT_EQ(total_bits, buffer.RemainingBitCount());

  EXPECT_FALSE(buffer.ConsumeBits(32 * 8));
  EXPECT_EQ(total_bits, buffer.RemainingBitCount());
}

TEST(BitBufferTest, ReadBytesAligned) {
  const uint8_t bytes[] = {0x0A, 0xBC, 0xDE, 0xF1, 0x23, 0x45, 0x67, 0x89};
  uint8_t val8;
  uint16_t val16;
  uint32_t val32;
  BitBuffer buffer(bytes, 8);
  EXPECT_TRUE(buffer.ReadUInt8(&val8));
  EXPECT_EQ(0x0Au, val8);
  EXPECT_TRUE(buffer.ReadUInt8(&val8));
  EXPECT_EQ(0xBCu, val8);
  EXPECT_TRUE(buffer.ReadUInt16(&val16));
  EXPECT_EQ(0xDEF1u, val16);
  EXPECT_TRUE(buffer.ReadUInt32(&val32));
  EXPECT_EQ(0x23456789u, val32);
}

TEST(BitBufferTest, ReadBytesOffset4) {
  const uint8_t bytes[] = {0x0A, 0xBC, 0xDE, 0xF1, 0x23,
                           0x45, 0x67, 0x89, 0x0A};
  uint8_t val8;
  uint16_t val16;
  uint32_t val32;
  BitBuffer buffer(bytes, 9);
  EXPECT_TRUE(buffer.ConsumeBits(4));

  EXPECT_TRUE(buffer.ReadUInt8(&val8));
  EXPECT_EQ(0xABu, val8);
  EXPECT_TRUE(buffer.ReadUInt8(&val8));
  EXPECT_EQ(0xCDu, val8);
  EXPECT_TRUE(buffer.ReadUInt16(&val16));
  EXPECT_EQ(0xEF12u, val16);
  EXPECT_TRUE(buffer.ReadUInt32(&val32));
  EXPECT_EQ(0x34567890u, val32);
}

TEST(BitBufferTest, ReadBytesOffset3) {
  // The pattern we'll check against is counting down from 0b1111. It looks
  // weird here because it's all offset by 3.
  // Byte pattern is:
  //    56701234
  //  0b00011111,
  //  0b11011011,
  //  0b10010111,
  //  0b01010011,
  //  0b00001110,
  //  0b11001010,
  //  0b10000110,
  //  0b01000010
  //       xxxxx <-- last 5 bits unused.

  // The bytes. It almost looks like counting down by two at a time, except the
  // jump at 5->3->0, since that's when the high bit is turned off.
  const uint8_t bytes[] = {0x1F, 0xDB, 0x97, 0x53, 0x0E, 0xCA, 0x86, 0x42};

  uint8_t val8;
  uint16_t val16;
  uint32_t val32;
  BitBuffer buffer(bytes, 8);
  EXPECT_TRUE(buffer.ConsumeBits(3));
  EXPECT_TRUE(buffer.ReadUInt8(&val8));
  EXPECT_EQ(0xFEu, val8);
  EXPECT_TRUE(buffer.ReadUInt16(&val16));
  EXPECT_EQ(0xDCBAu, val16);
  EXPECT_TRUE(buffer.ReadUInt32(&val32));
  EXPECT_EQ(0x98765432u, val32);
  // 5 bits left unread. Not enough to read a uint8_t.
  EXPECT_EQ(5u, buffer.RemainingBitCount());
  EXPECT_FALSE(buffer.ReadUInt8(&val8));
}

TEST(BitBufferTest, ReadBits) {
  // Bit values are:
  //  0b01001101,
  //  0b00110010
  const uint8_t bytes[] = {0x4D, 0x32};
  uint32_t val;
  BitBuffer buffer(bytes, 2);
  EXPECT_TRUE(buffer.ReadBits(&val, 3));
  // 0b010
  EXPECT_EQ(0x2u, val);
  EXPECT_TRUE(buffer.ReadBits(&val, 2));
  // 0b01
  EXPECT_EQ(0x1u, val);
  EXPECT_TRUE(buffer.ReadBits(&val, 7));
  // 0b1010011
  EXPECT_EQ(0x53u, val);
  EXPECT_TRUE(buffer.ReadBits(&val, 2));
  // 0b00
  EXPECT_EQ(0x0u, val);
  EXPECT_TRUE(buffer.ReadBits(&val, 1));
  // 0b1
  EXPECT_EQ(0x1u, val);
  EXPECT_TRUE(buffer.ReadBits(&val, 1));
  // 0b0
  EXPECT_EQ(0x0u, val);

  EXPECT_FALSE(buffer.ReadBits(&val, 1));
}

TEST(BitBufferTest, SetOffsetValues) {
  uint8_t bytes[4] = {0};
  BitBufferWriter buffer(bytes, 4);

  size_t byte_offset, bit_offset;
  // Bit offsets are [0,7].
  EXPECT_TRUE(buffer.Seek(0, 0));
  EXPECT_TRUE(buffer.Seek(0, 7));
  buffer.GetCurrentOffset(&byte_offset, &bit_offset);
  EXPECT_EQ(0u, byte_offset);
  EXPECT_EQ(7u, bit_offset);
  EXPECT_FALSE(buffer.Seek(0, 8));
  buffer.GetCurrentOffset(&byte_offset, &bit_offset);
  EXPECT_EQ(0u, byte_offset);
  EXPECT_EQ(7u, bit_offset);
  // Byte offsets are [0,length]. At byte offset length, the bit offset must be
  // 0.
  EXPECT_TRUE(buffer.Seek(0, 0));
  EXPECT_TRUE(buffer.Seek(2, 4));
  buffer.GetCurrentOffset(&byte_offset, &bit_offset);
  EXPECT_EQ(2u, byte_offset);
  EXPECT_EQ(4u, bit_offset);
  EXPECT_TRUE(buffer.Seek(4, 0));
  EXPECT_FALSE(buffer.Seek(5, 0));
  buffer.GetCurrentOffset(&byte_offset, &bit_offset);
  EXPECT_EQ(4u, byte_offset);
  EXPECT_EQ(0u, bit_offset);
  EXPECT_FALSE(buffer.Seek(4, 1));

  // Disable death test on Android because it relies on fork() and doesn't play
  // nicely.
#if defined(GTEST_HAS_DEATH_TEST)
#if !defined(WEBRTC_ANDROID)
  // Passing a NULL out parameter is death.
  EXPECT_DEATH(buffer.GetCurrentOffset(&byte_offset, NULL), "");
#endif
#endif
}

uint64_t GolombEncoded(uint32_t val) {
  val++;
  uint32_t bit_counter = val;
  uint64_t bit_count = 0;
  while (bit_counter > 0) {
    bit_count++;
    bit_counter >>= 1;
  }
  return static_cast<uint64_t>(val) << (64 - (bit_count * 2 - 1));
}

TEST(BitBufferTest, GolombUint32Values) {
  ByteBufferWriter byteBuffer;
  byteBuffer.Resize(16);
  BitBuffer buffer(reinterpret_cast<const uint8_t*>(byteBuffer.Data()),
                   byteBuffer.Capacity());
  // Test over the uint32_t range with a large enough step that the test doesn't
  // take forever. Around 20,000 iterations should do.
  const int kStep = std::numeric_limits<uint32_t>::max() / 20000;
  for (uint32_t i = 0; i < std::numeric_limits<uint32_t>::max() - kStep;
       i += kStep) {
    uint64_t encoded_val = GolombEncoded(i);
    byteBuffer.Clear();
    byteBuffer.WriteUInt64(encoded_val);
    uint32_t decoded_val;
    EXPECT_TRUE(buffer.Seek(0, 0));
    EXPECT_TRUE(buffer.ReadExponentialGolomb(&decoded_val));
    EXPECT_EQ(i, decoded_val);
  }
}

TEST(BitBufferTest, SignedGolombValues) {
  uint8_t golomb_bits[] = {
      0x80,  // 1
      0x40,  // 010
      0x60,  // 011
      0x20,  // 00100
      0x38,  // 00111
  };
  int32_t expected[] = {0, 1, -1, 2, -3};
  for (size_t i = 0; i < sizeof(golomb_bits); ++i) {
    BitBuffer buffer(&golomb_bits[i], 1);
    int32_t decoded_val;
    ASSERT_TRUE(buffer.ReadSignedExponentialGolomb(&decoded_val));
    EXPECT_EQ(expected[i], decoded_val)
        << "Mismatch in expected/decoded value for golomb_bits[" << i
        << "]: " << static_cast<int>(golomb_bits[i]);
  }
}

TEST(BitBufferTest, NoGolombOverread) {
  const uint8_t bytes[] = {0x00, 0xFF, 0xFF};
  // Make sure the bit buffer correctly enforces byte length on golomb reads.
  // If it didn't, the above buffer would be valid at 3 bytes.
  BitBuffer buffer(bytes, 1);
  uint32_t decoded_val;
  EXPECT_FALSE(buffer.ReadExponentialGolomb(&decoded_val));

  BitBuffer longer_buffer(bytes, 2);
  EXPECT_FALSE(longer_buffer.ReadExponentialGolomb(&decoded_val));

  BitBuffer longest_buffer(bytes, 3);
  EXPECT_TRUE(longest_buffer.ReadExponentialGolomb(&decoded_val));
  // Golomb should have read 9 bits, so 0x01FF, and since it is golomb, the
  // result is 0x01FF - 1 = 0x01FE.
  EXPECT_EQ(0x01FEu, decoded_val);
}

TEST(BitBufferWriterTest, SymmetricReadWrite) {
  uint8_t bytes[16] = {0};
  BitBufferWriter buffer(bytes, 4);

  // Write some bit data at various sizes.
  EXPECT_TRUE(buffer.WriteBits(0x2u, 3));
  EXPECT_TRUE(buffer.WriteBits(0x1u, 2));
  EXPECT_TRUE(buffer.WriteBits(0x53u, 7));
  EXPECT_TRUE(buffer.WriteBits(0x0u, 2));
  EXPECT_TRUE(buffer.WriteBits(0x1u, 1));
  EXPECT_TRUE(buffer.WriteBits(0x1ABCDu, 17));
  // That should be all that fits in the buffer.
  EXPECT_FALSE(buffer.WriteBits(1, 1));

  EXPECT_TRUE(buffer.Seek(0, 0));
  uint32_t val;
  EXPECT_TRUE(buffer.ReadBits(&val, 3));
  EXPECT_EQ(0x2u, val);
  EXPECT_TRUE(buffer.ReadBits(&val, 2));
  EXPECT_EQ(0x1u, val);
  EXPECT_TRUE(buffer.ReadBits(&val, 7));
  EXPECT_EQ(0x53u, val);
  EXPECT_TRUE(buffer.ReadBits(&val, 2));
  EXPECT_EQ(0x0u, val);
  EXPECT_TRUE(buffer.ReadBits(&val, 1));
  EXPECT_EQ(0x1u, val);
  EXPECT_TRUE(buffer.ReadBits(&val, 17));
  EXPECT_EQ(0x1ABCDu, val);
  // And there should be nothing left.
  EXPECT_FALSE(buffer.ReadBits(&val, 1));
}

TEST(BitBufferWriterTest, SymmetricBytesMisaligned) {
  uint8_t bytes[16] = {0};
  BitBufferWriter buffer(bytes, 16);

  // Offset 3, to get things misaligned.
  EXPECT_TRUE(buffer.ConsumeBits(3));
  EXPECT_TRUE(buffer.WriteUInt8(0x12u));
  EXPECT_TRUE(buffer.WriteUInt16(0x3456u));
  EXPECT_TRUE(buffer.WriteUInt32(0x789ABCDEu));

  buffer.Seek(0, 3);
  uint8_t val8;
  uint16_t val16;
  uint32_t val32;
  EXPECT_TRUE(buffer.ReadUInt8(&val8));
  EXPECT_EQ(0x12u, val8);
  EXPECT_TRUE(buffer.ReadUInt16(&val16));
  EXPECT_EQ(0x3456u, val16);
  EXPECT_TRUE(buffer.ReadUInt32(&val32));
  EXPECT_EQ(0x789ABCDEu, val32);
}

TEST(BitBufferWriterTest, SymmetricGolomb) {
  char test_string[] = "my precious";
  uint8_t bytes[64] = {0};
  BitBufferWriter buffer(bytes, 64);
  for (size_t i = 0; i < arraysize(test_string); ++i) {
    EXPECT_TRUE(buffer.WriteExponentialGolomb(test_string[i]));
  }
  buffer.Seek(0, 0);
  for (size_t i = 0; i < arraysize(test_string); ++i) {
    uint32_t val;
    EXPECT_TRUE(buffer.ReadExponentialGolomb(&val));
    EXPECT_LE(val, std::numeric_limits<uint8_t>::max());
    EXPECT_EQ(test_string[i], static_cast<char>(val));
  }
}

TEST(BitBufferWriterTest, WriteClearsBits) {
  uint8_t bytes[] = {0xFF, 0xFF};
  BitBufferWriter buffer(bytes, 2);
  EXPECT_TRUE(buffer.ConsumeBits(3));
  EXPECT_TRUE(buffer.WriteBits(0, 1));
  EXPECT_EQ(0xEFu, bytes[0]);
  EXPECT_TRUE(buffer.WriteBits(0, 3));
  EXPECT_EQ(0xE1u, bytes[0]);
  EXPECT_TRUE(buffer.WriteBits(0, 2));
  EXPECT_EQ(0xE0u, bytes[0]);
  EXPECT_EQ(0x7F, bytes[1]);
}

}  // namespace rtc
