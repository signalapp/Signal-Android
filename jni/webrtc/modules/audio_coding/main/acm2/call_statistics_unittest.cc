/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "gtest/gtest.h"
#include "webrtc/modules/audio_coding/main/acm2/call_statistics.h"

namespace webrtc {

namespace acm2 {

TEST(CallStatisticsTest, InitializedZero) {
  CallStatistics call_stats;
  AudioDecodingCallStats stats;

  stats = call_stats.GetDecodingStatistics();
  EXPECT_EQ(0, stats.calls_to_neteq);
  EXPECT_EQ(0, stats.calls_to_silence_generator);
  EXPECT_EQ(0, stats.decoded_normal);
  EXPECT_EQ(0, stats.decoded_cng);
  EXPECT_EQ(0, stats.decoded_plc);
  EXPECT_EQ(0, stats.decoded_plc_cng);
}

TEST(CallStatisticsTest, AllCalls) {
  CallStatistics call_stats;
  AudioDecodingCallStats stats;

  call_stats.DecodedBySilenceGenerator();
  call_stats.DecodedByNetEq(AudioFrame::kNormalSpeech);
  call_stats.DecodedByNetEq(AudioFrame::kPLC);
  call_stats.DecodedByNetEq(AudioFrame::kPLCCNG);
  call_stats.DecodedByNetEq(AudioFrame::kCNG);

  stats = call_stats.GetDecodingStatistics();
  EXPECT_EQ(4, stats.calls_to_neteq);
  EXPECT_EQ(1, stats.calls_to_silence_generator);
  EXPECT_EQ(1, stats.decoded_normal);
  EXPECT_EQ(1, stats.decoded_cng);
  EXPECT_EQ(1, stats.decoded_plc);
  EXPECT_EQ(1, stats.decoded_plc_cng);
}

}  // namespace acm2

}  // namespace webrtc



