/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_BASE_GUNIT_H_
#define WEBRTC_BASE_GUNIT_H_

#include "webrtc/base/fakeclock.h"
#include "webrtc/base/logging.h"
#include "webrtc/base/thread.h"
#if defined(GTEST_RELATIVE_PATH)
#include "testing/gtest/include/gtest/gtest.h"
#else
#include "testing/base/public/gunit.h"
#endif

// Wait until "ex" is true, or "timeout" expires.
#define WAIT(ex, timeout)                                       \
  for (int64_t start = rtc::SystemTimeMillis();                 \
       !(ex) && rtc::SystemTimeMillis() < start + (timeout);) { \
    rtc::Thread::Current()->ProcessMessages(0);                 \
    rtc::Thread::Current()->SleepMs(1);                         \
  }

// This returns the result of the test in res, so that we don't re-evaluate
// the expression in the XXXX_WAIT macros below, since that causes problems
// when the expression is only true the first time you check it.
#define WAIT_(ex, timeout, res)                                   \
  do {                                                            \
    int64_t start = rtc::SystemTimeMillis();                      \
    res = (ex);                                                   \
    while (!res && rtc::SystemTimeMillis() < start + (timeout)) { \
      rtc::Thread::Current()->ProcessMessages(0);                 \
      rtc::Thread::Current()->SleepMs(1);                         \
      res = (ex);                                                 \
    }                                                             \
  } while (0)

// The typical EXPECT_XXXX and ASSERT_XXXXs, but done until true or a timeout.
#define EXPECT_TRUE_WAIT(ex, timeout) \
  do { \
    bool res; \
    WAIT_(ex, timeout, res); \
    if (!res) EXPECT_TRUE(ex); \
  } while (0)

#define EXPECT_EQ_WAIT(v1, v2, timeout) \
  do { \
    bool res; \
    WAIT_(v1 == v2, timeout, res); \
    if (!res) EXPECT_EQ(v1, v2); \
  } while (0)

#define ASSERT_TRUE_WAIT(ex, timeout) \
  do { \
    bool res; \
    WAIT_(ex, timeout, res); \
    if (!res) ASSERT_TRUE(ex); \
  } while (0)

#define ASSERT_EQ_WAIT(v1, v2, timeout) \
  do { \
    bool res; \
    WAIT_(v1 == v2, timeout, res); \
    if (!res) ASSERT_EQ(v1, v2); \
  } while (0)

// Version with a "soft" timeout and a margin. This logs if the timeout is
// exceeded, but it only fails if the expression still isn't true after the
// margin time passes.
#define EXPECT_TRUE_WAIT_MARGIN(ex, timeout, margin)                       \
  do {                                                                     \
    bool res;                                                              \
    WAIT_(ex, timeout, res);                                               \
    if (res) {                                                             \
      break;                                                               \
    }                                                                      \
    LOG(LS_WARNING) << "Expression " << #ex << " still not true after "    \
                    << (timeout) << "ms; waiting an additional " << margin \
                    << "ms";                                               \
    WAIT_(ex, margin, res);                                                \
    if (!res) {                                                            \
      EXPECT_TRUE(ex);                                                     \
    }                                                                      \
  } while (0)

// Wait until "ex" is true, or "timeout" expires, using fake clock where
// messages are processed every millisecond.
#define SIMULATED_WAIT(ex, timeout, clock)                  \
  for (int64_t start = rtc::TimeMillis();                   \
       !(ex) && rtc::TimeMillis() < start + (timeout);) {   \
    clock.AdvanceTime(rtc::TimeDelta::FromMilliseconds(1)); \
  }

// This returns the result of the test in res, so that we don't re-evaluate
// the expression in the XXXX_WAIT macros below, since that causes problems
// when the expression is only true the first time you check it.
#define SIMULATED_WAIT_(ex, timeout, res, clock)              \
  do {                                                        \
    int64_t start = rtc::TimeMillis();                        \
    res = (ex);                                               \
    while (!res && rtc::TimeMillis() < start + (timeout)) {   \
      clock.AdvanceTime(rtc::TimeDelta::FromMilliseconds(1)); \
      res = (ex);                                             \
    }                                                         \
  } while (0)

// The typical EXPECT_XXXX, but done until true or a timeout with a fake clock.
#define EXPECT_TRUE_SIMULATED_WAIT(ex, timeout, clock) \
  do {                                                 \
    bool res;                                          \
    SIMULATED_WAIT_(ex, timeout, res, clock);          \
    if (!res) {                                        \
      EXPECT_TRUE(ex);                                 \
    }                                                  \
  } while (0)

#define EXPECT_EQ_SIMULATED_WAIT(v1, v2, timeout, clock) \
  do {                                                   \
    bool res;                                            \
    SIMULATED_WAIT_(v1 == v2, timeout, res, clock);      \
    if (!res) {                                          \
      EXPECT_EQ(v1, v2);                                 \
    }                                                    \
  } while (0)

#define ASSERT_TRUE_SIMULATED_WAIT(ex, timeout, clock) \
  do {                                                 \
    bool res;                                          \
    SIMULATED_WAIT_(ex, timeout, res, clock);          \
    if (!res)                                          \
      ASSERT_TRUE(ex);                                 \
  } while (0)

#define ASSERT_EQ_SIMULATED_WAIT(v1, v2, timeout, clock) \
  do {                                                   \
    bool res;                                            \
    SIMULATED_WAIT_(v1 == v2, timeout, res, clock);      \
    if (!res)                                            \
      ASSERT_EQ(v1, v2);                                 \
  } while (0)

#endif  // WEBRTC_BASE_GUNIT_H_
