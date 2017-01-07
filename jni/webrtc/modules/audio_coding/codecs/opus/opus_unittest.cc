/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <memory>
#include <string>

#include "testing/gtest/include/gtest/gtest.h"
#include "webrtc/base/checks.h"
#include "webrtc/modules/audio_coding/codecs/opus/opus_interface.h"
#include "webrtc/modules/audio_coding/codecs/opus/opus_inst.h"
#include "webrtc/modules/audio_coding/neteq/tools/audio_loop.h"
#include "webrtc/test/testsupport/fileutils.h"

namespace webrtc {

using test::AudioLoop;
using ::testing::TestWithParam;
using ::testing::Values;
using ::testing::Combine;

// Maximum number of bytes in output bitstream.
const size_t kMaxBytes = 1000;
// Sample rate of Opus.
const size_t kOpusRateKhz = 48;
// Number of samples-per-channel in a 20 ms frame, sampled at 48 kHz.
const size_t kOpus20msFrameSamples = kOpusRateKhz * 20;
// Number of samples-per-channel in a 10 ms frame, sampled at 48 kHz.
const size_t kOpus10msFrameSamples = kOpusRateKhz * 10;

class OpusTest : public TestWithParam<::testing::tuple<int, int>> {
 protected:
  OpusTest();

  void TestDtxEffect(bool dtx, int block_length_ms);

  // Prepare |speech_data_| for encoding, read from a hard-coded file.
  // After preparation, |speech_data_.GetNextBlock()| returns a pointer to a
  // block of |block_length_ms| milliseconds. The data is looped every
  // |loop_length_ms| milliseconds.
  void PrepareSpeechData(size_t channel,
                         int block_length_ms,
                         int loop_length_ms);

  int EncodeDecode(WebRtcOpusEncInst* encoder,
                   rtc::ArrayView<const int16_t> input_audio,
                   WebRtcOpusDecInst* decoder,
                   int16_t* output_audio,
                   int16_t* audio_type);

  void SetMaxPlaybackRate(WebRtcOpusEncInst* encoder,
                          opus_int32 expect, int32_t set);

  void CheckAudioBounded(const int16_t* audio, size_t samples, size_t channels,
                         uint16_t bound) const;

  WebRtcOpusEncInst* opus_encoder_;
  WebRtcOpusDecInst* opus_decoder_;

