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
#include "webrtc/modules/audio_coding/codecs/opus/interface/opus_interface.h"
#include "webrtc/modules/audio_coding/codecs/opus/opus_inst.h"
#include "webrtc/test/testsupport/fileutils.h"

namespace webrtc {

// Number of samples in a 60 ms stereo frame, sampled at 48 kHz.
const int kOpusMaxFrameSamples = 48 * 60 * 2;
// Maximum number of bytes in output bitstream.
const size_t kMaxBytes = 1000;
// Number of samples-per-channel in a 20 ms frame, sampled at 48 kHz.
const int kOpus20msFrameSamples = 48 * 20;
// Number of samples-per-channel in a 10 ms frame, sampled at 48 kHz.
const int kOpus10msFrameSamples = 48 * 10;

class OpusTest : public ::testing::Test {
 protected:
  OpusTest();
  virtual void SetUp();

  void TestSetMaxBandwidth(opus_int32 expect, int32_t set);

  WebRtcOpusEncInst* opus_mono_encoder_;
  WebRtcOpusEncInst* opus_stereo_encoder_;
  WebRtcOpusDecInst* opus_mono_decoder_;
  WebRtcOpusDecInst* opus_mono_decoder_new_;
  WebRtcOpusDecInst* opus_stereo_decoder_;
  WebRtcOpusDecInst* opus_stereo_decoder_new_;

