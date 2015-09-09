/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include <string>

#include "testing/gtest/include/gtest/gtest.h"
#include "webrtc/modules/audio_coding/codecs/isac/main/interface/isac.h"
#include "webrtc/test/testsupport/fileutils.h"

struct WebRtcISACStruct;

namespace webrtc {

// Number of samples in a 60 ms, sampled at 32 kHz.
const int kIsacNumberOfSamples = 320 * 6;
// Maximum number of bytes in output bitstream.
const size_t kMaxBytes = 1000;

class IsacTest : public ::testing::Test {
 protected:
  IsacTest();
  virtual void SetUp();

  WebRtcISACStruct* isac_codec_;

  int16_t speech_data_[kIsacNumberOfSamples];
  int16_t output_data_[kIsacNumberOfSamples];
  int16_t bitstream_[kMaxBytes / 2];
  uint8_t bitstream_small_[7];  // Simulate sync packets.
};

IsacTest::IsacTest()
    : isac_codec_(NULL) {
}

void IsacTest::SetUp() {
  // Read some samples from a speech file, to be used in the encode test.
  FILE* input_file;
  const std::string file_name =
        webrtc::test::ResourcePath("audio_coding/testfile32kHz", "pcm");
  input_file = fopen(file_name.c_str(), "rb");
  ASSERT_TRUE(input_file != NULL);
  ASSERT_EQ(kIsacNumberOfSamples,
            static_cast<int32_t>(fread(speech_data_, sizeof(int16_t),
                                       kIsacNumberOfSamples, input_file)));
  fclose(input_file);
  input_file = NULL;
}

// Test failing Create.
TEST_F(IsacTest, IsacCreateFail) {
  // Test to see that an invalid pointer is caught.
  EXPECT_EQ(-1, WebRtcIsac_Create(NULL));
}

// Test failing Free.
TEST_F(IsacTest, IsacFreeFail) {
  // Test to see that free function doesn't crash.
  EXPECT_EQ(0, WebRtcIsac_Free(NULL));
}

// Test normal Create and Free.
TEST_F(IsacTest, IsacCreateFree) {
  EXPECT_EQ(0, WebRtcIsac_Create(&isac_codec_));
  EXPECT_TRUE(isac_codec_ != NULL);
  EXPECT_EQ(0, WebRtcIsac_Free(isac_codec_));}

TEST_F(IsacTest, IsacUpdateBWE) {
  // Create encoder memory.
  EXPECT_EQ(0, WebRtcIsac_Create(&isac_codec_));

  // Init encoder (adaptive mode) and decoder.
  WebRtcIsac_EncoderInit(isac_codec_, 0);
  WebRtcIsac_DecoderInit(isac_codec_);

  // Encode & decode.
  int16_t encoded_bytes;
  uint16_t* coded = reinterpret_cast<uint16_t*>(bitstream_);
  uint16_t* coded_small = reinterpret_cast<uint16_t*>(bitstream_small_);

  // Test with call with a small packet (sync packet).
  EXPECT_EQ(-1, WebRtcIsac_UpdateBwEstimate(isac_codec_, coded_small, 7, 1,
                                            12345, 56789));

  // Encode 60 ms of data (needed to create a first packet).
  encoded_bytes =  WebRtcIsac_Encode(isac_codec_, speech_data_, bitstream_);
  EXPECT_EQ(0, encoded_bytes);
  encoded_bytes =  WebRtcIsac_Encode(isac_codec_, speech_data_, bitstream_);
  EXPECT_EQ(0, encoded_bytes);
  encoded_bytes =  WebRtcIsac_Encode(isac_codec_, speech_data_, bitstream_);
  EXPECT_EQ(0, encoded_bytes);
  encoded_bytes =  WebRtcIsac_Encode(isac_codec_, speech_data_, bitstream_);
  EXPECT_EQ(0, encoded_bytes);
  encoded_bytes =  WebRtcIsac_Encode(isac_codec_, speech_data_, bitstream_);
  EXPECT_EQ(0, encoded_bytes);
  encoded_bytes =  WebRtcIsac_Encode(isac_codec_, speech_data_, bitstream_);

  // Call to update bandwidth estimator with real data.
  EXPECT_EQ(0, WebRtcIsac_UpdateBwEstimate(isac_codec_, coded, encoded_bytes, 1,
                                           12345, 56789));

  // Free memory.
  EXPECT_EQ(0, WebRtcIsac_Free(isac_codec_));
}

}  // namespace webrtc
