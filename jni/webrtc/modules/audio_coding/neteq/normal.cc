/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/modules/audio_coding/neteq/normal.h"

#include <string.h>  // memset, memcpy

#include <algorithm>  // min

#include "webrtc/common_audio/signal_processing/include/signal_processing_library.h"
#include "webrtc/modules/audio_coding/codecs/audio_decoder.h"
#include "webrtc/modules/audio_coding/neteq/audio_multi_vector.h"
#include "webrtc/modules/audio_coding/neteq/background_noise.h"
#include "webrtc/modules/audio_coding/neteq/decoder_database.h"
#include "webrtc/modules/audio_coding/neteq/expand.h"

namespace webrtc {

int Normal::Process(const int16_t* input,
                    size_t length,
                    Modes last_mode,
                    int16_t* external_mute_factor_array,
                    AudioMultiVector* output) {
  if (length == 0) {
    // Nothing to process.
    output->Clear();
    return static_cast<int>(length);
  }

  assert(output->Empty());
  // Output should be empty at this point.
  if (length % output->Channels() != 0) {
    // The length does not match the number of channels.
    output->Clear();
    return 0;
  }
  output->PushBackInterleaved(input, length);

  const int fs_mult = fs_hz_ / 8000;
  assert(fs_mult > 0);
  // fs_shift = log2(fs_mult), rounded down.
  // Note that |fs_shift| is not "exact" for 48 kHz.
  // TODO(hlundin): Investigate this further.
  const int fs_shift = 30 - WebRtcSpl_NormW32(fs_mult);

  // Check if last RecOut call resulted in an Expand. If so, we have to take
  // care of some cross-fading and unmuting.
  if (last_mode == kModeExpand) {
    // Generate interpolation data using Expand.
    // First, set Expand parameters to appropriate values.
    expand_->SetParametersForNormalAfterExpand();

    // Call Expand.
    AudioMultiVector expanded(output->Channels());
    expand_->Process(&expanded);
    expand_->Reset();

    size_t length_per_channel = length / output->Channels();
    std::unique_ptr<int16_t[]> signal(new int16_t[length_per_channel]);
    for (size_t channel_ix = 0; channel_ix < output->Channels(); ++channel_ix) {
      // Adjust muting factor (main muting factor times expand muting factor).
      external_mute_factor_array[channel_ix] = static_cast<int16_t>(
          (external_mute_factor_array[channel_ix] *
          expand_->MuteFactor(channel_ix)) >> 14);

      (*output)[channel_ix].CopyTo(length_per_channel, 0, signal.get());

      // Find largest absolute value in new data.
      int16_t decoded_max =
          WebRtcSpl_MaxAbsValueW16(signal.get(), length_per_channel);
      // Adjust muting factor if needed (to BGN level).
      size_t energy_length =
          std::min(static_cast<size_t>(fs_mult * 64), length_per_channel);
      int scaling = 6 + fs_shift
          - WebRtcSpl_NormW32(decoded_max * decoded_max);
      scaling = std::max(scaling, 0);  // |scaling| should always be >= 0.
      int32_t energy = WebRtcSpl_DotProductWithScale(signal.get(), signal.get(),
                                                     energy_length, scaling);
      int32_t scaled_energy_length =
          static_cast<int32_t>(energy_length >> scaling);
      if (scaled_energy_length > 0) {
        energy = energy / scaled_energy_length;
      } else {
        energy = 0;
      }

      int mute_factor;
      if ((energy != 0) &&
          (energy > background_noise_.Energy(channel_ix))) {
        // Normalize new frame energy to 15 bits.
        scaling = WebRtcSpl_NormW32(energy) - 16;
        // We want background_noise_.energy() / energy in Q14.
        int32_t bgn_energy =
            background_noise_.Energy(channel_ix) << (scaling+14);
        int16_t energy_scaled =
            static_cast<int16_t>(WEBRTC_SPL_SHIFT_W32(energy, scaling));
        int32_t ratio = WebRtcSpl_DivW32W16(bgn_energy, energy_scaled);
        mute_factor = WebRtcSpl_SqrtFloor(ratio << 14);
      } else {
        mute_factor = 16384;  // 1.0 in Q14.
      }
      if (mute_factor > external_mute_factor_array[channel_ix]) {
        external_mute_factor_array[channel_ix] =
            static_cast<int16_t>(std::min(mute_factor, 16384));
      }

      // If muted increase by 0.64 for every 20 ms (NB/WB 0.0040/0.0020 in Q14).
      int increment = 64 / fs_mult;
      for (size_t i = 0; i < length_per_channel; i++) {
        // Scale with mute factor.
        assert(channel_ix < output->Channels());
        assert(i < output->Size());
        int32_t scaled_signal = (*output)[channel_ix][i] *
            external_mute_factor_array[channel_ix];
        // Shift 14 with proper rounding.
        (*output)[channel_ix][i] =
            static_cast<int16_t>((scaled_signal + 8192) >> 14);
        // Increase mute_factor towards 16384.
        external_mute_factor_array[channel_ix] = static_cast<int16_t>(std::min(
            external_mute_factor_array[channel_ix] + increment, 16384));
      }

      // Interpolate the expanded data into the new vector.
      // (NB/WB/SWB32/SWB48 8/16/32/48 samples.)
      assert(fs_shift < 3);  // Will always be 0, 1, or, 2.
      increment = 4 >> fs_shift;
      int fraction = increment;
      for (size_t i = 0; i < static_cast<size_t>(8 * fs_mult); i++) {
        // TODO(hlundin): Add 16 instead of 8 for correct rounding. Keeping 8
        // now for legacy bit-exactness.
        assert(channel_ix < output->Channels());
        assert(i < output->Size());
        (*output)[channel_ix][i] =
            static_cast<int16_t>((fraction * (*output)[channel_ix][i] +
                (32 - fraction) * expanded[channel_ix][i] + 8) >> 5);
        fraction += increment;
      }
    }
  } else if (last_mode == kModeRfc3389Cng) {
    assert(output->Channels() == 1);  // Not adapted for multi-channel yet.
    static const size_t kCngLength = 32;
    int16_t cng_output[kCngLength];
    // Reset mute factor and start up fresh.
    external_mute_factor_array[0] = 16384;
    ComfortNoiseDecoder* cng_decoder = decoder_database_->GetActiveCngDecoder();

    if (cng_decoder) {
      // Generate long enough for 32kHz.
      if (!cng_decoder->Generate(cng_output, 0)) {
        // Error returned; set return vector to all zeros.
        memset(cng_output, 0, sizeof(cng_output));
      }
    } else {
      // If no CNG instance is defined, just copy from the decoded data.
      // (This will result in interpolating the decoded with itself.)
      (*output)[0].CopyTo(fs_mult * 8, 0, cng_output);
    }
    // Interpolate the CNG into the new vector.
    // (NB/WB/SWB32/SWB48 8/16/32/48 samples.)
    assert(fs_shift < 3);  // Will always be 0, 1, or, 2.
    int16_t increment = 4 >> fs_shift;
    int16_t fraction = increment;
    for (size_t i = 0; i < static_cast<size_t>(8 * fs_mult); i++) {
      // TODO(hlundin): Add 16 instead of 8 for correct rounding. Keeping 8 now
      // for legacy bit-exactness.
      (*output)[0][i] = (fraction * (*output)[0][i] +
          (32 - fraction) * cng_output[i] + 8) >> 5;
      fraction += increment;
    }
  } else if (external_mute_factor_array[0] < 16384) {
    // Previous was neither of Expand, FadeToBGN or RFC3389_CNG, but we are
    // still ramping up from previous muting.
    // If muted increase by 0.64 for every 20 ms (NB/WB 0.0040/0.0020 in Q14).
    int increment = 64 / fs_mult;
    size_t length_per_channel = length / output->Channels();
    for (size_t i = 0; i < length_per_channel; i++) {
      for (size_t channel_ix = 0; channel_ix < output->Channels();
          ++channel_ix) {
        // Scale with mute factor.
        assert(channel_ix < output->Channels());
        assert(i < output->Size());
        int32_t scaled_signal = (*output)[channel_ix][i] *
            external_mute_factor_array[channel_ix];
        // Shift 14 with proper rounding.
        (*output)[channel_ix][i] =
            static_cast<int16_t>((scaled_signal + 8192) >> 14);
        // Increase mute_factor towards 16384.
        external_mute_factor_array[channel_ix] = static_cast<int16_t>(std::min(
            16384, external_mute_factor_array[channel_ix] + increment));
      }
    }
  }

  return static_cast<int>(length);
}

}  // namespace webrtc
