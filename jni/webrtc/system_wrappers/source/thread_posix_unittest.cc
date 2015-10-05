/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/system_wrappers/source/thread_posix.h"

#include "testing/gtest/include/gtest/gtest.h"

TEST(ThreadTestPosix, PrioritySettings) {
  // API assumes that max_prio - min_prio > 2. Test the extreme case.
  const int kMinPrio = -1;
  const int kMaxPrio = 2;

  int last_priority = kMinPrio;
  for (int priority = webrtc::kLowPriority;
       priority <= webrtc::kRealtimePriority; ++priority) {
    int system_priority = webrtc::ConvertToSystemPriority(
        static_cast<webrtc::ThreadPriority>(priority), kMinPrio, kMaxPrio);
    EXPECT_GT(system_priority, kMinPrio);
    EXPECT_LT(system_priority, kMaxPrio);
    EXPECT_GE(system_priority, last_priority);
    last_priority = system_priority;
  }
}
