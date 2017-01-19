/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/system_wrappers/interface/clock.h"

#include "testing/gtest/include/gtest/gtest.h"

namespace webrtc {

TEST(ClockTest, NtpTime) {
  Clock* clock = Clock::GetRealTimeClock();
  uint32_t seconds;
  uint32_t fractions;
  clock->CurrentNtp(seconds, fractions);
  int64_t milliseconds = clock->CurrentNtpInMilliseconds();
  EXPECT_GT(milliseconds / 1000, kNtpJan1970);
  EXPECT_GE(milliseconds, Clock::NtpToMs(seconds, fractions));
  EXPECT_NEAR(milliseconds, Clock::NtpToMs(seconds, fractions), 5);
}
}  // namespace webrtc
