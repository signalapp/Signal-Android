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

#include "webrtc/base/checks.h"
#include "webrtc/base/safe_conversions.h"
#include "webrtc/modules/audio_coding/codecs/g711/audio_encoder_pcm.h"
#include "webrtc/modules/audio_coding/neteq/tools/neteq_quality_test.h"
#include "webrtc/test/testsupport/fileutils.h"

using google::RegisterFlagValidator;
using google::ParseCommandLineFlags;
using std::string;
using testing::InitGoogleTest;

namespace webrtc {
namespace test {
namespace {
static const int kInputSampleRateKhz = 8;
static const int kOutputSampleRateKhz = 8;

// Define switch for frame size.
static bool ValidateFrameSize(const char* flagname, int32_t value) {
  if (value >= 10 && value <= 60 && (value % 10) == 0)
    return true;
  printf("Invalid frame size, should be 10, 20, ..., 60 ms.");
  return false;
}

DEFINE_int32(frame_size_ms, 20, "Codec frame size (milliseconds).");

static const bool frame_size_dummy =
    RegisterFlagValidator(&FLAGS_frame_size_ms, &ValidateFrameSize);

}  // namespace

class NetEqPcmuQualityTest : public NetEqQualityTest {
 protected:
  NetEqPcmuQualityTest()
      : NetEqQualityTest(FLAGS_frame_size_ms,
                         kInputSampleRateKhz,
                         kOutputSampleRateKhz,
                         NetEqDecoder::kDecoderPCMu) {}

  void SetUp() override {
    ASSERT_EQ(1u, channels_) << "PCMu supports only mono audio.";
    AudioEncoderPcmU::Config config;
    config.frame_size_ms = FLAGS_frame_size_ms;
    encoder_.reset(new AudioEncoderPcmU(config));
    NetEqQualityTest::SetUp();
  }

  int EncodeBlock(int16_t* in_data,
                  size_t block_size_samples,
                  rtc::Buffer* payload, size_t max_bytes) override {
    const size_t kFrameSizeSamples = 80;  // Samples per 10 ms.
    size_t encoded_samples = 0;
    uint32_t dummy_timestamp = 0;
    AudioEncoder::EncodedInfo info;
    do {
      info = encoder_->Encode(dummy_timestamp,
                              rtc::ArrayView<const int16_t>(
                                  in_data + encoded_samples, kFrameSizeSamples),
                              payload);
      encoded_samples += kFrameSizeSamples;
    } while (info.encoded_bytes == 0);
    return rtc::checked_cast<int>(info.encoded_bytes);
  }

 private:
  std::unique_ptr<AudioEncoderPcmU> encoder_;
};

TEST_F(NetEqPcmuQualityTest, Test) {
  Simulate();
}

}  // namespace test
}  // namespace webrtc
