/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_MODULES_AUDIO_CODING_CODECS_TOOLS_AUDIO_CODEC_SPEED_TEST_H_
#define WEBRTC_MODULES_AUDIO_CODING_CODECS_TOOLS_AUDIO_CODEC_SPEED_TEST_H_

#include <string>
#include "testing/gtest/include/gtest/gtest.h"
#include "webrtc/system_wrappers/interface/scoped_ptr.h"
#include "webrtc/typedefs.h"

namespace webrtc {

// Define coding parameter as
// <channels, bit_rate, file_name, extension, if_save_output>.
typedef std::tr1::tuple<int, int, std::string, std::string, bool> coding_param;

class AudioCodecSpeedTest : public testing::TestWithParam<coding_param> {
 protected:
  AudioCodecSpeedTest(int block_duration_ms,
                      int input_sampling_khz,
                      int output_sampling_khz);
  virtual void SetUp();
  virtual void TearDown();

  // EncodeABlock(...) does the following:
  // 1. encodes a block of audio, saved in |in_data|,
  // 2. save the bit stream to |bit_stream| of |max_bytes| bytes in size,
  // 3. assign |encoded_bytes| with the length of the bit stream (in bytes),
  // 4. return the cost of time (in millisecond) spent on actual encoding.
  virtual float EncodeABlock(int16_t* in_data, uint8_t* bit_stream,
                             int max_bytes, int* encoded_bytes) = 0;

  // DecodeABlock(...) does the following:
  // 1. decodes the bit stream in |bit_stream| with a length of |encoded_bytes|
  // (in bytes),
  // 2. save the decoded audio in |out_data|,
  // 3. return the cost of time (in millisecond) spent on actual decoding.
  virtual float DecodeABlock(const uint8_t* bit_stream, int encoded_bytes,
                             int16_t* out_data) = 0;

  // Encoding and decode an audio of |audio_duration| (in seconds) and
  // record the runtime for encoding and decoding separately.
  void EncodeDecode(size_t audio_duration);

  int block_duration_ms_;
  int input_sampling_khz_;
  int output_sampling_khz_;

  // Number of samples-per-channel in a frame.
  int input_length_sample_;

  // Expected output number of samples-per-channel in a frame.
  int output_length_sample_;

  scoped_ptr<int16_t[]> in_data_;
  scoped_ptr<int16_t[]> out_data_;
  size_t data_pointer_;
  size_t loop_length_samples_;
  scoped_ptr<uint8_t[]> bit_stream_;

  // Maximum number of bytes in output bitstream for a frame of audio.
  int max_bytes_;

  int encoded_bytes_;
  float encoding_time_ms_;
  float decoding_time_ms_;
  FILE* out_file_;

  int channels_;

  // Bit rate is in bit-per-second.
  int bit_rate_;

  std::string in_filename_;

  // Determines whether to save the output to file.
  bool save_out_data_;
};

}  // namespace webrtc

#endif  // WEBRTC_MODULES_AUDIO_CODING_CODECS_TOOLS_AUDIO_CODEC_SPEED_TEST_H_
