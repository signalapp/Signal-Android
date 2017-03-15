/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/modules/audio_coding/neteq/neteq_impl.h"

#include <assert.h>
#include <memory.h>  // memset

#include <algorithm>

#include "webrtc/common_audio/signal_processing/include/signal_processing_library.h"
#include "webrtc/modules/audio_coding/neteq/accelerate.h"
#include "webrtc/modules/audio_coding/neteq/background_noise.h"
#include "webrtc/modules/audio_coding/neteq/buffer_level_filter.h"
#include "webrtc/modules/audio_coding/neteq/comfort_noise.h"
#include "webrtc/modules/audio_coding/neteq/decision_logic.h"
#include "webrtc/modules/audio_coding/neteq/decoder_database.h"
#include "webrtc/modules/audio_coding/neteq/defines.h"
#include "webrtc/modules/audio_coding/neteq/delay_manager.h"
#include "webrtc/modules/audio_coding/neteq/delay_peak_detector.h"
#include "webrtc/modules/audio_coding/neteq/dtmf_buffer.h"
#include "webrtc/modules/audio_coding/neteq/dtmf_tone_generator.h"
#include "webrtc/modules/audio_coding/neteq/expand.h"
#include "webrtc/modules/audio_coding/neteq/interface/audio_decoder.h"
#include "webrtc/modules/audio_coding/neteq/merge.h"
#include "webrtc/modules/audio_coding/neteq/normal.h"
#include "webrtc/modules/audio_coding/neteq/packet_buffer.h"
#include "webrtc/modules/audio_coding/neteq/packet.h"
#include "webrtc/modules/audio_coding/neteq/payload_splitter.h"
#include "webrtc/modules/audio_coding/neteq/post_decode_vad.h"
#include "webrtc/modules/audio_coding/neteq/preemptive_expand.h"
#include "webrtc/modules/audio_coding/neteq/sync_buffer.h"
#include "webrtc/modules/audio_coding/neteq/timestamp_scaler.h"
#include "webrtc/modules/interface/module_common_types.h"
#include "webrtc/system_wrappers/interface/critical_section_wrapper.h"
#include "webrtc/system_wrappers/interface/logging.h"

// Modify the code to obtain backwards bit-exactness. Once bit-exactness is no
// longer required, this #define should be removed (and the code that it
// enables).
#define LEGACY_BITEXACT

