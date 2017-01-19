/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_MODULES_AUDIO_CODING_NETEQ_PACKET_BUFFER_H_
#define WEBRTC_MODULES_AUDIO_CODING_NETEQ_PACKET_BUFFER_H_

#include "webrtc/base/constructormagic.h"
#include "webrtc/modules/audio_coding/neteq/packet.h"
#include "webrtc/typedefs.h"

namespace webrtc {

// Forward declaration.
class DecoderDatabase;

// This is the actual buffer holding the packets before decoding.
class PacketBuffer {
 public:
  enum BufferReturnCodes {
    kOK = 0,
    kFlushed,
    kNotFound,
    kBufferEmpty,
    kInvalidPacket,
    kInvalidPointer
  };

  // Constructor creates a buffer which can hold a maximum of
  // |max_number_of_packets| packets.
  PacketBuffer(size_t max_number_of_packets);

  // Deletes all packets in the buffer before destroying the buffer.
  virtual ~PacketBuffer();

  // Flushes the buffer and deletes all packets in it.
  virtual void Flush();

  // Returns true for an empty buffer.
  virtual bool Empty() const { return buffer_.empty(); }

  // Inserts |packet| into the buffer. The buffer will take over ownership of
  // the packet object.
  // Returns PacketBuffer::kOK on success, PacketBuffer::kFlushed if the buffer
  // was flushed due to overfilling.
  virtual int InsertPacket(Packet* packet);

  // Inserts a list of packets into the buffer. The buffer will take over
  // ownership of the packet objects.
  // Returns PacketBuffer::kOK if all packets were inserted successfully.
  // If the buffer was flushed due to overfilling, only a subset of the list is
  // inserted, and PacketBuffer::kFlushed is returned.
  // The last three parameters are included for legacy compatibility.
  // TODO(hlundin): Redesign to not use current_*_payload_type and
  // decoder_database.
  virtual int InsertPacketList(PacketList* packet_list,
                               const DecoderDatabase& decoder_database,
                               uint8_t* current_rtp_payload_type,
                               uint8_t* current_cng_rtp_payload_type);

  // Gets the timestamp for the first packet in the buffer and writes it to the
  // output variable |next_timestamp|.
  // Returns PacketBuffer::kBufferEmpty if the buffer is empty,
  // PacketBuffer::kOK otherwise.
  virtual int NextTimestamp(uint32_t* next_timestamp) const;

  // Gets the timestamp for the first packet in the buffer with a timestamp no
  // lower than the input limit |timestamp|. The result is written to the output
  // variable |next_timestamp|.
  // Returns PacketBuffer::kBufferEmpty if the buffer is empty,
  // PacketBuffer::kOK otherwise.
  virtual int NextHigherTimestamp(uint32_t timestamp,
                                  uint32_t* next_timestamp) const;

  // Returns a (constant) pointer the RTP header of the first packet in the
  // buffer. Returns NULL if the buffer is empty.
  virtual const RTPHeader* NextRtpHeader() const;

  // Extracts the first packet in the buffer and returns a pointer to it.
  // Returns NULL if the buffer is empty. The caller is responsible for deleting
  // the packet.
  // Subsequent packets with the same timestamp as the one extracted will be
  // discarded and properly deleted. The number of discarded packets will be
  // written to the output variable |discard_count|.
  virtual Packet* GetNextPacket(int* discard_count);

  // Discards the first packet in the buffer. The packet is deleted.
  // Returns PacketBuffer::kBufferEmpty if the buffer is empty,
  // PacketBuffer::kOK otherwise.
  virtual int DiscardNextPacket();

  // Discards all packets that are (strictly) older than |timestamp_limit|.
  // Returns number of packets discarded.
  virtual int DiscardOldPackets(uint32_t timestamp_limit);

  // Returns the number of packets in the buffer, including duplicates and
  // redundant packets.
  virtual int NumPacketsInBuffer() const {
    return static_cast<int>(buffer_.size());
  }

  // Returns the number of samples in the buffer, including samples carried in
  // duplicate and redundant packets.
  virtual int NumSamplesInBuffer(DecoderDatabase* decoder_database,
                                 int last_decoded_length) const;

  // Increase the waiting time counter for every packet in the buffer by |inc|.
  // The default value for |inc| is 1.
  virtual void IncrementWaitingTimes(int inc = 1);

  virtual void BufferStat(int* num_packets, int* max_num_packets) const;

  // Static method that properly deletes the first packet, and its payload
  // array, in |packet_list|. Returns false if |packet_list| already was empty,
  // otherwise true.
  static bool DeleteFirstPacket(PacketList* packet_list);

  // Static method that properly deletes all packets, and their payload arrays,
  // in |packet_list|.
  static void DeleteAllPackets(PacketList* packet_list);

 private:
  size_t max_number_of_packets_;
  PacketList buffer_;
  DISALLOW_COPY_AND_ASSIGN(PacketBuffer);
};

}  // namespace webrtc
#endif  // WEBRTC_MODULES_AUDIO_CODING_NETEQ_PACKET_BUFFER_H_
