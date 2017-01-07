/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/modules/audio_coding/neteq/timestamp_scaler.h"

#include "webrtc/modules/audio_coding/neteq/decoder_database.h"
#include "webrtc/modules/audio_coding/neteq/defines.h"
#include "webrtc/system_wrappers/include/logging.h"

namespace webrtc {

void TimestampScaler::Reset() {
  first_packet_received_ = false;
}

void TimestampScaler::ToInternal(Packet* packet) {
  if (!packet) {
    return;
  }
  packet->header.timestamp = ToInternal(packet->header.timestamp,
                                        packet->header.payloadType);
}

void TimestampScaler::ToInternal(PacketList* packet_list) {
  PacketList::iterator it;
  for (it = packet_list->begin(); it != packet_list->end(); ++it) {
    ToInternal(*it);
  }
}

uint32_t TimestampScaler::ToInternal(uint32_t external_timestamp,
                                     uint8_t rtp_payload_type) {
  const DecoderDatabase::DecoderInfo* info =
      decoder_database_.GetDecoderInfo(rtp_payload_type);
  if (!info) {
    // Payload type is unknown. Do not scale.
    return external_timestamp;
  }
  switch (info->codec_type) {
    case NetEqDecoder::kDecoderG722:
    case NetEqDecoder::kDecoderG722_2ch: {
      // Use timestamp scaling with factor 2 (two output samples per RTP
      // timestamp).
      numerator_ = 2;
      denominator_ = 1;
      break;
    }
    case NetEqDecoder::kDecoderAVT:
    case NetEqDecoder::kDecoderCNGnb:
    case NetEqDecoder::kDecoderCNGwb:
    case NetEqDecoder::kDecoderCNGswb32kHz:
    case NetEqDecoder::kDecoderCNGswb48kHz: {
      // Do not change the timestamp scaling settings for DTMF or CNG.
      break;
    }
    default: {
      // Do not use timestamp scaling for any other codec.
      numerator_ = 1;
      denominator_ = 1;
      break;
    }
  }

  if (!(numerator_ == 1 && denominator_ == 1)) {
    // We have a scale factor != 1.
    if (!first_packet_received_) {
      external_ref_ = external_timestamp;
      internal_ref_ = external_timestamp;
      first_packet_received_ = true;
    }
    int64_t external_diff = external_timestamp - external_ref_;
    assert(denominator_ > 0);  // Should not be possible.
    external_ref_ = external_timestamp;
    internal_ref_ += (external_diff * numerator_) / denominator_;
    return internal_ref_;
  } else {
    // No scaling.
    return external_timestamp;
  }
}


uint32_t TimestampScaler::ToExternal(uint32_t internal_timestamp) const {
  if (!first_packet_received_ || (numerator_ == 1 && denominator_ == 1)) {
    // Not initialized, or scale factor is 1.
    return internal_timestamp;
  } else {
    int64_t internal_diff = internal_timestamp - internal_ref_;
    assert(numerator_ > 0);  // Should not be possible.
    // Do not update references in this method.
    // Switch |denominator_| and |numerator_| to convert the other way.
    return external_ref_ + (internal_diff * denominator_) / numerator_;
  }
}

}  // namespace webrtc
