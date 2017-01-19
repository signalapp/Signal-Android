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
#include "webrtc/modules/audio_coding/neteq/tools/neteq_performance_test.h"
#include "webrtc/test/testsupport/perf_test.h"
#include "webrtc/typedefs.h"

// Runs a test with 10% packet losses and 10% clock drift, to exercise
// both loss concealment and time-stretching code.
TEST(NetEqPerformanceTest, Run) {
  const int kSimulationTimeMs = 10000000;
  const int kLossPeriod = 10;  // Drop every 10th packet.
  const double kDriftFactor = 0.1;
  int64_t runtime = webrtc::test::NetEqPerformanceTest::Run(
      kSimulationTimeMs, kLossPeriod, kDriftFactor);
  ASSERT_GT(runtime, 0);
  webrtc::test::PrintResult(
      "neteq_performance", "", "10_pl_10_drift", runtime, "ms", true);
}

// Runs a test with neither packet losses nor clock drift, to put
// emphasis on the "good-weather" code path, which is presumably much
// more lightweight.
TEST(NetEqPerformanceTest, RunClean) {
  const int kSimulationTimeMs = 10000000;
  const int kLossPeriod = 0;  // No losses.
  const double kDriftFactor = 0.0;  // No clock drift.
  int64_t runtime = webrtc::test::NetEqPerformanceTest::Run(
      kSimulationTimeMs, kLossPeriod, kDriftFactor);
  ASSERT_GT(runtime, 0);
  webrtc::test::PrintResult(
      "neteq_performance", "", "0_pl_0_drift", runtime, "ms", true);
}
