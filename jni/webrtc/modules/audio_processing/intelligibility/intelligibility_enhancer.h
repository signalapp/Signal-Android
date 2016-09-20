/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_MODULES_AUDIO_PROCESSING_INTELLIGIBILITY_INTELLIGIBILITY_ENHANCER_H_
#define WEBRTC_MODULES_AUDIO_PROCESSING_INTELLIGIBILITY_INTELLIGIBILITY_ENHANCER_H_

#include <complex>
#include <memory>
#include <vector>

#include "webrtc/base/swap_queue.h"
#include "webrtc/common_audio/lapped_transform.h"
#include "webrtc/common_audio/channel_buffer.h"
#include "webrtc/modules/audio_processing/intelligibility/intelligibility_utils.h"
#include "webrtc/modules/audio_processing/render_queue_item_verifier.h"
#include "webrtc/modules/audio_processing/vad/voice_activity_detector.h"

namespace webrtc {

// Speech intelligibility enhancement module. Reads render and capture
// audio streams and modifies the render stream with a set of gains per
// frequency bin to enhance speech against the noise background.
// Details of the model and algorithm can be found in the original paper:
// http://ieeexplore.ieee.org/stamp/stamp.jsp?tp=&arnumber=6882788
class IntelligibilityEnhancer : public LappedTransform::Callback {
 public:
  IntelligibilityEnhancer(int sample_rate_hz,
                          size_t num_render_channels,
                          size_t num_noise_bins);

  // Sets the capture noise magnitude spectrum estimate.
  void SetCaptureNoiseEstimate(std::vector<float> noise, int gain_db);

  // Reads chunk of speech in time domain and updates with modified signal.
  void ProcessRenderAudio(float* const* audio,
                          int sample_rate_hz,
                          size_t num_channels);
  bool active() const;

 protected:
  // All in frequency domain, receives input |in_block|, applies
  // intelligibility enhancement, and writes result to |out_block|.
  void ProcessAudioBlock(const std::complex<float>* const* in_block,
                         size_t in_channels,
                         size_t frames,
                         size_t out_channels,
                         std::complex<float>* const* out_block) override;

 private:
  FRIEND_TEST_ALL_PREFIXES(IntelligibilityEnhancerTest, TestErbCreation);
  FRIEND_TEST_ALL_PREFIXES(IntelligibilityEnhancerTest, TestSolveForGains);
  FRIEND_TEST_ALL_PREFIXES(IntelligibilityEnhancerTest,
                           TestNoiseGainHasExpectedResult);

  // Updates the SNR estimation and enables or disables this component using a
  // hysteresis.
  void SnrBasedEffectActivation();

  // Bisection search for optimal |lambda|.
  void SolveForLambda(float power_target);

  // Transforms freq gains to ERB gains.
  void UpdateErbGains();

  // Returns number of ERB filters.
  static size_t GetBankSize(int sample_rate, size_t erb_resolution);

  // Initializes ERB filterbank.
  std::vector<std::vector<float>> CreateErbBank(size_t num_freqs);

  // Analytically solves quadratic for optimal gains given |lambda|.
  // Negative gains are set to 0. Stores the results in |sols|.
  void SolveForGainsGivenLambda(float lambda, size_t start_freq, float* sols);

  // Returns true if the audio is speech.
  bool IsSpeech(const float* audio);

  static const size_t kMaxNumNoiseEstimatesToBuffer = 5;

  const size_t freqs_;         // Num frequencies in frequency domain.
  const size_t num_noise_bins_;
  const size_t chunk_length_;  // Chunk size in samples.
  const size_t bank_size_;     // Num ERB filters.
  const int sample_rate_hz_;
  const size_t num_render_channels_;

  intelligibility::PowerEstimator<std::complex<float>> clear_power_estimator_;
  intelligibility::PowerEstimator<float> noise_power_estimator_;
  std::vector<float> filtered_clear_pow_;
  std::vector<float> filtered_noise_pow_;
  std::vector<float> center_freqs_;
  std::vector<std::vector<float>> capture_filter_bank_;
  std::vector<std::vector<float>> render_filter_bank_;
  size_t start_freq_;

  std::vector<float> gains_eq_;  // Pre-filter modified gains.
  intelligibility::GainApplier gain_applier_;

  std::unique_ptr<LappedTransform> render_mangler_;

  VoiceActivityDetector vad_;
  std::vector<int16_t> audio_s16_;
  size_t chunks_since_voice_;
  bool is_speech_;
  float snr_;
  bool is_active_;

  size_t num_chunks_;

  std::vector<float> noise_estimation_buffer_;
  SwapQueue<std::vector<float>, RenderQueueItemVerifier<float>>
      noise_estimation_queue_;
};

}  // namespace webrtc

#endif  // WEBRTC_MODULES_AUDIO_PROCESSING_INTELLIGIBILITY_INTELLIGIBILITY_ENHANCER_H_
