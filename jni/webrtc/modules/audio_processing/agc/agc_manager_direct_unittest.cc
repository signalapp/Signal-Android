/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/modules/audio_processing/agc/agc_manager_direct.h"

#include "testing/gmock/include/gmock/gmock.h"
#include "testing/gtest/include/gtest/gtest.h"
#include "webrtc/common_types.h"
#include "webrtc/modules/audio_processing/agc/mock_agc.h"
#include "webrtc/modules/audio_processing/include/mock_audio_processing.h"
#include "webrtc/system_wrappers/include/trace.h"
#include "webrtc/test/testsupport/trace_to_stderr.h"

using ::testing::_;
using ::testing::DoAll;
using ::testing::Eq;
using ::testing::Mock;
using ::testing::Return;
using ::testing::SetArgPointee;
using ::testing::SetArgReferee;

namespace webrtc {
namespace {

const int kSampleRateHz = 32000;
const int kNumChannels = 1;
const int kSamplesPerChannel = kSampleRateHz / 100;
const int kInitialVolume = 128;
const float kAboveClippedThreshold = 0.2f;

class TestVolumeCallbacks : public VolumeCallbacks {
 public:
  TestVolumeCallbacks() : volume_(0) {}
  void SetMicVolume(int volume) override { volume_ = volume; }
  int GetMicVolume() override { return volume_; }

 private:
  int volume_;
};

}  // namespace

class AgcManagerDirectTest : public ::testing::Test {
 protected:
  AgcManagerDirectTest()
      : agc_(new MockAgc), manager_(agc_, &gctrl_, &volume_, kInitialVolume) {
    ExpectInitialize();
    manager_.Initialize();
  }

  void FirstProcess() {
    EXPECT_CALL(*agc_, Reset());
    EXPECT_CALL(*agc_, GetRmsErrorDb(_)).WillOnce(Return(false));
    CallProcess(1);
  }

  void SetVolumeAndProcess(int volume) {
    volume_.SetMicVolume(volume);
    FirstProcess();
  }

  void ExpectCheckVolumeAndReset(int volume) {
    volume_.SetMicVolume(volume);
    EXPECT_CALL(*agc_, Reset());
  }

  void ExpectInitialize() {
    EXPECT_CALL(gctrl_, set_mode(GainControl::kFixedDigital));
    EXPECT_CALL(gctrl_, set_target_level_dbfs(2));
    EXPECT_CALL(gctrl_, set_compression_gain_db(7));
    EXPECT_CALL(gctrl_, enable_limiter(true));
  }

  void CallProcess(int num_calls) {
    for (int i = 0; i < num_calls; ++i) {
      EXPECT_CALL(*agc_, Process(_, _, _)).WillOnce(Return(0));
      manager_.Process(nullptr, kSamplesPerChannel, kSampleRateHz);
    }
  }

  void CallPreProc(int num_calls) {
    for (int i = 0; i < num_calls; ++i) {
      manager_.AnalyzePreProcess(nullptr, kNumChannels, kSamplesPerChannel);
    }
  }

