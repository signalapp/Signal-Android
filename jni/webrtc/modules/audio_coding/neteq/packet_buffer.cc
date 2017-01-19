/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

// This is the implementation of the PacketBuffer class. It is mostly based on
// an STL list. The list is kept sorted at all times so that the next packet to
// decode is at the beginning of the list.

#include "webrtc/modules/audio_coding/neteq/packet_buffer.h"

#include <algorithm>  // find_if()

#include "webrtc/modules/audio_coding/neteq/decoder_database.h"
#include "webrtc/modules/audio_coding/neteq/interface/audio_decoder.h"

namespace webrtc {

// Predicate used when inserting packets in the buffer list.
// Operator() returns true when |packet| goes before |new_packet|.
class NewTimestampIsLarger {
 public:
  explicit NewTimestampIsLarger(const Packet* new_packet)
      : new_packet_(new_packet) {
  }
  bool operator()(Packet* packet) {
    return (*new_packet_ >= *packet);
  }

 private:
  const Packet* new_packet_;
};

PacketBuffer::PacketBuffer(size_t max_number_of_packets)
    : max_number_of_packets_(max_number_of_packets) {}

// Destructor. All packets in the buffer will be destroyed.
PacketBuffer::~PacketBuffer() {
  Flush();
}

// Flush the buffer. All packets in the buffer will be destroyed.
void PacketBuffer::Flush() {
  DeleteAllPackets(&buffer_);
}

int PacketBuffer::InsertPacket(Packet* packet) {
  if (!packet || !packet->payload) {
    if (packet) {
      delete packet;
    }
    return kInvalidPacket;
  }

  int return_val = kOK;

  if (buffer_.size() >= max_number_of_packets_) {
    // Buffer is full. Flush it.
    Flush();
    return_val = kFlushed;
  }

  // Get an iterator pointing to the place in the buffer where the new packet
  // should be inserted. The list is searched from the back, since the most
  // likely case is that the new packet should be near the end of the list.
  PacketList::reverse_iterator rit = std::find_if(
      buffer_.rbegin(), buffer_.rend(),
      NewTimestampIsLarger(packet));
  buffer_.insert(rit.base(), packet);  // Insert the packet at that position.

  return return_val;
}

int PacketBuffer::InsertPacketList(PacketList* packet_list,
                                   const DecoderDatabase& decoder_database,
                                   uint8_t* current_rtp_payload_type,
                                   uint8_t* current_cng_rtp_payload_type) {
  bool flushed = false;
  while (!packet_list->empty()) {
    Packet* packet = packet_list->front();
    if (decoder_database.IsComfortNoise(packet->header.payloadType)) {
      if (*current_cng_rtp_payload_type != 0xFF &&
          *current_cng_rtp_payload_type != packet->header.payloadType) {
        // New CNG payload type implies new codec type.
        *current_rtp_payload_type = 0xFF;
        Flush();
        flushed = true;
      }
      *current_cng_rtp_payload_type = packet->header.payloadType;
    } else if (!decoder_database.IsDtmf(packet->header.payloadType)) {
      // This must be speech.
      if (*current_rtp_payload_type != 0xFF &&
          *current_rtp_payload_type != packet->header.payloadType) {
        *current_cng_rtp_payload_type = 0xFF;
        Flush();
        flushed = true;
      }
      *current_rtp_payload_type = packet->header.payloadType;
    }
    int return_val = InsertPacket(packet);
    packet_list->pop_front();
    if (return_val == kFlushed) {
      // The buffer flushed, but this is not an error. We can still continue.
      flushed = true;
    } else if (return_val != kOK) {
      // An error occurred. Delete remaining packets in list and return.
      DeleteAllPackets(packet_list);
      return return_val;
    }
  }
  return flushed ? kFlushed : kOK;
}

int PacketBuffer::NextTimestamp(uint32_t* next_timestamp) const {
  if (Empty()) {
    return kBufferEmpty;
  }
  if (!next_timestamp) {
    return kInvalidPointer;
  }
  *next_timestamp = buffer_.front()->header.timestamp;
  return kOK;
}

int PacketBuffer::NextHigherTimestamp(uint32_t timestamp,
                                      uint32_t* next_timestamp) const {
  if (Empty()) {
    return kBufferEmpty;
  }
  if (!next_timestamp) {
    return kInvalidPointer;
  }
  PacketList::const_iterator it;
  for (it = buffer_.begin(); it != buffer_.end(); ++it) {
    if ((*it)->header.timestamp >= timestamp) {
      // Found a packet matching the search.
      *next_timestamp = (*it)->header.timestamp;
      return kOK;
    }
  }
  return kNotFound;
}

const RTPHeader* PacketBuffer::NextRtpHeader() const {
  if (Empty()) {
    return NULL;
  }
  return const_cast<const RTPHeader*>(&(buffer_.front()->header));
}

Packet* PacketBuffer::GetNextPacket(int* discard_count) {
  if (Empty()) {
    // Buffer is empty.
    return NULL;
  }

  Packet* packet = buffer_.front();
  // Assert that the packet sanity checks in InsertPacket method works.
  assert(packet && packet->payload);
  buffer_.pop_front();
  // Discard other packets with the same timestamp. These are duplicates or
  // redundant payloads that should not be used.
  if (discard_count) {
    *discard_count = 0;
  }
  while (!Empty() &&
      buffer_.front()->header.timestamp == packet->header.timestamp) {
    if (DiscardNextPacket() != kOK) {
      assert(false);  // Must be ok by design.
    }
    if (discard_count) {
      ++(*discard_count);
    }
  }
  return packet;
}

int PacketBuffer::DiscardNextPacket() {
  if (Empty()) {
    return kBufferEmpty;
  }
  // Assert that the packet sanity checks in InsertPacket method works.
  assert(buffer_.front());
  assert(buffer_.front()->payload);
  DeleteFirstPacket(&buffer_);
  return kOK;
}

int PacketBuffer::DiscardOldPackets(uint32_t timestamp_limit) {
  while (!Empty() &&
      timestamp_limit != buffer_.front()->header.timestamp &&
      static_cast<uint32_t>(timestamp_limit
                            - buffer_.front()->header.timestamp) <
                            0xFFFFFFFF / 2) {
    if (DiscardNextPacket() != kOK) {
      assert(false);  // Must be ok by design.
    }
  }
  return 0;
}

int PacketBuffer::NumSamplesInBuffer(DecoderDatabase* decoder_database,
                                     int last_decoded_length) const {
  PacketList::const_iterator it;
  int num_samples = 0;
  int last_duration = last_decoded_length;
  for (it = buffer_.begin(); it != buffer_.end(); ++it) {
    Packet* packet = (*it);
    AudioDecoder* decoder =
        decoder_database->GetDecoder(packet->header.payloadType);
    if (decoder) {
      int duration;
      if (packet->sync_packet) {
        duration = last_duration;
      } else if (packet->primary) {
        duration =
            decoder->PacketDuration(packet->payload, packet->payload_length);
      } else {
        continue;
      }
      if (duration >= 0) {
        last_duration = duration;  // Save the most up-to-date (valid) duration.
      }
    }
    num_samples += last_duration;
  }
  return num_samples;
}

void PacketBuffer::IncrementWaitingTimes(int inc) {
  PacketList::iterator it;
  for (it = buffer_.begin(); it != buffer_.end(); ++it) {
    (*it)->waiting_time += inc;
  }
}

bool PacketBuffer::DeleteFirstPacket(PacketList* packet_list) {
  if (packet_list->empty()) {
    return false;
  }
  Packet* first_packet = packet_list->front();
  delete [] first_packet->payload;
  delete first_packet;
  packet_list->pop_front();
  return true;
}

void PacketBuffer::DeleteAllPackets(PacketList* packet_list) {
  while (DeleteFirstPacket(packet_list)) {
    // Continue while the list is not empty.
  }
}

void PacketBuffer::BufferStat(int* num_packets, int* max_num_packets) const {
  *num_packets = static_cast<int>(buffer_.size());
  *max_num_packets = static_cast<int>(max_number_of_packets_);
}

}  // namespace webrtc
