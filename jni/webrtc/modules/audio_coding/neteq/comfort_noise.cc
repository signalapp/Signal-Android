/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/modules/audio_coding/neteq/comfort_noise.h"

#include <assert.h>

#include "webrtc/modules/audio_coding/codecs/cng/include/webrtc_cng.h"
#include "webrtc/modules/audio_coding/neteq/decoder_database.h"
#include "webrtc/modules/audio_coding/neteq/dsp_helper.h"
#include "webrtc/modules/audio_coding/neteq/interface/audio_decoder.h"
#include "webrtc/modules/audio_coding/neteq/sync_buffer.h"

namespace webrtc {

void ComfortNoise::Reset() {
  first_call_ = true;
  internal_error_code_ = 0;
}

int ComfortNoise::UpdateParameters(Packet* packet) {
  assert(packet);  // Existence is verified by caller.
  // Get comfort noise decoder.
  AudioDecoder* cng_decoder = decoder_database_->GetDecoder(
      packet->header.payloadType);
  if (!cng_decoder) {
    delete [] packet->payload;
    delete packet;
    return kUnknownPayloadType;
  }
  decoder_database_->SetActiveCngDecoder(packet->header.payloadType);
  CNG_dec_inst* cng_inst = static_cast<CNG_dec_inst*>(cng_decoder->state());
  int16_t ret = WebRtcCng_UpdateSid(cng_inst,
                                    packet->payload,
                                    packet->payload_length);
  delete [] packet->payload;
  delete packet;
  if (ret < 0) {
    internal_error_code_ = WebRtcCng_GetErrorCodeDec(cng_inst);
    return kInternalError;
  }
  return kOK;
}

int ComfortNoise::Generate(size_t requested_length,
                           AudioMultiVector* output) {
  // TODO(hlundin): Change to an enumerator and skip assert.
  assert(fs_hz_ == 8000 || fs_hz_ == 16000 || fs_hz_ ==  32000 ||
         fs_hz_ == 48000);
  // Not adapted for multi-channel yet.
  if (output->Channels() != 1) {
    return kMultiChannelNotSupported;
  }

  size_t number_of_samples = requested_length;
  int16_t new_period = 0;
  if (first_call_) {
    // Generate noise and overlap slightly with old data.
    number_of_samples = requested_length + overlap_length_;
    new_period = 1;
  }
  output->AssertSize(number_of_samples);
  // Get the decoder from the database.
  AudioDecoder* cng_decoder = decoder_database_->GetActiveCngDecoder();
  if (!cng_decoder) {
    return kUnknownPayloadType;
  }
  CNG_dec_inst* cng_inst = static_cast<CNG_dec_inst*>(cng_decoder->state());
  // The expression &(*output)[0][0] is a pointer to the first element in
  // the first channel.
  if (WebRtcCng_Generate(cng_inst, &(*output)[0][0],
                         static_cast<int16_t>(number_of_samples),
                         new_period) < 0) {
    // Error returned.
    output->Zeros(requested_length);
    internal_error_code_ = WebRtcCng_GetErrorCodeDec(cng_inst);
    return kInternalError;
  }

  if (first_call_) {
    // Set tapering window parameters. Values are in Q15.
    int16_t muting_window;  // Mixing factor for overlap data.
    int16_t muting_window_increment;  // Mixing factor increment (negative).
    int16_t unmuting_window;  // Mixing factor for comfort noise.
    int16_t unmuting_window_increment;  // Mixing factor increment.
    if (fs_hz_ == 8000) {
      muting_window = DspHelper::kMuteFactorStart8kHz;
      muting_window_increment = DspHelper::kMuteFactorIncrement8kHz;
      unmuting_window = DspHelper::kUnmuteFactorStart8kHz;
      unmuting_window_increment = DspHelper::kUnmuteFactorIncrement8kHz;
    } else if (fs_hz_ == 16000) {
      muting_window = DspHelper::kMuteFactorStart16kHz;
      muting_window_increment = DspHelper::kMuteFactorIncrement16kHz;
      unmuting_window = DspHelper::kUnmuteFactorStart16kHz;
      unmuting_window_increment = DspHelper::kUnmuteFactorIncrement16kHz;
    } else if (fs_hz_ == 32000) {
      muting_window = DspHelper::kMuteFactorStart32kHz;
      muting_window_increment = DspHelper::kMuteFactorIncrement32kHz;
      unmuting_window = DspHelper::kUnmuteFactorStart32kHz;
      unmuting_window_increment = DspHelper::kUnmuteFactorIncrement32kHz;
    } else {  // fs_hz_ == 48000
      muting_window = DspHelper::kMuteFactorStart48kHz;
      muting_window_increment = DspHelper::kMuteFactorIncrement48kHz;
      unmuting_window = DspHelper::kUnmuteFactorStart48kHz;
      unmuting_window_increment = DspHelper::kUnmuteFactorIncrement48kHz;
    }

    // Do overlap-add between new vector and overlap.
    size_t start_ix = sync_buffer_->Size() - overlap_length_;
    for (size_t i = 0; i < overlap_length_; i++) {
      /* overlapVec[i] = WinMute * overlapVec[i] + WinUnMute * outData[i] */
      // The expression (*output)[0][i] is the i-th element in the first
      // channel.
      (*sync_buffer_)[0][start_ix + i] =
          (((*sync_buffer_)[0][start_ix + i] * muting_window) +
              ((*output)[0][i] * unmuting_window) + 16384) >> 15;
      muting_window += muting_window_increment;
      unmuting_window += unmuting_window_increment;
    }
    // Remove |overlap_length_| samples from the front of |output| since they
    // were mixed into |sync_buffer_| above.
    output->PopFront(overlap_length_);
  }
  first_call_ = false;
  return kOK;
}

}  // namespace webrtc