  MockAgc* agc_;
  MockGainControl gctrl_;
  TestVolumeCallbacks volume_;
  AgcManagerDirect manager_;
  test::TraceToStderr trace_to_stderr;
};

TEST_F(AgcManagerDirectTest, StartupMinVolumeConfigurationIsRespected) {
  FirstProcess();
  EXPECT_EQ(kInitialVolume, volume_.GetMicVolume());
}

TEST_F(AgcManagerDirectTest, MicVolumeResponseToRmsError) {
  FirstProcess();

  // Compressor default; no residual error.
  EXPECT_CALL(*agc_, GetRmsErrorDb(_))
      .WillOnce(DoAll(SetArgPointee<0>(5), Return(true)));
  CallProcess(1);

  // Inside the compressor's window; no change of volume.
  EXPECT_CALL(*agc_, GetRmsErrorDb(_))
      .WillOnce(DoAll(SetArgPointee<0>(10), Return(true)));
  CallProcess(1);

  // Above the compressor's window; volume should be increased.
  EXPECT_CALL(*agc_, GetRmsErrorDb(_))
      .WillOnce(DoAll(SetArgPointee<0>(11), Return(true)));
  CallProcess(1);
  EXPECT_EQ(130, volume_.GetMicVolume());

  EXPECT_CALL(*agc_, GetRmsErrorDb(_))
      .WillOnce(DoAll(SetArgPointee<0>(20), Return(true)));
  CallProcess(1);
  EXPECT_EQ(168, volume_.GetMicVolume());

  // Inside the compressor's window; no change of volume.
  EXPECT_CALL(*agc_, GetRmsErrorDb(_))
      .WillOnce(DoAll(SetArgPointee<0>(5), Return(true)));
  CallProcess(1);
  EXPECT_CALL(*agc_, GetRmsErrorDb(_))
      .WillOnce(DoAll(SetArgPointee<0>(0), Return(true)));
  CallProcess(1);

  // Below the compressor's window; volume should be decreased.
  EXPECT_CALL(*agc_, GetRmsErrorDb(_))
      .WillOnce(DoAll(SetArgPointee<0>(-1), Return(true)));
  CallProcess(1);
  EXPECT_EQ(167, volume_.GetMicVolume());

  EXPECT_CALL(*agc_, GetRmsErrorDb(_))
      .WillOnce(DoAll(SetArgPointee<0>(-1), Return(true)));
  CallProcess(1);
  EXPECT_EQ(163, volume_.GetMicVolume());

  EXPECT_CALL(*agc_, GetRmsErrorDb(_))
      .WillOnce(DoAll(SetArgPointee<0>(-9), Return(true)));
  CallProcess(1);
  EXPECT_EQ(129, volume_.GetMicVolume());
}

TEST_F(AgcManagerDirectTest, MicVolumeIsLimited) {
  FirstProcess();

  // Maximum upwards change is limited.
  EXPECT_CALL(*agc_, GetRmsErrorDb(_))
      .WillOnce(DoAll(SetArgPointee<0>(30), Return(true)));
  CallProcess(1);
  EXPECT_EQ(183, volume_.GetMicVolume());

  EXPECT_CALL(*agc_, GetRmsErrorDb(_))
      .WillOnce(DoAll(SetArgPointee<0>(30), Return(true)));
  CallProcess(1);
  EXPECT_EQ(243, volume_.GetMicVolume());

  // Won't go higher than the maximum.
  EXPECT_CALL(*agc_, GetRmsErrorDb(_))
      .WillOnce(DoAll(SetArgPointee<0>(30), Return(true)));
  CallProcess(1);
  EXPECT_EQ(255, volume_.GetMicVolume());

  EXPECT_CALL(*agc_, GetRmsErrorDb(_))
      .WillOnce(DoAll(SetArgPointee<0>(-1), Return(true)));
  CallProcess(1);
  EXPECT_EQ(254, volume_.GetMicVolume());

  // Maximum downwards change is limited.
  EXPECT_CALL(*agc_, GetRmsErrorDb(_))
      .WillOnce(DoAll(SetArgPointee<0>(-40), Return(true)));
  CallProcess(1);
  EXPECT_EQ(194, volume_.GetMicVolume());

  EXPECT_CALL(*agc_, GetRmsErrorDb(_))
      .WillOnce(DoAll(SetArgPointee<0>(-40), Return(true)));
  CallProcess(1);
  EXPECT_EQ(137, volume_.GetMicVolume());

  EXPECT_CALL(*agc_, GetRmsErrorDb(_))
      .WillOnce(DoAll(SetArgPointee<0>(-40), Return(true)));
  CallProcess(1);
  EXPECT_EQ(88, volume_.GetMicVolume());

  EXPECT_CALL(*agc_, GetRmsErrorDb(_))
      .WillOnce(DoAll(SetArgPointee<0>(-40), Return(true)));
  CallProcess(1);
  EXPECT_EQ(54, volume_.GetMicVolume());

  EXPECT_CALL(*agc_, GetRmsErrorDb(_))
      .WillOnce(DoAll(SetArgPointee<0>(-40), Return(true)));
  CallProcess(1);
  EXPECT_EQ(33, volume_.GetMicVolume());

  EXPECT_CALL(*agc_, GetRmsErrorDb(_))
      .WillOnce(DoAll(SetArgPointee<0>(-40), Return(true)));
  CallProcess(1);
  EXPECT_EQ(18, volume_.GetMicVolume());

  // Won't go lower than the minimum.
  EXPECT_CALL(*agc_, GetRmsErrorDb(_))
      .WillOnce(DoAll(SetArgPointee<0>(-40), Return(true)));
  CallProcess(1);
  EXPECT_EQ(12, volume_.GetMicVolume());
}

TEST_F(AgcManagerDirectTest, CompressorStepsTowardsTarget) {
  FirstProcess();

  // Compressor default; no call to set_compression_gain_db.
  EXPECT_CALL(*agc_, GetRmsErrorDb(_))
      .WillOnce(DoAll(SetArgPointee<0>(5), Return(true)))
      .WillRepeatedly(Return(false));
  EXPECT_CALL(gctrl_, set_compression_gain_db(_)).Times(0);
  CallProcess(20);

  // Moves slowly upwards.
  EXPECT_CALL(*agc_, GetRmsErrorDb(_))
      .WillOnce(DoAll(SetArgPointee<0>(9), Return(true)))
      .WillRepeatedly(Return(false));
  EXPECT_CALL(gctrl_, set_compression_gain_db(_)).Times(0);
  CallProcess(19);
  EXPECT_CALL(gctrl_, set_compression_gain_db(8)).WillOnce(Return(0));
  CallProcess(1);

  EXPECT_CALL(gctrl_, set_compression_gain_db(_)).Times(0);
  CallProcess(19);
  EXPECT_CALL(gctrl_, set_compression_gain_db(9)).WillOnce(Return(0));
  CallProcess(1);

  EXPECT_CALL(gctrl_, set_compression_gain_db(_)).Times(0);
  CallProcess(20);

  // Moves slowly downward, then reverses before reaching the original target.
  EXPECT_CALL(*agc_, GetRmsErrorDb(_))
      .WillOnce(DoAll(SetArgPointee<0>(5), Return(true)))
      .WillRepeatedly(Return(false));
  EXPECT_CALL(gctrl_, set_compression_gain_db(_)).Times(0);
  CallProcess(19);
  EXPECT_CALL(gctrl_, set_compression_gain_db(8)).WillOnce(Return(0));
  CallProcess(1);

  EXPECT_CALL(*agc_, GetRmsErrorDb(_))
      .WillOnce(DoAll(SetArgPointee<0>(9), Return(true)))
      .WillRepeatedly(Return(false));
  EXPECT_CALL(gctrl_, set_compression_gain_db(_)).Times(0);
  CallProcess(19);
  EXPECT_CALL(gctrl_, set_compression_gain_db(9)).WillOnce(Return(0));
  CallProcess(1);

  EXPECT_CALL(gctrl_, set_compression_gain_db(_)).Times(0);
  CallProcess(20);
}

TEST_F(AgcManagerDirectTest, CompressorErrorIsDeemphasized) {
  FirstProcess();

  EXPECT_CALL(*agc_, GetRmsErrorDb(_))
      .WillOnce(DoAll(SetArgPointee<0>(10), Return(true)))
      .WillRepeatedly(Return(false));
  CallProcess(19);
  EXPECT_CALL(gctrl_, set_compression_gain_db(8)).WillOnce(Return(0));
  CallProcess(20);
  EXPECT_CALL(gctrl_, set_compression_gain_db(9)).WillOnce(Return(0));
  CallProcess(1);
  EXPECT_CALL(gctrl_, set_compression_gain_db(_)).Times(0);
  CallProcess(20);

  EXPECT_CALL(*agc_, GetRmsErrorDb(_))
      .WillOnce(DoAll(SetArgPointee<0>(0), Return(true)))
      .WillRepeatedly(Return(false));
  CallProcess(19);
  EXPECT_CALL(gctrl_, set_compression_gain_db(8)).WillOnce(Return(0));
  CallProcess(20);
  EXPECT_CALL(gctrl_, set_compression_gain_db(7)).WillOnce(Return(0));
  CallProcess(20);
  EXPECT_CALL(gctrl_, set_compression_gain_db(6)).WillOnce(Return(0));
  CallProcess(1);
  EXPECT_CALL(gctrl_, set_compression_gain_db(_)).Times(0);
  CallProcess(20);
}

TEST_F(AgcManagerDirectTest, CompressorReachesMaximum) {
  FirstProcess();

  EXPECT_CALL(*agc_, GetRmsErrorDb(_))
      .WillOnce(DoAll(SetArgPointee<0>(10), Return(true)))
      .WillOnce(DoAll(SetArgPointee<0>(10), Return(true)))
      .WillOnce(DoAll(SetArgPointee<0>(10), Return(true)))
      .WillOnce(DoAll(SetArgPointee<0>(10), Return(true)))
      .WillRepeatedly(Return(false));
  CallProcess(19);
  EXPECT_CALL(gctrl_, set_compression_gain_db(8)).WillOnce(Return(0));
  CallProcess(20);
  EXPECT_CALL(gctrl_, set_compression_gain_db(9)).WillOnce(Return(0));
  CallProcess(20);
  EXPECT_CALL(gctrl_, set_compression_gain_db(10)).WillOnce(Return(0));
  CallProcess(20);
  EXPECT_CALL(gctrl_, set_compression_gain_db(11)).WillOnce(Return(0));
  CallProcess(20);
  EXPECT_CALL(gctrl_, set_compression_gain_db(12)).WillOnce(Return(0));
  CallProcess(1);
}

TEST_F(AgcManagerDirectTest, CompressorReachesMinimum) {
  FirstProcess();

  EXPECT_CALL(*agc_, GetRmsErrorDb(_))
      .WillOnce(DoAll(SetArgPointee<0>(0), Return(true)))
      .WillOnce(DoAll(SetArgPointee<0>(0), Return(true)))
      .WillOnce(DoAll(SetArgPointee<0>(0), Return(true)))
      .WillOnce(DoAll(SetArgPointee<0>(0), Return(true)))
      .WillRepeatedly(Return(false));
  CallProcess(19);
  EXPECT_CALL(gctrl_, set_compression_gain_db(6)).WillOnce(Return(0));
  CallProcess(20);
  EXPECT_CALL(gctrl_, set_compression_gain_db(5)).WillOnce(Return(0));
  CallProcess(20);
  EXPECT_CALL(gctrl_, set_compression_gain_db(4)).WillOnce(Return(0));
  CallProcess(20);
  EXPECT_CALL(gctrl_, set_compression_gain_db(3)).WillOnce(Return(0));
  CallProcess(20);
  EXPECT_CALL(gctrl_, set_compression_gain_db(2)).WillOnce(Return(0));
  CallProcess(1);
}

TEST_F(AgcManagerDirectTest, NoActionWhileMuted) {
  manager_.SetCaptureMuted(true);
  manager_.Process(nullptr, kSamplesPerChannel, kSampleRateHz);
}

TEST_F(AgcManagerDirectTest, UnmutingChecksVolumeWithoutRaising) {
  FirstProcess();

  manager_.SetCaptureMuted(true);
  manager_.SetCaptureMuted(false);
  ExpectCheckVolumeAndReset(127);
  // SetMicVolume should not be called.
  EXPECT_CALL(*agc_, GetRmsErrorDb(_)).WillOnce(Return(false));
  CallProcess(1);
  EXPECT_EQ(127, volume_.GetMicVolume());
}

TEST_F(AgcManagerDirectTest, UnmutingRaisesTooLowVolume) {
  FirstProcess();

  manager_.SetCaptureMuted(true);
  manager_.SetCaptureMuted(false);
  ExpectCheckVolumeAndReset(11);
  EXPECT_CALL(*agc_, GetRmsErrorDb(_)).WillOnce(Return(false));
  CallProcess(1);
  EXPECT_EQ(12, volume_.GetMicVolume());
}

TEST_F(AgcManagerDirectTest, ManualLevelChangeResultsInNoSetMicCall) {
  FirstProcess();

  // Change outside of compressor's range, which would normally trigger a call
  // to SetMicVolume.
  EXPECT_CALL(*agc_, GetRmsErrorDb(_))
      .WillOnce(DoAll(SetArgPointee<0>(11), Return(true)));
  // GetMicVolume returns a value outside of the quantization slack, indicating
  // a manual volume change.
  volume_.SetMicVolume(154);
  // SetMicVolume should not be called.
  EXPECT_CALL(*agc_, Reset()).Times(1);
  CallProcess(1);
  EXPECT_EQ(154, volume_.GetMicVolume());

  // Do the same thing, except downwards now.
  EXPECT_CALL(*agc_, GetRmsErrorDb(_))
      .WillOnce(DoAll(SetArgPointee<0>(-1), Return(true)));
  volume_.SetMicVolume(100);
  EXPECT_CALL(*agc_, Reset()).Times(1);
  CallProcess(1);
  EXPECT_EQ(100, volume_.GetMicVolume());

  // And finally verify the AGC continues working without a manual change.
  EXPECT_CALL(*agc_, GetRmsErrorDb(_))
      .WillOnce(DoAll(SetArgPointee<0>(-1), Return(true)));
  CallProcess(1);
  EXPECT_EQ(99, volume_.GetMicVolume());
}

TEST_F(AgcManagerDirectTest, RecoveryAfterManualLevelChangeFromMax) {
  FirstProcess();

  // Force the mic up to max volume. Takes a few steps due to the residual
  // gain limitation.
  EXPECT_CALL(*agc_, GetRmsErrorDb(_))
      .WillRepeatedly(DoAll(SetArgPointee<0>(30), Return(true)));
  CallProcess(1);
  EXPECT_EQ(183, volume_.GetMicVolume());
  CallProcess(1);
  EXPECT_EQ(243, volume_.GetMicVolume());
  CallProcess(1);
  EXPECT_EQ(255, volume_.GetMicVolume());

  // Manual change does not result in SetMicVolume call.
  EXPECT_CALL(*agc_, GetRmsErrorDb(_))
      .WillOnce(DoAll(SetArgPointee<0>(-1), Return(true)));
  volume_.SetMicVolume(50);
  EXPECT_CALL(*agc_, Reset()).Times(1);
  CallProcess(1);
  EXPECT_EQ(50, volume_.GetMicVolume());

  // Continues working as usual afterwards.
  EXPECT_CALL(*agc_, GetRmsErrorDb(_))
      .WillOnce(DoAll(SetArgPointee<0>(20), Return(true)));
  CallProcess(1);
  EXPECT_EQ(69, volume_.GetMicVolume());
}

TEST_F(AgcManagerDirectTest, RecoveryAfterManualLevelChangeBelowMin) {
  FirstProcess();

  // Manual change below min.
  EXPECT_CALL(*agc_, GetRmsErrorDb(_))
      .WillOnce(DoAll(SetArgPointee<0>(-1), Return(true)));
  // Don't set to zero, which will cause AGC to take no action.
  volume_.SetMicVolume(1);
  EXPECT_CALL(*agc_, Reset()).Times(1);
  CallProcess(1);
  EXPECT_EQ(1, volume_.GetMicVolume());

  // Continues working as usual afterwards.
  EXPECT_CALL(*agc_, GetRmsErrorDb(_))
      .WillOnce(DoAll(SetArgPointee<0>(11), Return(true)));
  CallProcess(1);
  EXPECT_EQ(2, volume_.GetMicVolume());

  EXPECT_CALL(*agc_, GetRmsErrorDb(_))
      .WillOnce(DoAll(SetArgPointee<0>(30), Return(true)));
  CallProcess(1);
  EXPECT_EQ(11, volume_.GetMicVolume());

  EXPECT_CALL(*agc_, GetRmsErrorDb(_))
      .WillOnce(DoAll(SetArgPointee<0>(20), Return(true)));
  CallProcess(1);
  EXPECT_EQ(18, volume_.GetMicVolume());
}

TEST_F(AgcManagerDirectTest, NoClippingHasNoImpact) {
  FirstProcess();

  EXPECT_CALL(*agc_, AnalyzePreproc(_, _)).WillRepeatedly(Return(0));
  CallPreProc(100);
  EXPECT_EQ(128, volume_.GetMicVolume());
}

TEST_F(AgcManagerDirectTest, ClippingUnderThresholdHasNoImpact) {
  FirstProcess();

  EXPECT_CALL(*agc_, AnalyzePreproc(_, _)).WillOnce(Return(0.099));
  CallPreProc(1);
  EXPECT_EQ(128, volume_.GetMicVolume());
}

TEST_F(AgcManagerDirectTest, ClippingLowersVolume) {
  SetVolumeAndProcess(255);

  EXPECT_CALL(*agc_, AnalyzePreproc(_, _)).WillOnce(Return(0.101));
  EXPECT_CALL(*agc_, Reset()).Times(1);
  CallPreProc(1);
  EXPECT_EQ(240, volume_.GetMicVolume());
}

TEST_F(AgcManagerDirectTest, WaitingPeriodBetweenClippingChecks) {
  SetVolumeAndProcess(255);

  EXPECT_CALL(*agc_, AnalyzePreproc(_, _))
      .WillOnce(Return(kAboveClippedThreshold));
  EXPECT_CALL(*agc_, Reset()).Times(1);
  CallPreProc(1);
  EXPECT_EQ(240, volume_.GetMicVolume());

  EXPECT_CALL(*agc_, AnalyzePreproc(_, _))
      .WillRepeatedly(Return(kAboveClippedThreshold));
  EXPECT_CALL(*agc_, Reset()).Times(0);
  CallPreProc(300);
  EXPECT_EQ(240, volume_.GetMicVolume());

  EXPECT_CALL(*agc_, AnalyzePreproc(_, _))
      .WillOnce(Return(kAboveClippedThreshold));
  EXPECT_CALL(*agc_, Reset()).Times(1);
  CallPreProc(1);
  EXPECT_EQ(225, volume_.GetMicVolume());
}

TEST_F(AgcManagerDirectTest, ClippingLoweringIsLimited) {
  SetVolumeAndProcess(180);

  EXPECT_CALL(*agc_, AnalyzePreproc(_, _))
      .WillOnce(Return(kAboveClippedThreshold));
  EXPECT_CALL(*agc_, Reset()).Times(1);
  CallPreProc(1);
  EXPECT_EQ(170, volume_.GetMicVolume());

  EXPECT_CALL(*agc_, AnalyzePreproc(_, _))
      .WillRepeatedly(Return(kAboveClippedThreshold));
  EXPECT_CALL(*agc_, Reset()).Times(0);
  CallPreProc(1000);
  EXPECT_EQ(170, volume_.GetMicVolume());
}

TEST_F(AgcManagerDirectTest, ClippingMaxIsRespectedWhenEqualToLevel) {
  SetVolumeAndProcess(255);

  EXPECT_CALL(*agc_, AnalyzePreproc(_, _))
      .WillOnce(Return(kAboveClippedThreshold));
  EXPECT_CALL(*agc_, Reset()).Times(1);
  CallPreProc(1);
  EXPECT_EQ(240, volume_.GetMicVolume());

  EXPECT_CALL(*agc_, GetRmsErrorDb(_))
      .WillRepeatedly(DoAll(SetArgPointee<0>(30), Return(true)));
  CallProcess(10);
  EXPECT_EQ(240, volume_.GetMicVolume());
}

TEST_F(AgcManagerDirectTest, ClippingMaxIsRespectedWhenHigherThanLevel) {
  SetVolumeAndProcess(200);

  EXPECT_CALL(*agc_, AnalyzePreproc(_, _))
      .WillOnce(Return(kAboveClippedThreshold));
  EXPECT_CALL(*agc_, Reset()).Times(1);
  CallPreProc(1);
  EXPECT_EQ(185, volume_.GetMicVolume());

  EXPECT_CALL(*agc_, GetRmsErrorDb(_))
      .WillRepeatedly(DoAll(SetArgPointee<0>(40), Return(true)));
  CallProcess(1);
  EXPECT_EQ(240, volume_.GetMicVolume());
  CallProcess(10);
  EXPECT_EQ(240, volume_.GetMicVolume());
}

TEST_F(AgcManagerDirectTest, MaxCompressionIsIncreasedAfterClipping) {
  SetVolumeAndProcess(210);

  EXPECT_CALL(*agc_, AnalyzePreproc(_, _))
      .WillOnce(Return(kAboveClippedThreshold));
  EXPECT_CALL(*agc_, Reset()).Times(1);
  CallPreProc(1);
  EXPECT_EQ(195, volume_.GetMicVolume());

  EXPECT_CALL(*agc_, GetRmsErrorDb(_))
      .WillOnce(DoAll(SetArgPointee<0>(11), Return(true)))
      .WillOnce(DoAll(SetArgPointee<0>(11), Return(true)))
      .WillOnce(DoAll(SetArgPointee<0>(11), Return(true)))
      .WillOnce(DoAll(SetArgPointee<0>(11), Return(true)))
      .WillOnce(DoAll(SetArgPointee<0>(11), Return(true)))
      .WillRepeatedly(Return(false));
  CallProcess(19);
  EXPECT_CALL(gctrl_, set_compression_gain_db(8)).WillOnce(Return(0));
  CallProcess(20);
  EXPECT_CALL(gctrl_, set_compression_gain_db(9)).WillOnce(Return(0));
  CallProcess(20);
  EXPECT_CALL(gctrl_, set_compression_gain_db(10)).WillOnce(Return(0));
  CallProcess(20);
  EXPECT_CALL(gctrl_, set_compression_gain_db(11)).WillOnce(Return(0));
  CallProcess(20);
  EXPECT_CALL(gctrl_, set_compression_gain_db(12)).WillOnce(Return(0));
  CallProcess(20);
  EXPECT_CALL(gctrl_, set_compression_gain_db(13)).WillOnce(Return(0));
  CallProcess(1);

  // Continue clipping until we hit the maximum surplus compression.
  CallPreProc(300);
  EXPECT_CALL(*agc_, AnalyzePreproc(_, _))
      .WillOnce(Return(kAboveClippedThreshold));
  EXPECT_CALL(*agc_, Reset()).Times(1);
  CallPreProc(1);
  EXPECT_EQ(180, volume_.GetMicVolume());

  CallPreProc(300);
  EXPECT_CALL(*agc_, AnalyzePreproc(_, _))
      .WillOnce(Return(kAboveClippedThreshold));
  EXPECT_CALL(*agc_, Reset()).Times(1);
  CallPreProc(1);
  EXPECT_EQ(170, volume_.GetMicVolume());

  // Current level is now at the minimum, but the maximum allowed level still
  // has more to decrease.
  CallPreProc(300);
  EXPECT_CALL(*agc_, AnalyzePreproc(_, _))
      .WillOnce(Return(kAboveClippedThreshold));
  CallPreProc(1);

  CallPreProc(300);
  EXPECT_CALL(*agc_, AnalyzePreproc(_, _))
      .WillOnce(Return(kAboveClippedThreshold));
  CallPreProc(1);

  CallPreProc(300);
  EXPECT_CALL(*agc_, AnalyzePreproc(_, _))
      .WillOnce(Return(kAboveClippedThreshold));
  CallPreProc(1);

  EXPECT_CALL(*agc_, GetRmsErrorDb(_))
      .WillOnce(DoAll(SetArgPointee<0>(16), Return(true)))
      .WillOnce(DoAll(SetArgPointee<0>(16), Return(true)))
      .WillOnce(DoAll(SetArgPointee<0>(16), Return(true)))
      .WillOnce(DoAll(SetArgPointee<0>(16), Return(true)))
      .WillRepeatedly(Return(false));
  CallProcess(19);
  EXPECT_CALL(gctrl_, set_compression_gain_db(14)).WillOnce(Return(0));
  CallProcess(20);
  EXPECT_CALL(gctrl_, set_compression_gain_db(15)).WillOnce(Return(0));
  CallProcess(20);
  EXPECT_CALL(gctrl_, set_compression_gain_db(16)).WillOnce(Return(0));
  CallProcess(20);
  EXPECT_CALL(gctrl_, set_compression_gain_db(17)).WillOnce(Return(0));
  CallProcess(20);
  EXPECT_CALL(gctrl_, set_compression_gain_db(18)).WillOnce(Return(0));
  CallProcess(1);
}

TEST_F(AgcManagerDirectTest, UserCanRaiseVolumeAfterClipping) {
  SetVolumeAndProcess(225);

  EXPECT_CALL(*agc_, AnalyzePreproc(_, _))
      .WillOnce(Return(kAboveClippedThreshold));
  EXPECT_CALL(*agc_, Reset()).Times(1);
  CallPreProc(1);
  EXPECT_EQ(210, volume_.GetMicVolume());

  // High enough error to trigger a volume check.
  EXPECT_CALL(*agc_, GetRmsErrorDb(_))
      .WillOnce(DoAll(SetArgPointee<0>(14), Return(true)));
  // User changed the volume.
  volume_.SetMicVolume(250);
  EXPECT_CALL(*agc_, Reset()).Times(1);
  CallProcess(1);
  EXPECT_EQ(250, volume_.GetMicVolume());

  // Move down...
  EXPECT_CALL(*agc_, GetRmsErrorDb(_))
      .WillOnce(DoAll(SetArgPointee<0>(-10), Return(true)));
  CallProcess(1);
  EXPECT_EQ(210, volume_.GetMicVolume());
  // And back up to the new max established by the user.
  EXPECT_CALL(*agc_, GetRmsErrorDb(_))
      .WillOnce(DoAll(SetArgPointee<0>(40), Return(true)));
  CallProcess(1);
  EXPECT_EQ(250, volume_.GetMicVolume());
  // Will not move above new maximum.
  EXPECT_CALL(*agc_, GetRmsErrorDb(_))
      .WillOnce(DoAll(SetArgPointee<0>(30), Return(true)));
  CallProcess(1);
  EXPECT_EQ(250, volume_.GetMicVolume());
}

TEST_F(AgcManagerDirectTest, ClippingDoesNotPullLowVolumeBackUp) {
  SetVolumeAndProcess(80);

  EXPECT_CALL(*agc_, AnalyzePreproc(_, _))
      .WillOnce(Return(kAboveClippedThreshold));
  EXPECT_CALL(*agc_, Reset()).Times(0);
  int initial_volume = volume_.GetMicVolume();
  CallPreProc(1);
  EXPECT_EQ(initial_volume, volume_.GetMicVolume());
}

TEST_F(AgcManagerDirectTest, TakesNoActionOnZeroMicVolume) {
  FirstProcess();

  EXPECT_CALL(*agc_, GetRmsErrorDb(_))
      .WillRepeatedly(DoAll(SetArgPointee<0>(30), Return(true)));
  volume_.SetMicVolume(0);
  CallProcess(10);
  EXPECT_EQ(0, volume_.GetMicVolume());
}

}  // namespace webrtc
