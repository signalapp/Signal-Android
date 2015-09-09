/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/modules/audio_coding/neteq/accelerate.h"

#include "webrtc/common_audio/signal_processing/include/signal_processing_library.h"

namespace webrtc {

Accelerate::ReturnCodes Accelerate::Process(
    const int16_t* input,
    size_t input_length,
    AudioMultiVector* output,
    int16_t* length_change_samples) {
  // Input length must be (almost) 30 ms.
  static const int k15ms = 120;  // 15 ms = 120 samples at 8 kHz sample rate.
  if (num_channels_ == 0 || static_cast<int>(input_length) / num_channels_ <
      (2 * k15ms - 1) * fs_mult_) {
    // Length of input data too short to do accelerate. Simply move all data
    // from input to output.
    output->PushBackInterleaved(input, input_length);
    return kError;
  }
  return TimeStretch::Process(input, input_length, output,
                              length_change_samples);
}

void Accelerate::SetParametersForPassiveSpeech(size_t /*len*/,
                                               int16_t* best_correlation,
                                               int* /*peak_index*/) const {
  // When the signal does not contain any active speech, the correlation does
  // not matter. Simply set it to zero.
  *best_correlation = 0;
}

Accelerate::ReturnCodes Accelerate::CheckCriteriaAndStretch(
    const int16_t* input, size_t input_length, size_t peak_index,
    int16_t best_correlation, bool active_speech,
    AudioMultiVector* output) const {
  // Check for strong correlation or passive speech.
  if ((best_correlation > kCorrelationThreshold) || !active_speech) {
    // Do accelerate operation by overlap add.

    // Pre-calculate common multiplication with |fs_mult_|.
    // 120 corresponds to 15 ms.
    size_t fs_mult_120 = fs_mult_ * 120;

    assert(fs_mult_120 >= peak_index);  // Should be handled in Process().
    // Copy first part; 0 to 15 ms.
    output->PushBackInterleaved(input, fs_mult_120 * num_channels_);
    // Copy the |peak_index| starting at 15 ms to |temp_vector|.
    AudioMultiVector temp_vector(num_channels_);
    temp_vector.PushBackInterleaved(&input[fs_mult_120 * num_channels_],
                                    peak_index * num_channels_);
    // Cross-fade |temp_vector| onto the end of |output|.
    output->CrossFade(temp_vector, peak_index);
    // Copy the last unmodified part, 15 ms + pitch period until the end.
    output->PushBackInterleaved(
        &input[(fs_mult_120 + peak_index) * num_channels_],
        input_length - (fs_mult_120 + peak_index) * num_channels_);

    if (active_speech) {
      return kSuccess;
    } else {
      return kSuccessLowEnergy;
    }
  } else {
    // Accelerate not allowed. Simply move all data from decoded to outData.
    output->PushBackInterleaved(input, input_length);
    return kNoStretch;
  }
}

Accelerate* AccelerateFactory::Create(
    int sample_rate_hz,
    size_t num_channels,
    const BackgroundNoise& background_noise) const {
  return new Accelerate(sample_rate_hz, num_channels, background_noise);
}

}  // namespace webrtc
