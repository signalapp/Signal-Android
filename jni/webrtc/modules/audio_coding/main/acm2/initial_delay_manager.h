/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_MODULES_AUDIO_CODING_MAIN_ACM2_INITIAL_DELAY_MANAGER_H_
#define WEBRTC_MODULES_AUDIO_CODING_MAIN_ACM2_INITIAL_DELAY_MANAGER_H_

#include "webrtc/modules/interface/module_common_types.h"
#include "webrtc/system_wrappers/interface/scoped_ptr.h"

namespace webrtc {

namespace acm2 {

class InitialDelayManager {
 public:
  enum PacketType {
    kUndefinedPacket, kCngPacket, kAvtPacket, kAudioPacket, kSyncPacket };

  // Specifies a stream of sync-packets.
  struct SyncStream {
    SyncStream()
        : num_sync_packets(0),
          receive_timestamp(0),
          timestamp_step(0) {
      memset(&rtp_info, 0, sizeof(rtp_info));
    }

    int num_sync_packets;

    // RTP header of the first sync-packet in the sequence.
    WebRtcRTPHeader rtp_info;

    // Received timestamp of the first sync-packet in the sequence.
    uint32_t receive_timestamp;

    // Samples per packet.
    uint32_t timestamp_step;
  };

  InitialDelayManager(int initial_delay_ms, int late_packet_threshold);

  // Update with the last received RTP header, |header|, and received timestamp,
  // |received_timestamp|. |type| indicates the packet type. If codec is changed
  // since the last time |new_codec| should be true. |sample_rate_hz| is the
  // decoder's sampling rate in Hz. |header| has a field to store sampling rate
  // but we are not sure if that is properly set at the send side, and |header|
  // is declared constant in the caller of this function
  // (AcmReceiver::InsertPacket()). |sync_stream| contains information required
  // to generate a stream of sync packets.
  void UpdateLastReceivedPacket(const WebRtcRTPHeader& header,
                                uint32_t receive_timestamp,
                                PacketType type,
                                bool new_codec,
                                int sample_rate_hz,
                                SyncStream* sync_stream);

  // Based on the last received timestamp and given the current timestamp,
  // sequence of late (or perhaps missing) packets is computed.
  void LatePackets(uint32_t timestamp_now, SyncStream* sync_stream);

  // Get playout timestamp.
  // Returns true if the timestamp is valid (when buffering), otherwise false.
  bool GetPlayoutTimestamp(uint32_t* playout_timestamp);

  // True if buffered audio is less than the given initial delay (specified at
  // the constructor). Buffering might be disabled by the client of this class.
  bool buffering() { return buffering_; }

  // Disable buffering in the class.
  void DisableBuffering();

  // True if any packet received for buffering.
  bool PacketBuffered() { return last_packet_type_ != kUndefinedPacket; }

 private:
  static const uint8_t kInvalidPayloadType = 0xFF;

  // Update playout timestamps. While buffering, this is about
  // |initial_delay_ms| millisecond behind the latest received timestamp.
  void UpdatePlayoutTimestamp(const RTPHeader& current_header,
                              int sample_rate_hz);

  // Record an RTP headr and related parameter
  void RecordLastPacket(const WebRtcRTPHeader& rtp_info,
                        uint32_t receive_timestamp,
                        PacketType type);

  PacketType last_packet_type_;
  WebRtcRTPHeader last_packet_rtp_info_;
  uint32_t last_receive_timestamp_;
  uint32_t timestamp_step_;
  uint8_t audio_payload_type_;
  const int initial_delay_ms_;
  int buffered_audio_ms_;
  bool buffering_;

  // During the initial phase where packets are being accumulated and silence
  // is played out, |playout_ts| is a timestamp which is equal to
  // |initial_delay_ms_| milliseconds earlier than the most recently received
  // RTP timestamp.
  uint32_t playout_timestamp_;

  // If the number of late packets exceed this value (computed based on current
  // timestamp and last received timestamp), sequence of sync-packets is
  // specified.
  const int late_packet_threshold_;
};

}  // namespace acm2

}  // namespace webrtc

#endif  // WEBRTC_MODULES_AUDIO_CODING_MAIN_ACM2_INITIAL_DELAY_MANAGER_H_
