/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/base/gunit.h"
#include "webrtc/base/profiler.h"
#include "webrtc/base/thread.h"

namespace {

const int kWaitMs = 250;
const double kWaitSec = 0.250;
const double kTolerance = 0.1;

const char* TestFunc() {
  PROFILE_F();
  rtc::Thread::SleepMs(kWaitMs);
  return __FUNCTION__;
}

}  // namespace

namespace rtc {

// Disable this test due to flakiness; see bug 5947.
#if defined(WEBRTC_LINUX)
#define MAYBE_TestFunction DISABLED_TestFunction
#else
#define MAYBE_TestFunction TestFunction
#endif
TEST(ProfilerTest, MAYBE_TestFunction) {
  ASSERT_TRUE(Profiler::Instance()->Clear());

  // Profile a long-running function.
  const char* function_name = TestFunc();
  const ProfilerEvent* event = Profiler::Instance()->GetEvent(function_name);
  ASSERT_TRUE(event != NULL);
  EXPECT_FALSE(event->is_started());
  EXPECT_EQ(1, event->event_count());
  EXPECT_NEAR(kWaitSec, event->mean(), kTolerance * 3);

  // Run it a second time.
  TestFunc();
  EXPECT_FALSE(event->is_started());
  EXPECT_EQ(2, event->event_count());
  EXPECT_NEAR(kWaitSec, event->mean(), kTolerance);
  EXPECT_NEAR(kWaitSec * 2, event->total_time(), kTolerance * 2);
  EXPECT_DOUBLE_EQ(event->mean(), event->total_time() / event->event_count());
}

TEST(ProfilerTest, TestScopedEvents) {
  const std::string kEvent1Name = "Event 1";
  const std::string kEvent2Name = "Event 2";
  const int kEvent2WaitMs = 150;
  const double kEvent2WaitSec = 0.150;
  const ProfilerEvent* event1;
  const ProfilerEvent* event2;
  ASSERT_TRUE(Profiler::Instance()->Clear());
  {  // Profile a scope.
    PROFILE(kEvent1Name);
    event1 = Profiler::Instance()->GetEvent(kEvent1Name);
    ASSERT_TRUE(event1 != NULL);
    EXPECT_TRUE(event1->is_started());
    EXPECT_EQ(0, event1->event_count());
    rtc::Thread::SleepMs(kWaitMs);
    EXPECT_TRUE(event1->is_started());
  }
  // Check the result.
  EXPECT_FALSE(event1->is_started());
  EXPECT_EQ(1, event1->event_count());
  EXPECT_NEAR(kWaitSec, event1->mean(), kTolerance);
  {  // Profile a second event.
    PROFILE(kEvent2Name);
    event2 = Profiler::Instance()->GetEvent(kEvent2Name);
    ASSERT_TRUE(event2 != NULL);
    EXPECT_FALSE(event1->is_started());
    EXPECT_TRUE(event2->is_started());
    rtc::Thread::SleepMs(kEvent2WaitMs);
  }
  // Check the result.
  EXPECT_FALSE(event2->is_started());
  EXPECT_EQ(1, event2->event_count());

  // The difference here can be as much as 0.33, so we need high tolerance.
  EXPECT_NEAR(kEvent2WaitSec, event2->mean(), kTolerance * 4);
  // Make sure event1 is unchanged.
  EXPECT_FALSE(event1->is_started());
  EXPECT_EQ(1, event1->event_count());
  {  // Run another event 1.
    PROFILE(kEvent1Name);
    EXPECT_TRUE(event1->is_started());
    rtc::Thread::SleepMs(kWaitMs);
  }
  // Check the result.
  EXPECT_FALSE(event1->is_started());
  EXPECT_EQ(2, event1->event_count());
  EXPECT_NEAR(kWaitSec, event1->mean(), kTolerance);
  EXPECT_NEAR(kWaitSec * 2, event1->total_time(), kTolerance * 2);
  EXPECT_DOUBLE_EQ(event1->mean(),
                   event1->total_time() / event1->event_count());
}

TEST(ProfilerTest, Clear) {
  ASSERT_TRUE(Profiler::Instance()->Clear());
  PROFILE_START("event");
  EXPECT_FALSE(Profiler::Instance()->Clear());
  EXPECT_TRUE(Profiler::Instance()->GetEvent("event") != NULL);
  PROFILE_STOP("event");
  EXPECT_TRUE(Profiler::Instance()->Clear());
  EXPECT_EQ(NULL, Profiler::Instance()->GetEvent("event"));
}

}  // namespace rtc