  AudioLoop speech_data_;
  uint8_t bitstream_[kMaxBytes];
  size_t encoded_bytes_;
  size_t channels_;
  int application_;
};

OpusTest::OpusTest()
    : opus_encoder_(NULL),
      opus_decoder_(NULL),
      encoded_bytes_(0),
      channels_(static_cast<size_t>(::testing::get<0>(GetParam()))),
      application_(::testing::get<1>(GetParam())) {
}

void OpusTest::PrepareSpeechData(size_t channel, int block_length_ms,
                                 int loop_length_ms) {
  const std::string file_name =
        webrtc::test::ResourcePath((channel == 1) ?
            "audio_coding/testfile32kHz" :
            "audio_coding/teststereo32kHz", "pcm");
  if (loop_length_ms < block_length_ms) {
    loop_length_ms = block_length_ms;
  }
  EXPECT_TRUE(speech_data_.Init(file_name,
                                loop_length_ms * kOpusRateKhz * channel,
                                block_length_ms * kOpusRateKhz * channel));
}

void OpusTest::SetMaxPlaybackRate(WebRtcOpusEncInst* encoder,
                                  opus_int32 expect,
                                  int32_t set) {
  opus_int32 bandwidth;
  EXPECT_EQ(0, WebRtcOpus_SetMaxPlaybackRate(opus_encoder_, set));
  opus_encoder_ctl(opus_encoder_->encoder,
                   OPUS_GET_MAX_BANDWIDTH(&bandwidth));
  EXPECT_EQ(expect, bandwidth);
}

void OpusTest::CheckAudioBounded(const int16_t* audio, size_t samples,
                                 size_t channels, uint16_t bound) const {
  for (size_t i = 0; i < samples; ++i) {
    for (size_t c = 0; c < channels; ++c) {
      ASSERT_GE(audio[i * channels + c], -bound);
      ASSERT_LE(audio[i * channels + c], bound);
    }
  }
}

int OpusTest::EncodeDecode(WebRtcOpusEncInst* encoder,
                           rtc::ArrayView<const int16_t> input_audio,
                           WebRtcOpusDecInst* decoder,
                           int16_t* output_audio,
                           int16_t* audio_type) {
  int encoded_bytes_int = WebRtcOpus_Encode(
      encoder, input_audio.data(),
      rtc::CheckedDivExact(input_audio.size(), channels_),
      kMaxBytes, bitstream_);
  EXPECT_GE(encoded_bytes_int, 0);
  encoded_bytes_ = static_cast<size_t>(encoded_bytes_int);
  int est_len = WebRtcOpus_DurationEst(decoder, bitstream_, encoded_bytes_);
  int act_len = WebRtcOpus_Decode(decoder, bitstream_,
                                  encoded_bytes_, output_audio,
                                  audio_type);
  EXPECT_EQ(est_len, act_len);
  return act_len;
}

// Test if encoder/decoder can enter DTX mode properly and do not enter DTX when
// they should not. This test is signal dependent.
void OpusTest::TestDtxEffect(bool dtx, int block_length_ms) {
  PrepareSpeechData(channels_, block_length_ms, 2000);
  const size_t samples = kOpusRateKhz * block_length_ms;

  // Create encoder memory.
  EXPECT_EQ(0, WebRtcOpus_EncoderCreate(&opus_encoder_,
                                        channels_,
                                        application_));
  EXPECT_EQ(0, WebRtcOpus_DecoderCreate(&opus_decoder_, channels_));

  // Set bitrate.
  EXPECT_EQ(0, WebRtcOpus_SetBitRate(opus_encoder_,
                                     channels_ == 1 ? 32000 : 64000));

  // Set input audio as silence.
  std::vector<int16_t> silence(samples * channels_, 0);

  // Setting DTX.
  EXPECT_EQ(0, dtx ? WebRtcOpus_EnableDtx(opus_encoder_) :
      WebRtcOpus_DisableDtx(opus_encoder_));

  int16_t audio_type;
  int16_t* output_data_decode = new int16_t[samples * channels_];

  for (int i = 0; i < 100; ++i) {
    EXPECT_EQ(samples,
              static_cast<size_t>(EncodeDecode(
                  opus_encoder_, speech_data_.GetNextBlock(), opus_decoder_,
                  output_data_decode, &audio_type)));
    // If not DTX, it should never enter DTX mode. If DTX, we do not care since
    // whether it enters DTX depends on the signal type.
    if (!dtx) {
      EXPECT_GT(encoded_bytes_, 1U);
      EXPECT_EQ(0, opus_encoder_->in_dtx_mode);
      EXPECT_EQ(0, opus_decoder_->in_dtx_mode);
      EXPECT_EQ(0, audio_type);  // Speech.
    }
  }

  // We input some silent segments. In DTX mode, the encoder will stop sending.
  // However, DTX may happen after a while.
  for (int i = 0; i < 30; ++i) {
    EXPECT_EQ(samples,
              static_cast<size_t>(EncodeDecode(
                  opus_encoder_, silence, opus_decoder_, output_data_decode,
                  &audio_type)));
    if (!dtx) {
      EXPECT_GT(encoded_bytes_, 1U);
      EXPECT_EQ(0, opus_encoder_->in_dtx_mode);
      EXPECT_EQ(0, opus_decoder_->in_dtx_mode);
      EXPECT_EQ(0, audio_type);  // Speech.
    } else if (encoded_bytes_ == 1) {
      EXPECT_EQ(1, opus_encoder_->in_dtx_mode);
      EXPECT_EQ(1, opus_decoder_->in_dtx_mode);
      EXPECT_EQ(2, audio_type);  // Comfort noise.
      break;
    }
  }

  // When Opus is in DTX, it wakes up in a regular basis. It sends two packets,
  // one with an arbitrary size and the other of 1-byte, then stops sending for
  // a certain number of frames.

  // |max_dtx_frames| is the maximum number of frames Opus can stay in DTX.
  const int max_dtx_frames = 400 / block_length_ms + 1;

  // We run |kRunTimeMs| milliseconds of pure silence.
  const int kRunTimeMs = 4500;

  // We check that, after a |kCheckTimeMs| milliseconds (given that the CNG in
  // Opus needs time to adapt), the absolute values of DTX decoded signal are
  // bounded by |kOutputValueBound|.
  const int kCheckTimeMs = 4000;

#if defined(OPUS_FIXED_POINT)
  // Fixed-point Opus generates a random (comfort) noise, which has a less
  // predictable value bound than its floating-point Opus. This value depends on
  // input signal, and the time window for checking the output values (between
  // |kCheckTimeMs| and |kRunTimeMs|).
  const uint16_t kOutputValueBound = 30;

#else
  const uint16_t kOutputValueBound = 2;
#endif

  int time = 0;
  while (time < kRunTimeMs) {
    // DTX mode is maintained for maximum |max_dtx_frames| frames.
    int i = 0;
    for (; i < max_dtx_frames; ++i) {
      time += block_length_ms;
      EXPECT_EQ(samples,
                static_cast<size_t>(EncodeDecode(
                    opus_encoder_, silence, opus_decoder_, output_data_decode,
                    &audio_type)));
      if (dtx) {
        if (encoded_bytes_ > 1)
          break;
        EXPECT_EQ(0U, encoded_bytes_)  // Send 0 byte.
            << "Opus should have entered DTX mode.";
        EXPECT_EQ(1, opus_encoder_->in_dtx_mode);
        EXPECT_EQ(1, opus_decoder_->in_dtx_mode);
        EXPECT_EQ(2, audio_type);  // Comfort noise.
        if (time >= kCheckTimeMs) {
          CheckAudioBounded(output_data_decode, samples, channels_,
                            kOutputValueBound);
        }
      } else {
        EXPECT_GT(encoded_bytes_, 1U);
        EXPECT_EQ(0, opus_encoder_->in_dtx_mode);
        EXPECT_EQ(0, opus_decoder_->in_dtx_mode);
        EXPECT_EQ(0, audio_type);  // Speech.
      }
    }

    if (dtx) {
      // With DTX, Opus must stop transmission for some time.
      EXPECT_GT(i, 1);
    }

    // We expect a normal payload.
    EXPECT_EQ(0, opus_encoder_->in_dtx_mode);
    EXPECT_EQ(0, opus_decoder_->in_dtx_mode);
    EXPECT_EQ(0, audio_type);  // Speech.

    // Enters DTX again immediately.
    time += block_length_ms;
    EXPECT_EQ(samples,
              static_cast<size_t>(EncodeDecode(
                  opus_encoder_, silence, opus_decoder_, output_data_decode,
                  &audio_type)));
    if (dtx) {
      EXPECT_EQ(1U, encoded_bytes_);  // Send 1 byte.
      EXPECT_EQ(1, opus_encoder_->in_dtx_mode);
      EXPECT_EQ(1, opus_decoder_->in_dtx_mode);
      EXPECT_EQ(2, audio_type);  // Comfort noise.
      if (time >= kCheckTimeMs) {
        CheckAudioBounded(output_data_decode, samples, channels_,
                          kOutputValueBound);
      }
    } else {
      EXPECT_GT(encoded_bytes_, 1U);
      EXPECT_EQ(0, opus_encoder_->in_dtx_mode);
      EXPECT_EQ(0, opus_decoder_->in_dtx_mode);
      EXPECT_EQ(0, audio_type);  // Speech.
    }
  }

  silence[0] = 10000;
  if (dtx) {
    // Verify that encoder/decoder can jump out from DTX mode.
    EXPECT_EQ(samples,
              static_cast<size_t>(EncodeDecode(
                  opus_encoder_, silence, opus_decoder_, output_data_decode,
                  &audio_type)));
    EXPECT_GT(encoded_bytes_, 1U);
    EXPECT_EQ(0, opus_encoder_->in_dtx_mode);
    EXPECT_EQ(0, opus_decoder_->in_dtx_mode);
    EXPECT_EQ(0, audio_type);  // Speech.
  }

  // Free memory.
  delete[] output_data_decode;
  EXPECT_EQ(0, WebRtcOpus_EncoderFree(opus_encoder_));
  EXPECT_EQ(0, WebRtcOpus_DecoderFree(opus_decoder_));
}

// Test failing Create.
TEST(OpusTest, OpusCreateFail) {
  WebRtcOpusEncInst* opus_encoder;
  WebRtcOpusDecInst* opus_decoder;

  // Test to see that an invalid pointer is caught.
  EXPECT_EQ(-1, WebRtcOpus_EncoderCreate(NULL, 1, 0));
  // Invalid channel number.
  EXPECT_EQ(-1, WebRtcOpus_EncoderCreate(&opus_encoder, 3, 0));
  // Invalid applciation mode.
  EXPECT_EQ(-1, WebRtcOpus_EncoderCreate(&opus_encoder, 1, 2));

  EXPECT_EQ(-1, WebRtcOpus_DecoderCreate(NULL, 1));
  // Invalid channel number.
  EXPECT_EQ(-1, WebRtcOpus_DecoderCreate(&opus_decoder, 3));
}

// Test failing Free.
TEST(OpusTest, OpusFreeFail) {
  // Test to see that an invalid pointer is caught.
  EXPECT_EQ(-1, WebRtcOpus_EncoderFree(NULL));
  EXPECT_EQ(-1, WebRtcOpus_DecoderFree(NULL));
}

// Test normal Create and Free.
TEST_P(OpusTest, OpusCreateFree) {
  EXPECT_EQ(0, WebRtcOpus_EncoderCreate(&opus_encoder_,
                                        channels_,
                                        application_));
  EXPECT_EQ(0, WebRtcOpus_DecoderCreate(&opus_decoder_, channels_));
  EXPECT_TRUE(opus_encoder_ != NULL);
  EXPECT_TRUE(opus_decoder_ != NULL);
  // Free encoder and decoder memory.
  EXPECT_EQ(0, WebRtcOpus_EncoderFree(opus_encoder_));
  EXPECT_EQ(0, WebRtcOpus_DecoderFree(opus_decoder_));
}

TEST_P(OpusTest, OpusEncodeDecode) {
  PrepareSpeechData(channels_, 20, 20);

  // Create encoder memory.
  EXPECT_EQ(0, WebRtcOpus_EncoderCreate(&opus_encoder_,
                                        channels_,
                                        application_));
  EXPECT_EQ(0, WebRtcOpus_DecoderCreate(&opus_decoder_,
                                        channels_));

  // Set bitrate.
  EXPECT_EQ(0, WebRtcOpus_SetBitRate(opus_encoder_,
                                     channels_ == 1 ? 32000 : 64000));

  // Check number of channels for decoder.
  EXPECT_EQ(channels_, WebRtcOpus_DecoderChannels(opus_decoder_));

  // Check application mode.
  opus_int32 app;
  opus_encoder_ctl(opus_encoder_->encoder,
                   OPUS_GET_APPLICATION(&app));
  EXPECT_EQ(application_ == 0 ? OPUS_APPLICATION_VOIP : OPUS_APPLICATION_AUDIO,
            app);

  // Encode & decode.
  int16_t audio_type;
  int16_t* output_data_decode = new int16_t[kOpus20msFrameSamples * channels_];
  EXPECT_EQ(kOpus20msFrameSamples,
            static_cast<size_t>(
                EncodeDecode(opus_encoder_, speech_data_.GetNextBlock(),
                             opus_decoder_, output_data_decode, &audio_type)));

  // Free memory.
  delete[] output_data_decode;
  EXPECT_EQ(0, WebRtcOpus_EncoderFree(opus_encoder_));
  EXPECT_EQ(0, WebRtcOpus_DecoderFree(opus_decoder_));
}

TEST_P(OpusTest, OpusSetBitRate) {
  // Test without creating encoder memory.
  EXPECT_EQ(-1, WebRtcOpus_SetBitRate(opus_encoder_, 60000));

  // Create encoder memory, try with different bitrates.
  EXPECT_EQ(0, WebRtcOpus_EncoderCreate(&opus_encoder_,
                                        channels_,
                                        application_));
  EXPECT_EQ(0, WebRtcOpus_SetBitRate(opus_encoder_, 30000));
  EXPECT_EQ(0, WebRtcOpus_SetBitRate(opus_encoder_, 60000));
  EXPECT_EQ(0, WebRtcOpus_SetBitRate(opus_encoder_, 300000));
  EXPECT_EQ(0, WebRtcOpus_SetBitRate(opus_encoder_, 600000));

  // Free memory.
  EXPECT_EQ(0, WebRtcOpus_EncoderFree(opus_encoder_));
}

TEST_P(OpusTest, OpusSetComplexity) {
  // Test without creating encoder memory.
  EXPECT_EQ(-1, WebRtcOpus_SetComplexity(opus_encoder_, 9));

  // Create encoder memory, try with different complexities.
  EXPECT_EQ(0, WebRtcOpus_EncoderCreate(&opus_encoder_,
                                        channels_,
                                        application_));

  EXPECT_EQ(0, WebRtcOpus_SetComplexity(opus_encoder_, 0));
  EXPECT_EQ(0, WebRtcOpus_SetComplexity(opus_encoder_, 10));
  EXPECT_EQ(-1, WebRtcOpus_SetComplexity(opus_encoder_, 11));

  // Free memory.
  EXPECT_EQ(0, WebRtcOpus_EncoderFree(opus_encoder_));
}

// Encode and decode one frame, initialize the decoder and
// decode once more.
TEST_P(OpusTest, OpusDecodeInit) {
  PrepareSpeechData(channels_, 20, 20);

  // Create encoder memory.
  EXPECT_EQ(0, WebRtcOpus_EncoderCreate(&opus_encoder_,
                                        channels_,
                                        application_));
  EXPECT_EQ(0, WebRtcOpus_DecoderCreate(&opus_decoder_, channels_));

  // Encode & decode.
  int16_t audio_type;
  int16_t* output_data_decode = new int16_t[kOpus20msFrameSamples * channels_];
  EXPECT_EQ(kOpus20msFrameSamples,
            static_cast<size_t>(
                EncodeDecode(opus_encoder_, speech_data_.GetNextBlock(),
                             opus_decoder_, output_data_decode, &audio_type)));

  WebRtcOpus_DecoderInit(opus_decoder_);

  EXPECT_EQ(kOpus20msFrameSamples,
            static_cast<size_t>(WebRtcOpus_Decode(
                opus_decoder_, bitstream_, encoded_bytes_, output_data_decode,
                &audio_type)));

  // Free memory.
  delete[] output_data_decode;
  EXPECT_EQ(0, WebRtcOpus_EncoderFree(opus_encoder_));
  EXPECT_EQ(0, WebRtcOpus_DecoderFree(opus_decoder_));
}

TEST_P(OpusTest, OpusEnableDisableFec) {
  // Test without creating encoder memory.
  EXPECT_EQ(-1, WebRtcOpus_EnableFec(opus_encoder_));
  EXPECT_EQ(-1, WebRtcOpus_DisableFec(opus_encoder_));

  // Create encoder memory.
  EXPECT_EQ(0, WebRtcOpus_EncoderCreate(&opus_encoder_,
                                        channels_,
                                        application_));

  EXPECT_EQ(0, WebRtcOpus_EnableFec(opus_encoder_));
  EXPECT_EQ(0, WebRtcOpus_DisableFec(opus_encoder_));

  // Free memory.
  EXPECT_EQ(0, WebRtcOpus_EncoderFree(opus_encoder_));
}

TEST_P(OpusTest, OpusEnableDisableDtx) {
  // Test without creating encoder memory.
  EXPECT_EQ(-1, WebRtcOpus_EnableDtx(opus_encoder_));
  EXPECT_EQ(-1, WebRtcOpus_DisableDtx(opus_encoder_));

  // Create encoder memory.
  EXPECT_EQ(0, WebRtcOpus_EncoderCreate(&opus_encoder_,
                                        channels_,
                                        application_));

  opus_int32 dtx;

  // DTX is off by default.
  opus_encoder_ctl(opus_encoder_->encoder,
                   OPUS_GET_DTX(&dtx));
  EXPECT_EQ(0, dtx);

  // Test to enable DTX.
  EXPECT_EQ(0, WebRtcOpus_EnableDtx(opus_encoder_));
  opus_encoder_ctl(opus_encoder_->encoder,
                   OPUS_GET_DTX(&dtx));
  EXPECT_EQ(1, dtx);

  // Test to disable DTX.
  EXPECT_EQ(0, WebRtcOpus_DisableDtx(opus_encoder_));
  opus_encoder_ctl(opus_encoder_->encoder,
                   OPUS_GET_DTX(&dtx));
  EXPECT_EQ(0, dtx);


  // Free memory.
  EXPECT_EQ(0, WebRtcOpus_EncoderFree(opus_encoder_));
}

TEST_P(OpusTest, OpusDtxOff) {
  TestDtxEffect(false, 10);
  TestDtxEffect(false, 20);
  TestDtxEffect(false, 40);
}

TEST_P(OpusTest, OpusDtxOn) {
  TestDtxEffect(true, 10);
  TestDtxEffect(true, 20);
  TestDtxEffect(true, 40);
}

TEST_P(OpusTest, OpusSetPacketLossRate) {
  // Test without creating encoder memory.
  EXPECT_EQ(-1, WebRtcOpus_SetPacketLossRate(opus_encoder_, 50));

  // Create encoder memory.
  EXPECT_EQ(0, WebRtcOpus_EncoderCreate(&opus_encoder_,
                                        channels_,
                                        application_));

  EXPECT_EQ(0, WebRtcOpus_SetPacketLossRate(opus_encoder_, 50));
  EXPECT_EQ(-1, WebRtcOpus_SetPacketLossRate(opus_encoder_, -1));
  EXPECT_EQ(-1, WebRtcOpus_SetPacketLossRate(opus_encoder_, 101));

  // Free memory.
  EXPECT_EQ(0, WebRtcOpus_EncoderFree(opus_encoder_));
}

TEST_P(OpusTest, OpusSetMaxPlaybackRate) {
  // Test without creating encoder memory.
  EXPECT_EQ(-1, WebRtcOpus_SetMaxPlaybackRate(opus_encoder_, 20000));

  // Create encoder memory.
  EXPECT_EQ(0, WebRtcOpus_EncoderCreate(&opus_encoder_,
                                        channels_,
                                        application_));

  SetMaxPlaybackRate(opus_encoder_, OPUS_BANDWIDTH_FULLBAND, 48000);
  SetMaxPlaybackRate(opus_encoder_, OPUS_BANDWIDTH_FULLBAND, 24001);
  SetMaxPlaybackRate(opus_encoder_, OPUS_BANDWIDTH_SUPERWIDEBAND, 24000);
  SetMaxPlaybackRate(opus_encoder_, OPUS_BANDWIDTH_SUPERWIDEBAND, 16001);
  SetMaxPlaybackRate(opus_encoder_, OPUS_BANDWIDTH_WIDEBAND, 16000);
  SetMaxPlaybackRate(opus_encoder_, OPUS_BANDWIDTH_WIDEBAND, 12001);
  SetMaxPlaybackRate(opus_encoder_, OPUS_BANDWIDTH_MEDIUMBAND, 12000);
  SetMaxPlaybackRate(opus_encoder_, OPUS_BANDWIDTH_MEDIUMBAND, 8001);
  SetMaxPlaybackRate(opus_encoder_, OPUS_BANDWIDTH_NARROWBAND, 8000);
  SetMaxPlaybackRate(opus_encoder_, OPUS_BANDWIDTH_NARROWBAND, 4000);

  // Free memory.
  EXPECT_EQ(0, WebRtcOpus_EncoderFree(opus_encoder_));
}

// Test PLC.
TEST_P(OpusTest, OpusDecodePlc) {
  PrepareSpeechData(channels_, 20, 20);

  // Create encoder memory.
  EXPECT_EQ(0, WebRtcOpus_EncoderCreate(&opus_encoder_,
                                        channels_,
                                        application_));
  EXPECT_EQ(0, WebRtcOpus_DecoderCreate(&opus_decoder_, channels_));

  // Set bitrate.
  EXPECT_EQ(0, WebRtcOpus_SetBitRate(opus_encoder_,
                                     channels_== 1 ? 32000 : 64000));

  // Check number of channels for decoder.
  EXPECT_EQ(channels_, WebRtcOpus_DecoderChannels(opus_decoder_));

  // Encode & decode.
  int16_t audio_type;
  int16_t* output_data_decode = new int16_t[kOpus20msFrameSamples * channels_];
  EXPECT_EQ(kOpus20msFrameSamples,
            static_cast<size_t>(
                EncodeDecode(opus_encoder_, speech_data_.GetNextBlock(),
                             opus_decoder_, output_data_decode, &audio_type)));

  // Call decoder PLC.
  int16_t* plc_buffer = new int16_t[kOpus20msFrameSamples * channels_];
  EXPECT_EQ(kOpus20msFrameSamples,
            static_cast<size_t>(WebRtcOpus_DecodePlc(
                opus_decoder_, plc_buffer, 1)));

  // Free memory.
  delete[] plc_buffer;
  delete[] output_data_decode;
  EXPECT_EQ(0, WebRtcOpus_EncoderFree(opus_encoder_));
  EXPECT_EQ(0, WebRtcOpus_DecoderFree(opus_decoder_));
}

// Duration estimation.
TEST_P(OpusTest, OpusDurationEstimation) {
  PrepareSpeechData(channels_, 20, 20);

  // Create.
  EXPECT_EQ(0, WebRtcOpus_EncoderCreate(&opus_encoder_,
                                        channels_,
                                        application_));
  EXPECT_EQ(0, WebRtcOpus_DecoderCreate(&opus_decoder_, channels_));

  // 10 ms. We use only first 10 ms of a 20 ms block.
  auto speech_block = speech_data_.GetNextBlock();
  int encoded_bytes_int = WebRtcOpus_Encode(
      opus_encoder_, speech_block.data(),
      rtc::CheckedDivExact(speech_block.size(), 2 * channels_),
      kMaxBytes, bitstream_);
  EXPECT_GE(encoded_bytes_int, 0);
  EXPECT_EQ(kOpus10msFrameSamples,
            static_cast<size_t>(WebRtcOpus_DurationEst(
                opus_decoder_, bitstream_,
                static_cast<size_t>(encoded_bytes_int))));

  // 20 ms
  speech_block = speech_data_.GetNextBlock();
  encoded_bytes_int = WebRtcOpus_Encode(
      opus_encoder_, speech_block.data(),
      rtc::CheckedDivExact(speech_block.size(), channels_),
      kMaxBytes, bitstream_);
  EXPECT_GE(encoded_bytes_int, 0);
  EXPECT_EQ(kOpus20msFrameSamples,
            static_cast<size_t>(WebRtcOpus_DurationEst(
                opus_decoder_, bitstream_,
                static_cast<size_t>(encoded_bytes_int))));

  // Free memory.
  EXPECT_EQ(0, WebRtcOpus_EncoderFree(opus_encoder_));
  EXPECT_EQ(0, WebRtcOpus_DecoderFree(opus_decoder_));
}

TEST_P(OpusTest, OpusDecodeRepacketized) {
  const int kPackets = 6;

  PrepareSpeechData(channels_, 20, 20 * kPackets);

  // Create encoder memory.
  ASSERT_EQ(0, WebRtcOpus_EncoderCreate(&opus_encoder_,
                                        channels_,
                                        application_));
  ASSERT_EQ(0, WebRtcOpus_DecoderCreate(&opus_decoder_,
                                        channels_));

  // Set bitrate.
  EXPECT_EQ(0, WebRtcOpus_SetBitRate(opus_encoder_,
                                     channels_ == 1 ? 32000 : 64000));

  // Check number of channels for decoder.
  EXPECT_EQ(channels_, WebRtcOpus_DecoderChannels(opus_decoder_));

  // Encode & decode.
  int16_t audio_type;
  std::unique_ptr<int16_t[]> output_data_decode(
      new int16_t[kPackets * kOpus20msFrameSamples * channels_]);
  OpusRepacketizer* rp = opus_repacketizer_create();

  for (int idx = 0; idx < kPackets; idx++) {
    auto speech_block = speech_data_.GetNextBlock();
    encoded_bytes_ =
        WebRtcOpus_Encode(opus_encoder_, speech_block.data(),
                          rtc::CheckedDivExact(speech_block.size(), channels_),
                          kMaxBytes, bitstream_);
    EXPECT_EQ(OPUS_OK, opus_repacketizer_cat(rp, bitstream_, encoded_bytes_));
  }

  encoded_bytes_ = opus_repacketizer_out(rp, bitstream_, kMaxBytes);

  EXPECT_EQ(kOpus20msFrameSamples * kPackets,
            static_cast<size_t>(WebRtcOpus_DurationEst(
                opus_decoder_, bitstream_, encoded_bytes_)));

  EXPECT_EQ(kOpus20msFrameSamples * kPackets,
            static_cast<size_t>(WebRtcOpus_Decode(
                opus_decoder_, bitstream_, encoded_bytes_,
                output_data_decode.get(), &audio_type)));

  // Free memory.
  opus_repacketizer_destroy(rp);
  EXPECT_EQ(0, WebRtcOpus_EncoderFree(opus_encoder_));
  EXPECT_EQ(0, WebRtcOpus_DecoderFree(opus_decoder_));
}

INSTANTIATE_TEST_CASE_P(VariousMode,
                        OpusTest,
                        Combine(Values(1, 2), Values(0, 1)));


}  // namespace webrtc
