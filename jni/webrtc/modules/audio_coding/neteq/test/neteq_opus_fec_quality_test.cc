/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/modules/audio_coding/codecs/opus/interface/opus_interface.h"
#include "webrtc/modules/audio_coding/neteq/tools/neteq_quality_test.h"
#include "webrtc/test/testsupport/fileutils.h"

using google::RegisterFlagValidator;
using google::ParseCommandLineFlags;
using std::string;
using testing::InitGoogleTest;

namespace webrtc {
namespace test {

static const int kOpusBlockDurationMs = 20;
static const int kOpusSamplingKhz = 48;

// Define switch for input file name.
static bool ValidateInFilename(const char* flagname, const string& value) {
  FILE* fid = fopen(value.c_str(), "rb");
  if (fid != NULL) {
    fclose(fid);
    return true;
  }
  printf("Invalid input filename.");
  return false;
}

DEFINE_string(in_filename,
              ResourcePath("audio_coding/speech_mono_32_48kHz", "pcm"),
              "Filename for input audio (should be 48 kHz sampled raw data).");

static const bool in_filename_dummy =
    RegisterFlagValidator(&FLAGS_in_filename, &ValidateInFilename);

// Define switch for output file name.
static bool ValidateOutFilename(const char* flagname, const string& value) {
  FILE* fid = fopen(value.c_str(), "wb");
  if (fid != NULL) {
    fclose(fid);
    return true;
  }
  printf("Invalid output filename.");
  return false;
}

DEFINE_string(out_filename, OutputPath() + "neteq4_opus_fec_quality_test.pcm",
              "Name of output audio file.");

static const bool out_filename_dummy =
    RegisterFlagValidator(&FLAGS_out_filename, &ValidateOutFilename);

// Define switch for channels.
static bool ValidateChannels(const char* flagname, int32_t value) {
  if (value == 1 || value == 2)
    return true;
  printf("Invalid number of channels, should be either 1 or 2.");
  return false;
}

DEFINE_int32(channels, 1, "Number of channels in input audio.");

static const bool channels_dummy =
    RegisterFlagValidator(&FLAGS_channels, &ValidateChannels);

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

// Define switch for runtime.
static bool ValidateRuntime(const char* flagname, int32_t value) {
  if (value > 0)
    return true;
  printf("Invalid runtime, should be greater than 0.");
  return false;
}

DEFINE_int32(runtime_ms, 10000, "Simulated runtime (milliseconds).");
static const bool runtime_dummy =
    RegisterFlagValidator(&FLAGS_runtime_ms, &ValidateRuntime);

DEFINE_bool(fec, true, "Whether to enable FEC for encoding.");

class NetEqOpusFecQualityTest : public NetEqQualityTest {
 protected:
  NetEqOpusFecQualityTest();
  virtual void SetUp() OVERRIDE;
  virtual void TearDown() OVERRIDE;
  virtual int EncodeBlock(int16_t* in_data, int block_size_samples,
                          uint8_t* payload, int max_bytes);
 private:
  WebRtcOpusEncInst* opus_encoder_;
  int channels_;
  int bit_rate_kbps_;
  bool fec_;
  int target_loss_rate_;
};

NetEqOpusFecQualityTest::NetEqOpusFecQualityTest()
    : NetEqQualityTest(kOpusBlockDurationMs, kOpusSamplingKhz,
                       kOpusSamplingKhz,
                       (FLAGS_channels == 1) ? kDecoderOpus : kDecoderOpus_2ch,
                       FLAGS_channels,
                       FLAGS_in_filename,
                       FLAGS_out_filename),
      opus_encoder_(NULL),
      channels_(FLAGS_channels),
      bit_rate_kbps_(FLAGS_bit_rate_kbps),
      fec_(FLAGS_fec),
      target_loss_rate_(FLAGS_reported_loss_rate) {
}

void NetEqOpusFecQualityTest::SetUp() {
  // Create encoder memory.
  WebRtcOpus_EncoderCreate(&opus_encoder_, channels_);
  ASSERT_TRUE(opus_encoder_ != NULL);
  // Set bitrate.
  EXPECT_EQ(0, WebRtcOpus_SetBitRate(opus_encoder_, bit_rate_kbps_ * 1000));
  if (fec_) {
    EXPECT_EQ(0, WebRtcOpus_EnableFec(opus_encoder_));
  }
  EXPECT_EQ(0, WebRtcOpus_SetPacketLossRate(opus_encoder_,
                                            target_loss_rate_));
  NetEqQualityTest::SetUp();
}

void NetEqOpusFecQualityTest::TearDown() {
  // Free memory.
  EXPECT_EQ(0, WebRtcOpus_EncoderFree(opus_encoder_));
  NetEqQualityTest::TearDown();
}

int NetEqOpusFecQualityTest::EncodeBlock(int16_t* in_data,
                                         int block_size_samples,
                                         uint8_t* payload, int max_bytes) {
  int value = WebRtcOpus_Encode(opus_encoder_, in_data,
                                block_size_samples, max_bytes,
                                payload);
  EXPECT_GT(value, 0);
  return value;
}

TEST_F(NetEqOpusFecQualityTest, Test) {
  Simulate(FLAGS_runtime_ms);
}

}  // namespace test
}  // namespace webrtc
