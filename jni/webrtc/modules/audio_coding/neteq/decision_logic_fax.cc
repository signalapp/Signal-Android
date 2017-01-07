/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/modules/audio_coding/neteq/decision_logic_fax.h"

#include <assert.h>

#include <algorithm>

#include "webrtc/modules/audio_coding/neteq/decoder_database.h"
#include "webrtc/modules/audio_coding/neteq/sync_buffer.h"

namespace webrtc {

Operations DecisionLogicFax::GetDecisionSpecialized(
    const SyncBuffer& sync_buffer,
    const Expand& expand,
    size_t decoder_frame_length,
    const RTPHeader* packet_header,
    Modes prev_mode,
    bool play_dtmf,
    bool* reset_decoder,
    size_t generated_noise_samples) {
  assert(playout_mode_ == kPlayoutFax || playout_mode_ == kPlayoutOff);
  uint32_t target_timestamp = sync_buffer.end_timestamp();
  uint32_t available_timestamp = 0;
  int is_cng_packet = 0;
  if (packet_header) {
    available_timestamp = packet_header->timestamp;
    is_cng_packet =
        decoder_database_->IsComfortNoise(packet_header->payloadType);
  }
  if (is_cng_packet) {
    if (static_cast<int32_t>((generated_noise_samples + target_timestamp)
        - available_timestamp) >= 0) {
      // Time to play this packet now.
      return kRfc3389Cng;
    } else {
      // Wait before playing this packet.
      return kRfc3389CngNoPacket;
    }
  }
  if (!packet_header) {
    // No packet. If in CNG mode, play as usual. Otherwise, use other method to
    // generate data.
    if (cng_state_ == kCngRfc3389On) {
      // Continue playing comfort noise.
      return kRfc3389CngNoPacket;
    } else if (cng_state_ == kCngInternalOn) {
      // Continue playing codec-internal comfort noise.
      return kCodecInternalCng;
    } else {
      // Nothing to play. Generate some data to play out.
      switch (playout_mode_) {
        case kPlayoutOff:
          return kAlternativePlc;
        case kPlayoutFax:
          return kAudioRepetition;
        default:
          assert(false);
          return kUndefined;
      }
    }
  } else if (target_timestamp == available_timestamp) {
    return kNormal;
  } else {
    if (static_cast<int32_t>((generated_noise_samples + target_timestamp)
        - available_timestamp) >= 0) {
      return kNormal;
    } else {
      // If currently playing comfort noise, continue with that. Do not
      // increase the timestamp counter since generated_noise_stopwatch_ in
      // NetEqImpl will take care of the time-keeping.
      if (cng_state_ == kCngRfc3389On) {
        return kRfc3389CngNoPacket;
      } else if (cng_state_ == kCngInternalOn) {
        return kCodecInternalCng;
      } else {
        // Otherwise, do packet-loss concealment and increase the
        // timestamp while waiting for the time to play this packet.
        switch (playout_mode_) {
          case kPlayoutOff:
            return kAlternativePlcIncreaseTimestamp;
          case kPlayoutFax:
            return kAudioRepetitionIncreaseTimestamp;
          default:
            assert(0);
            return kUndefined;
        }
      }
    }
  }
}


}  // namespace webrtc
