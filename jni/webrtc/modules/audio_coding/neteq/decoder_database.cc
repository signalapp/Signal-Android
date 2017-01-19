/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/modules/audio_coding/neteq/decoder_database.h"

#include <assert.h>
#include <utility>  // pair

#include "webrtc/modules/audio_coding/neteq/interface/audio_decoder.h"

namespace webrtc {

DecoderDatabase::DecoderDatabase()
    : active_decoder_(-1), active_cng_decoder_(-1) {}

DecoderDatabase::~DecoderDatabase() {}

DecoderDatabase::DecoderInfo::~DecoderInfo() {
  if (!external) delete decoder;
}

bool DecoderDatabase::Empty() const { return decoders_.empty(); }

int DecoderDatabase::Size() const { return static_cast<int>(decoders_.size()); }

void DecoderDatabase::Reset() {
  decoders_.clear();
  active_decoder_ = -1;
  active_cng_decoder_ = -1;
}

int DecoderDatabase::RegisterPayload(uint8_t rtp_payload_type,
                                     NetEqDecoder codec_type) {
  if (rtp_payload_type > kMaxRtpPayloadType) {
    return kInvalidRtpPayloadType;
  }
  if (!AudioDecoder::CodecSupported(codec_type)) {
    return kCodecNotSupported;
  }
  int fs_hz = AudioDecoder::CodecSampleRateHz(codec_type);
  std::pair<DecoderMap::iterator, bool> ret;
  DecoderInfo info(codec_type, fs_hz, NULL, false);
  ret = decoders_.insert(std::make_pair(rtp_payload_type, info));
  if (ret.second == false) {
    // Database already contains a decoder with type |rtp_payload_type|.
    return kDecoderExists;
  }
  return kOK;
}

int DecoderDatabase::InsertExternal(uint8_t rtp_payload_type,
                                    NetEqDecoder codec_type,
                                    int fs_hz,
                                    AudioDecoder* decoder) {
  if (rtp_payload_type > 0x7F) {
    return kInvalidRtpPayloadType;
  }
  if (!AudioDecoder::CodecSupported(codec_type)) {
    return kCodecNotSupported;
  }
  if (fs_hz != 8000 && fs_hz != 16000 && fs_hz != 32000 && fs_hz != 48000) {
    return kInvalidSampleRate;
  }
  if (!decoder) {
    return kInvalidPointer;
  }
  decoder->Init();
  std::pair<DecoderMap::iterator, bool> ret;
  DecoderInfo info(codec_type, fs_hz, decoder, true);
  ret = decoders_.insert(
      std::pair<uint8_t, DecoderInfo>(rtp_payload_type, info));
  if (ret.second == false) {
    // Database already contains a decoder with type |rtp_payload_type|.
    return kDecoderExists;
  }
  return kOK;
}

int DecoderDatabase::Remove(uint8_t rtp_payload_type) {
  if (decoders_.erase(rtp_payload_type) == 0) {
    // No decoder with that |rtp_payload_type|.
    return kDecoderNotFound;
  }
  if (active_decoder_ == rtp_payload_type) {
    active_decoder_ = -1;  // No active decoder.
  }
  if (active_cng_decoder_ == rtp_payload_type) {
    active_cng_decoder_ = -1;  // No active CNG decoder.
  }
  return kOK;
}

const DecoderDatabase::DecoderInfo* DecoderDatabase::GetDecoderInfo(
    uint8_t rtp_payload_type) const {
  DecoderMap::const_iterator it = decoders_.find(rtp_payload_type);
  if (it == decoders_.end()) {
    // Decoder not found.
    return NULL;
  }
  return &(*it).second;
}

uint8_t DecoderDatabase::GetRtpPayloadType(
    NetEqDecoder codec_type) const {
  DecoderMap::const_iterator it;
  for (it = decoders_.begin(); it != decoders_.end(); ++it) {
    if ((*it).second.codec_type == codec_type) {
      // Match found.
      return (*it).first;
    }
  }
  // No match.
  return kRtpPayloadTypeError;
}

AudioDecoder* DecoderDatabase::GetDecoder(uint8_t rtp_payload_type) {
  if (IsDtmf(rtp_payload_type) || IsRed(rtp_payload_type)) {
    // These are not real decoders.
    return NULL;
  }
  DecoderMap::iterator it = decoders_.find(rtp_payload_type);
  if (it == decoders_.end()) {
    // Decoder not found.
    return NULL;
  }
  DecoderInfo* info = &(*it).second;
  if (!info->decoder) {
    // Create the decoder object.
    AudioDecoder* decoder = AudioDecoder::CreateAudioDecoder(info->codec_type);
    assert(decoder);  // Should not be able to have an unsupported codec here.
    info->decoder = decoder;
    info->decoder->Init();
  }
  return info->decoder;
}

bool DecoderDatabase::IsType(uint8_t rtp_payload_type,
                             NetEqDecoder codec_type) const {
  DecoderMap::const_iterator it = decoders_.find(rtp_payload_type);
  if (it == decoders_.end()) {
    // Decoder not found.
    return false;
  }
  return ((*it).second.codec_type == codec_type);
}

bool DecoderDatabase::IsComfortNoise(uint8_t rtp_payload_type) const {
  if (IsType(rtp_payload_type, kDecoderCNGnb) ||
      IsType(rtp_payload_type, kDecoderCNGwb) ||
      IsType(rtp_payload_type, kDecoderCNGswb32kHz) ||
      IsType(rtp_payload_type, kDecoderCNGswb48kHz)) {
    return true;
  } else {
    return false;
  }
}

bool DecoderDatabase::IsDtmf(uint8_t rtp_payload_type) const {
  return IsType(rtp_payload_type, kDecoderAVT);
}

bool DecoderDatabase::IsRed(uint8_t rtp_payload_type) const {
  return IsType(rtp_payload_type, kDecoderRED);
}

int DecoderDatabase::SetActiveDecoder(uint8_t rtp_payload_type,
                                      bool* new_decoder) {
  // Check that |rtp_payload_type| exists in the database.
  DecoderMap::const_iterator it = decoders_.find(rtp_payload_type);
  if (it == decoders_.end()) {
    // Decoder not found.
    return kDecoderNotFound;
  }
  assert(new_decoder);
  *new_decoder = false;
  if (active_decoder_ < 0) {
    // This is the first active decoder.
    *new_decoder = true;
  } else if (active_decoder_ != rtp_payload_type) {
    // Moving from one active decoder to another. Delete the first one.
    DecoderMap::iterator it = decoders_.find(active_decoder_);
    if (it == decoders_.end()) {
      // Decoder not found. This should not be possible.
      assert(false);
      return kDecoderNotFound;
    }
    if (!(*it).second.external) {
      // Delete the AudioDecoder object, unless it is an externally created
      // decoder.
      delete (*it).second.decoder;
      (*it).second.decoder = NULL;
    }
    *new_decoder = true;
  }
  active_decoder_ = rtp_payload_type;
  return kOK;
}

AudioDecoder* DecoderDatabase::GetActiveDecoder() {
  if (active_decoder_ < 0) {
    // No active decoder.
    return NULL;
  }
  return GetDecoder(active_decoder_);
}

int DecoderDatabase::SetActiveCngDecoder(uint8_t rtp_payload_type) {
  // Check that |rtp_payload_type| exists in the database.
  DecoderMap::const_iterator it = decoders_.find(rtp_payload_type);
  if (it == decoders_.end()) {
    // Decoder not found.
    return kDecoderNotFound;
  }
  if (active_cng_decoder_ >= 0 && active_cng_decoder_ != rtp_payload_type) {
    // Moving from one active CNG decoder to another. Delete the first one.
    DecoderMap::iterator it = decoders_.find(active_cng_decoder_);
    if (it == decoders_.end()) {
      // Decoder not found. This should not be possible.
      assert(false);
      return kDecoderNotFound;
    }
    if (!(*it).second.external) {
      // Delete the AudioDecoder object, unless it is an externally created
      // decoder.
      delete (*it).second.decoder;
      (*it).second.decoder = NULL;
    }
  }
  active_cng_decoder_ = rtp_payload_type;
  return kOK;
}

AudioDecoder* DecoderDatabase::GetActiveCngDecoder() {
  if (active_cng_decoder_ < 0) {
    // No active CNG decoder.
    return NULL;
  }
  return GetDecoder(active_cng_decoder_);
}

int DecoderDatabase::CheckPayloadTypes(const PacketList& packet_list) const {
  PacketList::const_iterator it;
  for (it = packet_list.begin(); it != packet_list.end(); ++it) {
    if (decoders_.find((*it)->header.payloadType) == decoders_.end()) {
      // Payload type is not found.
      return kDecoderNotFound;
    }
  }
  return kOK;
}


}  // namespace webrtc
