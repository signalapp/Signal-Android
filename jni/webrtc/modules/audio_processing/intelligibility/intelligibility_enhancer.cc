/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/modules/audio_processing/intelligibility/intelligibility_enhancer.h"

#include <math.h>
#include <stdlib.h>
#include <algorithm>
#include <limits>
#include <numeric>

#include "webrtc/base/checks.h"
#include "webrtc/common_audio/include/audio_util.h"
#include "webrtc/common_audio/window_generator.h"

namespace webrtc {

namespace {

const size_t kErbResolution = 2;
const int kWindowSizeMs = 16;
const int kChunkSizeMs = 10;  // Size provided by APM.
const float kClipFreqKhz = 0.2f;
const float kKbdAlpha = 1.5f;
const float kLambdaBot = -1.f;      // Extreme values in bisection
const float kLambdaTop = -1e-5f;      // search for lamda.
const float kVoiceProbabilityThreshold = 0.5f;
// Number of chunks after voice activity which is still considered speech.
const size_t kSpeechOffsetDelay = 10;
const float kDecayRate = 0.995f;              // Power estimation decay rate.
const float kMaxRelativeGainChange = 0.005f;
const float kRho = 0.0004f;  // Default production and interpretation SNR.
const float kPowerNormalizationFactor = 1.f / (1 << 30);
const float kMaxActiveSNR = 128.f;  // 21dB
const float kMinInactiveSNR = 32.f;  // 15dB
const size_t kGainUpdatePeriod = 10u;

// Returns dot product of vectors |a| and |b| with size |length|.
float DotProduct(const float* a, const float* b, size_t length) {
  float ret = 0.f;
  for (size_t i = 0; i < length; ++i) {
    ret += a[i] * b[i];
  }
  return ret;
}

// Computes the power across ERB bands from the power spectral density |pow|.
// Stores it in |result|.
void MapToErbBands(const float* pow,
                   const std::vector<std::vector<float>>& filter_bank,
                   float* result) {
  for (size_t i = 0; i < filter_bank.size(); ++i) {
    RTC_DCHECK_GT(filter_bank[i].size(), 0u);
    result[i] = kPowerNormalizationFactor *
                DotProduct(filter_bank[i].data(), pow, filter_bank[i].size());
  }
}

}  // namespace

IntelligibilityEnhancer::IntelligibilityEnhancer(int sample_rate_hz,
                                                 size_t num_render_channels,
                                                 size_t num_noise_bins)
    : freqs_(RealFourier::ComplexLength(
          RealFourier::FftOrder(sample_rate_hz * kWindowSizeMs / 1000))),
      num_noise_bins_(num_noise_bins),
      chunk_length_(static_cast<size_t>(sample_rate_hz * kChunkSizeMs / 1000)),
      bank_size_(GetBankSize(sample_rate_hz, kErbResolution)),
      sample_rate_hz_(sample_rate_hz),
      num_render_channels_(num_render_channels),
      clear_power_estimator_(freqs_, kDecayRate),
      noise_power_estimator_(num_noise_bins, kDecayRate),
      filtered_clear_pow_(bank_size_, 0.f),
      filtered_noise_pow_(num_noise_bins, 0.f),
      center_freqs_(bank_size_),
      capture_filter_bank_(CreateErbBank(num_noise_bins)),
      render_filter_bank_(CreateErbBank(freqs_)),
      gains_eq_(bank_size_),
      gain_applier_(freqs_, kMaxRelativeGainChange),
      audio_s16_(chunk_length_),
      chunks_since_voice_(kSpeechOffsetDelay),
      is_speech_(false),
      snr_(kMaxActiveSNR),
      is_active_(false),
      num_chunks_(0u),
      noise_estimation_buffer_(num_noise_bins),
      noise_estimation_queue_(kMaxNumNoiseEstimatesToBuffer,
                              std::vector<float>(num_noise_bins),
                              RenderQueueItemVerifier<float>(num_noise_bins)) {
  RTC_DCHECK_LE(kRho, 1.f);

  const size_t erb_index = static_cast<size_t>(
      ceilf(11.17f * logf((kClipFreqKhz + 0.312f) / (kClipFreqKhz + 14.6575f)) +
            43.f));
  start_freq_ = std::max(static_cast<size_t>(1), erb_index * kErbResolution);

  size_t window_size = static_cast<size_t>(1) << RealFourier::FftOrder(freqs_);
  std::vector<float> kbd_window(window_size);
  WindowGenerator::KaiserBesselDerived(kKbdAlpha, window_size,
                                       kbd_window.data());
  render_mangler_.reset(new LappedTransform(
      num_render_channels_, num_render_channels_, chunk_length_,
      kbd_window.data(), window_size, window_size / 2, this));
}

void IntelligibilityEnhancer::SetCaptureNoiseEstimate(
    std::vector<float> noise, int gain_db) {
  RTC_DCHECK_EQ(noise.size(), num_noise_bins_);
  const float gain = std::pow(10.f, gain_db / 20.f);
  for (auto& bin : noise) {
    bin *= gain;
  }
  // Disregarding return value since buffer overflow is acceptable, because it
  // is not critical to get each noise estimate.
  if (noise_estimation_queue_.Insert(&noise)) {
  };
}

void IntelligibilityEnhancer::ProcessRenderAudio(float* const* audio,
                                                 int sample_rate_hz,
                                                 size_t num_channels) {
  RTC_CHECK_EQ(sample_rate_hz_, sample_rate_hz);
  RTC_CHECK_EQ(num_render_channels_, num_channels);
  while (noise_estimation_queue_.Remove(&noise_estimation_buffer_)) {
    noise_power_estimator_.Step(noise_estimation_buffer_.data());
  }
  is_speech_ = IsSpeech(audio[0]);
  render_mangler_->ProcessChunk(audio, audio);
}

void IntelligibilityEnhancer::ProcessAudioBlock(
    const std::complex<float>* const* in_block,
    size_t in_channels,
    size_t frames,
    size_t /* out_channels */,
    std::complex<float>* const* out_block) {
  RTC_DCHECK_EQ(freqs_, frames);
  if (is_speech_) {
    clear_power_estimator_.Step(in_block[0]);
  }
  SnrBasedEffectActivation();
  if (is_active_ && num_chunks_++ % kGainUpdatePeriod == 0) {
    MapToErbBands(clear_power_estimator_.power().data(), render_filter_bank_,
                  filtered_clear_pow_.data());
    MapToErbBands(noise_power_estimator_.power().data(), capture_filter_bank_,
                  filtered_noise_pow_.data());
    SolveForGainsGivenLambda(kLambdaTop, start_freq_, gains_eq_.data());
    const float power_target = std::accumulate(
        filtered_clear_pow_.data(),
        filtered_clear_pow_.data() + bank_size_,
        0.f);
    const float power_top =
        DotProduct(gains_eq_.data(), filtered_clear_pow_.data(), bank_size_);
    SolveForGainsGivenLambda(kLambdaBot, start_freq_, gains_eq_.data());
    const float power_bot =
        DotProduct(gains_eq_.data(), filtered_clear_pow_.data(), bank_size_);
    if (power_target >= power_bot && power_target <= power_top) {
      SolveForLambda(power_target);
      UpdateErbGains();
    }  // Else experiencing power underflow, so do nothing.
  }
  for (size_t i = 0; i < in_channels; ++i) {
    gain_applier_.Apply(in_block[i], out_block[i]);
  }
}

void IntelligibilityEnhancer::SnrBasedEffectActivation() {
  const float* clear_psd = clear_power_estimator_.power().data();
  const float* noise_psd = noise_power_estimator_.power().data();
  const float clear_power =
      std::accumulate(clear_psd, clear_psd + freqs_, 0.f);
  const float noise_power =
      std::accumulate(noise_psd, noise_psd + freqs_, 0.f);
  snr_ = kDecayRate * snr_ + (1.f - kDecayRate) * clear_power /
      (noise_power + std::numeric_limits<float>::epsilon());
  if (is_active_) {
    if (snr_ > kMaxActiveSNR) {
      is_active_ = false;
      // Set the target gains to unity.
      float* gains = gain_applier_.target();
      for (size_t i = 0; i < freqs_; ++i) {
        gains[i] = 1.f;
      }
    }
  } else {
    is_active_ = snr_ < kMinInactiveSNR;
  }
}

void IntelligibilityEnhancer::SolveForLambda(float power_target) {
  const float kConvergeThresh = 0.001f;  // TODO(ekmeyerson): Find best values
  const int kMaxIters = 100;             // for these, based on experiments.

  const float reciprocal_power_target =
      1.f / (power_target + std::numeric_limits<float>::epsilon());
  float lambda_bot = kLambdaBot;
  float lambda_top = kLambdaTop;
  float power_ratio = 2.f;  // Ratio of achieved power to target power.
  int iters = 0;
  while (std::fabs(power_ratio - 1.f) > kConvergeThresh && iters <= kMaxIters) {
    const float lambda = (lambda_bot + lambda_top) / 2.f;
    SolveForGainsGivenLambda(lambda, start_freq_, gains_eq_.data());
    const float power =
        DotProduct(gains_eq_.data(), filtered_clear_pow_.data(), bank_size_);
    if (power < power_target) {
      lambda_bot = lambda;
    } else {
      lambda_top = lambda;
    }
    power_ratio = std::fabs(power * reciprocal_power_target);
    ++iters;
  }
}

void IntelligibilityEnhancer::UpdateErbGains() {
  // (ERB gain) = filterbank' * (freq gain)
  float* gains = gain_applier_.target();
  for (size_t i = 0; i < freqs_; ++i) {
    gains[i] = 0.f;
    for (size_t j = 0; j < bank_size_; ++j) {
      gains[i] += render_filter_bank_[j][i] * gains_eq_[j];
    }
  }
}

size_t IntelligibilityEnhancer::GetBankSize(int sample_rate,
                                            size_t erb_resolution) {
  float freq_limit = sample_rate / 2000.f;
  size_t erb_scale = static_cast<size_t>(ceilf(
      11.17f * logf((freq_limit + 0.312f) / (freq_limit + 14.6575f)) + 43.f));
  return erb_scale * erb_resolution;
}

std::vector<std::vector<float>> IntelligibilityEnhancer::CreateErbBank(
    size_t num_freqs) {
  std::vector<std::vector<float>> filter_bank(bank_size_);
  size_t lf = 1, rf = 4;

  for (size_t i = 0; i < bank_size_; ++i) {
    float abs_temp = fabsf((i + 1.f) / static_cast<float>(kErbResolution));
    center_freqs_[i] = 676170.4f / (47.06538f - expf(0.08950404f * abs_temp));
    center_freqs_[i] -= 14678.49f;
  }
  float last_center_freq = center_freqs_[bank_size_ - 1];
  for (size_t i = 0; i < bank_size_; ++i) {
    center_freqs_[i] *= 0.5f * sample_rate_hz_ / last_center_freq;
  }

  for (size_t i = 0; i < bank_size_; ++i) {
    filter_bank[i].resize(num_freqs);
  }

  for (size_t i = 1; i <= bank_size_; ++i) {
    static const size_t kOne = 1;  // Avoids repeated static_cast<>s below.
    size_t lll =
        static_cast<size_t>(round(center_freqs_[std::max(kOne, i - lf) - 1] *
                                  num_freqs / (0.5f * sample_rate_hz_)));
    size_t ll = static_cast<size_t>(round(center_freqs_[std::max(kOne, i) - 1] *
                                   num_freqs / (0.5f * sample_rate_hz_)));
    lll = std::min(num_freqs, std::max(lll, kOne)) - 1;
    ll = std::min(num_freqs, std::max(ll, kOne)) - 1;

    size_t rrr = static_cast<size_t>(
        round(center_freqs_[std::min(bank_size_, i + rf) - 1] * num_freqs /
              (0.5f * sample_rate_hz_)));
    size_t rr = static_cast<size_t>(
        round(center_freqs_[std::min(bank_size_, i + 1) - 1] * num_freqs /
              (0.5f * sample_rate_hz_)));
    rrr = std::min(num_freqs, std::max(rrr, kOne)) - 1;
    rr = std::min(num_freqs, std::max(rr, kOne)) - 1;

    float step = ll == lll ? 0.f : 1.f / (ll - lll);
    float element = 0.f;
    for (size_t j = lll; j <= ll; ++j) {
      filter_bank[i - 1][j] = element;
      element += step;
    }
    step = rr == rrr ? 0.f : 1.f / (rrr - rr);
    element = 1.f;
    for (size_t j = rr; j <= rrr; ++j) {
      filter_bank[i - 1][j] = element;
      element -= step;
    }
    for (size_t j = ll; j <= rr; ++j) {
      filter_bank[i - 1][j] = 1.f;
    }
  }

  for (size_t i = 0; i < num_freqs; ++i) {
    float sum = 0.f;
    for (size_t j = 0; j < bank_size_; ++j) {
      sum += filter_bank[j][i];
    }
    for (size_t j = 0; j < bank_size_; ++j) {
      filter_bank[j][i] /= sum;
    }
  }
  return filter_bank;
}

void IntelligibilityEnhancer::SolveForGainsGivenLambda(float lambda,
                                                       size_t start_freq,
                                                       float* sols) {
  const float kMinPower = 1e-5f;

  const float* pow_x0 = filtered_clear_pow_.data();
  const float* pow_n0 = filtered_noise_pow_.data();

  for (size_t n = 0; n < start_freq; ++n) {
    sols[n] = 1.f;
  }

  // Analytic solution for optimal gains. See paper for derivation.
  for (size_t n = start_freq; n < bank_size_; ++n) {
    if (pow_x0[n] < kMinPower || pow_n0[n] < kMinPower) {
      sols[n] = 1.f;
    } else {
      const float gamma0 = 0.5f * kRho * pow_x0[n] * pow_n0[n] +
                           lambda * pow_x0[n] * pow_n0[n] * pow_n0[n];
      const float beta0 =
          lambda * pow_x0[n] * (2.f - kRho) * pow_x0[n] * pow_n0[n];
      const float alpha0 =
          lambda * pow_x0[n] * (1.f - kRho) * pow_x0[n] * pow_x0[n];
      RTC_DCHECK_LT(alpha0, 0.f);
      // The quadratic equation should always have real roots, but to guard
      // against numerical errors we limit it to a minimum of zero.
      sols[n] = std::max(
          0.f, (-beta0 - std::sqrt(std::max(
                             0.f, beta0 * beta0 - 4.f * alpha0 * gamma0))) /
                   (2.f * alpha0));
    }
  }
}

bool IntelligibilityEnhancer::IsSpeech(const float* audio) {
  FloatToS16(audio, chunk_length_, audio_s16_.data());
  vad_.ProcessChunk(audio_s16_.data(), chunk_length_, sample_rate_hz_);
  if (vad_.last_voice_probability() > kVoiceProbabilityThreshold) {
    chunks_since_voice_ = 0;
  } else if (chunks_since_voice_ < kSpeechOffsetDelay) {
    ++chunks_since_voice_;
  }
  return chunks_since_voice_ < kSpeechOffsetDelay;
}

}  // namespace webrtc
