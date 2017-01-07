/*
 *  Copyright 2010 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <string>

#include "webrtc/base/gunit.h"
#include "webrtc/base/nethelpers.h"
#include "webrtc/base/win32.h"
#include "webrtc/base/winping.h"

#if !defined(WEBRTC_WIN)
#error Only for Windows
#endif

namespace rtc {

class Win32Test : public testing::Test {
 public:
  Win32Test() {
  }
};

TEST_F(Win32Test, FileTimeToUInt64Test) {
  FILETIME ft;
  ft.dwHighDateTime = 0xBAADF00D;
  ft.dwLowDateTime = 0xFEED3456;

  uint64_t expected = 0xBAADF00DFEED3456;
  EXPECT_EQ(expected, ToUInt64(ft));
}

TEST_F(Win32Test, WinPingTest) {
  WinPing ping;
  ASSERT_TRUE(ping.IsValid());

  // Test valid ping cases.
  WinPing::PingResult result = ping.Ping(IPAddress(INADDR_LOOPBACK), 20, 50, 1,
                                         false);
  ASSERT_EQ(WinPing::PING_SUCCESS, result);
  if (HasIPv6Enabled()) {
    WinPing::PingResult v6result = ping.Ping(IPAddress(in6addr_loopback), 20,
                                             50, 1, false);
    ASSERT_EQ(WinPing::PING_SUCCESS, v6result);
  }

  // Test invalid parameter cases.
  ASSERT_EQ(WinPing::PING_INVALID_PARAMS, ping.Ping(
            IPAddress(INADDR_LOOPBACK), 0, 50, 1, false));
  ASSERT_EQ(WinPing::PING_INVALID_PARAMS, ping.Ping(
            IPAddress(INADDR_LOOPBACK), 20, 0, 1, false));
  ASSERT_EQ(WinPing::PING_INVALID_PARAMS, ping.Ping(
            IPAddress(INADDR_LOOPBACK), 20, 50, 0, false));
}

TEST_F(Win32Test, IPv6AddressCompression) {
  IPAddress ipv6;

  // Zero compression should be done on the leftmost 0s when there are
  // multiple longest series.
  ASSERT_TRUE(IPFromString("2a00:8a00:a000:1190:0000:0001:000:252", &ipv6));
  EXPECT_EQ("2a00:8a00:a000:1190::1:0:252", ipv6.ToString());

  // Ensure the zero compression could handle multiple octects.
  ASSERT_TRUE(IPFromString("0:0:0:0:0:0:0:1", &ipv6));
  EXPECT_EQ("::1", ipv6.ToString());

  // Make sure multiple 0 octects compressed.
  ASSERT_TRUE(IPFromString("fe80:0:0:0:2aa:ff:fe9a:4ca2", &ipv6));
  EXPECT_EQ("fe80::2aa:ff:fe9a:4ca2", ipv6.ToString());

  // Test zero compression at the end of string.
  ASSERT_TRUE(IPFromString("2a00:8a00:a000:1190:0000:0001:000:00", &ipv6));
  EXPECT_EQ("2a00:8a00:a000:1190:0:1::", ipv6.ToString());

  // Test zero compression at the beginning of string.
  ASSERT_TRUE(IPFromString("0:0:000:1190:0000:0001:000:00", &ipv6));
  EXPECT_EQ("::1190:0:1:0:0", ipv6.ToString());

  // Test zero compression only done once.
  ASSERT_TRUE(IPFromString("0:1:000:1190:0000:0001:000:01", &ipv6));
  EXPECT_EQ("::1:0:1190:0:1:0:1", ipv6.ToString());

  // Make sure noncompressable IPv6 is the same.
  ASSERT_TRUE(IPFromString("1234:5678:abcd:1234:5678:abcd:1234:5678", &ipv6));
  EXPECT_EQ("1234:5678:abcd:1234:5678:abcd:1234:5678", ipv6.ToString());
}

}  // namespace rtc
