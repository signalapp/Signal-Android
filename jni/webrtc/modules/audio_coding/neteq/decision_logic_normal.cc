/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/modules/audio_coding/neteq/decision_logic_normal.h"

#include <assert.h>

#include <algorithm>

#include "webrtc/modules/audio_coding/neteq/buffer_level_filter.h"
#include "webrtc/modules/audio_coding/neteq/decoder_database.h"
#include "webrtc/modules/audio_coding/neteq/delay_manager.h"
#include "webrtc/modules/audio_coding/neteq/expand.h"
#include "webrtc/modules/audio_coding/neteq/packet_buffer.h"
#include "webrtc/modules/audio_coding/neteq/sync_buffer.h"
#include "webrtc/modules/include/module_common_types.h"

namespace webrtc {

Operations DecisionLogicNormal::GetDecisionSpecialized(
    const SyncBuffer& sync_buffer,
    const Expand& expand,
    size_t decoder_frame_length,
    const RTPHeader* packet_header,
    Modes prev_mode,
    bool play_dtmf,
    bool* reset_decoder,
    size_t generated_noise_samples) {
  assert(playout_mode_ == kPlayoutOn || playout_mode_ == kPlayoutStreaming);
  // Guard for errors, to avoid getting stuck in error mode.
  if (prev_mode == kModeError) {
    if (!packet_header) {
      return kExpand;
    } else {
      return kUndefined;  // Use kUndefined to flag for a reset.
    }
  }

  uint32_t target_timestamp = sync_buffer.end_timestamp();
  uint32_t available_timestamp = 0;
  bool is_cng_packet = false;
  if (packet_header) {
    available_timestamp = packet_header->timestamp;
    is_cng_packet =
        decoder_database_->IsComfortNoise(packet_header->payloadType);
  }

  if (is_cng_packet) {
    return CngOperation(prev_mode, target_timestamp, available_timestamp,
                        generated_noise_samples);
  }

  // Handle the case with no packet at all available (except maybe DTMF).
  if (!packet_header) {
    return NoPacket(play_dtmf);
  }

  // If the expand period was very long, reset NetEQ since it is likely that the
  // sender was restarted.
  if (num_consecutive_expands_ > kReinitAfterExpands) {
    *reset_decoder = true;
    return kNormal;
  }

  const uint32_t five_seconds_samples =
      static_cast<uint32_t>(5 * 8000 * fs_mult_);
  // Check if the required packet is available.
  if (target_timestamp == available_timestamp) {
    return ExpectedPacketAvailable(prev_mode, play_dtmf);
  } else if (!PacketBuffer::IsObsoleteTimestamp(
                 available_timestamp, target_timestamp, five_seconds_samples)) {
    return FuturePacketAvailable(sync_buffer, expand, decoder_frame_length,
                                 prev_mode, target_timestamp,
                                 available_timestamp, play_dtmf,
                                 generated_noise_samples);
  } else {
    // This implies that available_timestamp < target_timestamp, which can
    // happen when a new stream or codec is received. Signal for a reset.
    return kUndefined;
  }
}

Operations DecisionLogicNormal::CngOperation(Modes prev_mode,
                                             uint32_t target_timestamp,
                                             uint32_t available_timestamp,
                                             size_t generated_noise_samples) {
  // Signed difference between target and available timestamp.
  int32_t timestamp_diff = static_cast<int32_t>(
      static_cast<uint32_t>(generated_noise_samples + target_timestamp) -
      available_timestamp);
  int32_t optimal_level_samp = static_cast<int32_t>(
      (delay_manager_->TargetLevel() * packet_length_samples_) >> 8);
  int32_t excess_waiting_time_samp = -timestamp_diff - optimal_level_samp;

  if (excess_waiting_time_samp > optimal_level_samp / 2) {
    // The waiting time for this packet will be longer than 1.5
    // times the wanted buffer delay. Apply fast-forward to cut the
    // waiting time down to the optimal.
    noise_fast_forward_ += excess_waiting_time_samp;
    timestamp_diff += excess_waiting_time_samp;
  }

  if (timestamp_diff < 0 && prev_mode == kModeRfc3389Cng) {
    // Not time to play this packet yet. Wait another round before using this
    // packet. Keep on playing CNG from previous CNG parameters.
    return kRfc3389CngNoPacket;
  } else {
    // Otherwise, go for the CNG packet now.
    noise_fast_forward_ = 0;
    return kRfc3389Cng;
  }
}

Operations DecisionLogicNormal::NoPacket(bool play_dtmf) {
  if (cng_state_ == kCngRfc3389On) {
    // Keep on playing comfort noise.
    return kRfc3389CngNoPacket;
  } else if (cng_state_ == kCngInternalOn) {
    // Keep on playing codec internal comfort noise.
    return kCodecInternalCng;
  } else if (play_dtmf) {
    return kDtmf;
  } else {
    // Nothing to play, do expand.
    return kExpand;
  }
}

Operations DecisionLogicNormal::ExpectedPacketAvailable(Modes prev_mode,
                                                        bool play_dtmf) {
  if (prev_mode != kModeExpand && !play_dtmf) {
    // Check criterion for time-stretching.
    int low_limit, high_limit;
    delay_manager_->BufferLimits(&low_limit, &high_limit);
    if (buffer_level_filter_->filtered_current_level() >= high_limit << 2)
      return kFastAccelerate;
    if (TimescaleAllowed()) {
      if (buffer_level_filter_->filtered_current_level() >= high_limit)
        return kAccelerate;
      if (buffer_level_filter_->filtered_current_level() < low_limit)
        return kPreemptiveExpand;
    }
  }
  return kNormal;
}

Operations DecisionLogicNormal::FuturePacketAvailable(
    const SyncBuffer& sync_buffer,
    const Expand& expand,
    size_t decoder_frame_length,
    Modes prev_mode,
    uint32_t target_timestamp,
    uint32_t available_timestamp,
    bool play_dtmf,
    size_t generated_noise_samples) {
  // Required packet is not available, but a future packet is.
  // Check if we should continue with an ongoing expand because the new packet
  // is too far into the future.
  uint32_t timestamp_leap = available_timestamp - target_timestamp;
  if ((prev_mode == kModeExpand) &&
      !ReinitAfterExpands(timestamp_leap) &&
      !MaxWaitForPacket() &&
      PacketTooEarly(timestamp_leap) &&
      UnderTargetLevel()) {
    if (play_dtmf) {
      // Still have DTMF to play, so do not do expand.
      return kDtmf;
    } else {
      // Nothing to play.
      return kExpand;
    }
  }

  const size_t samples_left =
      sync_buffer.FutureLength() - expand.overlap_length();
  const size_t cur_size_samples = samples_left +
      packet_buffer_.NumPacketsInBuffer() * decoder_frame_length;

  // If previous was comfort noise, then no merge is needed.
  if (prev_mode == kModeRfc3389Cng ||
      prev_mode == kModeCodecInternalCng) {
    // Keep the same delay as before the CNG (or maximum 70 ms in buffer as
    // safety precaution), but make sure that the number of samples in buffer
    // is no higher than 4 times the optimal level. (Note that TargetLevel()
    // is in Q8.)
    if (static_cast<uint32_t>(generated_noise_samples + target_timestamp) >=
            available_timestamp ||
        cur_size_samples >
            ((delay_manager_->TargetLevel() * packet_length_samples_) >> 8) *
            4) {
      // Time to play this new packet.
      return kNormal;
    } else {
      // Too early to play this new packet; keep on playing comfort noise.
      if (prev_mode == kModeRfc3389Cng) {
        return kRfc3389CngNoPacket;
      } else {  // prevPlayMode == kModeCodecInternalCng.
        return kCodecInternalCng;
      }
    }
  }
  // Do not merge unless we have done an expand before.
  // (Convert kAllowMergeWithoutExpand from ms to samples by multiplying with
  // fs_mult_ * 8 = fs / 1000.)
  if (prev_mode == kModeExpand ||
      (decoder_frame_length < output_size_samples_ &&
       cur_size_samples >
           static_cast<size_t>(kAllowMergeWithoutExpandMs * fs_mult_ * 8))) {
    return kMerge;
  } else if (play_dtmf) {
    // Play DTMF instead of expand.
    return kDtmf;
  } else {
    return kExpand;
  }
}

bool DecisionLogicNormal::UnderTargetLevel() const {
  return buffer_level_filter_->filtered_current_level() <=
      delay_manager_->TargetLevel();
}

bool DecisionLogicNormal::ReinitAfterExpands(uint32_t timestamp_leap) const {
  return timestamp_leap >=
      static_cast<uint32_t>(output_size_samples_ * kReinitAfterExpands);
}

bool DecisionLogicNormal::PacketTooEarly(uint32_t timestamp_leap) const {
  return timestamp_leap >
      static_cast<uint32_t>(output_size_samples_ * num_consecutive_expands_);
}

bool DecisionLogicNormal::MaxWaitForPacket() const {
  return num_consecutive_expands_ >= kMaxWaitForPacket;
}

}  // namespace webrtc
