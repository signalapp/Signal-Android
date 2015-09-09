/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/modules/audio_coding/main/acm2/acm_g7221c.h"

#ifdef WEBRTC_CODEC_G722_1C
// NOTE! G.722.1C is not included in the open-source package. The following
// interface file is needed:
#include "webrtc/modules/audio_coding/main/codecs/g7221c/interface/g7221c_interface.h"
#include "webrtc/modules/audio_coding/main/acm2/acm_codec_database.h"
#include "webrtc/modules/audio_coding/main/acm2/acm_common_defs.h"
#include "webrtc/system_wrappers/interface/trace.h"

// The API in the header file should match the one below.
//
// int16_t WebRtcG7221C_CreateEnc24(G722_1C_24_encinst_t_** enc_inst);
// int16_t WebRtcG7221C_CreateEnc32(G722_1C_32_encinst_t_** enc_inst);
// int16_t WebRtcG7221C_CreateEnc48(G722_1C_48_encinst_t_** enc_inst);
// int16_t WebRtcG7221C_CreateDec24(G722_1C_24_decinst_t_** dec_inst);
// int16_t WebRtcG7221C_CreateDec32(G722_1C_32_decinst_t_** dec_inst);
// int16_t WebRtcG7221C_CreateDec48(G722_1C_48_decinst_t_** dec_inst);
//
// int16_t WebRtcG7221C_FreeEnc24(G722_1C_24_encinst_t_** enc_inst);
// int16_t WebRtcG7221C_FreeEnc32(G722_1C_32_encinst_t_** enc_inst);
// int16_t WebRtcG7221C_FreeEnc48(G722_1C_48_encinst_t_** enc_inst);
// int16_t WebRtcG7221C_FreeDec24(G722_1C_24_decinst_t_** dec_inst);
// int16_t WebRtcG7221C_FreeDec32(G722_1C_32_decinst_t_** dec_inst);
// int16_t WebRtcG7221C_FreeDec48(G722_1C_48_decinst_t_** dec_inst);
//
// int16_t WebRtcG7221C_EncoderInit24(G722_1C_24_encinst_t_* enc_inst);
// int16_t WebRtcG7221C_EncoderInit32(G722_1C_32_encinst_t_* enc_inst);
// int16_t WebRtcG7221C_EncoderInit48(G722_1C_48_encinst_t_* enc_inst);
// int16_t WebRtcG7221C_DecoderInit24(G722_1C_24_decinst_t_* dec_inst);
// int16_t WebRtcG7221C_DecoderInit32(G722_1C_32_decinst_t_* dec_inst);
// int16_t WebRtcG7221C_DecoderInit48(G722_1C_48_decinst_t_* dec_inst);
//
// int16_t WebRtcG7221C_Encode24(G722_1C_24_encinst_t_* enc_inst,
//                               int16_t* input,
//                               int16_t len,
//                               int16_t* output);
// int16_t WebRtcG7221C_Encode32(G722_1C_32_encinst_t_* enc_inst,
//                               int16_t* input,
//                               int16_t len,
//                               int16_t* output);
// int16_t WebRtcG7221C_Encode48(G722_1C_48_encinst_t_* enc_inst,
//                               int16_t* input,
//                               int16_t len,
//                               int16_t* output);
//
// int16_t WebRtcG7221C_Decode24(G722_1C_24_decinst_t_* dec_inst,
//                               int16_t* bitstream,
//                               int16_t len,
//                               int16_t* output);
// int16_t WebRtcG7221C_Decode32(G722_1C_32_decinst_t_* dec_inst,
//                               int16_t* bitstream,
//                               int16_t len,
//                               int16_t* output);
// int16_t WebRtcG7221C_Decode48(G722_1C_48_decinst_t_* dec_inst,
//                               int16_t* bitstream,
//                               int16_t len,
//                               int16_t* output);
//
// int16_t WebRtcG7221C_DecodePlc24(G722_1C_24_decinst_t_* dec_inst,
//                                  int16_t* output,
//                                  int16_t nr_lost_frames);
// int16_t WebRtcG7221C_DecodePlc32(G722_1C_32_decinst_t_* dec_inst,
//                                  int16_t* output,
//                                  int16_t nr_lost_frames);
// int16_t WebRtcG7221C_DecodePlc48(G722_1C_48_decinst_t_* dec_inst,
//                                  int16_t* output,
//                                  int16_t nr_lost_frames);
#endif

