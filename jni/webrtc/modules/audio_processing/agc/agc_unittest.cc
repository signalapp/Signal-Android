/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/modules/audio_processing/agc/agc.h"

#include "testing/gmock/include/gmock/gmock.h"
#include "testing/gtest/include/gtest/gtest.h"

#include "webrtc/modules/include/module_common_types.h"
#include "webrtc/test/testsupport/fileutils.h"
#include "webrtc/tools/agc/test_utils.h"

using ::testing::_;
using ::testing::AllOf;
using ::testing::AtLeast;
using ::testing::Eq;
using ::testing::Gt;
using ::testing::InSequence;
using ::testing::Lt;
using ::testing::Mock;
using ::testing::SaveArg;

namespace webrtc {
namespace {

// The tested values depend on this assumed gain.
const int kMaxGain = 80;

MATCHER_P(GtPointee, p, "") { return arg > *p; }
MATCHER_P(LtPointee, p, "") { return arg < *p; }

class AgcChecker {
 public:
  MOCK_METHOD2(LevelChanged, void(int iterations, int level));
};

class AgcTest : public ::testing::Test {
 protected:
  AgcTest()
      : agc_(),
        checker_(),
        mic_level_(128) {
  }

  // A gain of <= -100 will zero out the signal.
  void RunAgc(int iterations, float gain_db) {
    FILE* input_file = fopen(
        test::ResourcePath("voice_engine/audio_long16", "pcm").c_str(), "rb");
    ASSERT_TRUE(input_file != NULL);

    AudioFrame frame;
    frame.sample_rate_hz_ = 16000;
    frame.num_channels_ = 1;
    frame.samples_per_channel_ = frame.sample_rate_hz_ / 100;
    const size_t length = frame.samples_per_channel_ * frame.num_channels_;

    float gain = Db2Linear(gain_db);
    if (gain_db <= -100) {
      gain = 0;
    }

    for (int i = 0; i < iterations; ++i) {
      ASSERT_EQ(length, fread(frame.data_, sizeof(int16_t), length,
                input_file));
      SimulateMic(kMaxGain, mic_level_, &frame);
      ApplyGainLinear(gain, &frame);
      ASSERT_GE(agc_.Process(frame), 0);

      int mic_level = agc_.MicLevel();
      if (mic_level != mic_level_) {
        printf("mic_level=%d\n", mic_level);
        checker_.LevelChanged(i, mic_level);
      }
      mic_level_ = mic_level;
    }
    fclose(input_file);
  }

  Agc agc_;
  AgcChecker checker_;
  // Stores mic level between multiple runs of RunAgc in one test.
  int mic_level_;
};

TEST_F(AgcTest, UpwardsChangeIsLimited) {
  {
    InSequence seq;
    EXPECT_CALL(checker_, LevelChanged(Lt(500), Eq(179))).Times(1);
    EXPECT_CALL(checker_, LevelChanged(_, Gt(179))).Times(AtLeast(1));
  }
  RunAgc(1000, -40);
}

TEST_F(AgcTest, DownwardsChangeIsLimited) {
  {
    InSequence seq;
    EXPECT_CALL(checker_, LevelChanged(Lt(500), Eq(77))).Times(1);
    EXPECT_CALL(checker_, LevelChanged(_, Lt(77))).Times(AtLeast(1));
  }
  RunAgc(1000, 40);
}

TEST_F(AgcTest, MovesUpToMaxAndDownToMin) {
  int last_level = 128;
  EXPECT_CALL(checker_, LevelChanged(_, GtPointee(&last_level)))
      .Times(AtLeast(2))
      .WillRepeatedly(SaveArg<1>(&last_level));
  RunAgc(1000, -30);
  EXPECT_EQ(255, last_level);
  Mock::VerifyAndClearExpectations(&checker_);

  EXPECT_CALL(checker_, LevelChanged(_, LtPointee(&last_level)))
      .Times(AtLeast(2))
      .WillRepeatedly(SaveArg<1>(&last_level));
  RunAgc(1000, 50);
  EXPECT_EQ(1, last_level);
}

TEST_F(AgcTest, HandlesZeroSignal) {
  int last_level = 128;
  // Doesn't respond to a zero signal.
  EXPECT_CALL(checker_, LevelChanged(_, _)).Times(0);
  RunAgc(1000, -100);
  Mock::VerifyAndClearExpectations(&checker_);

  // Reacts as usual afterwards.
  EXPECT_CALL(checker_, LevelChanged(_, GtPointee(&last_level)))
      .Times(AtLeast(2))
      .WillRepeatedly(SaveArg<1>(&last_level));
  RunAgc(500, -20);
}

TEST_F(AgcTest, ReachesSteadyState) {
  int last_level = 128;
  EXPECT_CALL(checker_, LevelChanged(_, _))
      .Times(AtLeast(2))
      .WillRepeatedly(SaveArg<1>(&last_level));
  RunAgc(1000, -20);
  Mock::VerifyAndClearExpectations(&checker_);

  // If the level changes, it should be in a narrow band around the previous
  // adaptation.
  EXPECT_CALL(checker_, LevelChanged(_,
      AllOf(Gt(last_level * 0.95), Lt(last_level * 1.05))))
      .Times(AtLeast(0));
  RunAgc(1000, -20);
}

// TODO(ajm): Add this test; requires measuring the signal RMS.
TEST_F(AgcTest, AdaptsToCorrectRMS) {
}

}  // namespace
}  // namespace webrtc

