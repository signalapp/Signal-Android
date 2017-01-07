/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_COMMON_AUDIO_WAV_HEADER_H_
#define WEBRTC_COMMON_AUDIO_WAV_HEADER_H_

#include <stddef.h>
#include <stdint.h>

namespace webrtc {

static const size_t kWavHeaderSize = 44;

class ReadableWav {
 public:
  // Returns the number of bytes read.
  size_t virtual Read(void* buf, size_t num_bytes) = 0;
  virtual ~ReadableWav() {}
};

enum WavFormat {
  kWavFormatPcm   = 1,  // PCM, each sample of size bytes_per_sample
  kWavFormatALaw  = 6,  // 8-bit ITU-T G.711 A-law
  kWavFormatMuLaw = 7,  // 8-bit ITU-T G.711 mu-law
};

// Return true if the given parameters will make a well-formed WAV header.
bool CheckWavParameters(size_t num_channels,
                        int sample_rate,
                        WavFormat format,
                        size_t bytes_per_sample,
                        size_t num_samples);

// Write a kWavHeaderSize bytes long WAV header to buf. The payload that
// follows the header is supposed to have the specified number of interleaved
// channels and contain the specified total number of samples of the specified
// type. CHECKs the input parameters for validity.
void WriteWavHeader(uint8_t* buf,
                    size_t num_channels,
                    int sample_rate,
                    WavFormat format,
                    size_t bytes_per_sample,
                    size_t num_samples);

// Read a WAV header from an implemented ReadableWav and parse the values into
// the provided output parameters. ReadableWav is used because the header can
// be variably sized. Returns false if the header is invalid.
bool ReadWavHeader(ReadableWav* readable,
                   size_t* num_channels,
                   int* sample_rate,
                   WavFormat* format,
                   size_t* bytes_per_sample,
                   size_t* num_samples);

}  // namespace webrtc

#endif  // WEBRTC_COMMON_AUDIO_WAV_HEADER_H_