namespace webrtc {

namespace acm2 {

#ifndef WEBRTC_CODEC_G722_1C

ACMG722_1C::ACMG722_1C(int16_t /* codec_id */)
    : operational_rate_(-1),
      encoder_inst_ptr_(NULL),
      encoder_inst_ptr_right_(NULL),
      encoder_inst24_ptr_(NULL),
      encoder_inst24_ptr_right_(NULL),
      encoder_inst32_ptr_(NULL),
      encoder_inst32_ptr_right_(NULL),
      encoder_inst48_ptr_(NULL),
      encoder_inst48_ptr_right_(NULL) {
  return;
}

ACMG722_1C::~ACMG722_1C() { return; }

int16_t ACMG722_1C::InternalEncode(uint8_t* /* bitstream */,
                                   int16_t* /* bitstream_len_byte */) {
  return -1;
}

int16_t ACMG722_1C::InternalInitEncoder(
    WebRtcACMCodecParams* /* codec_params */) {
  return -1;
}

ACMGenericCodec* ACMG722_1C::CreateInstance(void) { return NULL; }

int16_t ACMG722_1C::InternalCreateEncoder() { return -1; }

void ACMG722_1C::DestructEncoderSafe() { return; }

void ACMG722_1C::InternalDestructEncoderInst(void* /* ptr_inst */) { return; }

#else  //===================== Actual Implementation =======================
ACMG722_1C::ACMG722_1C(int16_t codec_id)
    : encoder_inst_ptr_(NULL),
      encoder_inst_ptr_right_(NULL),
      encoder_inst24_ptr_(NULL),
      encoder_inst24_ptr_right_(NULL),
      encoder_inst32_ptr_(NULL),
      encoder_inst32_ptr_right_(NULL),
      encoder_inst48_ptr_(NULL),
      encoder_inst48_ptr_right_(NULL) {
  codec_id_ = codec_id;
  if (codec_id_ == ACMCodecDB::kG722_1C_24) {
    operational_rate_ = 24000;
  } else if (codec_id_ == ACMCodecDB::kG722_1C_32) {
    operational_rate_ = 32000;
  } else if (codec_id_ == ACMCodecDB::kG722_1C_48) {
    operational_rate_ = 48000;
  } else {
    WEBRTC_TRACE(webrtc::kTraceError, webrtc::kTraceAudioCoding, unique_id_,
                 "Wrong codec id for G722_1c.");
    operational_rate_ = -1;
  }
  return;
}

ACMG722_1C::~ACMG722_1C() {
  if (encoder_inst_ptr_ != NULL) {
    delete encoder_inst_ptr_;
    encoder_inst_ptr_ = NULL;
  }
  if (encoder_inst_ptr_right_ != NULL) {
    delete encoder_inst_ptr_right_;
    encoder_inst_ptr_right_ = NULL;
  }

  switch (operational_rate_) {
    case 24000: {
      encoder_inst24_ptr_ = NULL;
      encoder_inst24_ptr_right_ = NULL;
      break;
    }
    case 32000: {
      encoder_inst32_ptr_ = NULL;
      encoder_inst32_ptr_right_ = NULL;
      break;
    }
    case 48000: {
      encoder_inst48_ptr_ = NULL;
      encoder_inst48_ptr_right_ = NULL;
      break;
    }
    default: {
      WEBRTC_TRACE(webrtc::kTraceError, webrtc::kTraceAudioCoding, unique_id_,
                   "Wrong rate for G722_1c.");
      break;
    }
  }
  return;
}

int16_t ACMG722_1C::InternalEncode(uint8_t* bitstream,
                                   int16_t* bitstream_len_byte) {
  int16_t left_channel[640];
  int16_t right_channel[640];
  int16_t len_in_bytes;
  int16_t out_bits[240];

  // If stereo, split input signal in left and right channel before encoding
  if (num_channels_ == 2) {
    for (int i = 0, j = 0; i < frame_len_smpl_ * 2; i += 2, j++) {
      left_channel[j] = in_audio_[in_audio_ix_read_ + i];
      right_channel[j] = in_audio_[in_audio_ix_read_ + i + 1];
    }
  } else {
    memcpy(left_channel, &in_audio_[in_audio_ix_read_], 640);
  }

  switch (operational_rate_) {
    case 24000: {
      len_in_bytes = WebRtcG7221C_Encode24(encoder_inst24_ptr_, left_channel,
                                           640, &out_bits[0]);
      if (num_channels_ == 2) {
        len_in_bytes += WebRtcG7221C_Encode24(encoder_inst24_ptr_right_,
                                              right_channel, 640,
                                              &out_bits[len_in_bytes / 2]);
      }
      break;
    }
    case 32000: {
      len_in_bytes = WebRtcG7221C_Encode32(encoder_inst32_ptr_, left_channel,
                                           640, &out_bits[0]);
      if (num_channels_ == 2) {
        len_in_bytes += WebRtcG7221C_Encode32(encoder_inst32_ptr_right_,
                                              right_channel, 640,
                                              &out_bits[len_in_bytes / 2]);
      }
      break;
    }
    case 48000: {
      len_in_bytes = WebRtcG7221C_Encode48(encoder_inst48_ptr_, left_channel,
                                           640, &out_bits[0]);
      if (num_channels_ == 2) {
        len_in_bytes += WebRtcG7221C_Encode48(encoder_inst48_ptr_right_,
                                              right_channel, 640,
                                              &out_bits[len_in_bytes / 2]);
      }
      break;
    }
    default: {
      WEBRTC_TRACE(webrtc::kTraceError, webrtc::kTraceAudioCoding, unique_id_,
                   "InternalEncode: Wrong rate for G722_1c.");
      return -1;
    }
  }

  memcpy(bitstream, out_bits, len_in_bytes);
  *bitstream_len_byte = len_in_bytes;

  // increment the read index this tell the caller that how far
  // we have gone forward in reading the audio buffer
  in_audio_ix_read_ += 640 * num_channels_;

  return *bitstream_len_byte;
}

int16_t ACMG722_1C::InternalInitEncoder(WebRtcACMCodecParams* codec_params) {
  int16_t ret;

  switch (operational_rate_) {
    case 24000: {
      ret = WebRtcG7221C_EncoderInit24(encoder_inst24_ptr_right_);
      if (ret < 0) {
        return ret;
      }
      return WebRtcG7221C_EncoderInit24(encoder_inst24_ptr_);
    }
    case 32000: {
      ret = WebRtcG7221C_EncoderInit32(encoder_inst32_ptr_right_);
      if (ret < 0) {
        return ret;
      }
      return WebRtcG7221C_EncoderInit32(encoder_inst32_ptr_);
    }
    case 48000: {
      ret = WebRtcG7221C_EncoderInit48(encoder_inst48_ptr_right_);
      if (ret < 0) {
        return ret;
      }
      return WebRtcG7221C_EncoderInit48(encoder_inst48_ptr_);
    }
    default: {
      WEBRTC_TRACE(webrtc::kTraceError, webrtc::kTraceAudioCoding, unique_id_,
                   "InternalInitEncode: Wrong rate for G722_1c.");
      return -1;
    }
  }
}

ACMGenericCodec* ACMG722_1C::CreateInstance(void) { return NULL; }

int16_t ACMG722_1C::InternalCreateEncoder() {
  if ((encoder_inst_ptr_ == NULL) || (encoder_inst_ptr_right_ == NULL)) {
    return -1;
  }
  switch (operational_rate_) {
    case 24000: {
      WebRtcG7221C_CreateEnc24(&encoder_inst24_ptr_);
      WebRtcG7221C_CreateEnc24(&encoder_inst24_ptr_right_);
      break;
    }
    case 32000: {
      WebRtcG7221C_CreateEnc32(&encoder_inst32_ptr_);
      WebRtcG7221C_CreateEnc32(&encoder_inst32_ptr_right_);
      break;
    }
    case 48000: {
      WebRtcG7221C_CreateEnc48(&encoder_inst48_ptr_);
      WebRtcG7221C_CreateEnc48(&encoder_inst48_ptr_right_);
      break;
    }
    default: {
      WEBRTC_TRACE(webrtc::kTraceError, webrtc::kTraceAudioCoding, unique_id_,
                   "InternalCreateEncoder: Wrong rate for G722_1c.");
      return -1;
    }
  }
  return 0;
}

void ACMG722_1C::DestructEncoderSafe() {
  encoder_exist_ = false;
  encoder_initialized_ = false;
  if (encoder_inst_ptr_ != NULL) {
    delete encoder_inst_ptr_;
    encoder_inst_ptr_ = NULL;
  }
  if (encoder_inst_ptr_right_ != NULL) {
    delete encoder_inst_ptr_right_;
    encoder_inst_ptr_right_ = NULL;
  }
  encoder_inst24_ptr_ = NULL;
  encoder_inst32_ptr_ = NULL;
  encoder_inst48_ptr_ = NULL;
}

void ACMG722_1C::InternalDestructEncoderInst(void* ptr_inst) {
  if (ptr_inst != NULL) {
    delete ptr_inst;
  }
  return;
}

#endif

}  // namespace acm2

}  // namespace webrtc
