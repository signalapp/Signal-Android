/*
 *  Copyright (c) 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <memory>

#include "testing/gtest/include/gtest/gtest.h"
#include "webrtc/base/checks.h"
#include "webrtc/common_types.h"
#include "webrtc/modules/audio_coding/codecs/opus/audio_encoder_opus.h"

namespace webrtc {

namespace {
const CodecInst kOpusSettings = {105, "opus", 48000, 960, 1, 32000};
}  // namespace

class AudioEncoderOpusTest : public ::testing::Test {
 protected:
  void CreateCodec(int num_channels) {
    codec_inst_.channels = num_channels;
    encoder_.reset(new AudioEncoderOpus(codec_inst_));
    auto expected_app =
        num_channels == 1 ? AudioEncoderOpus::kVoip : AudioEncoderOpus::kAudio;
    EXPECT_EQ(expected_app, encoder_->application());
  }

  CodecInst codec_inst_ = kOpusSettings;
  std::unique_ptr<AudioEncoderOpus> encoder_;
};

TEST_F(AudioEncoderOpusTest, DefaultApplicationModeMono) {
  CreateCodec(1);
}

TEST_F(AudioEncoderOpusTest, DefaultApplicationModeStereo) {
  CreateCodec(2);
}

TEST_F(AudioEncoderOpusTest, ChangeApplicationMode) {
  CreateCodec(2);
  EXPECT_TRUE(encoder_->SetApplication(AudioEncoder::Application::kSpeech));
  EXPECT_EQ(AudioEncoderOpus::kVoip, encoder_->application());
}

TEST_F(AudioEncoderOpusTest, ResetWontChangeApplicationMode) {
  CreateCodec(2);

  // Trigger a reset.
  encoder_->Reset();
  // Verify that the mode is still kAudio.
  EXPECT_EQ(AudioEncoderOpus::kAudio, encoder_->application());

  // Now change to kVoip.
  EXPECT_TRUE(encoder_->SetApplication(AudioEncoder::Application::kSpeech));
  EXPECT_EQ(AudioEncoderOpus::kVoip, encoder_->application());

  // Trigger a reset again.
  encoder_->Reset();
  // Verify that the mode is still kVoip.
  EXPECT_EQ(AudioEncoderOpus::kVoip, encoder_->application());
}

TEST_F(AudioEncoderOpusTest, ToggleDtx) {
  CreateCodec(2);
  // Enable DTX
  EXPECT_TRUE(encoder_->SetDtx(true));
  // Verify that the mode is still kAudio.
  EXPECT_EQ(AudioEncoderOpus::kAudio, encoder_->application());
  // Turn off DTX.
  EXPECT_TRUE(encoder_->SetDtx(false));
}

TEST_F(AudioEncoderOpusTest, SetBitrate) {
  CreateCodec(1);
  // Constants are replicated from audio_encoder_opus.cc.
  const int kMinBitrateBps = 500;
  const int kMaxBitrateBps = 512000;
  // Set a too low bitrate.
  encoder_->SetTargetBitrate(kMinBitrateBps - 1);
  EXPECT_EQ(kMinBitrateBps, encoder_->GetTargetBitrate());
  // Set a too high bitrate.
  encoder_->SetTargetBitrate(kMaxBitrateBps + 1);
  EXPECT_EQ(kMaxBitrateBps, encoder_->GetTargetBitrate());
  // Set the minimum rate.
  encoder_->SetTargetBitrate(kMinBitrateBps);
  EXPECT_EQ(kMinBitrateBps, encoder_->GetTargetBitrate());
  // Set the maximum rate.
  encoder_->SetTargetBitrate(kMaxBitrateBps);
  EXPECT_EQ(kMaxBitrateBps, encoder_->GetTargetBitrate());
  // Set rates from 1000 up to 32000 bps.
  for (int rate = 1000; rate <= 32000; rate += 1000) {
    encoder_->SetTargetBitrate(rate);
    EXPECT_EQ(rate, encoder_->GetTargetBitrate());
  }
}

namespace {

// Returns a vector with the n evenly-spaced numbers a, a + (b - a)/(n - 1),
// ..., b.
std::vector<double> IntervalSteps(double a, double b, size_t n) {
  RTC_DCHECK_GT(n, 1u);
  const double step = (b - a) / (n - 1);
  std::vector<double> points;
  for (size_t i = 0; i < n; ++i)
    points.push_back(a + i * step);
  return points;
}

// Sets the packet loss rate to each number in the vector in turn, and verifies
// that the loss rate as reported by the encoder is |expected_return| for all
// of them.
void TestSetPacketLossRate(AudioEncoderOpus* encoder,
                           const std::vector<double>& losses,
                           double expected_return) {
  for (double loss : losses) {
    encoder->SetProjectedPacketLossRate(loss);
    EXPECT_DOUBLE_EQ(expected_return, encoder->packet_loss_rate());
  }
}

}  // namespace

TEST_F(AudioEncoderOpusTest, PacketLossRateOptimized) {
  CreateCodec(1);
  auto I = [](double a, double b) { return IntervalSteps(a, b, 10); };
  const double eps = 1e-15;

  // Note that the order of the following calls is critical.

  // clang-format off
  TestSetPacketLossRate(encoder_.get(), I(0.00      , 0.01 - eps), 0.00);
  TestSetPacketLossRate(encoder_.get(), I(0.01 + eps, 0.06 - eps), 0.01);
  TestSetPacketLossRate(encoder_.get(), I(0.06 + eps, 0.11 - eps), 0.05);
  TestSetPacketLossRate(encoder_.get(), I(0.11 + eps, 0.22 - eps), 0.10);
  TestSetPacketLossRate(encoder_.get(), I(0.22 + eps, 1.00      ), 0.20);

  TestSetPacketLossRate(encoder_.get(), I(1.00      , 0.18 + eps), 0.20);
  TestSetPacketLossRate(encoder_.get(), I(0.18 - eps, 0.09 + eps), 0.10);
  TestSetPacketLossRate(encoder_.get(), I(0.09 - eps, 0.04 + eps), 0.05);
  TestSetPacketLossRate(encoder_.get(), I(0.04 - eps, 0.01 + eps), 0.01);
  TestSetPacketLossRate(encoder_.get(), I(0.01 - eps, 0.00      ), 0.00);
  // clang-format on
}

}  // namespace webrtc
