/*
 *  Copyright (c) 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <utility>

#include "webrtc/base/checks.h"
#include "webrtc/modules/audio_processing/test/test_utils.h"

namespace webrtc {

RawFile::RawFile(const std::string& filename)
    : file_handle_(fopen(filename.c_str(), "wb")) {}

RawFile::~RawFile() {
  fclose(file_handle_);
}

void RawFile::WriteSamples(const int16_t* samples, size_t num_samples) {
#ifndef WEBRTC_ARCH_LITTLE_ENDIAN
#error "Need to convert samples to little-endian when writing to PCM file"
#endif
  fwrite(samples, sizeof(*samples), num_samples, file_handle_);
}

void RawFile::WriteSamples(const float* samples, size_t num_samples) {
  fwrite(samples, sizeof(*samples), num_samples, file_handle_);
}

ChannelBufferWavReader::ChannelBufferWavReader(std::unique_ptr<WavReader> file)
    : file_(std::move(file)) {}

bool ChannelBufferWavReader::Read(ChannelBuffer<float>* buffer) {
  RTC_CHECK_EQ(file_->num_channels(), buffer->num_channels());
  interleaved_.resize(buffer->size());
  if (file_->ReadSamples(interleaved_.size(), &interleaved_[0]) !=
      interleaved_.size()) {
    return false;
  }

  FloatS16ToFloat(&interleaved_[0], interleaved_.size(), &interleaved_[0]);
  Deinterleave(&interleaved_[0], buffer->num_frames(), buffer->num_channels(),
               buffer->channels());
  return true;
}

ChannelBufferWavWriter::ChannelBufferWavWriter(std::unique_ptr<WavWriter> file)
    : file_(std::move(file)) {}

void ChannelBufferWavWriter::Write(const ChannelBuffer<float>& buffer) {
  RTC_CHECK_EQ(file_->num_channels(), buffer.num_channels());
  interleaved_.resize(buffer.size());
  Interleave(buffer.channels(), buffer.num_frames(), buffer.num_channels(),
             &interleaved_[0]);
  FloatToFloatS16(&interleaved_[0], interleaved_.size(), &interleaved_[0]);
  file_->WriteSamples(&interleaved_[0], interleaved_.size());
}

void WriteIntData(const int16_t* data,
                  size_t length,
                  WavWriter* wav_file,
                  RawFile* raw_file) {
  if (wav_file) {
    wav_file->WriteSamples(data, length);
  }
  if (raw_file) {
    raw_file->WriteSamples(data, length);
  }
}

void WriteFloatData(const float* const* data,
                    size_t samples_per_channel,
                    size_t num_channels,
                    WavWriter* wav_file,
                    RawFile* raw_file) {
  size_t length = num_channels * samples_per_channel;
  std::unique_ptr<float[]> buffer(new float[length]);
  Interleave(data, samples_per_channel, num_channels, buffer.get());
  if (raw_file) {
    raw_file->WriteSamples(buffer.get(), length);
  }
  // TODO(aluebs): Use ScaleToInt16Range() from audio_util
  for (size_t i = 0; i < length; ++i) {
    buffer[i] = buffer[i] > 0 ?
                buffer[i] * std::numeric_limits<int16_t>::max() :
                -buffer[i] * std::numeric_limits<int16_t>::min();
  }
  if (wav_file) {
    wav_file->WriteSamples(buffer.get(), length);
  }
}

FILE* OpenFile(const std::string& filename, const char* mode) {
  FILE* file = fopen(filename.c_str(), mode);
  if (!file) {
    printf("Unable to open file %s\n", filename.c_str());
    exit(1);
  }
  return file;
}

size_t SamplesFromRate(int rate) {
  return static_cast<size_t>(AudioProcessing::kChunkSizeMs * rate / 1000);
}

void SetFrameSampleRate(AudioFrame* frame,
                        int sample_rate_hz) {
  frame->sample_rate_hz_ = sample_rate_hz;
  frame->samples_per_channel_ = AudioProcessing::kChunkSizeMs *
      sample_rate_hz / 1000;
}

AudioProcessing::ChannelLayout LayoutFromChannels(size_t num_channels) {
  switch (num_channels) {
    case 1:
      return AudioProcessing::kMono;
    case 2:
      return AudioProcessing::kStereo;
    default:
      RTC_CHECK(false);
      return AudioProcessing::kMono;
  }
}

std::vector<Point> ParseArrayGeometry(const std::string& mic_positions) {
  const std::vector<float> values = ParseList<float>(mic_positions);
  const size_t num_mics =
      rtc::CheckedDivExact(values.size(), static_cast<size_t>(3));
  RTC_CHECK_GT(num_mics, 0u) << "mic_positions is not large enough.";

  std::vector<Point> result;
  result.reserve(num_mics);
  for (size_t i = 0; i < values.size(); i += 3) {
    result.push_back(Point(values[i + 0], values[i + 1], values[i + 2]));
  }

  return result;
}

std::vector<Point> ParseArrayGeometry(const std::string& mic_positions,
                                      size_t num_mics) {
  std::vector<Point> result = ParseArrayGeometry(mic_positions);
  RTC_CHECK_EQ(result.size(), num_mics)
      << "Could not parse mic_positions or incorrect number of points.";
  return result;
}

}  // namespace webrtc