namespace webrtc {

NetEqImpl::NetEqImpl(const NetEq::Config& config,
                     BufferLevelFilter* buffer_level_filter,
                     DecoderDatabase* decoder_database,
                     DelayManager* delay_manager,
                     DelayPeakDetector* delay_peak_detector,
                     DtmfBuffer* dtmf_buffer,
                     DtmfToneGenerator* dtmf_tone_generator,
                     PacketBuffer* packet_buffer,
                     PayloadSplitter* payload_splitter,
                     TimestampScaler* timestamp_scaler,
                     AccelerateFactory* accelerate_factory,
                     ExpandFactory* expand_factory,
                     PreemptiveExpandFactory* preemptive_expand_factory,
                     bool create_components)
    : crit_sect_(CriticalSectionWrapper::CreateCriticalSection()),
      buffer_level_filter_(buffer_level_filter),
      decoder_database_(decoder_database),
      delay_manager_(delay_manager),
      delay_peak_detector_(delay_peak_detector),
      dtmf_buffer_(dtmf_buffer),
      dtmf_tone_generator_(dtmf_tone_generator),
      packet_buffer_(packet_buffer),
      payload_splitter_(payload_splitter),
      timestamp_scaler_(timestamp_scaler),
      vad_(new PostDecodeVad()),
      expand_factory_(expand_factory),
      accelerate_factory_(accelerate_factory),
      preemptive_expand_factory_(preemptive_expand_factory),
      last_mode_(kModeNormal),
      decoded_buffer_length_(kMaxFrameSize),
      decoded_buffer_(new int16_t[decoded_buffer_length_]),
      playout_timestamp_(0),
      new_codec_(false),
      timestamp_(0),
      reset_decoder_(false),
      current_rtp_payload_type_(0xFF),  // Invalid RTP payload type.
      current_cng_rtp_payload_type_(0xFF),  // Invalid RTP payload type.
      ssrc_(0),
      first_packet_(true),
      error_code_(0),
      decoder_error_code_(0),
      background_noise_mode_(config.background_noise_mode),
      decoded_packet_sequence_number_(-1),
      decoded_packet_timestamp_(0) {
  int fs = config.sample_rate_hz;
  if (fs != 8000 && fs != 16000 && fs != 32000 && fs != 48000) {
    LOG(LS_ERROR) << "Sample rate " << fs << " Hz not supported. " <<
        "Changing to 8000 Hz.";
    fs = 8000;
  }
  LOG(LS_VERBOSE) << "Create NetEqImpl object with fs = " << fs << ".";
  fs_hz_ = fs;
  fs_mult_ = fs / 8000;
  output_size_samples_ = kOutputSizeMs * 8 * fs_mult_;
  decoder_frame_length_ = 3 * output_size_samples_;
  WebRtcSpl_Init();
  if (create_components) {
    SetSampleRateAndChannels(fs, 1);  // Default is 1 channel.
  }
}

NetEqImpl::~NetEqImpl() {
  LOG(LS_INFO) << "Deleting NetEqImpl object.";
}

int NetEqImpl::InsertPacket(const WebRtcRTPHeader& rtp_header,
                            const uint8_t* payload,
                            int length_bytes,
                            uint32_t receive_timestamp) {
  CriticalSectionScoped lock(crit_sect_.get());
  LOG(LS_VERBOSE) << "InsertPacket: ts=" << rtp_header.header.timestamp <<
      ", sn=" << rtp_header.header.sequenceNumber <<
      ", pt=" << static_cast<int>(rtp_header.header.payloadType) <<
      ", ssrc=" << rtp_header.header.ssrc <<
      ", len=" << length_bytes;
  int error = InsertPacketInternal(rtp_header, payload, length_bytes,
                                   receive_timestamp, false);
  if (error != 0) {
    LOG_FERR1(LS_WARNING, InsertPacketInternal, error);
    error_code_ = error;
    return kFail;
  }
  return kOK;
}

int NetEqImpl::InsertSyncPacket(const WebRtcRTPHeader& rtp_header,
                                uint32_t receive_timestamp) {
  CriticalSectionScoped lock(crit_sect_.get());
  LOG(LS_VERBOSE) << "InsertPacket-Sync: ts="
      << rtp_header.header.timestamp <<
      ", sn=" << rtp_header.header.sequenceNumber <<
      ", pt=" << static_cast<int>(rtp_header.header.payloadType) <<
      ", ssrc=" << rtp_header.header.ssrc;

  const uint8_t kSyncPayload[] = { 's', 'y', 'n', 'c' };
  int error = InsertPacketInternal(
      rtp_header, kSyncPayload, sizeof(kSyncPayload), receive_timestamp, true);

  if (error != 0) {
    LOG_FERR1(LS_WARNING, InsertPacketInternal, error);
    error_code_ = error;
    return kFail;
  }
  return kOK;
}

int NetEqImpl::GetAudio(size_t max_length, int16_t* output_audio,
                        int* samples_per_channel, int* num_channels,
                        NetEqOutputType* type) {
  CriticalSectionScoped lock(crit_sect_.get());
  LOG(LS_VERBOSE) << "GetAudio";
  int error = GetAudioInternal(max_length, output_audio, samples_per_channel,
                               num_channels);
  LOG(LS_VERBOSE) << "Produced " << *samples_per_channel <<
      " samples/channel for " << *num_channels << " channel(s)";
  if (error != 0) {
    LOG_FERR1(LS_WARNING, GetAudioInternal, error);
    error_code_ = error;
    return kFail;
  }
  if (type) {
    *type = LastOutputType();
  }
  return kOK;
}

int NetEqImpl::RegisterPayloadType(enum NetEqDecoder codec,
                                   uint8_t rtp_payload_type) {
  CriticalSectionScoped lock(crit_sect_.get());
  LOG_API2(static_cast<int>(rtp_payload_type), codec);
  int ret = decoder_database_->RegisterPayload(rtp_payload_type, codec);
  if (ret != DecoderDatabase::kOK) {
    LOG_FERR2(LS_WARNING, RegisterPayload, rtp_payload_type, codec);
    switch (ret) {
      case DecoderDatabase::kInvalidRtpPayloadType:
        error_code_ = kInvalidRtpPayloadType;
        break;
      case DecoderDatabase::kCodecNotSupported:
        error_code_ = kCodecNotSupported;
        break;
      case DecoderDatabase::kDecoderExists:
        error_code_ = kDecoderExists;
        break;
      default:
        error_code_ = kOtherError;
    }
    return kFail;
  }
  return kOK;
}

int NetEqImpl::RegisterExternalDecoder(AudioDecoder* decoder,
                                       enum NetEqDecoder codec,
                                       uint8_t rtp_payload_type) {
  CriticalSectionScoped lock(crit_sect_.get());
  LOG_API2(static_cast<int>(rtp_payload_type), codec);
  if (!decoder) {
    LOG(LS_ERROR) << "Cannot register external decoder with NULL pointer";
    assert(false);
    return kFail;
  }
  const int sample_rate_hz = AudioDecoder::CodecSampleRateHz(codec);
  int ret = decoder_database_->InsertExternal(rtp_payload_type, codec,
                                              sample_rate_hz, decoder);
  if (ret != DecoderDatabase::kOK) {
    LOG_FERR2(LS_WARNING, InsertExternal, rtp_payload_type, codec);
    switch (ret) {
      case DecoderDatabase::kInvalidRtpPayloadType:
        error_code_ = kInvalidRtpPayloadType;
        break;
      case DecoderDatabase::kCodecNotSupported:
        error_code_ = kCodecNotSupported;
        break;
      case DecoderDatabase::kDecoderExists:
        error_code_ = kDecoderExists;
        break;
      case DecoderDatabase::kInvalidSampleRate:
        error_code_ = kInvalidSampleRate;
        break;
      case DecoderDatabase::kInvalidPointer:
        error_code_ = kInvalidPointer;
        break;
      default:
        error_code_ = kOtherError;
    }
    return kFail;
  }
  return kOK;
}

int NetEqImpl::RemovePayloadType(uint8_t rtp_payload_type) {
  CriticalSectionScoped lock(crit_sect_.get());
  LOG_API1(static_cast<int>(rtp_payload_type));
  int ret = decoder_database_->Remove(rtp_payload_type);
  if (ret == DecoderDatabase::kOK) {
    return kOK;
  } else if (ret == DecoderDatabase::kDecoderNotFound) {
    error_code_ = kDecoderNotFound;
  } else {
    error_code_ = kOtherError;
  }
  LOG_FERR1(LS_WARNING, Remove, rtp_payload_type);
  return kFail;
}

bool NetEqImpl::SetMinimumDelay(int delay_ms) {
  CriticalSectionScoped lock(crit_sect_.get());
  if (delay_ms >= 0 && delay_ms < 10000) {
    assert(delay_manager_.get());
    return delay_manager_->SetMinimumDelay(delay_ms);
  }
  return false;
}

bool NetEqImpl::SetMaximumDelay(int delay_ms) {
  CriticalSectionScoped lock(crit_sect_.get());
  if (delay_ms >= 0 && delay_ms < 10000) {
    assert(delay_manager_.get());
    return delay_manager_->SetMaximumDelay(delay_ms);
  }
  return false;
}

int NetEqImpl::LeastRequiredDelayMs() const {
  CriticalSectionScoped lock(crit_sect_.get());
  assert(delay_manager_.get());
  return delay_manager_->least_required_delay_ms();
}

void NetEqImpl::SetPlayoutMode(NetEqPlayoutMode mode) {
  CriticalSectionScoped lock(crit_sect_.get());
  if (!decision_logic_.get() || mode != decision_logic_->playout_mode()) {
    // The reset() method calls delete for the old object.
    CreateDecisionLogic(mode);
  }
}

NetEqPlayoutMode NetEqImpl::PlayoutMode() const {
  CriticalSectionScoped lock(crit_sect_.get());
  assert(decision_logic_.get());
  return decision_logic_->playout_mode();
}

int NetEqImpl::NetworkStatistics(NetEqNetworkStatistics* stats) {
  CriticalSectionScoped lock(crit_sect_.get());
  assert(decoder_database_.get());
  const int total_samples_in_buffers = packet_buffer_->NumSamplesInBuffer(
      decoder_database_.get(), decoder_frame_length_) +
          static_cast<int>(sync_buffer_->FutureLength());
  assert(delay_manager_.get());
  assert(decision_logic_.get());
  stats_.GetNetworkStatistics(fs_hz_, total_samples_in_buffers,
                              decoder_frame_length_, *delay_manager_.get(),
                              *decision_logic_.get(), stats);
  return 0;
}

void NetEqImpl::WaitingTimes(std::vector<int>* waiting_times) {
  CriticalSectionScoped lock(crit_sect_.get());
  stats_.WaitingTimes(waiting_times);
}

void NetEqImpl::GetRtcpStatistics(RtcpStatistics* stats) {
  CriticalSectionScoped lock(crit_sect_.get());
  if (stats) {
    rtcp_.GetStatistics(false, stats);
  }
}

void NetEqImpl::GetRtcpStatisticsNoReset(RtcpStatistics* stats) {
  CriticalSectionScoped lock(crit_sect_.get());
  if (stats) {
    rtcp_.GetStatistics(true, stats);
  }
}

void NetEqImpl::EnableVad() {
  CriticalSectionScoped lock(crit_sect_.get());
  assert(vad_.get());
  vad_->Enable();
}

void NetEqImpl::DisableVad() {
  CriticalSectionScoped lock(crit_sect_.get());
  assert(vad_.get());
  vad_->Disable();
}

bool NetEqImpl::GetPlayoutTimestamp(uint32_t* timestamp) {
  CriticalSectionScoped lock(crit_sect_.get());
  if (first_packet_) {
    // We don't have a valid RTP timestamp until we have decoded our first
    // RTP packet.
    return false;
  }
  *timestamp = timestamp_scaler_->ToExternal(playout_timestamp_);
  return true;
}

int NetEqImpl::LastError() {
  CriticalSectionScoped lock(crit_sect_.get());
  return error_code_;
}

int NetEqImpl::LastDecoderError() {
  CriticalSectionScoped lock(crit_sect_.get());
  return decoder_error_code_;
}

void NetEqImpl::FlushBuffers() {
  CriticalSectionScoped lock(crit_sect_.get());
  LOG_API0();
  packet_buffer_->Flush();
  assert(sync_buffer_.get());
  assert(expand_.get());
  sync_buffer_->Flush();
  sync_buffer_->set_next_index(sync_buffer_->next_index() -
                               expand_->overlap_length());
  // Set to wait for new codec.
  first_packet_ = true;
}

void NetEqImpl::PacketBufferStatistics(int* current_num_packets,
                                       int* max_num_packets) const {
  CriticalSectionScoped lock(crit_sect_.get());
  packet_buffer_->BufferStat(current_num_packets, max_num_packets);
}

int NetEqImpl::DecodedRtpInfo(int* sequence_number, uint32_t* timestamp) const {
  CriticalSectionScoped lock(crit_sect_.get());
  if (decoded_packet_sequence_number_ < 0)
    return -1;
  *sequence_number = decoded_packet_sequence_number_;
  *timestamp = decoded_packet_timestamp_;
  return 0;
}

const SyncBuffer* NetEqImpl::sync_buffer_for_test() const {
  CriticalSectionScoped lock(crit_sect_.get());
  return sync_buffer_.get();
}

// Methods below this line are private.

int NetEqImpl::InsertPacketInternal(const WebRtcRTPHeader& rtp_header,
                                    const uint8_t* payload,
                                    int length_bytes,
                                    uint32_t receive_timestamp,
                                    bool is_sync_packet) {
  if (!payload) {
    LOG_F(LS_ERROR) << "payload == NULL";
    return kInvalidPointer;
  }
  // Sanity checks for sync-packets.
  if (is_sync_packet) {
    if (decoder_database_->IsDtmf(rtp_header.header.payloadType) ||
        decoder_database_->IsRed(rtp_header.header.payloadType) ||
        decoder_database_->IsComfortNoise(rtp_header.header.payloadType)) {
      LOG_F(LS_ERROR) << "Sync-packet with an unacceptable payload type "
          << rtp_header.header.payloadType;
      return kSyncPacketNotAccepted;
    }
    if (first_packet_ ||
        rtp_header.header.payloadType != current_rtp_payload_type_ ||
        rtp_header.header.ssrc != ssrc_) {
      // Even if |current_rtp_payload_type_| is 0xFF, sync-packet isn't
      // accepted.
      LOG_F(LS_ERROR) << "Changing codec, SSRC or first packet "
          "with sync-packet.";
      return kSyncPacketNotAccepted;
    }
  }
  PacketList packet_list;
  RTPHeader main_header;
  {
    // Convert to Packet.
    // Create |packet| within this separate scope, since it should not be used
    // directly once it's been inserted in the packet list. This way, |packet|
    // is not defined outside of this block.
    Packet* packet = new Packet;
    packet->header.markerBit = false;
    packet->header.payloadType = rtp_header.header.payloadType;
    packet->header.sequenceNumber = rtp_header.header.sequenceNumber;
    packet->header.timestamp = rtp_header.header.timestamp;
    packet->header.ssrc = rtp_header.header.ssrc;
    packet->header.numCSRCs = 0;
    packet->payload_length = length_bytes;
    packet->primary = true;
    packet->waiting_time = 0;
    packet->payload = new uint8_t[packet->payload_length];
    packet->sync_packet = is_sync_packet;
    if (!packet->payload) {
      LOG_F(LS_ERROR) << "Payload pointer is NULL.";
    }
    assert(payload);  // Already checked above.
    memcpy(packet->payload, payload, packet->payload_length);
    // Insert packet in a packet list.
    packet_list.push_back(packet);
    // Save main payloads header for later.
    memcpy(&main_header, &packet->header, sizeof(main_header));
  }

  bool update_sample_rate_and_channels = false;
  // Reinitialize NetEq if it's needed (changed SSRC or first call).
  if ((main_header.ssrc != ssrc_) || first_packet_) {
    rtcp_.Init(main_header.sequenceNumber);
    first_packet_ = false;

    // Flush the packet buffer and DTMF buffer.
    packet_buffer_->Flush();
    dtmf_buffer_->Flush();

    // Store new SSRC.
    ssrc_ = main_header.ssrc;

    // Update audio buffer timestamp.
    sync_buffer_->IncreaseEndTimestamp(main_header.timestamp - timestamp_);

    // Update codecs.
    timestamp_ = main_header.timestamp;
    current_rtp_payload_type_ = main_header.payloadType;

    // Set MCU to update codec on next SignalMCU call.
    new_codec_ = true;

    // Reset timestamp scaling.
    timestamp_scaler_->Reset();

    // Triger an update of sampling rate and the number of channels.
    update_sample_rate_and_channels = true;
  }

  // Update RTCP statistics, only for regular packets.
  if (!is_sync_packet)
    rtcp_.Update(main_header, receive_timestamp);

  // Check for RED payload type, and separate payloads into several packets.
  if (decoder_database_->IsRed(main_header.payloadType)) {
    assert(!is_sync_packet);  // We had a sanity check for this.
    if (payload_splitter_->SplitRed(&packet_list) != PayloadSplitter::kOK) {
      LOG_FERR1(LS_WARNING, SplitRed, packet_list.size());
      PacketBuffer::DeleteAllPackets(&packet_list);
      return kRedundancySplitError;
    }
    // Only accept a few RED payloads of the same type as the main data,
    // DTMF events and CNG.
    payload_splitter_->CheckRedPayloads(&packet_list, *decoder_database_);
    // Update the stored main payload header since the main payload has now
    // changed.
    memcpy(&main_header, &packet_list.front()->header, sizeof(main_header));
  }

  // Check payload types.
  if (decoder_database_->CheckPayloadTypes(packet_list) ==
      DecoderDatabase::kDecoderNotFound) {
    LOG_FERR1(LS_WARNING, CheckPayloadTypes, packet_list.size());
    PacketBuffer::DeleteAllPackets(&packet_list);
    return kUnknownRtpPayloadType;
  }

  // Scale timestamp to internal domain (only for some codecs).
  timestamp_scaler_->ToInternal(&packet_list);

  // Process DTMF payloads. Cycle through the list of packets, and pick out any
  // DTMF payloads found.
  PacketList::iterator it = packet_list.begin();
  while (it != packet_list.end()) {
    Packet* current_packet = (*it);
    assert(current_packet);
    assert(current_packet->payload);
    if (decoder_database_->IsDtmf(current_packet->header.payloadType)) {
      assert(!current_packet->sync_packet);  // We had a sanity check for this.
      DtmfEvent event;
      int ret = DtmfBuffer::ParseEvent(
          current_packet->header.timestamp,
          current_packet->payload,
          current_packet->payload_length,
          &event);
      if (ret != DtmfBuffer::kOK) {
        LOG_FERR2(LS_WARNING, ParseEvent, ret,
                  current_packet->payload_length);
        PacketBuffer::DeleteAllPackets(&packet_list);
        return kDtmfParsingError;
      }
      if (dtmf_buffer_->InsertEvent(event) != DtmfBuffer::kOK) {
        LOG_FERR0(LS_WARNING, InsertEvent);
        PacketBuffer::DeleteAllPackets(&packet_list);
        return kDtmfInsertError;
      }
      // TODO(hlundin): Let the destructor of Packet handle the payload.
      delete [] current_packet->payload;
      delete current_packet;
      it = packet_list.erase(it);
    } else {
      ++it;
    }
  }

  // Check for FEC in packets, and separate payloads into several packets.
  int ret = payload_splitter_->SplitFec(&packet_list, decoder_database_.get());
  if (ret != PayloadSplitter::kOK) {
    LOG_FERR1(LS_WARNING, SplitFec, packet_list.size());
    PacketBuffer::DeleteAllPackets(&packet_list);
    switch (ret) {
      case PayloadSplitter::kUnknownPayloadType:
        return kUnknownRtpPayloadType;
      default:
        return kOtherError;
    }
  }

  // Split payloads into smaller chunks. This also verifies that all payloads
  // are of a known payload type. SplitAudio() method is protected against
  // sync-packets.
  ret = payload_splitter_->SplitAudio(&packet_list, *decoder_database_);
  if (ret != PayloadSplitter::kOK) {
    LOG_FERR1(LS_WARNING, SplitAudio, packet_list.size());
    PacketBuffer::DeleteAllPackets(&packet_list);
    switch (ret) {
      case PayloadSplitter::kUnknownPayloadType:
        return kUnknownRtpPayloadType;
      case PayloadSplitter::kFrameSplitError:
        return kFrameSplitError;
      default:
        return kOtherError;
    }
  }

  // Update bandwidth estimate, if the packet is not sync-packet.
  if (!packet_list.empty() && !packet_list.front()->sync_packet) {
    // The list can be empty here if we got nothing but DTMF payloads.
    AudioDecoder* decoder =
        decoder_database_->GetDecoder(main_header.payloadType);
    assert(decoder);  // Should always get a valid object, since we have
                      // already checked that the payload types are known.
    decoder->IncomingPacket(packet_list.front()->payload,
                            packet_list.front()->payload_length,
                            packet_list.front()->header.sequenceNumber,
                            packet_list.front()->header.timestamp,
                            receive_timestamp);
  }

  // Insert packets in buffer.
  int temp_bufsize = packet_buffer_->NumPacketsInBuffer();
  ret = packet_buffer_->InsertPacketList(
      &packet_list,
      *decoder_database_,
      &current_rtp_payload_type_,
      &current_cng_rtp_payload_type_);
  if (ret == PacketBuffer::kFlushed) {
    // Reset DSP timestamp etc. if packet buffer flushed.
    new_codec_ = true;
    update_sample_rate_and_channels = true;
    LOG_F(LS_WARNING) << "Packet buffer flushed";
  } else if (ret != PacketBuffer::kOK) {
    LOG_FERR1(LS_WARNING, InsertPacketList, packet_list.size());
    PacketBuffer::DeleteAllPackets(&packet_list);
    return kOtherError;
  }
  if (current_rtp_payload_type_ != 0xFF) {
    const DecoderDatabase::DecoderInfo* dec_info =
        decoder_database_->GetDecoderInfo(current_rtp_payload_type_);
    if (!dec_info) {
      assert(false);  // Already checked that the payload type is known.
    }
  }

  if (update_sample_rate_and_channels && !packet_buffer_->Empty()) {
    // We do not use |current_rtp_payload_type_| to |set payload_type|, but
    // get the next RTP header from |packet_buffer_| to obtain the payload type.
    // The reason for it is the following corner case. If NetEq receives a
    // CNG packet with a sample rate different than the current CNG then it
    // flushes its buffer, assuming send codec must have been changed. However,
    // payload type of the hypothetically new send codec is not known.
    const RTPHeader* rtp_header = packet_buffer_->NextRtpHeader();
    assert(rtp_header);
    int payload_type = rtp_header->payloadType;
    AudioDecoder* decoder = decoder_database_->GetDecoder(payload_type);
    assert(decoder);  // Payloads are already checked to be valid.
    const DecoderDatabase::DecoderInfo* decoder_info =
        decoder_database_->GetDecoderInfo(payload_type);
    assert(decoder_info);
    if (decoder_info->fs_hz != fs_hz_ ||
        decoder->channels() != algorithm_buffer_->Channels())
      SetSampleRateAndChannels(decoder_info->fs_hz, decoder->channels());
  }

  // TODO(hlundin): Move this code to DelayManager class.
  const DecoderDatabase::DecoderInfo* dec_info =
          decoder_database_->GetDecoderInfo(main_header.payloadType);
  assert(dec_info);  // Already checked that the payload type is known.
  delay_manager_->LastDecoderType(dec_info->codec_type);
  if (delay_manager_->last_pack_cng_or_dtmf() == 0) {
    // Calculate the total speech length carried in each packet.
    temp_bufsize = packet_buffer_->NumPacketsInBuffer() - temp_bufsize;
    temp_bufsize *= decoder_frame_length_;

    if ((temp_bufsize > 0) &&
        (temp_bufsize != decision_logic_->packet_length_samples())) {
      decision_logic_->set_packet_length_samples(temp_bufsize);
      delay_manager_->SetPacketAudioLength((1000 * temp_bufsize) / fs_hz_);
    }

    // Update statistics.
    if ((int32_t) (main_header.timestamp - timestamp_) >= 0 &&
        !new_codec_) {
      // Only update statistics if incoming packet is not older than last played
      // out packet, and if new codec flag is not set.
      delay_manager_->Update(main_header.sequenceNumber, main_header.timestamp,
                             fs_hz_);
    }
  } else if (delay_manager_->last_pack_cng_or_dtmf() == -1) {
    // This is first "normal" packet after CNG or DTMF.
    // Reset packet time counter and measure time until next packet,
    // but don't update statistics.
    delay_manager_->set_last_pack_cng_or_dtmf(0);
    delay_manager_->ResetPacketIatCount();
  }
  return 0;
}

int NetEqImpl::GetAudioInternal(size_t max_length, int16_t* output,
                                int* samples_per_channel, int* num_channels) {
  PacketList packet_list;
  DtmfEvent dtmf_event;
  Operations operation;
  bool play_dtmf;
  int return_value = GetDecision(&operation, &packet_list, &dtmf_event,
                                 &play_dtmf);
  if (return_value != 0) {
    LOG_FERR1(LS_WARNING, GetDecision, return_value);
    assert(false);
    last_mode_ = kModeError;
    return return_value;
  }
  LOG(LS_VERBOSE) << "GetDecision returned operation=" << operation <<
      " and " << packet_list.size() << " packet(s)";

  AudioDecoder::SpeechType speech_type;
  int length = 0;
  int decode_return_value = Decode(&packet_list, &operation,
                                   &length, &speech_type);

  assert(vad_.get());
  bool sid_frame_available =
      (operation == kRfc3389Cng && !packet_list.empty());
  vad_->Update(decoded_buffer_.get(), length, speech_type,
               sid_frame_available, fs_hz_);

  algorithm_buffer_->Clear();
  switch (operation) {
    case kNormal: {
      DoNormal(decoded_buffer_.get(), length, speech_type, play_dtmf);
      break;
    }
    case kMerge: {
      DoMerge(decoded_buffer_.get(), length, speech_type, play_dtmf);
      break;
    }
    case kExpand: {
      return_value = DoExpand(play_dtmf);
      break;
    }
    case kAccelerate: {
      return_value = DoAccelerate(decoded_buffer_.get(), length, speech_type,
                                  play_dtmf);
      break;
    }
    case kPreemptiveExpand: {
      return_value = DoPreemptiveExpand(decoded_buffer_.get(), length,
                                        speech_type, play_dtmf);
      break;
    }
    case kRfc3389Cng:
    case kRfc3389CngNoPacket: {
      return_value = DoRfc3389Cng(&packet_list, play_dtmf);
      break;
    }
    case kCodecInternalCng: {
      // This handles the case when there is no transmission and the decoder
      // should produce internal comfort noise.
      // TODO(hlundin): Write test for codec-internal CNG.
      DoCodecInternalCng();
      break;
    }
    case kDtmf: {
      // TODO(hlundin): Write test for this.
      return_value = DoDtmf(dtmf_event, &play_dtmf);
      break;
    }
    case kAlternativePlc: {
      // TODO(hlundin): Write test for this.
      DoAlternativePlc(false);
      break;
    }
    case kAlternativePlcIncreaseTimestamp: {
      // TODO(hlundin): Write test for this.
      DoAlternativePlc(true);
      break;
    }
    case kAudioRepetitionIncreaseTimestamp: {
      // TODO(hlundin): Write test for this.
      sync_buffer_->IncreaseEndTimestamp(output_size_samples_);
      // Skipping break on purpose. Execution should move on into the
      // next case.
    }
    case kAudioRepetition: {
      // TODO(hlundin): Write test for this.
      // Copy last |output_size_samples_| from |sync_buffer_| to
      // |algorithm_buffer|.
      algorithm_buffer_->PushBackFromIndex(
          *sync_buffer_, sync_buffer_->Size() - output_size_samples_);
      expand_->Reset();
      break;
    }
    case kUndefined: {
      LOG_F(LS_ERROR) << "Invalid operation kUndefined.";
      assert(false);  // This should not happen.
      last_mode_ = kModeError;
      return kInvalidOperation;
    }
  }  // End of switch.
  if (return_value < 0) {
    return return_value;
  }

  if (last_mode_ != kModeRfc3389Cng) {
    comfort_noise_->Reset();
  }

  // Copy from |algorithm_buffer| to |sync_buffer_|.
  sync_buffer_->PushBack(*algorithm_buffer_);

  // Extract data from |sync_buffer_| to |output|.
  size_t num_output_samples_per_channel = output_size_samples_;
  size_t num_output_samples = output_size_samples_ * sync_buffer_->Channels();
  if (num_output_samples > max_length) {
    LOG(LS_WARNING) << "Output array is too short. " << max_length << " < " <<
        output_size_samples_ << " * " << sync_buffer_->Channels();
    num_output_samples = max_length;
    num_output_samples_per_channel = static_cast<int>(
        max_length / sync_buffer_->Channels());
  }
  int samples_from_sync = static_cast<int>(
      sync_buffer_->GetNextAudioInterleaved(num_output_samples_per_channel,
                                            output));
  *num_channels = static_cast<int>(sync_buffer_->Channels());
  LOG(LS_VERBOSE) << "Sync buffer (" << *num_channels << " channel(s)):" <<
      " insert " << algorithm_buffer_->Size() << " samples, extract " <<
      samples_from_sync << " samples";
  if (samples_from_sync != output_size_samples_) {
    LOG_F(LS_ERROR) << "samples_from_sync != output_size_samples_";
    // TODO(minyue): treatment of under-run, filling zeros
    memset(output, 0, num_output_samples * sizeof(int16_t));
    *samples_per_channel = output_size_samples_;
    return kSampleUnderrun;
  }
  *samples_per_channel = output_size_samples_;

  // Should always have overlap samples left in the |sync_buffer_|.
  assert(sync_buffer_->FutureLength() >= expand_->overlap_length());

  if (play_dtmf) {
    return_value = DtmfOverdub(dtmf_event, sync_buffer_->Channels(), output);
  }

  // Update the background noise parameters if last operation wrote data
  // straight from the decoder to the |sync_buffer_|. That is, none of the
  // operations that modify the signal can be followed by a parameter update.
  if ((last_mode_ == kModeNormal) ||
      (last_mode_ == kModeAccelerateFail) ||
      (last_mode_ == kModePreemptiveExpandFail) ||
      (last_mode_ == kModeRfc3389Cng) ||
      (last_mode_ == kModeCodecInternalCng)) {
    background_noise_->Update(*sync_buffer_, *vad_.get());
  }

  if (operation == kDtmf) {
    // DTMF data was written the end of |sync_buffer_|.
    // Update index to end of DTMF data in |sync_buffer_|.
    sync_buffer_->set_dtmf_index(sync_buffer_->Size());
  }

  if (last_mode_ != kModeExpand) {
    // If last operation was not expand, calculate the |playout_timestamp_| from
    // the |sync_buffer_|. However, do not update the |playout_timestamp_| if it
    // would be moved "backwards".
    uint32_t temp_timestamp = sync_buffer_->end_timestamp() -
        static_cast<uint32_t>(sync_buffer_->FutureLength());
    if (static_cast<int32_t>(temp_timestamp - playout_timestamp_) > 0) {
      playout_timestamp_ = temp_timestamp;
    }
  } else {
    // Use dead reckoning to estimate the |playout_timestamp_|.
    playout_timestamp_ += output_size_samples_;
  }

  if (decode_return_value) return decode_return_value;
  return return_value;
}

int NetEqImpl::GetDecision(Operations* operation,
                           PacketList* packet_list,
                           DtmfEvent* dtmf_event,
                           bool* play_dtmf) {
  // Initialize output variables.
  *play_dtmf = false;
  *operation = kUndefined;

  // Increment time counters.
  packet_buffer_->IncrementWaitingTimes();
  stats_.IncreaseCounter(output_size_samples_, fs_hz_);

  assert(sync_buffer_.get());
  uint32_t end_timestamp = sync_buffer_->end_timestamp();
  if (!new_codec_) {
    packet_buffer_->DiscardOldPackets(end_timestamp);
  }
  const RTPHeader* header = packet_buffer_->NextRtpHeader();

  if (decision_logic_->CngRfc3389On() || last_mode_ == kModeRfc3389Cng) {
    // Because of timestamp peculiarities, we have to "manually" disallow using
    // a CNG packet with the same timestamp as the one that was last played.
    // This can happen when using redundancy and will cause the timing to shift.
    while (header && decoder_database_->IsComfortNoise(header->payloadType) &&
           (end_timestamp >= header->timestamp ||
            end_timestamp + decision_logic_->generated_noise_samples() >
                header->timestamp)) {
      // Don't use this packet, discard it.
      if (packet_buffer_->DiscardNextPacket() != PacketBuffer::kOK) {
        assert(false);  // Must be ok by design.
      }
      // Check buffer again.
      if (!new_codec_) {
        packet_buffer_->DiscardOldPackets(end_timestamp);
      }
      header = packet_buffer_->NextRtpHeader();
    }
  }

  assert(expand_.get());
  const int samples_left = static_cast<int>(sync_buffer_->FutureLength() -
      expand_->overlap_length());
  if (last_mode_ == kModeAccelerateSuccess ||
      last_mode_ == kModeAccelerateLowEnergy ||
      last_mode_ == kModePreemptiveExpandSuccess ||
      last_mode_ == kModePreemptiveExpandLowEnergy) {
    // Subtract (samples_left + output_size_samples_) from sampleMemory.
    decision_logic_->AddSampleMemory(-(samples_left + output_size_samples_));
  }

  // Check if it is time to play a DTMF event.
  if (dtmf_buffer_->GetEvent(end_timestamp +
                             decision_logic_->generated_noise_samples(),
                             dtmf_event)) {
    *play_dtmf = true;
  }

  // Get instruction.
  assert(sync_buffer_.get());
  assert(expand_.get());
  *operation = decision_logic_->GetDecision(*sync_buffer_,
                                            *expand_,
                                            decoder_frame_length_,
                                            header,
                                            last_mode_,
                                            *play_dtmf,
                                            &reset_decoder_);

  // Check if we already have enough samples in the |sync_buffer_|. If so,
  // change decision to normal, unless the decision was merge, accelerate, or
  // preemptive expand.
  if (samples_left >= output_size_samples_ &&
      *operation != kMerge &&
      *operation != kAccelerate &&
      *operation != kPreemptiveExpand) {
    *operation = kNormal;
    return 0;
  }

  decision_logic_->ExpandDecision(*operation);

  // Check conditions for reset.
  if (new_codec_ || *operation == kUndefined) {
    // The only valid reason to get kUndefined is that new_codec_ is set.
    assert(new_codec_);
    if (*play_dtmf && !header) {
      timestamp_ = dtmf_event->timestamp;
    } else {
      assert(header);
      if (!header) {
        LOG_F(LS_ERROR) << "Packet missing where it shouldn't.";
        return -1;
      }
      timestamp_ = header->timestamp;
      if (*operation == kRfc3389CngNoPacket
#ifndef LEGACY_BITEXACT
          // Without this check, it can happen that a non-CNG packet is sent to
          // the CNG decoder as if it was a SID frame. This is clearly a bug,
          // but is kept for now to maintain bit-exactness with the test
          // vectors.
          && decoder_database_->IsComfortNoise(header->payloadType)
#endif
      ) {
        // Change decision to CNG packet, since we do have a CNG packet, but it
        // was considered too early to use. Now, use it anyway.
        *operation = kRfc3389Cng;
      } else if (*operation != kRfc3389Cng) {
        *operation = kNormal;
      }
    }
    // Adjust |sync_buffer_| timestamp before setting |end_timestamp| to the
    // new value.
    sync_buffer_->IncreaseEndTimestamp(timestamp_ - end_timestamp);
    end_timestamp = timestamp_;
    new_codec_ = false;
    decision_logic_->SoftReset();
    buffer_level_filter_->Reset();
    delay_manager_->Reset();
    stats_.ResetMcu();
  }

  int required_samples = output_size_samples_;
  const int samples_10_ms = 80 * fs_mult_;
  const int samples_20_ms = 2 * samples_10_ms;
  const int samples_30_ms = 3 * samples_10_ms;

  switch (*operation) {
    case kExpand: {
      timestamp_ = end_timestamp;
      return 0;
    }
    case kRfc3389CngNoPacket:
    case kCodecInternalCng: {
      return 0;
    }
    case kDtmf: {
      // TODO(hlundin): Write test for this.
      // Update timestamp.
      timestamp_ = end_timestamp;
      if (decision_logic_->generated_noise_samples() > 0 &&
          last_mode_ != kModeDtmf) {
        // Make a jump in timestamp due to the recently played comfort noise.
        uint32_t timestamp_jump = decision_logic_->generated_noise_samples();
        sync_buffer_->IncreaseEndTimestamp(timestamp_jump);
        timestamp_ += timestamp_jump;
      }
      decision_logic_->set_generated_noise_samples(0);
      return 0;
    }
    case kAccelerate: {
      // In order to do a accelerate we need at least 30 ms of audio data.
      if (samples_left >= samples_30_ms) {
        // Already have enough data, so we do not need to extract any more.
        decision_logic_->set_sample_memory(samples_left);
        decision_logic_->set_prev_time_scale(true);
        return 0;
      } else if (samples_left >= samples_10_ms &&
          decoder_frame_length_ >= samples_30_ms) {
        // Avoid decoding more data as it might overflow the playout buffer.
        *operation = kNormal;
        return 0;
      } else if (samples_left < samples_20_ms &&
          decoder_frame_length_ < samples_30_ms) {
        // Build up decoded data by decoding at least 20 ms of audio data. Do
        // not perform accelerate yet, but wait until we only need to do one
        // decoding.
        required_samples = 2 * output_size_samples_;
        *operation = kNormal;
      }
      // If none of the above is true, we have one of two possible situations:
      // (1) 20 ms <= samples_left < 30 ms and decoder_frame_length_ < 30 ms; or
      // (2) samples_left < 10 ms and decoder_frame_length_ >= 30 ms.
      // In either case, we move on with the accelerate decision, and decode one
      // frame now.
      break;
    }
    case kPreemptiveExpand: {
      // In order to do a preemptive expand we need at least 30 ms of decoded
      // audio data.
      if ((samples_left >= samples_30_ms) ||
          (samples_left >= samples_10_ms &&
              decoder_frame_length_ >= samples_30_ms)) {
        // Already have enough data, so we do not need to extract any more.
        // Or, avoid decoding more data as it might overflow the playout buffer.
        // Still try preemptive expand, though.
        decision_logic_->set_sample_memory(samples_left);
        decision_logic_->set_prev_time_scale(true);
        return 0;
      }
      if (samples_left < samples_20_ms &&
          decoder_frame_length_ < samples_30_ms) {
        // Build up decoded data by decoding at least 20 ms of audio data.
        // Still try to perform preemptive expand.
        required_samples = 2 * output_size_samples_;
      }
      // Move on with the preemptive expand decision.
      break;
    }
    case kMerge: {
      required_samples =
          std::max(merge_->RequiredFutureSamples(), required_samples);
      break;
    }
    default: {
      // Do nothing.
    }
  }

  // Get packets from buffer.
  int extracted_samples = 0;
  if (header &&
      *operation != kAlternativePlc &&
      *operation != kAlternativePlcIncreaseTimestamp &&
      *operation != kAudioRepetition &&
      *operation != kAudioRepetitionIncreaseTimestamp) {
    sync_buffer_->IncreaseEndTimestamp(header->timestamp - end_timestamp);
    if (decision_logic_->CngOff()) {
      // Adjustment of timestamp only corresponds to an actual packet loss
      // if comfort noise is not played. If comfort noise was just played,
      // this adjustment of timestamp is only done to get back in sync with the
      // stream timestamp; no loss to report.
      stats_.LostSamples(header->timestamp - end_timestamp);
    }

    if (*operation != kRfc3389Cng) {
      // We are about to decode and use a non-CNG packet.
      decision_logic_->SetCngOff();
    }
    // Reset CNG timestamp as a new packet will be delivered.
    // (Also if this is a CNG packet, since playedOutTS is updated.)
    decision_logic_->set_generated_noise_samples(0);

    extracted_samples = ExtractPackets(required_samples, packet_list);
    if (extracted_samples < 0) {
      LOG_F(LS_WARNING) << "Failed to extract packets from buffer.";
      return kPacketBufferCorruption;
    }
  }

  if (*operation == kAccelerate ||
      *operation == kPreemptiveExpand) {
    decision_logic_->set_sample_memory(samples_left + extracted_samples);
    decision_logic_->set_prev_time_scale(true);
  }

  if (*operation == kAccelerate) {
    // Check that we have enough data (30ms) to do accelerate.
    if (extracted_samples + samples_left < samples_30_ms) {
      // TODO(hlundin): Write test for this.
      // Not enough, do normal operation instead.
      *operation = kNormal;
    }
  }

  timestamp_ = end_timestamp;
  return 0;
}

int NetEqImpl::Decode(PacketList* packet_list, Operations* operation,
                      int* decoded_length,
                      AudioDecoder::SpeechType* speech_type) {
  *speech_type = AudioDecoder::kSpeech;
  AudioDecoder* decoder = NULL;
  if (!packet_list->empty()) {
    const Packet* packet = packet_list->front();
    int payload_type = packet->header.payloadType;
    if (!decoder_database_->IsComfortNoise(payload_type)) {
      decoder = decoder_database_->GetDecoder(payload_type);
      assert(decoder);
      if (!decoder) {
        LOG_FERR1(LS_WARNING, GetDecoder, payload_type);
        PacketBuffer::DeleteAllPackets(packet_list);
        return kDecoderNotFound;
      }
      bool decoder_changed;
      decoder_database_->SetActiveDecoder(payload_type, &decoder_changed);
      if (decoder_changed) {
        // We have a new decoder. Re-init some values.
        const DecoderDatabase::DecoderInfo* decoder_info = decoder_database_
            ->GetDecoderInfo(payload_type);
        assert(decoder_info);
        if (!decoder_info) {
          LOG_FERR1(LS_WARNING, GetDecoderInfo, payload_type);
          PacketBuffer::DeleteAllPackets(packet_list);
          return kDecoderNotFound;
        }
        // If sampling rate or number of channels has changed, we need to make
        // a reset.
        if (decoder_info->fs_hz != fs_hz_ ||
            decoder->channels() != algorithm_buffer_->Channels()) {
          // TODO(tlegrand): Add unittest to cover this event.
          SetSampleRateAndChannels(decoder_info->fs_hz, decoder->channels());
        }
        sync_buffer_->set_end_timestamp(timestamp_);
        playout_timestamp_ = timestamp_;
      }
    }
  }

  if (reset_decoder_) {
    // TODO(hlundin): Write test for this.
    // Reset decoder.
    if (decoder) {
      decoder->Init();
    }
    // Reset comfort noise decoder.
    AudioDecoder* cng_decoder = decoder_database_->GetActiveCngDecoder();
    if (cng_decoder) {
      cng_decoder->Init();
    }
    reset_decoder_ = false;
  }

#ifdef LEGACY_BITEXACT
  // Due to a bug in old SignalMCU, it could happen that CNG operation was
  // decided, but a speech packet was provided. The speech packet will be used
  // to update the comfort noise decoder, as if it was a SID frame, which is
  // clearly wrong.
  if (*operation == kRfc3389Cng) {
    return 0;
  }
#endif

  *decoded_length = 0;
  // Update codec-internal PLC state.
  if ((*operation == kMerge) && decoder && decoder->HasDecodePlc()) {
    decoder->DecodePlc(1, &decoded_buffer_[*decoded_length]);
  }

  int return_value = DecodeLoop(packet_list, operation, decoder,
                                decoded_length, speech_type);

  if (*decoded_length < 0) {
    // Error returned from the decoder.
    *decoded_length = 0;
    sync_buffer_->IncreaseEndTimestamp(decoder_frame_length_);
    int error_code = 0;
    if (decoder)
      error_code = decoder->ErrorCode();
    if (error_code != 0) {
      // Got some error code from the decoder.
      decoder_error_code_ = error_code;
      return_value = kDecoderErrorCode;
    } else {
      // Decoder does not implement error codes. Return generic error.
      return_value = kOtherDecoderError;
    }
    LOG_FERR2(LS_WARNING, DecodeLoop, error_code, packet_list->size());
    *operation = kExpand;  // Do expansion to get data instead.
  }
  if (*speech_type != AudioDecoder::kComfortNoise) {
    // Don't increment timestamp if codec returned CNG speech type
    // since in this case, the we will increment the CNGplayedTS counter.
    // Increase with number of samples per channel.
    assert(*decoded_length == 0 ||
           (decoder && decoder->channels() == sync_buffer_->Channels()));
    sync_buffer_->IncreaseEndTimestamp(
        *decoded_length / static_cast<int>(sync_buffer_->Channels()));
  }
  return return_value;
}

int NetEqImpl::DecodeLoop(PacketList* packet_list, Operations* operation,
                          AudioDecoder* decoder, int* decoded_length,
                          AudioDecoder::SpeechType* speech_type) {
  Packet* packet = NULL;
  if (!packet_list->empty()) {
    packet = packet_list->front();
  }
  // Do decoding.
  while (packet &&
      !decoder_database_->IsComfortNoise(packet->header.payloadType)) {
    assert(decoder);  // At this point, we must have a decoder object.
    // The number of channels in the |sync_buffer_| should be the same as the
    // number decoder channels.
    assert(sync_buffer_->Channels() == decoder->channels());
    assert(decoded_buffer_length_ >= kMaxFrameSize * decoder->channels());
    assert(*operation == kNormal || *operation == kAccelerate ||
           *operation == kMerge || *operation == kPreemptiveExpand);
    packet_list->pop_front();
    int payload_length = packet->payload_length;
    int16_t decode_length;
    if (packet->sync_packet) {
      // Decode to silence with the same frame size as the last decode.
      LOG(LS_VERBOSE) << "Decoding sync-packet: " <<
          " ts=" << packet->header.timestamp <<
          ", sn=" << packet->header.sequenceNumber <<
          ", pt=" << static_cast<int>(packet->header.payloadType) <<
          ", ssrc=" << packet->header.ssrc <<
          ", len=" << packet->payload_length;
      memset(&decoded_buffer_[*decoded_length], 0, decoder_frame_length_ *
             decoder->channels() * sizeof(decoded_buffer_[0]));
      decode_length = decoder_frame_length_;
    } else if (!packet->primary) {
      // This is a redundant payload; call the special decoder method.
      LOG(LS_VERBOSE) << "Decoding packet (redundant):" <<
          " ts=" << packet->header.timestamp <<
          ", sn=" << packet->header.sequenceNumber <<
          ", pt=" << static_cast<int>(packet->header.payloadType) <<
          ", ssrc=" << packet->header.ssrc <<
          ", len=" << packet->payload_length;
      decode_length = decoder->DecodeRedundant(
          packet->payload, packet->payload_length,
          &decoded_buffer_[*decoded_length], decoded_buffer_length_ - *decoded_length, speech_type);
    } else {
      LOG(LS_VERBOSE) << "Decoding packet: ts=" << packet->header.timestamp <<
          ", sn=" << packet->header.sequenceNumber <<
          ", pt=" << static_cast<int>(packet->header.payloadType) <<
          ", ssrc=" << packet->header.ssrc <<
          ", len=" << packet->payload_length;
      decode_length = decoder->Decode(packet->payload,
                                      packet->payload_length,
                                      &decoded_buffer_[*decoded_length],
                                      decoded_buffer_length_ - *decoded_length,
                                      speech_type);
    }

    delete[] packet->payload;
    delete packet;
    packet = NULL;
    if (decode_length > 0) {
      *decoded_length += decode_length;
      // Update |decoder_frame_length_| with number of samples per channel.
      decoder_frame_length_ = decode_length /
          static_cast<int>(decoder->channels());
      LOG(LS_VERBOSE) << "Decoded " << decode_length << " samples (" <<
          decoder->channels() << " channel(s) -> " << decoder_frame_length_ <<
          " samples per channel)";
    } else if (decode_length < 0) {
      // Error.
      LOG_FERR2(LS_WARNING, Decode, decode_length, payload_length);
      *decoded_length = -1;
      PacketBuffer::DeleteAllPackets(packet_list);
      break;
    }
    if (*decoded_length > static_cast<int>(decoded_buffer_length_)) {
      // Guard against overflow.
      LOG_F(LS_WARNING) << "Decoded too much.";
      PacketBuffer::DeleteAllPackets(packet_list);
      return kDecodedTooMuch;
    }
    if (!packet_list->empty()) {
      packet = packet_list->front();
    } else {
      packet = NULL;
    }
  }  // End of decode loop.

  // If the list is not empty at this point, either a decoding error terminated
  // the while-loop, or list must hold exactly one CNG packet.
  assert(packet_list->empty() || *decoded_length < 0 ||
         (packet_list->size() == 1 && packet &&
             decoder_database_->IsComfortNoise(packet->header.payloadType)));
  return 0;
}

void NetEqImpl::DoNormal(const int16_t* decoded_buffer, size_t decoded_length,
                         AudioDecoder::SpeechType speech_type, bool play_dtmf) {
  assert(normal_.get());
  assert(mute_factor_array_.get());
  normal_->Process(decoded_buffer, decoded_length, last_mode_,
                   mute_factor_array_.get(), algorithm_buffer_.get());
  if (decoded_length != 0) {
    last_mode_ = kModeNormal;
  }

  // If last packet was decoded as an inband CNG, set mode to CNG instead.
  if ((speech_type == AudioDecoder::kComfortNoise)
      || ((last_mode_ == kModeCodecInternalCng)
          && (decoded_length == 0))) {
    // TODO(hlundin): Remove second part of || statement above.
    last_mode_ = kModeCodecInternalCng;
  }

  if (!play_dtmf) {
    dtmf_tone_generator_->Reset();
  }
}

void NetEqImpl::DoMerge(int16_t* decoded_buffer, size_t decoded_length,
                        AudioDecoder::SpeechType speech_type, bool play_dtmf) {
  assert(mute_factor_array_.get());
  assert(merge_.get());
  int new_length = merge_->Process(decoded_buffer, decoded_length,
                                   mute_factor_array_.get(),
                                   algorithm_buffer_.get());

  // Update in-call and post-call statistics.
  if (expand_->MuteFactor(0) == 0) {
    // Expand generates only noise.
    stats_.ExpandedNoiseSamples(new_length - static_cast<int>(decoded_length));
  } else {
    // Expansion generates more than only noise.
    stats_.ExpandedVoiceSamples(new_length - static_cast<int>(decoded_length));
  }

  last_mode_ = kModeMerge;
  // If last packet was decoded as an inband CNG, set mode to CNG instead.
  if (speech_type == AudioDecoder::kComfortNoise) {
    last_mode_ = kModeCodecInternalCng;
  }
  expand_->Reset();
  if (!play_dtmf) {
    dtmf_tone_generator_->Reset();
  }
}

int NetEqImpl::DoExpand(bool play_dtmf) {
  while ((sync_buffer_->FutureLength() - expand_->overlap_length()) <
      static_cast<size_t>(output_size_samples_)) {
    algorithm_buffer_->Clear();
    int return_value = expand_->Process(algorithm_buffer_.get());
    int length = static_cast<int>(algorithm_buffer_->Size());

    // Update in-call and post-call statistics.
    if (expand_->MuteFactor(0) == 0) {
      // Expand operation generates only noise.
      stats_.ExpandedNoiseSamples(length);
    } else {
      // Expand operation generates more than only noise.
      stats_.ExpandedVoiceSamples(length);
    }

    last_mode_ = kModeExpand;

    if (return_value < 0) {
      return return_value;
    }

    sync_buffer_->PushBack(*algorithm_buffer_);
    algorithm_buffer_->Clear();
  }
  if (!play_dtmf) {
    dtmf_tone_generator_->Reset();
  }
  return 0;
}

int NetEqImpl::DoAccelerate(int16_t* decoded_buffer, size_t decoded_length,
                            AudioDecoder::SpeechType speech_type,
                            bool play_dtmf) {
  const size_t required_samples = 240 * fs_mult_;  // Must have 30 ms.
  size_t borrowed_samples_per_channel = 0;
  size_t num_channels = algorithm_buffer_->Channels();
  size_t decoded_length_per_channel = decoded_length / num_channels;
  if (decoded_length_per_channel < required_samples) {
    // Must move data from the |sync_buffer_| in order to get 30 ms.
    borrowed_samples_per_channel = static_cast<int>(required_samples -
        decoded_length_per_channel);
    memmove(&decoded_buffer[borrowed_samples_per_channel * num_channels],
            decoded_buffer,
            sizeof(int16_t) * decoded_length);
    sync_buffer_->ReadInterleavedFromEnd(borrowed_samples_per_channel,
                                         decoded_buffer);
    decoded_length = required_samples * num_channels;
  }

  int16_t samples_removed;
  Accelerate::ReturnCodes return_code = accelerate_->Process(
      decoded_buffer, decoded_length, algorithm_buffer_.get(),
      &samples_removed);
  stats_.AcceleratedSamples(samples_removed);
  switch (return_code) {
    case Accelerate::kSuccess:
      last_mode_ = kModeAccelerateSuccess;
      break;
    case Accelerate::kSuccessLowEnergy:
      last_mode_ = kModeAccelerateLowEnergy;
      break;
    case Accelerate::kNoStretch:
      last_mode_ = kModeAccelerateFail;
      break;
    case Accelerate::kError:
      // TODO(hlundin): Map to kModeError instead?
      last_mode_ = kModeAccelerateFail;
      return kAccelerateError;
  }

  if (borrowed_samples_per_channel > 0) {
    // Copy borrowed samples back to the |sync_buffer_|.
    size_t length = algorithm_buffer_->Size();
    if (length < borrowed_samples_per_channel) {
      // This destroys the beginning of the buffer, but will not cause any
      // problems.
      sync_buffer_->ReplaceAtIndex(*algorithm_buffer_,
                                   sync_buffer_->Size() -
                                   borrowed_samples_per_channel);
      sync_buffer_->PushFrontZeros(borrowed_samples_per_channel - length);
      algorithm_buffer_->PopFront(length);
      assert(algorithm_buffer_->Empty());
    } else {
      sync_buffer_->ReplaceAtIndex(*algorithm_buffer_,
                                   borrowed_samples_per_channel,
                                   sync_buffer_->Size() -
                                   borrowed_samples_per_channel);
      algorithm_buffer_->PopFront(borrowed_samples_per_channel);
    }
  }

  // If last packet was decoded as an inband CNG, set mode to CNG instead.
  if (speech_type == AudioDecoder::kComfortNoise) {
    last_mode_ = kModeCodecInternalCng;
  }
  if (!play_dtmf) {
    dtmf_tone_generator_->Reset();
  }
  expand_->Reset();
  return 0;
}

int NetEqImpl::DoPreemptiveExpand(int16_t* decoded_buffer,
                                  size_t decoded_length,
                                  AudioDecoder::SpeechType speech_type,
                                  bool play_dtmf) {
  const size_t required_samples = 240 * fs_mult_;  // Must have 30 ms.
  size_t num_channels = algorithm_buffer_->Channels();
  int borrowed_samples_per_channel = 0;
  int old_borrowed_samples_per_channel = 0;
  size_t decoded_length_per_channel = decoded_length / num_channels;
  if (decoded_length_per_channel < required_samples) {
    // Must move data from the |sync_buffer_| in order to get 30 ms.
    borrowed_samples_per_channel = static_cast<int>(required_samples -
        decoded_length_per_channel);
    // Calculate how many of these were already played out.
    old_borrowed_samples_per_channel = static_cast<int>(
        borrowed_samples_per_channel - sync_buffer_->FutureLength());
    old_borrowed_samples_per_channel = std::max(
        0, old_borrowed_samples_per_channel);
    memmove(&decoded_buffer[borrowed_samples_per_channel * num_channels],
            decoded_buffer,
            sizeof(int16_t) * decoded_length);
    sync_buffer_->ReadInterleavedFromEnd(borrowed_samples_per_channel,
                                         decoded_buffer);
    decoded_length = required_samples * num_channels;
  }

  int16_t samples_added;
  PreemptiveExpand::ReturnCodes return_code = preemptive_expand_->Process(
      decoded_buffer, static_cast<int>(decoded_length),
      old_borrowed_samples_per_channel,
      algorithm_buffer_.get(), &samples_added);
  stats_.PreemptiveExpandedSamples(samples_added);
  switch (return_code) {
    case PreemptiveExpand::kSuccess:
      last_mode_ = kModePreemptiveExpandSuccess;
      break;
    case PreemptiveExpand::kSuccessLowEnergy:
      last_mode_ = kModePreemptiveExpandLowEnergy;
      break;
    case PreemptiveExpand::kNoStretch:
      last_mode_ = kModePreemptiveExpandFail;
      break;
    case PreemptiveExpand::kError:
      // TODO(hlundin): Map to kModeError instead?
      last_mode_ = kModePreemptiveExpandFail;
      return kPreemptiveExpandError;
  }

  if (borrowed_samples_per_channel > 0) {
    // Copy borrowed samples back to the |sync_buffer_|.
    sync_buffer_->ReplaceAtIndex(
        *algorithm_buffer_, borrowed_samples_per_channel,
        sync_buffer_->Size() - borrowed_samples_per_channel);
    algorithm_buffer_->PopFront(borrowed_samples_per_channel);
  }

  // If last packet was decoded as an inband CNG, set mode to CNG instead.
  if (speech_type == AudioDecoder::kComfortNoise) {
    last_mode_ = kModeCodecInternalCng;
  }
  if (!play_dtmf) {
    dtmf_tone_generator_->Reset();
  }
  expand_->Reset();
  return 0;
}

int NetEqImpl::DoRfc3389Cng(PacketList* packet_list, bool play_dtmf) {
  if (!packet_list->empty()) {
    // Must have exactly one SID frame at this point.
    assert(packet_list->size() == 1);
    Packet* packet = packet_list->front();
    packet_list->pop_front();
    if (!decoder_database_->IsComfortNoise(packet->header.payloadType)) {
#ifdef LEGACY_BITEXACT
      // This can happen due to a bug in GetDecision. Change the payload type
      // to a CNG type, and move on. Note that this means that we are in fact
      // sending a non-CNG payload to the comfort noise decoder for decoding.
      // Clearly wrong, but will maintain bit-exactness with legacy.
      if (fs_hz_ == 8000) {
        packet->header.payloadType =
            decoder_database_->GetRtpPayloadType(kDecoderCNGnb);
      } else if (fs_hz_ == 16000) {
        packet->header.payloadType =
            decoder_database_->GetRtpPayloadType(kDecoderCNGwb);
      } else if (fs_hz_ == 32000) {
        packet->header.payloadType =
            decoder_database_->GetRtpPayloadType(kDecoderCNGswb32kHz);
      } else if (fs_hz_ == 48000) {
        packet->header.payloadType =
            decoder_database_->GetRtpPayloadType(kDecoderCNGswb48kHz);
      }
      assert(decoder_database_->IsComfortNoise(packet->header.payloadType));
#else
      LOG(LS_ERROR) << "Trying to decode non-CNG payload as CNG.";
      return kOtherError;
#endif
    }
    // UpdateParameters() deletes |packet|.
    if (comfort_noise_->UpdateParameters(packet) ==
        ComfortNoise::kInternalError) {
      LOG_FERR0(LS_WARNING, UpdateParameters);
      algorithm_buffer_->Zeros(output_size_samples_);
      return -comfort_noise_->internal_error_code();
    }
  }
  int cn_return = comfort_noise_->Generate(output_size_samples_,
                                           algorithm_buffer_.get());
  expand_->Reset();
  last_mode_ = kModeRfc3389Cng;
  if (!play_dtmf) {
    dtmf_tone_generator_->Reset();
  }
  if (cn_return == ComfortNoise::kInternalError) {
    LOG_FERR1(LS_WARNING, comfort_noise_->Generate, cn_return);
    decoder_error_code_ = comfort_noise_->internal_error_code();
    return kComfortNoiseErrorCode;
  } else if (cn_return == ComfortNoise::kUnknownPayloadType) {
    LOG_FERR1(LS_WARNING, comfort_noise_->Generate, cn_return);
    return kUnknownRtpPayloadType;
  }
  return 0;
}

void NetEqImpl::DoCodecInternalCng() {
  int length = 0;
  // TODO(hlundin): Will probably need a longer buffer for multi-channel.
  int16_t decoded_buffer[kMaxFrameSize];
  AudioDecoder* decoder = decoder_database_->GetActiveDecoder();
  if (decoder) {
    const uint8_t* dummy_payload = NULL;
    AudioDecoder::SpeechType speech_type;
    length = decoder->Decode(dummy_payload, 0, decoded_buffer, kMaxFrameSize, &speech_type);
  }
  assert(mute_factor_array_.get());
  normal_->Process(decoded_buffer, length, last_mode_, mute_factor_array_.get(),
                   algorithm_buffer_.get());
  last_mode_ = kModeCodecInternalCng;
  expand_->Reset();
}

int NetEqImpl::DoDtmf(const DtmfEvent& dtmf_event, bool* play_dtmf) {
  // This block of the code and the block further down, handling |dtmf_switch|
  // are commented out. Otherwise playing out-of-band DTMF would fail in VoE
  // test, DtmfTest.ManualSuccessfullySendsOutOfBandTelephoneEvents. This is
  // equivalent to |dtmf_switch| always be false.
  //
  // See http://webrtc-codereview.appspot.com/1195004/ for discussion
  // On this issue. This change might cause some glitches at the point of
  // switch from audio to DTMF. Issue 1545 is filed to track this.
  //
  //  bool dtmf_switch = false;
  //  if ((last_mode_ != kModeDtmf) && dtmf_tone_generator_->initialized()) {
  //    // Special case; see below.
  //    // We must catch this before calling Generate, since |initialized| is
  //    // modified in that call.
  //    dtmf_switch = true;
  //  }

  int dtmf_return_value = 0;
  if (!dtmf_tone_generator_->initialized()) {
    // Initialize if not already done.
    dtmf_return_value = dtmf_tone_generator_->Init(fs_hz_, dtmf_event.event_no,
                                                   dtmf_event.volume);
  }

  if (dtmf_return_value == 0) {
    // Generate DTMF signal.
    dtmf_return_value = dtmf_tone_generator_->Generate(output_size_samples_,
                                                       algorithm_buffer_.get());
  }

  if (dtmf_return_value < 0) {
    algorithm_buffer_->Zeros(output_size_samples_);
    return dtmf_return_value;
  }

  //  if (dtmf_switch) {
  //    // This is the special case where the previous operation was DTMF
  //    // overdub, but the current instruction is "regular" DTMF. We must make
  //    // sure that the DTMF does not have any discontinuities. The first DTMF
  //    // sample that we generate now must be played out immediately, therefore
  //    // it must be copied to the speech buffer.
  //    // TODO(hlundin): This code seems incorrect. (Legacy.) Write test and
  //    // verify correct operation.
  //    assert(false);
  //    // Must generate enough data to replace all of the |sync_buffer_|
  //    // "future".
  //    int required_length = sync_buffer_->FutureLength();
  //    assert(dtmf_tone_generator_->initialized());
  //    dtmf_return_value = dtmf_tone_generator_->Generate(required_length,
  //                                                       algorithm_buffer_);
  //    assert((size_t) required_length == algorithm_buffer_->Size());
  //    if (dtmf_return_value < 0) {
  //      algorithm_buffer_->Zeros(output_size_samples_);
  //      return dtmf_return_value;
  //    }
  //
  //    // Overwrite the "future" part of the speech buffer with the new DTMF
  //    // data.
  //    // TODO(hlundin): It seems that this overwriting has gone lost.
  //    // Not adapted for multi-channel yet.
  //    assert(algorithm_buffer_->Channels() == 1);
  //    if (algorithm_buffer_->Channels() != 1) {
  //      LOG(LS_WARNING) << "DTMF not supported for more than one channel";
  //      return kStereoNotSupported;
  //    }
  //    // Shuffle the remaining data to the beginning of algorithm buffer.
  //    algorithm_buffer_->PopFront(sync_buffer_->FutureLength());
  //  }

  sync_buffer_->IncreaseEndTimestamp(output_size_samples_);
  expand_->Reset();
  last_mode_ = kModeDtmf;

  // Set to false because the DTMF is already in the algorithm buffer.
  *play_dtmf = false;
  return 0;
}

void NetEqImpl::DoAlternativePlc(bool increase_timestamp) {
  AudioDecoder* decoder = decoder_database_->GetActiveDecoder();
  int length;
  if (decoder && decoder->HasDecodePlc()) {
    // Use the decoder's packet-loss concealment.
    // TODO(hlundin): Will probably need a longer buffer for multi-channel.
    int16_t decoded_buffer[kMaxFrameSize];
    length = decoder->DecodePlc(1, decoded_buffer);
    if (length > 0) {
      algorithm_buffer_->PushBackInterleaved(decoded_buffer, length);
    } else {
      length = 0;
    }
  } else {
    // Do simple zero-stuffing.
    length = output_size_samples_;
    algorithm_buffer_->Zeros(length);
    // By not advancing the timestamp, NetEq inserts samples.
    stats_.AddZeros(length);
  }
  if (increase_timestamp) {
    sync_buffer_->IncreaseEndTimestamp(length);
  }
  expand_->Reset();
}

int NetEqImpl::DtmfOverdub(const DtmfEvent& dtmf_event, size_t num_channels,
                           int16_t* output) const {
  size_t out_index = 0;
  int overdub_length = output_size_samples_;  // Default value.

  if (sync_buffer_->dtmf_index() > sync_buffer_->next_index()) {
    // Special operation for transition from "DTMF only" to "DTMF overdub".
    out_index = std::min(
        sync_buffer_->dtmf_index() - sync_buffer_->next_index(),
        static_cast<size_t>(output_size_samples_));
    overdub_length = output_size_samples_ - static_cast<int>(out_index);
  }

  AudioMultiVector dtmf_output(num_channels);
  int dtmf_return_value = 0;
  if (!dtmf_tone_generator_->initialized()) {
    dtmf_return_value = dtmf_tone_generator_->Init(fs_hz_, dtmf_event.event_no,
                                                   dtmf_event.volume);
  }
  if (dtmf_return_value == 0) {
    dtmf_return_value = dtmf_tone_generator_->Generate(overdub_length,
                                                       &dtmf_output);
    assert((size_t) overdub_length == dtmf_output.Size());
  }
  dtmf_output.ReadInterleaved(overdub_length, &output[out_index]);
  return dtmf_return_value < 0 ? dtmf_return_value : 0;
}

int NetEqImpl::ExtractPackets(int required_samples, PacketList* packet_list) {
  bool first_packet = true;
  uint8_t prev_payload_type = 0;
  uint32_t prev_timestamp = 0;
  uint16_t prev_sequence_number = 0;
  bool next_packet_available = false;

  const RTPHeader* header = packet_buffer_->NextRtpHeader();
  assert(header);
  if (!header) {
    return -1;
  }
  uint32_t first_timestamp = header->timestamp;
  int extracted_samples = 0;

  // Packet extraction loop.
  do {
    timestamp_ = header->timestamp;
    int discard_count = 0;
    Packet* packet = packet_buffer_->GetNextPacket(&discard_count);
    // |header| may be invalid after the |packet_buffer_| operation.
    header = NULL;
    if (!packet) {
      LOG_FERR1(LS_ERROR, GetNextPacket, discard_count) <<
          "Should always be able to extract a packet here";
      assert(false);  // Should always be able to extract a packet here.
      return -1;
    }
    stats_.PacketsDiscarded(discard_count);
    // Store waiting time in ms; packets->waiting_time is in "output blocks".
    stats_.StoreWaitingTime(packet->waiting_time * kOutputSizeMs);
    assert(packet->payload_length > 0);
    packet_list->push_back(packet);  // Store packet in list.

    if (first_packet) {
      first_packet = false;
      decoded_packet_sequence_number_ = prev_sequence_number =
          packet->header.sequenceNumber;
      decoded_packet_timestamp_ = prev_timestamp = packet->header.timestamp;
      prev_payload_type = packet->header.payloadType;
    }

    // Store number of extracted samples.
    int packet_duration = 0;
    AudioDecoder* decoder = decoder_database_->GetDecoder(
        packet->header.payloadType);
    if (decoder) {
      if (packet->sync_packet) {
        packet_duration = decoder_frame_length_;
      } else {
        packet_duration = packet->primary ?
            decoder->PacketDuration(packet->payload, packet->payload_length) :
            decoder->PacketDurationRedundant(packet->payload,
                                             packet->payload_length);
      }
    } else {
      LOG_FERR1(LS_WARNING, GetDecoder, packet->header.payloadType) <<
          "Could not find a decoder for a packet about to be extracted.";
      assert(false);
    }
    if (packet_duration <= 0) {
      // Decoder did not return a packet duration. Assume that the packet
      // contains the same number of samples as the previous one.
      packet_duration = decoder_frame_length_;
    }
    extracted_samples = packet->header.timestamp - first_timestamp +
        packet_duration;

    // Check what packet is available next.
    header = packet_buffer_->NextRtpHeader();
    next_packet_available = false;
    if (header && prev_payload_type == header->payloadType) {
      int16_t seq_no_diff = header->sequenceNumber - prev_sequence_number;
      int32_t ts_diff = header->timestamp - prev_timestamp;
      if (seq_no_diff == 1 ||
          (seq_no_diff == 0 && ts_diff == decoder_frame_length_)) {
        // The next sequence number is available, or the next part of a packet
        // that was split into pieces upon insertion.
        next_packet_available = true;
      }
      prev_sequence_number = header->sequenceNumber;
    }
  } while (extracted_samples < required_samples && next_packet_available);

  return extracted_samples;
}

void NetEqImpl::UpdatePlcComponents(int fs_hz, size_t channels) {
  // Delete objects and create new ones.
  expand_.reset(expand_factory_->Create(background_noise_.get(),
                                        sync_buffer_.get(), &random_vector_,
                                        fs_hz, channels));
  merge_.reset(new Merge(fs_hz, channels, expand_.get(), sync_buffer_.get()));
}

void NetEqImpl::SetSampleRateAndChannels(int fs_hz, size_t channels) {
  LOG_API2(fs_hz, channels);
  // TODO(hlundin): Change to an enumerator and skip assert.
  assert(fs_hz == 8000 || fs_hz == 16000 || fs_hz ==  32000 || fs_hz == 48000);
  assert(channels > 0);

  fs_hz_ = fs_hz;
  fs_mult_ = fs_hz / 8000;
  output_size_samples_ = kOutputSizeMs * 8 * fs_mult_;
  decoder_frame_length_ = 3 * output_size_samples_;  // Initialize to 30ms.

  last_mode_ = kModeNormal;

  // Create a new array of mute factors and set all to 1.
  mute_factor_array_.reset(new int16_t[channels]);
  for (size_t i = 0; i < channels; ++i) {
    mute_factor_array_[i] = 16384;  // 1.0 in Q14.
  }

  // Reset comfort noise decoder, if there is one active.
  AudioDecoder* cng_decoder = decoder_database_->GetActiveCngDecoder();
  if (cng_decoder) {
    cng_decoder->Init();
  }

  // Reinit post-decode VAD with new sample rate.
  assert(vad_.get());  // Cannot be NULL here.
  vad_->Init();

  // Delete algorithm buffer and create a new one.
  algorithm_buffer_.reset(new AudioMultiVector(channels));

  // Delete sync buffer and create a new one.
  sync_buffer_.reset(new SyncBuffer(channels, kSyncBufferSize * fs_mult_));

  // Delete BackgroundNoise object and create a new one.
  background_noise_.reset(new BackgroundNoise(channels));
  background_noise_->set_mode(background_noise_mode_);

  // Reset random vector.
  random_vector_.Reset();

  UpdatePlcComponents(fs_hz, channels);

  // Move index so that we create a small set of future samples (all 0).
  sync_buffer_->set_next_index(sync_buffer_->next_index() -
      expand_->overlap_length());

  normal_.reset(new Normal(fs_hz, decoder_database_.get(), *background_noise_,
                           expand_.get()));
  accelerate_.reset(
      accelerate_factory_->Create(fs_hz, channels, *background_noise_));
  preemptive_expand_.reset(preemptive_expand_factory_->Create(
      fs_hz, channels,
      *background_noise_,
      static_cast<int>(expand_->overlap_length())));

  // Delete ComfortNoise object and create a new one.
  comfort_noise_.reset(new ComfortNoise(fs_hz, decoder_database_.get(),
                                        sync_buffer_.get()));

  // Verify that |decoded_buffer_| is long enough.
  if (decoded_buffer_length_ < kMaxFrameSize * channels) {
    // Reallocate to larger size.
    decoded_buffer_length_ = kMaxFrameSize * channels;
    decoded_buffer_.reset(new int16_t[decoded_buffer_length_]);
  }

  // Create DecisionLogic if it is not created yet, then communicate new sample
  // rate and output size to DecisionLogic object.
  if (!decision_logic_.get()) {
    CreateDecisionLogic(kPlayoutOn);
  }
  decision_logic_->SetSampleRate(fs_hz_, output_size_samples_);
}

NetEqOutputType NetEqImpl::LastOutputType() {
  assert(vad_.get());
  assert(expand_.get());
  if (last_mode_ == kModeCodecInternalCng || last_mode_ == kModeRfc3389Cng) {
    return kOutputCNG;
  } else if (last_mode_ == kModeExpand && expand_->MuteFactor(0) == 0) {
    // Expand mode has faded down to background noise only (very long expand).
    return kOutputPLCtoCNG;
  } else if (last_mode_ == kModeExpand) {
    return kOutputPLC;
  } else if (vad_->running() && !vad_->active_speech()) {
    return kOutputVADPassive;
  } else {
    return kOutputNormal;
  }
}

void NetEqImpl::CreateDecisionLogic(NetEqPlayoutMode mode) {
  decision_logic_.reset(DecisionLogic::Create(fs_hz_, output_size_samples_,
                                              mode,
                                              decoder_database_.get(),
                                              *packet_buffer_.get(),
                                              delay_manager_.get(),
                                              buffer_level_filter_.get()));
}
}  // namespace webrtc
