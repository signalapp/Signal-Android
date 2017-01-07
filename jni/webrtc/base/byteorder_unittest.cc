/*
 *  Copyright 2012 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/base/byteorder.h"

#include "webrtc/base/basictypes.h"
#include "webrtc/base/gunit.h"

namespace rtc {

// Test memory set functions put values into memory in expected order.
TEST(ByteOrderTest, TestSet) {
  uint8_t buf[8] = {0u, 0u, 0u, 0u, 0u, 0u, 0u, 0u};
  Set8(buf, 0, 0xfb);
  Set8(buf, 1, 0x12);
  EXPECT_EQ(0xfb, buf[0]);
  EXPECT_EQ(0x12, buf[1]);
  SetBE16(buf, 0x1234);
  EXPECT_EQ(0x12, buf[0]);
  EXPECT_EQ(0x34, buf[1]);
  SetLE16(buf, 0x1234);
  EXPECT_EQ(0x34, buf[0]);
  EXPECT_EQ(0x12, buf[1]);
  SetBE32(buf, 0x12345678);
  EXPECT_EQ(0x12, buf[0]);
  EXPECT_EQ(0x34, buf[1]);
  EXPECT_EQ(0x56, buf[2]);
  EXPECT_EQ(0x78, buf[3]);
  SetLE32(buf, 0x12345678);
  EXPECT_EQ(0x78, buf[0]);
  EXPECT_EQ(0x56, buf[1]);
  EXPECT_EQ(0x34, buf[2]);
  EXPECT_EQ(0x12, buf[3]);
  SetBE64(buf, UINT64_C(0x0123456789abcdef));
  EXPECT_EQ(0x01, buf[0]);
  EXPECT_EQ(0x23, buf[1]);
  EXPECT_EQ(0x45, buf[2]);
  EXPECT_EQ(0x67, buf[3]);
  EXPECT_EQ(0x89, buf[4]);
  EXPECT_EQ(0xab, buf[5]);
  EXPECT_EQ(0xcd, buf[6]);
  EXPECT_EQ(0xef, buf[7]);
  SetLE64(buf, UINT64_C(0x0123456789abcdef));
  EXPECT_EQ(0xef, buf[0]);
  EXPECT_EQ(0xcd, buf[1]);
  EXPECT_EQ(0xab, buf[2]);
  EXPECT_EQ(0x89, buf[3]);
  EXPECT_EQ(0x67, buf[4]);
  EXPECT_EQ(0x45, buf[5]);
  EXPECT_EQ(0x23, buf[6]);
  EXPECT_EQ(0x01, buf[7]);
}

// Test memory get functions get values from memory in expected order.
TEST(ByteOrderTest, TestGet) {
  uint8_t buf[8];
  buf[0] = 0x01u;
  buf[1] = 0x23u;
  buf[2] = 0x45u;
  buf[3] = 0x67u;
  buf[4] = 0x89u;
  buf[5] = 0xabu;
  buf[6] = 0xcdu;
  buf[7] = 0xefu;
  EXPECT_EQ(0x01u, Get8(buf, 0));
  EXPECT_EQ(0x23u, Get8(buf, 1));
  EXPECT_EQ(0x0123u, GetBE16(buf));
  EXPECT_EQ(0x2301u, GetLE16(buf));
  EXPECT_EQ(0x01234567u, GetBE32(buf));
  EXPECT_EQ(0x67452301u, GetLE32(buf));
  EXPECT_EQ(UINT64_C(0x0123456789abcdef), GetBE64(buf));
  EXPECT_EQ(UINT64_C(0xefcdab8967452301), GetLE64(buf));
}

}  // namespace rtc