  int16_t speech_data_[kOpusMaxFrameSamples];
  int16_t output_data_[kOpusMaxFrameSamples];
  uint8_t bitstream_[kMaxBytes];
};

OpusTest::OpusTest()
    : opus_mono_encoder_(NULL),
      opus_stereo_encoder_(NULL),
      opus_mono_decoder_(NULL),
      opus_mono_decoder_new_(NULL),
      opus_stereo_decoder_(NULL),
      opus_stereo_decoder_new_(NULL) {
}

void OpusTest::SetUp() {
  FILE* input_file;
  const std::string file_name =
        webrtc::test::ResourcePath("audio_coding/speech_mono_32_48kHz", "pcm");
  input_file = fopen(file_name.c_str(), "rb");
  ASSERT_TRUE(input_file != NULL);
  ASSERT_EQ(kOpusMaxFrameSamples,
            static_cast<int32_t>(fread(speech_data_, sizeof(int16_t),
                                       kOpusMaxFrameSamples, input_file)));
  fclose(input_file);
  input_file = NULL;
}

void OpusTest::TestSetMaxBandwidth(opus_int32 expect, int32_t set) {
  opus_int32 bandwidth;
  // Test mono encoder.
  EXPECT_EQ(0, WebRtcOpus_SetMaxBandwidth(opus_mono_encoder_, set));
  opus_encoder_ctl(opus_mono_encoder_->encoder,
                   OPUS_GET_MAX_BANDWIDTH(&bandwidth));
  EXPECT_EQ(expect, bandwidth);
  // Test stereo encoder.
  EXPECT_EQ(0, WebRtcOpus_SetMaxBandwidth(opus_stereo_encoder_, set));
  opus_encoder_ctl(opus_stereo_encoder_->encoder,
                   OPUS_GET_MAX_BANDWIDTH(&bandwidth));
  EXPECT_EQ(expect, bandwidth);
}

// Test failing Create.
TEST_F(OpusTest, OpusCreateFail) {
  // Test to see that an invalid pointer is caught.
  EXPECT_EQ(-1, WebRtcOpus_EncoderCreate(NULL, 1));
  EXPECT_EQ(-1, WebRtcOpus_EncoderCreate(&opus_mono_encoder_, 3));
  EXPECT_EQ(-1, WebRtcOpus_DecoderCreate(NULL, 1));
  EXPECT_EQ(-1, WebRtcOpus_DecoderCreate(&opus_mono_decoder_, 3));
}

// Test failing Free.
TEST_F(OpusTest, OpusFreeFail) {
  // Test to see that an invalid pointer is caught.
  EXPECT_EQ(-1, WebRtcOpus_EncoderFree(NULL));
  EXPECT_EQ(-1, WebRtcOpus_DecoderFree(NULL));
}

// Test normal Create and Free.
TEST_F(OpusTest, OpusCreateFree) {
  EXPECT_EQ(0, WebRtcOpus_EncoderCreate(&opus_mono_encoder_, 1));
  EXPECT_EQ(0, WebRtcOpus_DecoderCreate(&opus_mono_decoder_, 1));
  EXPECT_EQ(0, WebRtcOpus_EncoderCreate(&opus_stereo_encoder_, 2));
  EXPECT_EQ(0, WebRtcOpus_DecoderCreate(&opus_stereo_decoder_, 2));
  EXPECT_TRUE(opus_mono_encoder_ != NULL);
  EXPECT_TRUE(opus_mono_decoder_ != NULL);
  EXPECT_TRUE(opus_stereo_encoder_ != NULL);
  EXPECT_TRUE(opus_stereo_decoder_ != NULL);
  // Free encoder and decoder memory.
  EXPECT_EQ(0, WebRtcOpus_EncoderFree(opus_mono_encoder_));
  EXPECT_EQ(0, WebRtcOpus_DecoderFree(opus_mono_decoder_));
  EXPECT_EQ(0, WebRtcOpus_EncoderFree(opus_stereo_encoder_));
  EXPECT_EQ(0, WebRtcOpus_DecoderFree(opus_stereo_decoder_));
}

TEST_F(OpusTest, OpusEncodeDecodeMono) {
  // Create encoder memory.
  EXPECT_EQ(0, WebRtcOpus_EncoderCreate(&opus_mono_encoder_, 1));
  EXPECT_EQ(0, WebRtcOpus_DecoderCreate(&opus_mono_decoder_, 1));
  EXPECT_EQ(0, WebRtcOpus_DecoderCreate(&opus_mono_decoder_new_, 1));

  // Set bitrate.
  EXPECT_EQ(0, WebRtcOpus_SetBitRate(opus_mono_encoder_, 32000));

  // Check number of channels for decoder.
  EXPECT_EQ(1, WebRtcOpus_DecoderChannels(opus_mono_decoder_));
  EXPECT_EQ(1, WebRtcOpus_DecoderChannels(opus_mono_decoder_new_));

  // Encode & decode.
  int16_t encoded_bytes;
  int16_t audio_type;
  int16_t output_data_decode_new[kOpusMaxFrameSamples];
  int16_t output_data_decode[kOpusMaxFrameSamples];
  int16_t* coded = reinterpret_cast<int16_t*>(bitstream_);
  encoded_bytes = WebRtcOpus_Encode(opus_mono_encoder_, speech_data_,
                                    kOpus20msFrameSamples, kMaxBytes,
                                    bitstream_);
  EXPECT_EQ(kOpus20msFrameSamples,
            WebRtcOpus_DecodeNew(opus_mono_decoder_new_, bitstream_,
                                 encoded_bytes, output_data_decode_new,
                                 &audio_type));
  EXPECT_EQ(kOpus20msFrameSamples,
            WebRtcOpus_Decode(opus_mono_decoder_, coded,
                              encoded_bytes, output_data_decode,
                              &audio_type));

  // Data in |output_data_decode_new| should be the same as in
  // |output_data_decode|.
  for (int i = 0; i < kOpus20msFrameSamples; i++) {
    EXPECT_EQ(output_data_decode_new[i], output_data_decode[i]);
  }

  // Free memory.
  EXPECT_EQ(0, WebRtcOpus_EncoderFree(opus_mono_encoder_));
  EXPECT_EQ(0, WebRtcOpus_DecoderFree(opus_mono_decoder_));
  EXPECT_EQ(0, WebRtcOpus_DecoderFree(opus_mono_decoder_new_));
}

TEST_F(OpusTest, OpusEncodeDecodeStereo) {
  // Create encoder memory.
  EXPECT_EQ(0, WebRtcOpus_EncoderCreate(&opus_stereo_encoder_, 2));
  EXPECT_EQ(0, WebRtcOpus_DecoderCreate(&opus_stereo_decoder_, 2));
  EXPECT_EQ(0, WebRtcOpus_DecoderCreate(&opus_stereo_decoder_new_, 2));

  // Set bitrate.
  EXPECT_EQ(0, WebRtcOpus_SetBitRate(opus_stereo_encoder_, 64000));

  // Check number of channels for decoder.
  EXPECT_EQ(2, WebRtcOpus_DecoderChannels(opus_stereo_decoder_));
  EXPECT_EQ(2, WebRtcOpus_DecoderChannels(opus_stereo_decoder_new_));

  // Encode & decode.
  int16_t encoded_bytes;
  int16_t audio_type;
  int16_t output_data_decode_new[kOpusMaxFrameSamples];
  int16_t output_data_decode[kOpusMaxFrameSamples];
  int16_t output_data_decode_slave[kOpusMaxFrameSamples];
  int16_t* coded = reinterpret_cast<int16_t*>(bitstream_);
  encoded_bytes = WebRtcOpus_Encode(opus_stereo_encoder_, speech_data_,
                                    kOpus20msFrameSamples, kMaxBytes,
                                    bitstream_);
  EXPECT_EQ(kOpus20msFrameSamples,
            WebRtcOpus_DecodeNew(opus_stereo_decoder_new_, bitstream_,
                                 encoded_bytes, output_data_decode_new,
                                 &audio_type));
  EXPECT_EQ(kOpus20msFrameSamples,
            WebRtcOpus_Decode(opus_stereo_decoder_, coded,
                              encoded_bytes, output_data_decode,
                              &audio_type));
  EXPECT_EQ(kOpus20msFrameSamples,
            WebRtcOpus_DecodeSlave(opus_stereo_decoder_, coded,
                                   encoded_bytes, output_data_decode_slave,
                                   &audio_type));

  // Data in |output_data_decode_new| should be the same as in
  // |output_data_decode| and |output_data_decode_slave| interleaved to a
  // stereo signal.
  for (int i = 0; i < kOpus20msFrameSamples; i++) {
    EXPECT_EQ(output_data_decode_new[i * 2], output_data_decode[i]);
    EXPECT_EQ(output_data_decode_new[i * 2 + 1], output_data_decode_slave[i]);
  }

  // Free memory.
  EXPECT_EQ(0, WebRtcOpus_EncoderFree(opus_stereo_encoder_));
  EXPECT_EQ(0, WebRtcOpus_DecoderFree(opus_stereo_decoder_));
  EXPECT_EQ(0, WebRtcOpus_DecoderFree(opus_stereo_decoder_new_));
}

TEST_F(OpusTest, OpusSetBitRate) {
  // Test without creating encoder memory.
  EXPECT_EQ(-1, WebRtcOpus_SetBitRate(opus_mono_encoder_, 60000));
  EXPECT_EQ(-1, WebRtcOpus_SetBitRate(opus_stereo_encoder_, 60000));

  // Create encoder memory, try with different bitrates.
  EXPECT_EQ(0, WebRtcOpus_EncoderCreate(&opus_mono_encoder_, 1));
  EXPECT_EQ(0, WebRtcOpus_EncoderCreate(&opus_stereo_encoder_, 2));
  EXPECT_EQ(0, WebRtcOpus_SetBitRate(opus_mono_encoder_, 30000));
  EXPECT_EQ(0, WebRtcOpus_SetBitRate(opus_stereo_encoder_, 60000));
  EXPECT_EQ(0, WebRtcOpus_SetBitRate(opus_mono_encoder_, 300000));
  EXPECT_EQ(0, WebRtcOpus_SetBitRate(opus_stereo_encoder_, 600000));

  // Free memory.
  EXPECT_EQ(0, WebRtcOpus_EncoderFree(opus_mono_encoder_));
  EXPECT_EQ(0, WebRtcOpus_EncoderFree(opus_stereo_encoder_));
}

TEST_F(OpusTest, OpusSetComplexity) {
  // Test without creating encoder memory.
  EXPECT_EQ(-1, WebRtcOpus_SetComplexity(opus_mono_encoder_, 9));
  EXPECT_EQ(-1, WebRtcOpus_SetComplexity(opus_stereo_encoder_, 9));

  // Create encoder memory, try with different complexities.
  EXPECT_EQ(0, WebRtcOpus_EncoderCreate(&opus_mono_encoder_, 1));
  EXPECT_EQ(0, WebRtcOpus_EncoderCreate(&opus_stereo_encoder_, 2));

  EXPECT_EQ(0, WebRtcOpus_SetComplexity(opus_mono_encoder_, 0));
  EXPECT_EQ(0, WebRtcOpus_SetComplexity(opus_stereo_encoder_, 0));
  EXPECT_EQ(0, WebRtcOpus_SetComplexity(opus_mono_encoder_, 10));
  EXPECT_EQ(0, WebRtcOpus_SetComplexity(opus_stereo_encoder_, 10));
  EXPECT_EQ(-1, WebRtcOpus_SetComplexity(opus_mono_encoder_, 11));
  EXPECT_EQ(-1, WebRtcOpus_SetComplexity(opus_stereo_encoder_, 11));

  // Free memory.
  EXPECT_EQ(0, WebRtcOpus_EncoderFree(opus_mono_encoder_));
  EXPECT_EQ(0, WebRtcOpus_EncoderFree(opus_stereo_encoder_));
}

// Encode and decode one frame (stereo), initialize the decoder and
// decode once more.
TEST_F(OpusTest, OpusDecodeInit) {
  // Create encoder memory.
  EXPECT_EQ(0, WebRtcOpus_EncoderCreate(&opus_stereo_encoder_, 2));
  EXPECT_EQ(0, WebRtcOpus_DecoderCreate(&opus_stereo_decoder_, 2));
  EXPECT_EQ(0, WebRtcOpus_DecoderCreate(&opus_stereo_decoder_new_, 2));

  // Encode & decode.
  int16_t encoded_bytes;
  int16_t audio_type;
  int16_t output_data_decode_new[kOpusMaxFrameSamples];
  int16_t output_data_decode[kOpusMaxFrameSamples];
  int16_t output_data_decode_slave[kOpusMaxFrameSamples];
  int16_t* coded = reinterpret_cast<int16_t*>(bitstream_);
  encoded_bytes = WebRtcOpus_Encode(opus_stereo_encoder_, speech_data_,
                                    kOpus20msFrameSamples, kMaxBytes,
                                    bitstream_);
  EXPECT_EQ(kOpus20msFrameSamples,
            WebRtcOpus_DecodeNew(opus_stereo_decoder_new_, bitstream_,
                                 encoded_bytes, output_data_decode_new,
                                 &audio_type));
  EXPECT_EQ(kOpus20msFrameSamples,
            WebRtcOpus_Decode(opus_stereo_decoder_, coded,
                              encoded_bytes, output_data_decode,
                              &audio_type));
  EXPECT_EQ(kOpus20msFrameSamples,
            WebRtcOpus_DecodeSlave(opus_stereo_decoder_, coded,
                                   encoded_bytes, output_data_decode_slave,
                                   &audio_type));

  // Data in |output_data_decode_new| should be the same as in
  // |output_data_decode| and |output_data_decode_slave| interleaved to a
  // stereo signal.
  for (int i = 0; i < kOpus20msFrameSamples; i++) {
    EXPECT_EQ(output_data_decode_new[i * 2], output_data_decode[i]);
    EXPECT_EQ(output_data_decode_new[i * 2 + 1], output_data_decode_slave[i]);
  }

  EXPECT_EQ(0, WebRtcOpus_DecoderInitNew(opus_stereo_decoder_new_));
  EXPECT_EQ(0, WebRtcOpus_DecoderInit(opus_stereo_decoder_));
  EXPECT_EQ(0, WebRtcOpus_DecoderInitSlave(opus_stereo_decoder_));

  EXPECT_EQ(kOpus20msFrameSamples,
            WebRtcOpus_DecodeNew(opus_stereo_decoder_new_, bitstream_,
                                 encoded_bytes, output_data_decode_new,
                                 &audio_type));
  EXPECT_EQ(kOpus20msFrameSamples,
            WebRtcOpus_Decode(opus_stereo_decoder_, coded,
                              encoded_bytes, output_data_decode,
                              &audio_type));
  EXPECT_EQ(kOpus20msFrameSamples,
            WebRtcOpus_DecodeSlave(opus_stereo_decoder_, coded,
                                   encoded_bytes, output_data_decode_slave,
                                   &audio_type));

  // Data in |output_data_decode_new| should be the same as in
  // |output_data_decode| and |output_data_decode_slave| interleaved to a
  // stereo signal.
  for (int i = 0; i < kOpus20msFrameSamples; i++) {
    EXPECT_EQ(output_data_decode_new[i * 2], output_data_decode[i]);
    EXPECT_EQ(output_data_decode_new[i * 2 + 1], output_data_decode_slave[i]);
  }

  // Free memory.
  EXPECT_EQ(0, WebRtcOpus_EncoderFree(opus_stereo_encoder_));
  EXPECT_EQ(0, WebRtcOpus_DecoderFree(opus_stereo_decoder_));
  EXPECT_EQ(0, WebRtcOpus_DecoderFree(opus_stereo_decoder_new_));
}

TEST_F(OpusTest, OpusEnableDisableFec) {
  // Test without creating encoder memory.
  EXPECT_EQ(-1, WebRtcOpus_EnableFec(opus_mono_encoder_));
  EXPECT_EQ(-1, WebRtcOpus_DisableFec(opus_stereo_encoder_));

  // Create encoder memory, try with different bitrates.
  EXPECT_EQ(0, WebRtcOpus_EncoderCreate(&opus_mono_encoder_, 1));
  EXPECT_EQ(0, WebRtcOpus_EncoderCreate(&opus_stereo_encoder_, 2));

  EXPECT_EQ(0, WebRtcOpus_EnableFec(opus_mono_encoder_));
  EXPECT_EQ(0, WebRtcOpus_EnableFec(opus_stereo_encoder_));
  EXPECT_EQ(0, WebRtcOpus_DisableFec(opus_mono_encoder_));
  EXPECT_EQ(0, WebRtcOpus_DisableFec(opus_stereo_encoder_));

  // Free memory.
  EXPECT_EQ(0, WebRtcOpus_EncoderFree(opus_mono_encoder_));
  EXPECT_EQ(0, WebRtcOpus_EncoderFree(opus_stereo_encoder_));
}

TEST_F(OpusTest, OpusSetPacketLossRate) {
  // Test without creating encoder memory.
  EXPECT_EQ(-1, WebRtcOpus_SetPacketLossRate(opus_mono_encoder_, 50));
  EXPECT_EQ(-1, WebRtcOpus_SetPacketLossRate(opus_stereo_encoder_, 50));

  // Create encoder memory, try with different bitrates.
  EXPECT_EQ(0, WebRtcOpus_EncoderCreate(&opus_mono_encoder_, 1));
  EXPECT_EQ(0, WebRtcOpus_EncoderCreate(&opus_stereo_encoder_, 2));

  EXPECT_EQ(0, WebRtcOpus_SetPacketLossRate(opus_mono_encoder_, 50));
  EXPECT_EQ(0, WebRtcOpus_SetPacketLossRate(opus_stereo_encoder_, 50));
  EXPECT_EQ(-1, WebRtcOpus_SetPacketLossRate(opus_mono_encoder_, -1));
  EXPECT_EQ(-1, WebRtcOpus_SetPacketLossRate(opus_stereo_encoder_, -1));
  EXPECT_EQ(-1, WebRtcOpus_SetPacketLossRate(opus_mono_encoder_, 101));
  EXPECT_EQ(-1, WebRtcOpus_SetPacketLossRate(opus_stereo_encoder_, 101));

  // Free memory.
  EXPECT_EQ(0, WebRtcOpus_EncoderFree(opus_mono_encoder_));
  EXPECT_EQ(0, WebRtcOpus_EncoderFree(opus_stereo_encoder_));
}

TEST_F(OpusTest, OpusSetMaxBandwidth) {
  // Test without creating encoder memory.
  EXPECT_EQ(-1, WebRtcOpus_SetMaxBandwidth(opus_mono_encoder_, 20000));
  EXPECT_EQ(-1, WebRtcOpus_SetMaxBandwidth(opus_stereo_encoder_, 20000));

  // Create encoder memory, try with different bitrates.
  EXPECT_EQ(0, WebRtcOpus_EncoderCreate(&opus_mono_encoder_, 1));
  EXPECT_EQ(0, WebRtcOpus_EncoderCreate(&opus_stereo_encoder_, 2));

  TestSetMaxBandwidth(OPUS_BANDWIDTH_FULLBAND, 24000);
  TestSetMaxBandwidth(OPUS_BANDWIDTH_FULLBAND, 14000);
  TestSetMaxBandwidth(OPUS_BANDWIDTH_SUPERWIDEBAND, 10000);
  TestSetMaxBandwidth(OPUS_BANDWIDTH_WIDEBAND, 7000);
  TestSetMaxBandwidth(OPUS_BANDWIDTH_MEDIUMBAND, 6000);
  TestSetMaxBandwidth(OPUS_BANDWIDTH_NARROWBAND, 4000);
  TestSetMaxBandwidth(OPUS_BANDWIDTH_NARROWBAND, 3000);

  // Free memory.
  EXPECT_EQ(0, WebRtcOpus_EncoderFree(opus_mono_encoder_));
  EXPECT_EQ(0, WebRtcOpus_EncoderFree(opus_stereo_encoder_));
}

// PLC in mono mode.
TEST_F(OpusTest, OpusDecodePlcMono) {
  // Create encoder memory.
  EXPECT_EQ(0, WebRtcOpus_EncoderCreate(&opus_mono_encoder_, 1));
  EXPECT_EQ(0, WebRtcOpus_DecoderCreate(&opus_mono_decoder_, 1));
  EXPECT_EQ(0, WebRtcOpus_DecoderCreate(&opus_mono_decoder_new_, 1));

  // Set bitrate.
  EXPECT_EQ(0, WebRtcOpus_SetBitRate(opus_mono_encoder_, 32000));

  // Check number of channels for decoder.
  EXPECT_EQ(1, WebRtcOpus_DecoderChannels(opus_mono_decoder_));
  EXPECT_EQ(1, WebRtcOpus_DecoderChannels(opus_mono_decoder_new_));

  // Encode & decode.
  int16_t encoded_bytes;
  int16_t audio_type;
  int16_t output_data_decode_new[kOpusMaxFrameSamples];
  int16_t output_data_decode[kOpusMaxFrameSamples];
  int16_t* coded = reinterpret_cast<int16_t*>(bitstream_);
  encoded_bytes = WebRtcOpus_Encode(opus_mono_encoder_, speech_data_,
                                    kOpus20msFrameSamples, kMaxBytes,
                                    bitstream_);
  EXPECT_EQ(kOpus20msFrameSamples,
            WebRtcOpus_DecodeNew(opus_mono_decoder_new_, bitstream_,
                                 encoded_bytes, output_data_decode_new,
                                 &audio_type));
  EXPECT_EQ(kOpus20msFrameSamples,
            WebRtcOpus_Decode(opus_mono_decoder_, coded,
                              encoded_bytes, output_data_decode,
                              &audio_type));

  // Call decoder PLC for both versions of the decoder.
  int16_t plc_buffer[kOpusMaxFrameSamples];
  int16_t plc_buffer_new[kOpusMaxFrameSamples];
  EXPECT_EQ(kOpus20msFrameSamples,
            WebRtcOpus_DecodePlcMaster(opus_mono_decoder_, plc_buffer, 1));
  EXPECT_EQ(kOpus20msFrameSamples,
            WebRtcOpus_DecodePlc(opus_mono_decoder_new_, plc_buffer_new, 1));

  // Data in |plc_buffer| should be the same as in |plc_buffer_new|.
  for (int i = 0; i < kOpus20msFrameSamples; i++) {
    EXPECT_EQ(plc_buffer[i], plc_buffer_new[i]);
  }

  // Free memory.
  EXPECT_EQ(0, WebRtcOpus_EncoderFree(opus_mono_encoder_));
  EXPECT_EQ(0, WebRtcOpus_DecoderFree(opus_mono_decoder_));
  EXPECT_EQ(0, WebRtcOpus_DecoderFree(opus_mono_decoder_new_));
}

// PLC in stereo mode.
TEST_F(OpusTest, OpusDecodePlcStereo) {
  // Create encoder memory.
  EXPECT_EQ(0, WebRtcOpus_EncoderCreate(&opus_stereo_encoder_, 2));
  EXPECT_EQ(0, WebRtcOpus_DecoderCreate(&opus_stereo_decoder_, 2));
  EXPECT_EQ(0, WebRtcOpus_DecoderCreate(&opus_stereo_decoder_new_, 2));

  // Set bitrate.
  EXPECT_EQ(0, WebRtcOpus_SetBitRate(opus_stereo_encoder_, 64000));

  // Check number of channels for decoder.
  EXPECT_EQ(2, WebRtcOpus_DecoderChannels(opus_stereo_decoder_));
  EXPECT_EQ(2, WebRtcOpus_DecoderChannels(opus_stereo_decoder_new_));

  // Encode & decode.
  int16_t encoded_bytes;
  int16_t audio_type;
  int16_t output_data_decode_new[kOpusMaxFrameSamples];
  int16_t output_data_decode[kOpusMaxFrameSamples];
  int16_t output_data_decode_slave[kOpusMaxFrameSamples];
  int16_t* coded = reinterpret_cast<int16_t*>(bitstream_);
  encoded_bytes = WebRtcOpus_Encode(opus_stereo_encoder_, speech_data_,
                                    kOpus20msFrameSamples, kMaxBytes,
                                    bitstream_);
  EXPECT_EQ(kOpus20msFrameSamples,
            WebRtcOpus_DecodeNew(opus_stereo_decoder_new_, bitstream_,
                                 encoded_bytes, output_data_decode_new,
                                 &audio_type));
  EXPECT_EQ(kOpus20msFrameSamples,
            WebRtcOpus_Decode(opus_stereo_decoder_, coded,
                              encoded_bytes, output_data_decode,
                              &audio_type));
  EXPECT_EQ(kOpus20msFrameSamples,
            WebRtcOpus_DecodeSlave(opus_stereo_decoder_, coded,
                                   encoded_bytes,
                                   output_data_decode_slave,
                                   &audio_type));

  // Call decoder PLC for both versions of the decoder.
  int16_t plc_buffer_left[kOpusMaxFrameSamples];
  int16_t plc_buffer_right[kOpusMaxFrameSamples];
  int16_t plc_buffer_new[kOpusMaxFrameSamples];
  EXPECT_EQ(kOpus20msFrameSamples,
            WebRtcOpus_DecodePlcMaster(opus_stereo_decoder_,
                                       plc_buffer_left, 1));
  EXPECT_EQ(kOpus20msFrameSamples,
            WebRtcOpus_DecodePlcSlave(opus_stereo_decoder_,
                                      plc_buffer_right, 1));
  EXPECT_EQ(kOpus20msFrameSamples,
            WebRtcOpus_DecodePlc(opus_stereo_decoder_new_, plc_buffer_new, 1));
  // Data in |plc_buffer_left| and |plc_buffer_right|should be the same as the
  // interleaved samples in |plc_buffer_new|.
  for (int i = 0, j = 0; i < kOpus20msFrameSamples; i++) {
    EXPECT_EQ(plc_buffer_left[i], plc_buffer_new[j++]);
    EXPECT_EQ(plc_buffer_right[i], plc_buffer_new[j++]);
  }

  // Free memory.
  EXPECT_EQ(0, WebRtcOpus_EncoderFree(opus_stereo_encoder_));
  EXPECT_EQ(0, WebRtcOpus_DecoderFree(opus_stereo_decoder_));
  EXPECT_EQ(0, WebRtcOpus_DecoderFree(opus_stereo_decoder_new_));
}

// Duration estimation.
TEST_F(OpusTest, OpusDurationEstimation) {
  // Create.
  EXPECT_EQ(0, WebRtcOpus_EncoderCreate(&opus_stereo_encoder_, 2));
  EXPECT_EQ(0, WebRtcOpus_DecoderCreate(&opus_stereo_decoder_, 2));

  int16_t encoded_bytes;

  // 10 ms.
  encoded_bytes = WebRtcOpus_Encode(opus_stereo_encoder_, speech_data_,
                                    kOpus10msFrameSamples, kMaxBytes,
                                    bitstream_);
  EXPECT_EQ(kOpus10msFrameSamples,
            WebRtcOpus_DurationEst(opus_stereo_decoder_, bitstream_,
                                   encoded_bytes));

  // 20 ms
  encoded_bytes = WebRtcOpus_Encode(opus_stereo_encoder_, speech_data_,
                                    kOpus20msFrameSamples, kMaxBytes,
                                    bitstream_);
  EXPECT_EQ(kOpus20msFrameSamples,
            WebRtcOpus_DurationEst(opus_stereo_decoder_, bitstream_,
                                   encoded_bytes));

  // Free memory.
  EXPECT_EQ(0, WebRtcOpus_EncoderFree(opus_stereo_encoder_));
  EXPECT_EQ(0, WebRtcOpus_DecoderFree(opus_stereo_decoder_));
}

}  // namespace webrtc
