/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/modules/audio_coding/main/acm2/acm_g722.h"

#ifdef WEBRTC_CODEC_G722
#include "webrtc/modules/audio_coding/codecs/g722/include/g722_interface.h"
#include "webrtc/modules/audio_coding/main/acm2/acm_codec_database.h"
#include "webrtc/modules/audio_coding/main/acm2/acm_common_defs.h"
#include "webrtc/system_wrappers/interface/trace.h"
#endif

namespace webrtc {

namespace acm2 {

#ifndef WEBRTC_CODEC_G722

ACMG722::ACMG722(int16_t /* codec_id */)
    : ptr_enc_str_(NULL),
      encoder_inst_ptr_(NULL),
      encoder_inst_ptr_right_(NULL) {}

ACMG722::~ACMG722() {}

int32_t ACMG722::Add10MsDataSafe(const uint32_t /* timestamp */,
                                 const int16_t* /* data */,
                                 const uint16_t /* length_smpl */,
                                 const uint8_t /* audio_channel */) {
  return -1;
}

int16_t ACMG722::InternalEncode(uint8_t* /* bitstream */,
                                int16_t* /* bitstream_len_byte */) {
  return -1;
}

int16_t ACMG722::InternalInitEncoder(WebRtcACMCodecParams* /* codec_params */) {
  return -1;
}

ACMGenericCodec* ACMG722::CreateInstance(void) { return NULL; }

int16_t ACMG722::InternalCreateEncoder() { return -1; }

void ACMG722::DestructEncoderSafe() { return; }

void ACMG722::InternalDestructEncoderInst(void* /* ptr_inst */) { return; }

#else     //===================== Actual Implementation =======================

// Encoder and decoder memory
struct ACMG722EncStr {
  G722EncInst* inst;  // instance for left channel in case of stereo
  G722EncInst* inst_right;  // instance for right channel in case of stereo
};
struct ACMG722DecStr {
  G722DecInst* inst;  // instance for left channel in case of stereo
  G722DecInst* inst_right;  // instance for right channel in case of stereo
};

ACMG722::ACMG722(int16_t codec_id)
    : encoder_inst_ptr_(NULL), encoder_inst_ptr_right_(NULL) {
  ptr_enc_str_ = new ACMG722EncStr;
  if (ptr_enc_str_ != NULL) {
    ptr_enc_str_->inst = NULL;
    ptr_enc_str_->inst_right = NULL;
  }
  codec_id_ = codec_id;
  return;
}

ACMG722::~ACMG722() {
  // Encoder
  if (ptr_enc_str_ != NULL) {
    if (ptr_enc_str_->inst != NULL) {
      WebRtcG722_FreeEncoder(ptr_enc_str_->inst);
      ptr_enc_str_->inst = NULL;
    }
    if (ptr_enc_str_->inst_right != NULL) {
      WebRtcG722_FreeEncoder(ptr_enc_str_->inst_right);
      ptr_enc_str_->inst_right = NULL;
    }
    delete ptr_enc_str_;
    ptr_enc_str_ = NULL;
  }
  return;
}

int32_t ACMG722::Add10MsDataSafe(const uint32_t timestamp,
                                 const int16_t* data,
                                 const uint16_t length_smpl,
                                 const uint8_t audio_channel) {
  return ACMGenericCodec::Add10MsDataSafe(
      (timestamp >> 1), data, length_smpl, audio_channel);
}

int16_t ACMG722::InternalEncode(uint8_t* bitstream,
                                int16_t* bitstream_len_byte) {
  // If stereo, split input signal in left and right channel before encoding
  if (num_channels_ == 2) {
    int16_t left_channel[960];
    int16_t right_channel[960];
    uint8_t out_left[480];
    uint8_t out_right[480];
    int16_t len_in_bytes;
    for (int i = 0, j = 0; i < frame_len_smpl_ * 2; i += 2, j++) {
      left_channel[j] = in_audio_[in_audio_ix_read_ + i];
      right_channel[j] = in_audio_[in_audio_ix_read_ + i + 1];
    }
    len_in_bytes = WebRtcG722_Encode(
        encoder_inst_ptr_, left_channel, frame_len_smpl_,
        reinterpret_cast<int16_t*>(out_left));
    len_in_bytes += WebRtcG722_Encode(encoder_inst_ptr_right_,
                                      right_channel,
                                      frame_len_smpl_,
                                      reinterpret_cast<int16_t*>(out_right));
    *bitstream_len_byte = len_in_bytes;

    // Interleave the 4 bits per sample from left and right channel
    for (int i = 0, j = 0; i < len_in_bytes; i += 2, j++) {
      bitstream[i] = (out_left[j] & 0xF0) + (out_right[j] >> 4);
      bitstream[i + 1] = ((out_left[j] & 0x0F) << 4) + (out_right[j] & 0x0F);
    }
  } else {
    *bitstream_len_byte = WebRtcG722_Encode(
        encoder_inst_ptr_, &in_audio_[in_audio_ix_read_], frame_len_smpl_,
        reinterpret_cast<int16_t*>(bitstream));
  }

  // increment the read index this tell the caller how far
  // we have gone forward in reading the audio buffer
  in_audio_ix_read_ += frame_len_smpl_ * num_channels_;
  return *bitstream_len_byte;
}

int16_t ACMG722::InternalInitEncoder(WebRtcACMCodecParams* codec_params) {
  if (codec_params->codec_inst.channels == 2) {
    // Create codec struct for right channel
    if (ptr_enc_str_->inst_right == NULL) {
      WebRtcG722_CreateEncoder(&ptr_enc_str_->inst_right);
      if (ptr_enc_str_->inst_right == NULL) {
        return -1;
      }
    }
    encoder_inst_ptr_right_ = ptr_enc_str_->inst_right;
    if (WebRtcG722_EncoderInit(encoder_inst_ptr_right_) < 0) {
      return -1;
    }
  }

  return WebRtcG722_EncoderInit(encoder_inst_ptr_);
}

ACMGenericCodec* ACMG722::CreateInstance(void) { return NULL; }

int16_t ACMG722::InternalCreateEncoder() {
  if (ptr_enc_str_ == NULL) {
    // this structure must be created at the costructor
    // if it is still NULL then there is a probelm and
    // we dont continue
    return -1;
  }
  WebRtcG722_CreateEncoder(&ptr_enc_str_->inst);
  if (ptr_enc_str_->inst == NULL) {
    return -1;
  }
  encoder_inst_ptr_ = ptr_enc_str_->inst;
  return 0;
}

void ACMG722::DestructEncoderSafe() {
  if (ptr_enc_str_ != NULL) {
    if (ptr_enc_str_->inst != NULL) {
      WebRtcG722_FreeEncoder(ptr_enc_str_->inst);
      ptr_enc_str_->inst = NULL;
    }
  }
  encoder_exist_ = false;
  encoder_initialized_ = false;
}

void ACMG722::InternalDestructEncoderInst(void* ptr_inst) {
  if (ptr_inst != NULL) {
    WebRtcG722_FreeEncoder(static_cast<G722EncInst*>(ptr_inst));
  }
  return;
}

#endif

}  // namespace acm2

}  // namespace webrtc
