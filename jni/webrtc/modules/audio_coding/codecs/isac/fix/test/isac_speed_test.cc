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
#include "webrtc/modules/audio_coding/codecs/isac/fix/source/settings.h"
#include "webrtc/modules/audio_coding/codecs/tools/audio_codec_speed_test.h"

using ::std::string;

namespace webrtc {

static const int kIsacBlockDurationMs = 30;
static const int kIsacInputSamplingKhz = 16;
static const int kIsacOutputSamplingKhz = 16;

class IsacSpeedTest : public AudioCodecSpeedTest {
 protected:
  IsacSpeedTest();
  void SetUp() override;
  void TearDown() override;
  float EncodeABlock(int16_t* in_data, uint8_t* bit_stream,
                     size_t max_bytes, size_t* encoded_bytes) override;
  float DecodeABlock(const uint8_t* bit_stream, size_t encoded_bytes,
                     int16_t* out_data) override;
  ISACFIX_MainStruct *ISACFIX_main_inst_;
};

IsacSpeedTest::IsacSpeedTest()
    : AudioCodecSpeedTest(kIsacBlockDurationMs,
                          kIsacInputSamplingKhz,
                          kIsacOutputSamplingKhz),
      ISACFIX_main_inst_(NULL) {
}

void IsacSpeedTest::SetUp() {
  AudioCodecSpeedTest::SetUp();

  // Check whether the allocated buffer for the bit stream is large enough.
  EXPECT_GE(max_bytes_, static_cast<size_t>(STREAM_MAXW16_60MS));

  // Create encoder memory.
  EXPECT_EQ(0, WebRtcIsacfix_Create(&ISACFIX_main_inst_));
  EXPECT_EQ(0, WebRtcIsacfix_EncoderInit(ISACFIX_main_inst_, 1));
  WebRtcIsacfix_DecoderInit(ISACFIX_main_inst_);
  // Set bitrate and block length.
  EXPECT_EQ(0, WebRtcIsacfix_Control(ISACFIX_main_inst_, bit_rate_,
                                     block_duration_ms_));
}

void IsacSpeedTest::TearDown() {
  AudioCodecSpeedTest::TearDown();
  // Free memory.
  EXPECT_EQ(0, WebRtcIsacfix_Free(ISACFIX_main_inst_));
}

float IsacSpeedTest::EncodeABlock(int16_t* in_data, uint8_t* bit_stream,
                                  size_t max_bytes, size_t* encoded_bytes) {
  // ISAC takes 10 ms everycall
  const int subblocks = block_duration_ms_ / 10;
  const int subblock_length = 10 * input_sampling_khz_;
  int value = 0;

  clock_t clocks = clock();
  size_t pointer = 0;
  for (int idx = 0; idx < subblocks; idx++, pointer += subblock_length) {
    value = WebRtcIsacfix_Encode(ISACFIX_main_inst_, &in_data[pointer],
                                 bit_stream);
    if (idx == subblocks - 1)
      EXPECT_GT(value, 0);
    else
      EXPECT_EQ(0, value);
  }
  clocks = clock() - clocks;
  *encoded_bytes = static_cast<size_t>(value);
  assert(*encoded_bytes <= max_bytes);
  return 1000.0 * clocks / CLOCKS_PER_SEC;
}

float IsacSpeedTest::DecodeABlock(const uint8_t* bit_stream,
                                  size_t encoded_bytes,
                                  int16_t* out_data) {
  int value;
  int16_t audio_type;
  clock_t clocks = clock();
  value = WebRtcIsacfix_Decode(ISACFIX_main_inst_, bit_stream, encoded_bytes,
                               out_data, &audio_type);
  clocks = clock() - clocks;
  EXPECT_EQ(output_length_sample_, static_cast<size_t>(value));
  return 1000.0 * clocks / CLOCKS_PER_SEC;
}

TEST_P(IsacSpeedTest, IsacEncodeDecodeTest) {
  size_t kDurationSec = 400;  // Test audio length in second.
  EncodeDecode(kDurationSec);
}

const coding_param param_set[] =
    {::std::tr1::make_tuple(1, 32000, string("audio_coding/speech_mono_16kHz"),
                            string("pcm"), true)};

INSTANTIATE_TEST_CASE_P(AllTest, IsacSpeedTest,
                        ::testing::ValuesIn(param_set));

}  // namespace webrtc
