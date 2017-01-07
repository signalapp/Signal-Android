/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/modules/audio_coding/codecs/isac/fix/include/isacfix.h"
#include "webrtc/modules/audio_coding/neteq/tools/neteq_quality_test.h"

using google::RegisterFlagValidator;
using google::ParseCommandLineFlags;
using std::string;
using testing::InitGoogleTest;

namespace webrtc {
namespace test {
namespace {
static const int kIsacBlockDurationMs = 30;
static const int kIsacInputSamplingKhz = 16;
static const int kIsacOutputSamplingKhz = 16;

// Define switch for bit rate.
static bool ValidateBitRate(const char* flagname, int32_t value) {
  if (value >= 10 && value <= 32)
    return true;
  printf("Invalid bit rate, should be between 10 and 32 kbps.");
  return false;
}

DEFINE_int32(bit_rate_kbps, 32, "Target bit rate (kbps).");

static const bool bit_rate_dummy =
    RegisterFlagValidator(&FLAGS_bit_rate_kbps, &ValidateBitRate);

}  // namespace

class NetEqIsacQualityTest : public NetEqQualityTest {
 protected:
  NetEqIsacQualityTest();
  void SetUp() override;
  void TearDown() override;
  int EncodeBlock(int16_t* in_data, size_t block_size_samples,
                  rtc::Buffer* payload, size_t max_bytes) override;
 private:
  ISACFIX_MainStruct* isac_encoder_;
  int bit_rate_kbps_;
};

NetEqIsacQualityTest::NetEqIsacQualityTest()
    : NetEqQualityTest(kIsacBlockDurationMs,
                       kIsacInputSamplingKhz,
                       kIsacOutputSamplingKhz,
                       NetEqDecoder::kDecoderISAC),
      isac_encoder_(NULL),
      bit_rate_kbps_(FLAGS_bit_rate_kbps) {}

void NetEqIsacQualityTest::SetUp() {
  ASSERT_EQ(1u, channels_) << "iSAC supports only mono audio.";
  // Create encoder memory.
  WebRtcIsacfix_Create(&isac_encoder_);
  ASSERT_TRUE(isac_encoder_ != NULL);
  EXPECT_EQ(0, WebRtcIsacfix_EncoderInit(isac_encoder_, 1));
  // Set bitrate and block length.
  EXPECT_EQ(0, WebRtcIsacfix_Control(isac_encoder_, bit_rate_kbps_ * 1000,
                                     kIsacBlockDurationMs));
  NetEqQualityTest::SetUp();
}

void NetEqIsacQualityTest::TearDown() {
  // Free memory.
  EXPECT_EQ(0, WebRtcIsacfix_Free(isac_encoder_));
  NetEqQualityTest::TearDown();
}

int NetEqIsacQualityTest::EncodeBlock(int16_t* in_data,
                                      size_t block_size_samples,
                                      rtc::Buffer* payload, size_t max_bytes) {
  // ISAC takes 10 ms for every call.
  const int subblocks = kIsacBlockDurationMs / 10;
  const int subblock_length = 10 * kIsacInputSamplingKhz;
  int value = 0;

  int pointer = 0;
  for (int idx = 0; idx < subblocks; idx++, pointer += subblock_length) {
    // The Isac encoder does not perform encoding (and returns 0) until it
    // receives a sequence of sub-blocks that amount to the frame duration.
    EXPECT_EQ(0, value);
    payload->AppendData(max_bytes, [&] (rtc::ArrayView<uint8_t> payload) {
        value = WebRtcIsacfix_Encode(isac_encoder_, &in_data[pointer],
                                     payload.data());
        return (value >= 0) ? static_cast<size_t>(value) : 0;
      });
  }
  EXPECT_GT(value, 0);
  return value;
}

TEST_F(NetEqIsacQualityTest, Test) {
  Simulate();
}

}  // namespace test
}  // namespace webrtc
