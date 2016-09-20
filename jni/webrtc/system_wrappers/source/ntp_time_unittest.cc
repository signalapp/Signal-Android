/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "testing/gtest/include/gtest/gtest.h"

#include "webrtc/system_wrappers/include/ntp_time.h"

namespace webrtc {
namespace {

const uint32_t kNtpSec = 0x12345678;
const uint32_t kNtpFrac = 0x23456789;

TEST(NtpTimeTest, NoValueMeansInvalid) {
  NtpTime ntp;
  EXPECT_FALSE(ntp.Valid());
}

TEST(NtpTimeTest, CanResetValue) {
  NtpTime ntp(kNtpSec, kNtpFrac);
  EXPECT_TRUE(ntp.Valid());
  ntp.Reset();
  EXPECT_FALSE(ntp.Valid());
}

TEST(NtpTimeTest, CanGetWhatIsSet) {
  NtpTime ntp;
  ntp.Set(kNtpSec, kNtpFrac);
  EXPECT_EQ(kNtpSec, ntp.seconds());
  EXPECT_EQ(kNtpFrac, ntp.fractions());
}

TEST(NtpTimeTest, SetIsSameAs2ParameterConstructor) {
  NtpTime ntp1(kNtpSec, kNtpFrac);
  NtpTime ntp2;
  EXPECT_NE(ntp1, ntp2);

  ntp2.Set(kNtpSec, kNtpFrac);
  EXPECT_EQ(ntp1, ntp2);
}

TEST(NtpTimeTest, SetCurrentIsSameAs1ParameterConstructor) {
  SimulatedClock clock(0x0123456789abcdef);

  NtpTime ntp1(clock);
  NtpTime ntp2;
  EXPECT_NE(ntp1, ntp2);

  ntp2.SetCurrent(clock);
  EXPECT_EQ(ntp1, ntp2);
}

TEST(NtpTimeTest, ToMsMeansToNtpMilliseconds) {
  SimulatedClock clock(0x123456789abc);

  NtpTime ntp(clock);
  EXPECT_EQ(ntp.ToMs(), Clock::NtpToMs(ntp.seconds(), ntp.fractions()));
  EXPECT_EQ(ntp.ToMs(), clock.CurrentNtpInMilliseconds());
}

}  // namespace
}  // namespace webrtc
