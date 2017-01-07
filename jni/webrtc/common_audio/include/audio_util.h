/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_COMMON_AUDIO_INCLUDE_AUDIO_UTIL_H_
#define WEBRTC_COMMON_AUDIO_INCLUDE_AUDIO_UTIL_H_

#include <limits>
#include <cstring>

#include "webrtc/base/checks.h"
#include "webrtc/typedefs.h"

namespace webrtc {

typedef std::numeric_limits<int16_t> limits_int16;

// The conversion functions use the following naming convention:
// S16:      int16_t [-32768, 32767]
// Float:    float   [-1.0, 1.0]
// FloatS16: float   [-32768.0, 32767.0]
static inline int16_t FloatToS16(float v) {
  if (v > 0)
    return v >= 1 ? limits_int16::max()
                  : static_cast<int16_t>(v * limits_int16::max() + 0.5f);
  return v <= -1 ? limits_int16::min()
                 : static_cast<int16_t>(-v * limits_int16::min() - 0.5f);
}

static inline float S16ToFloat(int16_t v) {
  static const float kMaxInt16Inverse = 1.f / limits_int16::max();
  static const float kMinInt16Inverse = 1.f / limits_int16::min();
  return v * (v > 0 ? kMaxInt16Inverse : -kMinInt16Inverse);
}

static inline int16_t FloatS16ToS16(float v) {
  static const float kMaxRound = limits_int16::max() - 0.5f;
  static const float kMinRound = limits_int16::min() + 0.5f;
  if (v > 0)
    return v >= kMaxRound ? limits_int16::max()
                          : static_cast<int16_t>(v + 0.5f);
  return v <= kMinRound ? limits_int16::min() : static_cast<int16_t>(v - 0.5f);
}

static inline float FloatToFloatS16(float v) {
  return v * (v > 0 ? limits_int16::max() : -limits_int16::min());
}

static inline float FloatS16ToFloat(float v) {
  static const float kMaxInt16Inverse = 1.f / limits_int16::max();
  static const float kMinInt16Inverse = 1.f / limits_int16::min();
  return v * (v > 0 ? kMaxInt16Inverse : -kMinInt16Inverse);
}

void FloatToS16(const float* src, size_t size, int16_t* dest);
void S16ToFloat(const int16_t* src, size_t size, float* dest);
void FloatS16ToS16(const float* src, size_t size, int16_t* dest);
void FloatToFloatS16(const float* src, size_t size, float* dest);
void FloatS16ToFloat(const float* src, size_t size, float* dest);

// Copy audio from |src| channels to |dest| channels unless |src| and |dest|
// point to the same address. |src| and |dest| must have the same number of
// channels, and there must be sufficient space allocated in |dest|.
template <typename T>
void CopyAudioIfNeeded(const T* const* src,
                       int num_frames,
                       int num_channels,
                       T* const* dest) {
  for (int i = 0; i < num_channels; ++i) {
    if (src[i] != dest[i]) {
      std::copy(src[i], src[i] + num_frames, dest[i]);
    }
  }
}

// Deinterleave audio from |interleaved| to the channel buffers pointed to
// by |deinterleaved|. There must be sufficient space allocated in the
// |deinterleaved| buffers (|num_channel| buffers with |samples_per_channel|
// per buffer).
template <typename T>
void Deinterleave(const T* interleaved,
                  size_t samples_per_channel,
                  size_t num_channels,
                  T* const* deinterleaved) {
  for (size_t i = 0; i < num_channels; ++i) {
    T* channel = deinterleaved[i];
    size_t interleaved_idx = i;
    for (size_t j = 0; j < samples_per_channel; ++j) {
      channel[j] = interleaved[interleaved_idx];
      interleaved_idx += num_channels;
    }
  }
}

// Interleave audio from the channel buffers pointed to by |deinterleaved| to
// |interleaved|. There must be sufficient space allocated in |interleaved|
// (|samples_per_channel| * |num_channels|).
template <typename T>
void Interleave(const T* const* deinterleaved,
                size_t samples_per_channel,
                size_t num_channels,
                T* interleaved) {
  for (size_t i = 0; i < num_channels; ++i) {
    const T* channel = deinterleaved[i];
    size_t interleaved_idx = i;
    for (size_t j = 0; j < samples_per_channel; ++j) {
      interleaved[interleaved_idx] = channel[j];
      interleaved_idx += num_channels;
    }
  }
}

// Copies audio from a single channel buffer pointed to by |mono| to each
// channel of |interleaved|. There must be sufficient space allocated in
// |interleaved| (|samples_per_channel| * |num_channels|).
template <typename T>
void UpmixMonoToInterleaved(const T* mono,
                            int num_frames,
                            int num_channels,
                            T* interleaved) {
  int interleaved_idx = 0;
  for (int i = 0; i < num_frames; ++i) {
    for (int j = 0; j < num_channels; ++j) {
      interleaved[interleaved_idx++] = mono[i];
    }
  }
}

template <typename T, typename Intermediate>
void DownmixToMono(const T* const* input_channels,
                   size_t num_frames,
                   int num_channels,
                   T* out) {
  for (size_t i = 0; i < num_frames; ++i) {
    Intermediate value = input_channels[0][i];
    for (int j = 1; j < num_channels; ++j) {
      value += input_channels[j][i];
    }
    out[i] = value / num_channels;
  }
}

// Downmixes an interleaved multichannel signal to a single channel by averaging
// all channels.
template <typename T, typename Intermediate>
void DownmixInterleavedToMonoImpl(const T* interleaved,
                                  size_t num_frames,
                                  int num_channels,
                                  T* deinterleaved) {
  RTC_DCHECK_GT(num_channels, 0);
  RTC_DCHECK_GT(num_frames, 0u);

  const T* const end = interleaved + num_frames * num_channels;

  while (interleaved < end) {
    const T* const frame_end = interleaved + num_channels;

    Intermediate value = *interleaved++;
    while (interleaved < frame_end) {
      value += *interleaved++;
    }

    *deinterleaved++ = value / num_channels;
  }
}

template <typename T>
void DownmixInterleavedToMono(const T* interleaved,
                              size_t num_frames,
                              int num_channels,
                              T* deinterleaved);

template <>
void DownmixInterleavedToMono<int16_t>(const int16_t* interleaved,
                                       size_t num_frames,
                                       int num_channels,
                                       int16_t* deinterleaved);

}  // namespace webrtc

#endif  // WEBRTC_COMMON_AUDIO_INCLUDE_AUDIO_UTIL_H_
