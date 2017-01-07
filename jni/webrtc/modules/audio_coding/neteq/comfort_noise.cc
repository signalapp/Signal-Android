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

#include "webrtc/base/logging.h"
#include "webrtc/modules/audio_coding/codecs/audio_decoder.h"
#include "webrtc/modules/audio_coding/neteq/decoder_database.h"
#include "webrtc/modules/audio_coding/neteq/dsp_helper.h"
#include "webrtc/modules/audio_coding/neteq/sync_buffer.h"

namespace webrtc {

void ComfortNoise::Reset() {
  first_call_ = true;
}

int ComfortNoise::UpdateParameters(Packet* packet) {
  assert(packet);  // Existence is verified by caller.
  // Get comfort noise decoder.
  if (decoder_database_->SetActiveCngDecoder(packet->header.payloadType)
      != kOK) {
    delete [] packet->payload;
    delete packet;
    return kUnknownPayloadType;
  }
  ComfortNoiseDecoder* cng_decoder = decoder_database_->GetActiveCngDecoder();
  RTC_DCHECK(cng_decoder);
  cng_decoder->UpdateSid(rtc::ArrayView<const uint8_t>(
      packet->payload, packet->payload_length));
  delete [] packet->payload;
  delete packet;
  return kOK;
}

int ComfortNoise::Generate(size_t requested_length,
                           AudioMultiVector* output) {
  // TODO(hlundin): Change to an enumerator and skip assert.
  assert(fs_hz_ == 8000 || fs_hz_ == 16000 || fs_hz_ ==  32000 ||
         fs_hz_ == 48000);
  // Not adapted for multi-channel yet.
  if (output->Channels() != 1) {
    LOG(LS_ERROR) << "No multi-channel support";
    return kMultiChannelNotSupported;
  }

  size_t number_of_samples = requested_length;
  bool new_period = false;
  if (first_call_) {
    // Generate noise and overlap slightly with old data.
    number_of_samples = requested_length + overlap_length_;
    new_period = true;
  }
  output->AssertSize(number_of_samples);
  // Get the decoder from the database.
  ComfortNoiseDecoder* cng_decoder = decoder_database_->GetActiveCngDecoder();
  if (!cng_decoder) {
    LOG(LS_ERROR) << "Unknwown payload type";
    return kUnknownPayloadType;
  }

  std::unique_ptr<int16_t[]> temp(new int16_t[number_of_samples]);
  if (!cng_decoder->Generate(
          rtc::ArrayView<int16_t>(temp.get(), number_of_samples),
          new_period)) {
    // Error returned.
    output->Zeros(requested_length);
    LOG(LS_ERROR) <<
        "ComfortNoiseDecoder::Genererate failed to generate comfort noise";
    return kInternalError;
  }
  (*output)[0].OverwriteAt(temp.get(), number_of_samples, 0);

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
