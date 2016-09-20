/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_MODULES_AUDIO_PROCESSING_TEST_TEST_UTILS_H_
#define WEBRTC_MODULES_AUDIO_PROCESSING_TEST_TEST_UTILS_H_

#include <math.h>
#include <iterator>
#include <limits>
#include <memory>
#include <string>
#include <vector>

#include "webrtc/base/constructormagic.h"
#include "webrtc/common_audio/channel_buffer.h"
#include "webrtc/common_audio/wav_file.h"
#include "webrtc/modules/audio_processing/include/audio_processing.h"
#include "webrtc/modules/include/module_common_types.h"

namespace webrtc {

static const AudioProcessing::Error kNoErr = AudioProcessing::kNoError;
#define EXPECT_NOERR(expr) EXPECT_EQ(kNoErr, (expr))

class RawFile final {
 public:
  explicit RawFile(const std::string& filename);
  ~RawFile();

  void WriteSamples(const int16_t* samples, size_t num_samples);
  void WriteSamples(const float* samples, size_t num_samples);

 private:
  FILE* file_handle_;

  RTC_DISALLOW_COPY_AND_ASSIGN(RawFile);
};

// Reads ChannelBuffers from a provided WavReader.
class ChannelBufferWavReader final {
 public:
  explicit ChannelBufferWavReader(std::unique_ptr<WavReader> file);

  // Reads data from the file according to the |buffer| format. Returns false if
  // a full buffer can't be read from the file.
  bool Read(ChannelBuffer<float>* buffer);

 private:
  std::unique_ptr<WavReader> file_;
  std::vector<float> interleaved_;

  RTC_DISALLOW_COPY_AND_ASSIGN(ChannelBufferWavReader);
};

// Writes ChannelBuffers to a provided WavWriter.
class ChannelBufferWavWriter final {
 public:
  explicit ChannelBufferWavWriter(std::unique_ptr<WavWriter> file);
  void Write(const ChannelBuffer<float>& buffer);

 private:
  std::unique_ptr<WavWriter> file_;
  std::vector<float> interleaved_;

  RTC_DISALLOW_COPY_AND_ASSIGN(ChannelBufferWavWriter);
};

void WriteIntData(const int16_t* data,
                  size_t length,
                  WavWriter* wav_file,
                  RawFile* raw_file);

void WriteFloatData(const float* const* data,
                    size_t samples_per_channel,
                    size_t num_channels,
                    WavWriter* wav_file,
                    RawFile* raw_file);

// Exits on failure; do not use in unit tests.
FILE* OpenFile(const std::string& filename, const char* mode);

size_t SamplesFromRate(int rate);

void SetFrameSampleRate(AudioFrame* frame,
                        int sample_rate_hz);

template <typename T>
void SetContainerFormat(int sample_rate_hz,
                        size_t num_channels,
                        AudioFrame* frame,
                        std::unique_ptr<ChannelBuffer<T> >* cb) {
  SetFrameSampleRate(frame, sample_rate_hz);
  frame->num_channels_ = num_channels;
  cb->reset(new ChannelBuffer<T>(frame->samples_per_channel_, num_channels));
}

AudioProcessing::ChannelLayout LayoutFromChannels(size_t num_channels);

template <typename T>
float ComputeSNR(const T* ref, const T* test, size_t length, float* variance) {
  float mse = 0;
  float mean = 0;
  *variance = 0;
  for (size_t i = 0; i < length; ++i) {
    T error = ref[i] - test[i];
    mse += error * error;
    *variance += ref[i] * ref[i];
    mean += ref[i];
  }
  mse /= length;
  *variance /= length;
  mean /= length;
  *variance -= mean * mean;

  float snr = 100;  // We assign 100 dB to the zero-error case.
  if (mse > 0)
    snr = 10 * log10(*variance / mse);
  return snr;
}

// Returns a vector<T> parsed from whitespace delimited values in to_parse,
// or an empty vector if the string could not be parsed.
template<typename T>
std::vector<T> ParseList(const std::string& to_parse) {
  std::vector<T> values;

  std::istringstream str(to_parse);
  std::copy(
      std::istream_iterator<T>(str),
      std::istream_iterator<T>(),
      std::back_inserter(values));

  return values;
}

// Parses the array geometry from the command line.
//
// If a vector with size != num_mics is returned, an error has occurred and an
// appropriate error message has been printed to stdout.
std::vector<Point> ParseArrayGeometry(const std::string& mic_positions,
                                      size_t num_mics);

// Same as above, but without the num_mics check for when it isn't available.
std::vector<Point> ParseArrayGeometry(const std::string& mic_positions);

}  // namespace webrtc

#endif  // WEBRTC_MODULES_AUDIO_PROCESSING_TEST_TEST_UTILS_H_
