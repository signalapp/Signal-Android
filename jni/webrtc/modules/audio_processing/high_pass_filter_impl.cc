/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/modules/audio_processing/high_pass_filter_impl.h"

#include "webrtc/common_audio/signal_processing/include/signal_processing_library.h"
#include "webrtc/modules/audio_processing/audio_buffer.h"
#include "webrtc/system_wrappers/include/critical_section_wrapper.h"

namespace webrtc {
namespace {
const int16_t kFilterCoefficients8kHz[5] = {3798, -7596, 3798, 7807, -3733};
const int16_t kFilterCoefficients[5] = {4012, -8024, 4012, 8002, -3913};
}  // namespace

class HighPassFilterImpl::BiquadFilter {
 public:
  explicit BiquadFilter(int sample_rate_hz) :
      ba_(sample_rate_hz == AudioProcessing::kSampleRate8kHz ?
          kFilterCoefficients8kHz : kFilterCoefficients)
  {
    Reset();
  }

  void Reset() {
    std::memset(x_, 0, sizeof(x_));
    std::memset(y_, 0, sizeof(y_));
  }

  void Process(int16_t* data, size_t length) {
    const int16_t* const ba = ba_;
    int16_t* x = x_;
    int16_t* y = y_;
    int32_t tmp_int32 = 0;

    for (size_t i = 0; i < length; i++) {
      //  y[i] = b[0] * x[i] +  b[1] * x[i-1] +  b[2] * x[i-2]
      //                     + -a[1] * y[i-1] + -a[2] * y[i-2];

      tmp_int32 = y[1] * ba[3];      // -a[1] * y[i-1] (low part)
      tmp_int32 += y[3] * ba[4];     // -a[2] * y[i-2] (low part)
      tmp_int32 = (tmp_int32 >> 15);
      tmp_int32 += y[0] * ba[3];     // -a[1] * y[i-1] (high part)
      tmp_int32 += y[2] * ba[4];     // -a[2] * y[i-2] (high part)
      tmp_int32 = (tmp_int32 << 1);

      tmp_int32 += data[i] * ba[0];  // b[0] * x[0]
      tmp_int32 += x[0] * ba[1];     // b[1] * x[i-1]
      tmp_int32 += x[1] * ba[2];     // b[2] * x[i-2]

      // Update state (input part).
      x[1] = x[0];
      x[0] = data[i];

      // Update state (filtered part).
      y[2] = y[0];
      y[3] = y[1];
      y[0] = static_cast<int16_t>(tmp_int32 >> 13);
      y[1] = static_cast<int16_t>(
          (tmp_int32 - (static_cast<int32_t>(y[0]) << 13)) << 2);

      // Rounding in Q12, i.e. add 2^11.
      tmp_int32 += 2048;

      // Saturate (to 2^27) so that the HP filtered signal does not overflow.
      tmp_int32 = WEBRTC_SPL_SAT(static_cast<int32_t>(134217727),
                                 tmp_int32,
                                 static_cast<int32_t>(-134217728));

      // Convert back to Q0 and use rounding.
      data[i] = static_cast<int16_t>(tmp_int32 >> 12);
    }
  }

 private:
  const int16_t* const ba_ = nullptr;
  int16_t x_[2];
  int16_t y_[4];
};

HighPassFilterImpl::HighPassFilterImpl(rtc::CriticalSection* crit)
    : crit_(crit) {
  RTC_DCHECK(crit_);
}

HighPassFilterImpl::~HighPassFilterImpl() {}

void HighPassFilterImpl::Initialize(size_t channels, int sample_rate_hz) {
  std::vector<std::unique_ptr<BiquadFilter>> new_filters(channels);
  for (size_t i = 0; i < channels; i++) {
    new_filters[i].reset(new BiquadFilter(sample_rate_hz));
  }
  rtc::CritScope cs(crit_);
  filters_.swap(new_filters);
}

void HighPassFilterImpl::ProcessCaptureAudio(AudioBuffer* audio) {
  RTC_DCHECK(audio);
  rtc::CritScope cs(crit_);
  if (!enabled_) {
    return;
  }

  RTC_DCHECK_GE(160u, audio->num_frames_per_band());
  RTC_DCHECK_EQ(filters_.size(), audio->num_channels());
  for (size_t i = 0; i < filters_.size(); i++) {
    filters_[i]->Process(audio->split_bands(i)[kBand0To8kHz],
                         audio->num_frames_per_band());
  }
}

int HighPassFilterImpl::Enable(bool enable) {
  rtc::CritScope cs(crit_);
  if (!enabled_ && enable) {
    for (auto& filter : filters_) {
      filter->Reset();
    }
  }
  enabled_ = enable;
  return AudioProcessing::kNoError;
}

bool HighPassFilterImpl::is_enabled() const {
  rtc::CritScope cs(crit_);
  return enabled_;
}
}  // namespace webrtc
