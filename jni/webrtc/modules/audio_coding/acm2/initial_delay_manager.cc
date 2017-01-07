/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/modules/audio_coding/acm2/initial_delay_manager.h"

namespace webrtc {

namespace acm2 {

InitialDelayManager::InitialDelayManager(int initial_delay_ms,
                                         int late_packet_threshold)
    : last_packet_type_(kUndefinedPacket),
      last_receive_timestamp_(0),
      timestamp_step_(0),
      audio_payload_type_(kInvalidPayloadType),
      initial_delay_ms_(initial_delay_ms),
      buffered_audio_ms_(0),
      buffering_(true),
      playout_timestamp_(0),
      late_packet_threshold_(late_packet_threshold) {
  last_packet_rtp_info_.header.payloadType = kInvalidPayloadType;
  last_packet_rtp_info_.header.ssrc = 0;
  last_packet_rtp_info_.header.sequenceNumber = 0;
  last_packet_rtp_info_.header.timestamp = 0;
}

void InitialDelayManager::UpdateLastReceivedPacket(
    const WebRtcRTPHeader& rtp_info,
    uint32_t receive_timestamp,
    PacketType type,
    bool new_codec,
    int sample_rate_hz,
    SyncStream* sync_stream) {
  assert(sync_stream);

  // If payload of audio packets is changing |new_codec| has to be true.
  assert(!(!new_codec && type == kAudioPacket &&
         rtp_info.header.payloadType != audio_payload_type_));

  // Just shorthands.
  const RTPHeader* current_header = &rtp_info.header;
  RTPHeader* last_header = &last_packet_rtp_info_.header;

  // Don't do anything if getting DTMF. The chance of DTMF in applications where
  // initial delay is required is very low (we don't know of any). This avoids a
  // lot of corner cases. The effect of ignoring DTMF packet is minimal. Note
  // that DTMFs are inserted into NetEq just not accounted here.
  if (type == kAvtPacket ||
      (last_packet_type_ != kUndefinedPacket &&
      !IsNewerSequenceNumber(current_header->sequenceNumber,
                             last_header->sequenceNumber))) {
    sync_stream->num_sync_packets = 0;
    return;
  }

  // Either if it is a new packet or the first packet record and set variables.
  if (new_codec ||
      last_packet_rtp_info_.header.payloadType == kInvalidPayloadType) {
    timestamp_step_ = 0;
    if (type == kAudioPacket)
      audio_payload_type_ = rtp_info.header.payloadType;
    else
      audio_payload_type_ = kInvalidPayloadType;  // Invalid.

    RecordLastPacket(rtp_info, receive_timestamp, type);
    sync_stream->num_sync_packets = 0;
    buffered_audio_ms_ = 0;
    buffering_ = true;

    // If |buffering_| is set then |playout_timestamp_| should have correct
    // value.
    UpdatePlayoutTimestamp(*current_header, sample_rate_hz);
    return;
  }

  uint32_t timestamp_increase = current_header->timestamp -
      last_header->timestamp;

  // |timestamp_increase| is invalid if this is the first packet. The effect is
  // that |buffered_audio_ms_| is not increased.
  if (last_packet_type_ == kUndefinedPacket) {
    timestamp_increase = 0;
  }

  if (buffering_) {
    buffered_audio_ms_ += timestamp_increase * 1000 / sample_rate_hz;

    // A timestamp that reflects the initial delay, while buffering.
    UpdatePlayoutTimestamp(*current_header, sample_rate_hz);

    if (buffered_audio_ms_ >= initial_delay_ms_)
      buffering_ = false;
  }

  if (current_header->sequenceNumber == last_header->sequenceNumber + 1) {
    // Two consecutive audio packets, the previous packet-type is audio, so we
    // can update |timestamp_step_|.
    if (last_packet_type_ == kAudioPacket)
      timestamp_step_ = timestamp_increase;
    RecordLastPacket(rtp_info, receive_timestamp, type);
    sync_stream->num_sync_packets = 0;
    return;
  }

  uint16_t packet_gap = current_header->sequenceNumber -
      last_header->sequenceNumber - 1;

  // For smooth transitions leave a gap between audio and sync packets.
  sync_stream->num_sync_packets = last_packet_type_ == kSyncPacket ?
      packet_gap - 1 : packet_gap - 2;

  // Do nothing if we haven't received any audio packet.
  if (sync_stream->num_sync_packets > 0 &&
      audio_payload_type_ != kInvalidPayloadType) {
    if (timestamp_step_ == 0) {
      // Make an estimate for |timestamp_step_| if it is not updated, yet.
      assert(packet_gap > 0);
      timestamp_step_ = timestamp_increase / (packet_gap + 1);
    }
    sync_stream->timestamp_step = timestamp_step_;

    // Build the first sync-packet based on the current received packet.
    memcpy(&sync_stream->rtp_info, &rtp_info, sizeof(rtp_info));
    sync_stream->rtp_info.header.payloadType = audio_payload_type_;

    uint16_t sequence_number_update = sync_stream->num_sync_packets + 1;
    uint32_t timestamp_update = timestamp_step_ * sequence_number_update;

    // Rewind sequence number and timestamps. This will give a more accurate
    // description of the missing packets.
    //
    // Note that we leave a gap between the last packet in sync-stream and the
    // current received packet, so it should be compensated for in the following
    // computation of timestamps and sequence number.
    sync_stream->rtp_info.header.sequenceNumber -= sequence_number_update;
    sync_stream->receive_timestamp = receive_timestamp - timestamp_update;
    sync_stream->rtp_info.header.timestamp -= timestamp_update;
    sync_stream->rtp_info.header.payloadType = audio_payload_type_;
  } else {
    sync_stream->num_sync_packets = 0;
  }

  RecordLastPacket(rtp_info, receive_timestamp, type);
  return;
}

void InitialDelayManager::RecordLastPacket(const WebRtcRTPHeader& rtp_info,
                                           uint32_t receive_timestamp,
                                           PacketType type) {
  last_packet_type_ = type;
  last_receive_timestamp_ = receive_timestamp;
  memcpy(&last_packet_rtp_info_, &rtp_info, sizeof(rtp_info));
}

void InitialDelayManager::LatePackets(
    uint32_t timestamp_now, SyncStream* sync_stream) {
  assert(sync_stream);
  sync_stream->num_sync_packets = 0;

  // If there is no estimate of timestamp increment, |timestamp_step_|, then
  // we cannot estimate the number of late packets.
  // If the last packet has been CNG, estimating late packets is not meaningful,
  // as a CNG packet is on unknown length.
  // We can set a higher threshold if the last packet is CNG and continue
  // execution, but this is how ACM1 code was written.
  if (timestamp_step_ <= 0 ||
      last_packet_type_ == kCngPacket ||
      last_packet_type_ == kUndefinedPacket ||
      audio_payload_type_ == kInvalidPayloadType)  // No audio packet received.
    return;

  int num_late_packets = (timestamp_now - last_receive_timestamp_) /
      timestamp_step_;

  if (num_late_packets < late_packet_threshold_)
    return;

  int sync_offset = 1;  // One gap at the end of the sync-stream.
  if (last_packet_type_ != kSyncPacket) {
    ++sync_offset;  // One more gap at the beginning of the sync-stream.
    --num_late_packets;
  }
  uint32_t timestamp_update = sync_offset * timestamp_step_;

  sync_stream->num_sync_packets = num_late_packets;
  if (num_late_packets == 0)
    return;

  // Build the first sync-packet in the sync-stream.
  memcpy(&sync_stream->rtp_info, &last_packet_rtp_info_,
         sizeof(last_packet_rtp_info_));

  // Increase sequence number and timestamps.
  sync_stream->rtp_info.header.sequenceNumber += sync_offset;
  sync_stream->rtp_info.header.timestamp += timestamp_update;
  sync_stream->receive_timestamp = last_receive_timestamp_ + timestamp_update;
  sync_stream->timestamp_step = timestamp_step_;

  // Sync-packets have audio payload-type.
  sync_stream->rtp_info.header.payloadType = audio_payload_type_;

  uint16_t sequence_number_update = num_late_packets + sync_offset - 1;
  timestamp_update = sequence_number_update * timestamp_step_;

  // Fake the last RTP, assuming the caller will inject the whole sync-stream.
  last_packet_rtp_info_.header.timestamp += timestamp_update;
  last_packet_rtp_info_.header.sequenceNumber += sequence_number_update;
  last_packet_rtp_info_.header.payloadType = audio_payload_type_;
  last_receive_timestamp_ += timestamp_update;

  last_packet_type_ = kSyncPacket;
  return;
}

bool InitialDelayManager::GetPlayoutTimestamp(uint32_t* playout_timestamp) {
  if (!buffering_) {
    return false;
  }
  *playout_timestamp = playout_timestamp_;
  return true;
}

void InitialDelayManager::DisableBuffering() {
  buffering_ = false;
}

void InitialDelayManager::UpdatePlayoutTimestamp(
    const RTPHeader& current_header, int sample_rate_hz) {
  playout_timestamp_ = current_header.timestamp - static_cast<uint32_t>(
      initial_delay_ms_ * sample_rate_hz / 1000);
}

}  // namespace acm2

}  // namespace webrtc
