/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_COMMON_AUDIO_WAV_WRITER_H_
#define WEBRTC_COMMON_AUDIO_WAV_WRITER_H_

#ifdef __cplusplus

#include <stdint.h>
#include <cstddef>
#include <string>

namespace webrtc {

// Simple C++ class for writing 16-bit PCM WAV files. All error handling is
// by calls to FATAL_ERROR(), making it unsuitable for anything but debug code.
class WavFile {
 public:
  // Open a new WAV file for writing.
  WavFile(const std::string& filename, int sample_rate, int num_channels);

  // Close the WAV file, after writing its header.
  ~WavFile();

  // Write additional samples to the file. Each sample is in the range
  // [-32768,32767], and there must be the previously specified number of
  // interleaved channels.
  void WriteSamples(const float* samples, size_t num_samples);

  int sample_rate() const { return sample_rate_; }
  int num_channels() const { return num_channels_; }
  uint32_t num_samples() const { return num_samples_; }

 private:
  void WriteSamples(const int16_t* samples, size_t num_samples);
  void Close();
  const int sample_rate_;
  const int num_channels_;
  uint32_t num_samples_;  // total number of samples written to file
  FILE* file_handle_;  // output file, owned by this class
};

}  // namespace webrtc

extern "C" {
#endif  // __cplusplus

// C wrappers for the WavFile class.
typedef struct rtc_WavFile rtc_WavFile;
rtc_WavFile* rtc_WavOpen(const char* filename,
                         int sample_rate,
                         int num_channels);
void rtc_WavClose(rtc_WavFile* wf);
void rtc_WavWriteSamples(rtc_WavFile* wf,
                         const float* samples,
                         size_t num_samples);
int rtc_WavSampleRate(const rtc_WavFile* wf);
int rtc_WavNumChannels(const rtc_WavFile* wf);
uint32_t rtc_WavNumSamples(const rtc_WavFile* wf);

#ifdef __cplusplus
}  // extern "C"
#endif

#endif  // WEBRTC_COMMON_AUDIO_WAV_WRITER_H_
