/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_MODULES_AUDIO_CODING_NETEQ_PAYLOAD_SPLITTER_H_
#define WEBRTC_MODULES_AUDIO_CODING_NETEQ_PAYLOAD_SPLITTER_H_

#include "webrtc/base/constructormagic.h"
#include "webrtc/modules/audio_coding/neteq/packet.h"

namespace webrtc {

// Forward declarations.
class DecoderDatabase;

// This class handles splitting of payloads into smaller parts.
// The class does not have any member variables, and the methods could have
// been made static. The reason for not making them static is testability.
// With this design, the splitting functionality can be mocked during testing
// of the NetEqImpl class.
class PayloadSplitter {
 public:
  enum SplitterReturnCodes {
    kOK = 0,
    kNoSplit = 1,
    kTooLargePayload = -1,
    kFrameSplitError = -2,
    kUnknownPayloadType = -3,
    kRedLengthMismatch = -4,
    kFecSplitError = -5,
  };

  PayloadSplitter() {}

  virtual ~PayloadSplitter() {}

  // Splits each packet in |packet_list| into its separate RED payloads. Each
  // RED payload is packetized into a Packet. The original elements in
  // |packet_list| are properly deleted, and replaced by the new packets.
  // Note that all packets in |packet_list| must be RED payloads, i.e., have
  // RED headers according to RFC 2198 at the very beginning of the payload.
  // Returns kOK or an error.
  virtual int SplitRed(PacketList* packet_list);

  // Iterates through |packet_list| and, duplicate each audio payload that has
  // FEC as new packet for redundant decoding. The decoder database is needed to
  // get information about which payload type each packet contains.
  virtual int SplitFec(PacketList* packet_list,
                       DecoderDatabase* decoder_database);

  // Checks all packets in |packet_list|. Packets that are DTMF events or
  // comfort noise payloads are kept. Except that, only one single payload type
  // is accepted. Any packet with another payload type is discarded.
  virtual int CheckRedPayloads(PacketList* packet_list,
                               const DecoderDatabase& decoder_database);

  // Iterates through |packet_list| and, if possible, splits each audio payload
  // into suitable size chunks. The result is written back to |packet_list| as
  // new packets. The decoder database is needed to get information about which
  // payload type each packet contains.
  virtual int SplitAudio(PacketList* packet_list,
                         const DecoderDatabase& decoder_database);

 private:
  // Splits the payload in |packet|. The payload is assumed to be from a
  // sample-based codec.
  virtual void SplitBySamples(const Packet* packet,
                              int bytes_per_ms,
                              int timestamps_per_ms,
                              PacketList* new_packets);

  // Splits the payload in |packet|. The payload will be split into chunks of
  // size |bytes_per_frame|, corresponding to a |timestamps_per_frame|
  // RTP timestamps.
  virtual int SplitByFrames(const Packet* packet,
                            int bytes_per_frame,
                            int timestamps_per_frame,
                            PacketList* new_packets);

  DISALLOW_COPY_AND_ASSIGN(PayloadSplitter);
};

}  // namespace webrtc
#endif  // WEBRTC_MODULES_AUDIO_CODING_NETEQ_PAYLOAD_SPLITTER_H_
