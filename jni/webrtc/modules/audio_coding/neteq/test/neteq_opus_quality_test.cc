/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/modules/audio_coding/codecs/opus/opus_interface.h"
#include "webrtc/modules/audio_coding/codecs/opus/opus_inst.h"
#include "webrtc/modules/audio_coding/neteq/tools/neteq_quality_test.h"

using google::RegisterFlagValidator;
using google::ParseCommandLineFlags;
using std::string;
using testing::InitGoogleTest;

namespace webrtc {
namespace test {
namespace {

static const int kOpusBlockDurationMs = 20;
static const int kOpusSamplingKhz = 48;

// Define switch for bit rate.
static bool ValidateBitRate(const char* flagname, int32_t value) {
  if (value >= 6 && value <= 510)
    return true;
  printf("Invalid bit rate, should be between 6 and 510 kbps.");
  return false;
}

DEFINE_int32(bit_rate_kbps, 32, "Target bit rate (kbps).");

static const bool bit_rate_dummy =
    RegisterFlagValidator(&FLAGS_bit_rate_kbps, &ValidateBitRate);

// Define switch for complexity.
static bool ValidateComplexity(const char* flagname, int32_t value) {
  if (value >= -1 && value <= 10)
    return true;
  printf("Invalid complexity setting, should be between 0 and 10.");
  return false;
}

DEFINE_int32(complexity, 10, "Complexity: 0 ~ 10 -- defined as in Opus"
    "specification.");

static const bool complexity_dummy =
    RegisterFlagValidator(&FLAGS_complexity, &ValidateComplexity);

// Define switch for maxplaybackrate
DEFINE_int32(maxplaybackrate, 48000, "Maximum playback rate (Hz).");

// Define switch for application mode.
static bool ValidateApplication(const char* flagname, int32_t value) {
  if (value != 0 && value != 1) {
    printf("Invalid application mode, should be 0 or 1.");
    return false;
  }
  return true;
}

DEFINE_int32(application, 0, "Application mode: 0 -- VOIP, 1 -- Audio.");

static const bool application_dummy =
    RegisterFlagValidator(&FLAGS_application, &ValidateApplication);

// Define switch for reported packet loss rate.
static bool ValidatePacketLossRate(const char* flagname, int32_t value) {
  if (value >= 0 && value <= 100)
    return true;
  printf("Invalid packet loss percentile, should be between 0 and 100.");
  return false;
}

DEFINE_int32(reported_loss_rate, 10, "Reported percentile of packet loss.");

static const bool reported_loss_rate_dummy =
    RegisterFlagValidator(&FLAGS_reported_loss_rate, &ValidatePacketLossRate);

DEFINE_bool(fec, false, "Enable FEC for encoding (-nofec to disable).");

DEFINE_bool(dtx, false, "Enable DTX for encoding (-nodtx to disable).");

// Define switch for number of sub packets to repacketize.
static bool ValidateSubPackets(const char* flagname, int32_t value) {
  if (value >= 1 && value <= 3)
    return true;
  printf("Invalid number of sub packets, should be between 1 and 3.");
  return false;
}
DEFINE_int32(sub_packets, 1, "Number of sub packets to repacketize.");
static const bool sub_packets_dummy =
    RegisterFlagValidator(&FLAGS_sub_packets, &ValidateSubPackets);

}  // namepsace

class NetEqOpusQualityTest : public NetEqQualityTest {
 protected:
  NetEqOpusQualityTest();
  void SetUp() override;
  void TearDown() override;
  int EncodeBlock(int16_t* in_data, size_t block_size_samples,
                  rtc::Buffer* payload, size_t max_bytes) override;
 private:
  WebRtcOpusEncInst* opus_encoder_;
  OpusRepacketizer* repacketizer_;
  size_t sub_block_size_samples_;
  int bit_rate_kbps_;
  bool fec_;
  bool dtx_;
  int complexity_;
  int maxplaybackrate_;
  int target_loss_rate_;
  int sub_packets_;
  int application_;
};

NetEqOpusQualityTest::NetEqOpusQualityTest()
    : NetEqQualityTest(kOpusBlockDurationMs * FLAGS_sub_packets,
                       kOpusSamplingKhz,
                       kOpusSamplingKhz,
                       NetEqDecoder::kDecoderOpus),
      opus_encoder_(NULL),
      repacketizer_(NULL),
      sub_block_size_samples_(
          static_cast<size_t>(kOpusBlockDurationMs * kOpusSamplingKhz)),
      bit_rate_kbps_(FLAGS_bit_rate_kbps),
      fec_(FLAGS_fec),
      dtx_(FLAGS_dtx),
      complexity_(FLAGS_complexity),
      maxplaybackrate_(FLAGS_maxplaybackrate),
      target_loss_rate_(FLAGS_reported_loss_rate),
      sub_packets_(FLAGS_sub_packets) {
  // Redefine decoder type if input is stereo.
  if (channels_ > 1) {
    decoder_type_ = NetEqDecoder::kDecoderOpus_2ch;
  }
  application_ = FLAGS_application;
}

void NetEqOpusQualityTest::SetUp() {
  // Create encoder memory.
  WebRtcOpus_EncoderCreate(&opus_encoder_, channels_, application_);
  ASSERT_TRUE(opus_encoder_);

  // Create repacketizer.
  repacketizer_ = opus_repacketizer_create();
  ASSERT_TRUE(repacketizer_);

  // Set bitrate.
  EXPECT_EQ(0, WebRtcOpus_SetBitRate(opus_encoder_, bit_rate_kbps_ * 1000));
  if (fec_) {
    EXPECT_EQ(0, WebRtcOpus_EnableFec(opus_encoder_));
  }
  if (dtx_) {
    EXPECT_EQ(0, WebRtcOpus_EnableDtx(opus_encoder_));
  }
  EXPECT_EQ(0, WebRtcOpus_SetComplexity(opus_encoder_, complexity_));
  EXPECT_EQ(0, WebRtcOpus_SetMaxPlaybackRate(opus_encoder_, maxplaybackrate_));
  EXPECT_EQ(0, WebRtcOpus_SetPacketLossRate(opus_encoder_,
                                            target_loss_rate_));
  NetEqQualityTest::SetUp();
}

void NetEqOpusQualityTest::TearDown() {
  // Free memory.
  EXPECT_EQ(0, WebRtcOpus_EncoderFree(opus_encoder_));
  opus_repacketizer_destroy(repacketizer_);
  NetEqQualityTest::TearDown();
}

int NetEqOpusQualityTest::EncodeBlock(int16_t* in_data,
                                      size_t block_size_samples,
                                      rtc::Buffer* payload, size_t max_bytes) {
  EXPECT_EQ(block_size_samples, sub_block_size_samples_ * sub_packets_);
  int16_t* pointer = in_data;
  int value;
  opus_repacketizer_init(repacketizer_);
  for (int idx = 0; idx < sub_packets_; idx++) {
    payload->AppendData(max_bytes, [&] (rtc::ArrayView<uint8_t> payload) {
        value = WebRtcOpus_Encode(opus_encoder_,
                                  pointer, sub_block_size_samples_,
                                  max_bytes, payload.data());

        Log() << "Encoded a frame with Opus mode "
              << (value == 0 ? 0 : payload[0] >> 3)
              << std::endl;

        return (value >= 0) ? static_cast<size_t>(value) : 0;
      });

    if (OPUS_OK != opus_repacketizer_cat(repacketizer_,
                                         payload->data(), value)) {
      opus_repacketizer_init(repacketizer_);
      // If the repacketization fails, we discard this frame.
      return 0;
    }
    pointer += sub_block_size_samples_ * channels_;
  }
  value = opus_repacketizer_out(repacketizer_, payload->data(),
                                static_cast<opus_int32>(max_bytes));
  EXPECT_GE(value, 0);
  return value;
}

TEST_F(NetEqOpusQualityTest, Test) {
  Simulate();
}

}  // namespace test
}  // namespace webrtc
