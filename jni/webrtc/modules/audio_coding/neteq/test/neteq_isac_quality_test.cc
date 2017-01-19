/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/modules/audio_coding/codecs/isac/fix/interface/isacfix.h"
#include "webrtc/modules/audio_coding/neteq/tools/neteq_quality_test.h"
#include "webrtc/test/testsupport/fileutils.h"

using google::RegisterFlagValidator;
using google::ParseCommandLineFlags;
using std::string;
using testing::InitGoogleTest;

namespace webrtc {
namespace test {

static const int kIsacBlockDurationMs = 30;
static const int kIsacInputSamplingKhz = 16;
static const int kIsacOutputSamplingKhz = 16;

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
              ResourcePath("audio_coding/speech_mono_16kHz", "pcm"),
              "Filename for input audio (should be 16 kHz sampled mono).");

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

DEFINE_string(out_filename, OutputPath() + "neteq4_isac_quality_test.pcm",
              "Name of output audio file.");

static const bool out_filename_dummy =
    RegisterFlagValidator(&FLAGS_out_filename, &ValidateOutFilename);

// Define switch for bir rate.
static bool ValidateBitRate(const char* flagname, int32_t value) {
  if (value >= 10 && value <= 32)
    return true;
  printf("Invalid bit rate, should be between 10 and 32 kbps.");
  return false;
}

DEFINE_int32(bit_rate_kbps, 32, "Target bit rate (kbps).");

static const bool bit_rate_dummy =
    RegisterFlagValidator(&FLAGS_bit_rate_kbps, &ValidateBitRate);

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

class NetEqIsacQualityTest : public NetEqQualityTest {
 protected:
  NetEqIsacQualityTest();
  virtual void SetUp() OVERRIDE;
  virtual void TearDown() OVERRIDE;
  virtual int EncodeBlock(int16_t* in_data, int block_size_samples,
                          uint8_t* payload, int max_bytes);
 private:
  ISACFIX_MainStruct* isac_encoder_;
  int bit_rate_kbps_;
};

NetEqIsacQualityTest::NetEqIsacQualityTest()
    : NetEqQualityTest(kIsacBlockDurationMs, kIsacInputSamplingKhz,
                       kIsacOutputSamplingKhz,
                       kDecoderISAC,
                       1,
                       FLAGS_in_filename,
                       FLAGS_out_filename),
      isac_encoder_(NULL),
      bit_rate_kbps_(FLAGS_bit_rate_kbps) {
}

void NetEqIsacQualityTest::SetUp() {
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
                                      int block_size_samples,
                                      uint8_t* payload, int max_bytes) {
  // ISAC takes 10 ms for every call.
  const int subblocks = kIsacBlockDurationMs / 10;
  const int subblock_length = 10 * kIsacInputSamplingKhz;
  int value = 0;

  int pointer = 0;
  for (int idx = 0; idx < subblocks; idx++, pointer += subblock_length) {
    // The Isac encoder does not perform encoding (and returns 0) until it
    // receives a sequence of sub-blocks that amount to the frame duration.
    EXPECT_EQ(0, value);
    value = WebRtcIsacfix_Encode(isac_encoder_, &in_data[pointer],
                                 reinterpret_cast<int16_t*>(payload));
  }
  EXPECT_GT(value, 0);
  return value;
}

TEST_F(NetEqIsacQualityTest, Test) {
  Simulate(FLAGS_runtime_ms);
}

}  // namespace test
}  // namespace webrtc
