/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/common_audio/resampler/include/push_resampler.h"

#include <string.h>

#include "webrtc/base/checks.h"
#include "webrtc/common_audio/include/audio_util.h"
#include "webrtc/common_audio/resampler/include/resampler.h"
#include "webrtc/common_audio/resampler/push_sinc_resampler.h"

namespace webrtc {
namespace {
// These checks were factored out into a non-templatized function
// due to problems with clang on Windows in debug builds.
// For some reason having the DCHECKs inline in the template code
// caused the compiler to generate code that threw off the linker.
// TODO(tommi): Re-enable when we've figured out what the problem is.
// http://crbug.com/615050
void CheckValidInitParams(int src_sample_rate_hz, int dst_sample_rate_hz,
                          size_t num_channels) {
// The below checks are temporarily disabled on WEBRTC_WIN due to problems
// with clang debug builds.
#if !defined(WEBRTC_WIN) && defined(__clang__) && !defined(NDEBUG)
  RTC_DCHECK_GT(src_sample_rate_hz, 0);
  RTC_DCHECK_GT(dst_sample_rate_hz, 0);
  RTC_DCHECK_GT(num_channels, 0u);
  RTC_DCHECK_LE(num_channels, 2u);
#endif
}

void CheckExpectedBufferSizes(size_t src_length,
                              size_t dst_capacity,
                              size_t num_channels,
                              int src_sample_rate,
                              int dst_sample_rate) {
// The below checks are temporarily disabled on WEBRTC_WIN due to problems
// with clang debug builds.
// TODO(tommi): Re-enable when we've figured out what the problem is.
// http://crbug.com/615050
#if !defined(WEBRTC_WIN) && defined(__clang__) && !defined(NDEBUG)
  const size_t src_size_10ms = src_sample_rate * num_channels / 100;
  const size_t dst_size_10ms = dst_sample_rate * num_channels / 100;
  RTC_CHECK_EQ(src_length, src_size_10ms);
  RTC_CHECK_GE(dst_capacity, dst_size_10ms);
#endif
}
}

template <typename T>
PushResampler<T>::PushResampler()
    : src_sample_rate_hz_(0),
      dst_sample_rate_hz_(0),
      num_channels_(0) {
}

template <typename T>
PushResampler<T>::~PushResampler() {
}

template <typename T>
int PushResampler<T>::InitializeIfNeeded(int src_sample_rate_hz,
                                         int dst_sample_rate_hz,
                                         size_t num_channels) {
  CheckValidInitParams(src_sample_rate_hz, dst_sample_rate_hz, num_channels);

  if (src_sample_rate_hz == src_sample_rate_hz_ &&
      dst_sample_rate_hz == dst_sample_rate_hz_ &&
      num_channels == num_channels_) {
    // No-op if settings haven't changed.
    return 0;
  }

  if (src_sample_rate_hz <= 0 || dst_sample_rate_hz <= 0 || num_channels <= 0 ||
      num_channels > 2) {
    return -1;
  }

  src_sample_rate_hz_ = src_sample_rate_hz;
  dst_sample_rate_hz_ = dst_sample_rate_hz;
  num_channels_ = num_channels;

  const size_t src_size_10ms_mono =
      static_cast<size_t>(src_sample_rate_hz / 100);
  const size_t dst_size_10ms_mono =
      static_cast<size_t>(dst_sample_rate_hz / 100);
  sinc_resampler_.reset(new PushSincResampler(src_size_10ms_mono,
                                              dst_size_10ms_mono));
  if (num_channels_ == 2) {
    src_left_.reset(new T[src_size_10ms_mono]);
    src_right_.reset(new T[src_size_10ms_mono]);
    dst_left_.reset(new T[dst_size_10ms_mono]);
    dst_right_.reset(new T[dst_size_10ms_mono]);
    sinc_resampler_right_.reset(new PushSincResampler(src_size_10ms_mono,
                                                      dst_size_10ms_mono));
  }

  return 0;
}

template <typename T>
int PushResampler<T>::Resample(const T* src, size_t src_length, T* dst,
                               size_t dst_capacity) {
  CheckExpectedBufferSizes(src_length, dst_capacity, num_channels_,
                           src_sample_rate_hz_, dst_sample_rate_hz_);

  if (src_sample_rate_hz_ == dst_sample_rate_hz_) {
    // The old resampler provides this memcpy facility in the case of matching
    // sample rates, so reproduce it here for the sinc resampler.
    memcpy(dst, src, src_length * sizeof(T));
    return static_cast<int>(src_length);
  }
  if (num_channels_ == 2) {
    const size_t src_length_mono = src_length / num_channels_;
    const size_t dst_capacity_mono = dst_capacity / num_channels_;
    T* deinterleaved[] = {src_left_.get(), src_right_.get()};
    Deinterleave(src, src_length_mono, num_channels_, deinterleaved);

    size_t dst_length_mono =
        sinc_resampler_->Resample(src_left_.get(), src_length_mono,
                                  dst_left_.get(), dst_capacity_mono);
    sinc_resampler_right_->Resample(src_right_.get(), src_length_mono,
                                    dst_right_.get(), dst_capacity_mono);

    deinterleaved[0] = dst_left_.get();
    deinterleaved[1] = dst_right_.get();
    Interleave(deinterleaved, dst_length_mono, num_channels_, dst);
    return static_cast<int>(dst_length_mono * num_channels_);
  } else {
    return static_cast<int>(
        sinc_resampler_->Resample(src, src_length, dst, dst_capacity));
  }
}

// Explictly generate required instantiations.
template class PushResampler<int16_t>;
template class PushResampler<float>;

}  // namespace webrtc
