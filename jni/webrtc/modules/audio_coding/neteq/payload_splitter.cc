/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/modules/audio_coding/neteq/payload_splitter.h"

#include <assert.h>

#include "webrtc/modules/audio_coding/neteq/decoder_database.h"

namespace webrtc {

// The method loops through a list of packets {A, B, C, ...}. Each packet is
// split into its corresponding RED payloads, {A1, A2, ...}, which is
// temporarily held in the list |new_packets|.
// When the first packet in |packet_list| has been processed, the orignal packet
// is replaced by the new ones in |new_packets|, so that |packet_list| becomes:
// {A1, A2, ..., B, C, ...}. The method then continues with B, and C, until all
// the original packets have been replaced by their split payloads.
int PayloadSplitter::SplitRed(PacketList* packet_list) {
  int ret = kOK;
  PacketList::iterator it = packet_list->begin();
  while (it != packet_list->end()) {
    PacketList new_packets;  // An empty list to store the split packets in.
    Packet* red_packet = (*it);
    assert(red_packet->payload);
    uint8_t* payload_ptr = red_packet->payload;

    // Read RED headers (according to RFC 2198):
    //
    //    0                   1                   2                   3
    //    0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
    //   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    //   |F|   block PT  |  timestamp offset         |   block length    |
    //   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    // Last RED header:
    //    0 1 2 3 4 5 6 7
    //   +-+-+-+-+-+-+-+-+
    //   |0|   Block PT  |
    //   +-+-+-+-+-+-+-+-+

    bool last_block = false;
    int sum_length = 0;
    while (!last_block) {
      Packet* new_packet = new Packet;
      new_packet->header = red_packet->header;
      // Check the F bit. If F == 0, this was the last block.
      last_block = ((*payload_ptr & 0x80) == 0);
      // Bits 1 through 7 are payload type.
      new_packet->header.payloadType = payload_ptr[0] & 0x7F;
      if (last_block) {
        // No more header data to read.
        ++sum_length;  // Account for RED header size of 1 byte.
        new_packet->payload_length = red_packet->payload_length - sum_length;
        new_packet->primary = true;  // Last block is always primary.
        payload_ptr += 1;  // Advance to first payload byte.
      } else {
        // Bits 8 through 21 are timestamp offset.
        int timestamp_offset = (payload_ptr[1] << 6) +
            ((payload_ptr[2] & 0xFC) >> 2);
        new_packet->header.timestamp = red_packet->header.timestamp -
            timestamp_offset;
        // Bits 22 through 31 are payload length.
        new_packet->payload_length = ((payload_ptr[2] & 0x03) << 8) +
            payload_ptr[3];
        new_packet->primary = false;
        payload_ptr += 4;  // Advance to next RED header.
      }
      sum_length += new_packet->payload_length;
      sum_length += 4;  // Account for RED header size of 4 bytes.
      // Store in new list of packets.
      new_packets.push_back(new_packet);
    }

    // Populate the new packets with payload data.
    // |payload_ptr| now points at the first payload byte.
    PacketList::iterator new_it;
    for (new_it = new_packets.begin(); new_it != new_packets.end(); ++new_it) {
      int payload_length = (*new_it)->payload_length;
      if (payload_ptr + payload_length >
          red_packet->payload + red_packet->payload_length) {
        // The block lengths in the RED headers do not match the overall packet
        // length. Something is corrupt. Discard this and the remaining
        // payloads from this packet.
        while (new_it != new_packets.end()) {
          // Payload should not have been allocated yet.
          assert(!(*new_it)->payload);
          delete (*new_it);
          new_it = new_packets.erase(new_it);
        }
        ret = kRedLengthMismatch;
        break;
      }
      (*new_it)->payload = new uint8_t[payload_length];
      memcpy((*new_it)->payload, payload_ptr, payload_length);
      payload_ptr += payload_length;
    }
    // Reverse the order of the new packets, so that the primary payload is
    // always first.
    new_packets.reverse();
    // Insert new packets into original list, before the element pointed to by
    // iterator |it|.
    packet_list->splice(it, new_packets, new_packets.begin(),
                        new_packets.end());
    // Delete old packet payload.
    delete [] (*it)->payload;
    delete (*it);
    // Remove |it| from the packet list. This operation effectively moves the
    // iterator |it| to the next packet in the list. Thus, we do not have to
    // increment it manually.
    it = packet_list->erase(it);
  }
  return ret;
}

int PayloadSplitter::SplitFec(PacketList* packet_list,
                              DecoderDatabase* decoder_database) {
  PacketList::iterator it = packet_list->begin();
  // Iterate through all packets in |packet_list|.
  while (it != packet_list->end()) {
    Packet* packet = (*it);  // Just to make the notation more intuitive.
    // Get codec type for this payload.
    uint8_t payload_type = packet->header.payloadType;
    const DecoderDatabase::DecoderInfo* info =
        decoder_database->GetDecoderInfo(payload_type);
    if (!info) {
      return kUnknownPayloadType;
    }
    // No splitting for a sync-packet.
    if (packet->sync_packet) {
      ++it;
      continue;
    }

    // Not an FEC packet.
    AudioDecoder* decoder = decoder_database->GetDecoder(payload_type);
    // decoder should not return NULL.
    assert(decoder != NULL);
    if (!decoder ||
        !decoder->PacketHasFec(packet->payload, packet->payload_length)) {
      ++it;
      continue;
    }

    switch (info->codec_type) {
      case kDecoderOpus:
      case kDecoderOpus_2ch: {
        Packet* new_packet = new Packet;

        new_packet->header = packet->header;
        int duration = decoder->
            PacketDurationRedundant(packet->payload, packet->payload_length);
        new_packet->header.timestamp -= duration;
        new_packet->payload = new uint8_t[packet->payload_length];
        memcpy(new_packet->payload, packet->payload, packet->payload_length);
        new_packet->payload_length = packet->payload_length;
        new_packet->primary = false;
        new_packet->waiting_time = packet->waiting_time;
        new_packet->sync_packet = packet->sync_packet;

        packet_list->insert(it, new_packet);
        break;
      }
      default: {
        return kFecSplitError;
      }
    }

    ++it;
  }
  return kOK;
}

int PayloadSplitter::CheckRedPayloads(PacketList* packet_list,
                                      const DecoderDatabase& decoder_database) {
  PacketList::iterator it = packet_list->begin();
  int main_payload_type = -1;
  int num_deleted_packets = 0;
  while (it != packet_list->end()) {
    uint8_t this_payload_type = (*it)->header.payloadType;
    if (!decoder_database.IsDtmf(this_payload_type) &&
        !decoder_database.IsComfortNoise(this_payload_type)) {
      if (main_payload_type == -1) {
        // This is the first packet in the list which is non-DTMF non-CNG.
        main_payload_type = this_payload_type;
      } else {
        if (this_payload_type != main_payload_type) {
          // We do not allow redundant payloads of a different type.
          // Discard this payload.
          delete [] (*it)->payload;
          delete (*it);
          // Remove |it| from the packet list. This operation effectively
          // moves the iterator |it| to the next packet in the list. Thus, we
          // do not have to increment it manually.
          it = packet_list->erase(it);
          ++num_deleted_packets;
          continue;
        }
      }
    }
    ++it;
  }
  return num_deleted_packets;
}

int PayloadSplitter::SplitAudio(PacketList* packet_list,
                                const DecoderDatabase& decoder_database) {
  PacketList::iterator it = packet_list->begin();
  // Iterate through all packets in |packet_list|.
  while (it != packet_list->end()) {
    Packet* packet = (*it);  // Just to make the notation more intuitive.
    // Get codec type for this payload.
    const DecoderDatabase::DecoderInfo* info =
        decoder_database.GetDecoderInfo(packet->header.payloadType);
    if (!info) {
      return kUnknownPayloadType;
    }
    // No splitting for a sync-packet.
    if (packet->sync_packet) {
      ++it;
      continue;
    }
    PacketList new_packets;
    switch (info->codec_type) {
      case kDecoderPCMu:
      case kDecoderPCMa: {
        // 8 bytes per ms; 8 timestamps per ms.
        SplitBySamples(packet, 8, 8, &new_packets);
        break;
      }
      case kDecoderPCMu_2ch:
      case kDecoderPCMa_2ch: {
        // 2 * 8 bytes per ms; 8 timestamps per ms.
        SplitBySamples(packet, 2 * 8, 8, &new_packets);
        break;
      }
      case kDecoderG722: {
        // 8 bytes per ms; 16 timestamps per ms.
        SplitBySamples(packet, 8, 16, &new_packets);
        break;
      }
      case kDecoderPCM16B: {
        // 16 bytes per ms; 8 timestamps per ms.
        SplitBySamples(packet, 16, 8, &new_packets);
        break;
      }
      case kDecoderPCM16Bwb: {
        // 32 bytes per ms; 16 timestamps per ms.
        SplitBySamples(packet, 32, 16, &new_packets);
        break;
      }
      case kDecoderPCM16Bswb32kHz: {
        // 64 bytes per ms; 32 timestamps per ms.
        SplitBySamples(packet, 64, 32, &new_packets);
        break;
      }
      case kDecoderPCM16Bswb48kHz: {
        // 96 bytes per ms; 48 timestamps per ms.
        SplitBySamples(packet, 96, 48, &new_packets);
        break;
      }
      case kDecoderPCM16B_2ch: {
        // 2 * 16 bytes per ms; 8 timestamps per ms.
        SplitBySamples(packet, 2 * 16, 8, &new_packets);
        break;
      }
      case kDecoderPCM16Bwb_2ch: {
        // 2 * 32 bytes per ms; 16 timestamps per ms.
        SplitBySamples(packet, 2 * 32, 16, &new_packets);
        break;
      }
      case kDecoderPCM16Bswb32kHz_2ch: {
        // 2 * 64 bytes per ms; 32 timestamps per ms.
        SplitBySamples(packet, 2 * 64, 32, &new_packets);
        break;
      }
      case kDecoderPCM16Bswb48kHz_2ch: {
        // 2 * 96 bytes per ms; 48 timestamps per ms.
        SplitBySamples(packet, 2 * 96, 48, &new_packets);
        break;
      }
      case kDecoderPCM16B_5ch: {
        // 5 * 16 bytes per ms; 8 timestamps per ms.
        SplitBySamples(packet, 5 * 16, 8, &new_packets);
        break;
      }
      case kDecoderILBC: {
        int bytes_per_frame;
        int timestamps_per_frame;
        if (packet->payload_length >= 950) {
          return kTooLargePayload;
        } else if (packet->payload_length % 38 == 0) {
          // 20 ms frames.
          bytes_per_frame = 38;
          timestamps_per_frame = 160;
        } else if (packet->payload_length % 50 == 0) {
          // 30 ms frames.
          bytes_per_frame = 50;
          timestamps_per_frame = 240;
        } else {
          return kFrameSplitError;
        }
        int ret = SplitByFrames(packet, bytes_per_frame, timestamps_per_frame,
                                &new_packets);
        if (ret < 0) {
          return ret;
        } else if (ret == kNoSplit) {
          // Do not split at all. Simply advance to the next packet in the list.
          ++it;
          // We do not have any new packets to insert, and should not delete the
          // old one. Skip the code after the switch case, and jump straight to
          // the next packet in the while loop.
          continue;
        }
        break;
      }
      default: {
        // Do not split at all. Simply advance to the next packet in the list.
        ++it;
        // We do not have any new packets to insert, and should not delete the
        // old one. Skip the code after the switch case, and jump straight to
        // the next packet in the while loop.
        continue;
      }
    }
    // Insert new packets into original list, before the element pointed to by
    // iterator |it|.
    packet_list->splice(it, new_packets, new_packets.begin(),
                        new_packets.end());
    // Delete old packet payload.
    delete [] (*it)->payload;
    delete (*it);
    // Remove |it| from the packet list. This operation effectively moves the
    // iterator |it| to the next packet in the list. Thus, we do not have to
    // increment it manually.
    it = packet_list->erase(it);
  }
  return kOK;
}

void PayloadSplitter::SplitBySamples(const Packet* packet,
                                     int bytes_per_ms,
                                     int timestamps_per_ms,
                                     PacketList* new_packets) {
  assert(packet);
  assert(new_packets);

  int split_size_bytes = packet->payload_length;

  // Find a "chunk size" >= 20 ms and < 40 ms.
  int min_chunk_size = bytes_per_ms * 20;
  // Reduce the split size by half as long as |split_size_bytes| is at least
  // twice the minimum chunk size (so that the resulting size is at least as
  // large as the minimum chunk size).
  while (split_size_bytes >= 2 * min_chunk_size) {
    split_size_bytes >>= 1;
  }
  int timestamps_per_chunk =
      split_size_bytes * timestamps_per_ms / bytes_per_ms;
  uint32_t timestamp = packet->header.timestamp;

  uint8_t* payload_ptr = packet->payload;
  int len = packet->payload_length;
  while (len >= (2 * split_size_bytes)) {
    Packet* new_packet = new Packet;
    new_packet->payload_length = split_size_bytes;
    new_packet->header = packet->header;
    new_packet->header.timestamp = timestamp;
    timestamp += timestamps_per_chunk;
    new_packet->primary = packet->primary;
    new_packet->payload = new uint8_t[split_size_bytes];
    memcpy(new_packet->payload, payload_ptr, split_size_bytes);
    payload_ptr += split_size_bytes;
    new_packets->push_back(new_packet);
    len -= split_size_bytes;
  }

  if (len > 0) {
    Packet* new_packet = new Packet;
    new_packet->payload_length = len;
    new_packet->header = packet->header;
    new_packet->header.timestamp = timestamp;
    new_packet->primary = packet->primary;
    new_packet->payload = new uint8_t[len];
    memcpy(new_packet->payload, payload_ptr, len);
    new_packets->push_back(new_packet);
  }
}

int PayloadSplitter::SplitByFrames(const Packet* packet,
                                   int bytes_per_frame,
                                   int timestamps_per_frame,
                                   PacketList* new_packets) {
  if (packet->payload_length % bytes_per_frame != 0) {
    return kFrameSplitError;
  }

  int num_frames = packet->payload_length / bytes_per_frame;
  if (num_frames == 1) {
    // Special case. Do not split the payload.
    return kNoSplit;
  }

  uint32_t timestamp = packet->header.timestamp;
  uint8_t* payload_ptr = packet->payload;
  int len = packet->payload_length;
  while (len > 0) {
    assert(len >= bytes_per_frame);
    Packet* new_packet = new Packet;
    new_packet->payload_length = bytes_per_frame;
    new_packet->header = packet->header;
    new_packet->header.timestamp = timestamp;
    timestamp += timestamps_per_frame;
    new_packet->primary = packet->primary;
    new_packet->payload = new uint8_t[bytes_per_frame];
    memcpy(new_packet->payload, payload_ptr, bytes_per_frame);
    payload_ptr += bytes_per_frame;
    new_packets->push_back(new_packet);
    len -= bytes_per_frame;
  }
  return kOK;
}

}  // namespace webrtc
