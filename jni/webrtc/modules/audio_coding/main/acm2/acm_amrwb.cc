/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/modules/audio_coding/main/acm2/acm_amrwb.h"

#ifdef WEBRTC_CODEC_AMRWB
// NOTE! GSM AMR-wb is not included in the open-source package. The
// following interface file is needed:
#include "webrtc/modules/audio_coding/main/codecs/amrwb/interface/amrwb_interface.h"
#include "webrtc/modules/audio_coding/main/interface/audio_coding_module_typedefs.h"
#include "webrtc/modules/audio_coding/main/acm2/acm_common_defs.h"
#include "webrtc/system_wrappers/interface/rw_lock_wrapper.h"
#include "webrtc/system_wrappers/interface/trace.h"

// The API in the header file should match the one below.
//
// int16_t WebRtcAmrWb_CreateEnc(AMRWB_encinst_t_** enc_inst);
// int16_t WebRtcAmrWb_CreateDec(AMRWB_decinst_t_** dec_inst);
// int16_t WebRtcAmrWb_FreeEnc(AMRWB_encinst_t_* enc_inst);
// int16_t WebRtcAmrWb_FreeDec(AMRWB_decinst_t_* dec_inst);
// int16_t WebRtcAmrWb_Encode(AMRWB_encinst_t_* enc_inst, int16_t* input,
//                            int16_t len, int16_t* output, int16_t mode);
// int16_t WebRtcAmrWb_EncoderInit(AMRWB_encinst_t_* enc_inst,
//                                 int16_t dtx_mode);
// int16_t WebRtcAmrWb_EncodeBitmode(AMRWB_encinst_t_* enc_inst,
//                                    int format);
// int16_t WebRtcAmrWb_Decode(AMRWB_decinst_t_* dec_inst);
// int16_t WebRtcAmrWb_DecodePlc(AMRWB_decinst_t_* dec_inst);
// int16_t WebRtcAmrWb_DecoderInit(AMRWB_decinst_t_* dec_inst);
// int16_t WebRtcAmrWb_DecodeBitmode(AMRWB_decinst_t_* dec_inst,
//                                   int format);
#endif

namespace webrtc {

namespace acm2 {

#ifndef WEBRTC_CODEC_AMRWB
ACMAMRwb::ACMAMRwb(int16_t /* codec_id */)
    : encoder_inst_ptr_(NULL),
      encoding_mode_(-1),  // invalid value
      encoding_rate_(0),   // invalid value
      encoder_packing_format_(AMRBandwidthEfficient) {}

ACMAMRwb::~ACMAMRwb() {}

int16_t ACMAMRwb::InternalEncode(uint8_t* /* bitstream */,
                                 int16_t* /* bitstream_len_byte */) {
  return -1;
}

int16_t ACMAMRwb::EnableDTX() { return -1; }

int16_t ACMAMRwb::DisableDTX() { return -1; }

int16_t ACMAMRwb::InternalInitEncoder(
    WebRtcACMCodecParams* /* codec_params */) {
  return -1;
}

ACMGenericCodec* ACMAMRwb::CreateInstance(void) { return NULL; }

int16_t ACMAMRwb::InternalCreateEncoder() { return -1; }

void ACMAMRwb::DestructEncoderSafe() { return; }

int16_t ACMAMRwb::SetBitRateSafe(const int32_t /* rate */) { return -1; }

void ACMAMRwb::InternalDestructEncoderInst(void* /* ptr_inst */) { return; }

int16_t ACMAMRwb::SetAMRwbEncoderPackingFormat(
    ACMAMRPackingFormat /* packing_format */) {
  return -1;
}

ACMAMRPackingFormat ACMAMRwb::AMRwbEncoderPackingFormat() const {
  return AMRUndefined;
}

int16_t ACMAMRwb::SetAMRwbDecoderPackingFormat(
    ACMAMRPackingFormat /* packing_format */) {
  return -1;
}

ACMAMRPackingFormat ACMAMRwb::AMRwbDecoderPackingFormat() const {
  return AMRUndefined;
}

#else     //===================== Actual Implementation =======================

#define AMRWB_MODE_7k 0
#define AMRWB_MODE_9k 1
#define AMRWB_MODE_12k 2
#define AMRWB_MODE_14k 3
#define AMRWB_MODE_16k 4
#define AMRWB_MODE_18k 5
#define AMRWB_MODE_20k 6
#define AMRWB_MODE_23k 7
#define AMRWB_MODE_24k 8

ACMAMRwb::ACMAMRwb(int16_t codec_id)
    : encoder_inst_ptr_(NULL),
      encoding_mode_(-1),  // invalid value
      encoding_rate_(0) {  // invalid value
  codec_id_ = codec_id;
  has_internal_dtx_ = true;
  encoder_packing_format_ = AMRBandwidthEfficient;
  return;
}

ACMAMRwb::~ACMAMRwb() {
  if (encoder_inst_ptr_ != NULL) {
    WebRtcAmrWb_FreeEnc(encoder_inst_ptr_);
    encoder_inst_ptr_ = NULL;
  }
  return;
}

int16_t ACMAMRwb::InternalEncode(uint8_t* bitstream,
                                 int16_t* bitstream_len_byte) {
  int16_t vad_decision = 1;
  // sanity check, if the rate is set correctly. we might skip this
  // sanity check. if rate is not set correctly, initialization flag
  // should be false and should not be here.
  if ((encoding_mode_ < AMRWB_MODE_7k) || (encoding_mode_ > AMRWB_MODE_24k)) {
    *bitstream_len_byte = 0;
    return -1;
  }
  *bitstream_len_byte = WebRtcAmrWb_Encode(
      encoder_inst_ptr_, &in_audio_[in_audio_ix_read_], frame_len_smpl_,
      reinterpret_cast<int16_t*>(bitstream), encoding_mode_);

  // Update VAD, if internal DTX is used
  if (has_internal_dtx_ && dtx_enabled_) {
    if (*bitstream_len_byte <= (7 * frame_len_smpl_ / 160)) {
      vad_decision = 0;
    }
    for (int16_t n = 0; n < MAX_FRAME_SIZE_10MSEC; n++) {
      vad_label_[n] = vad_decision;
    }
  }
  // increment the read index this tell the caller that how far
  // we have gone forward in reading the audio buffer
  in_audio_ix_read_ += frame_len_smpl_;
  return *bitstream_len_byte;
}

int16_t ACMAMRwb::EnableDTX() {
  if (dtx_enabled_) {
    return 0;
  } else if (encoder_exist_) {  // check if encoder exist
    // enable DTX
    if (WebRtcAmrWb_EncoderInit(encoder_inst_ptr_, 1) < 0) {
      return -1;
    }
    dtx_enabled_ = true;
    return 0;
  } else {
    return -1;
  }
}

int16_t ACMAMRwb::DisableDTX() {
  if (!dtx_enabled_) {
    return 0;
  } else if (encoder_exist_) {  // check if encoder exist
    // disable DTX
    if (WebRtcAmrWb_EncoderInit(encoder_inst_ptr_, 0) < 0) {
      return -1;
    }
    dtx_enabled_ = false;
    return 0;
  } else {
    // encoder doesn't exists, therefore disabling is harmless
    return 0;
  }
}

int16_t ACMAMRwb::InternalInitEncoder(WebRtcACMCodecParams* codec_params) {
  // sanity check
  if (encoder_inst_ptr_ == NULL) {
    return -1;
  }

  int16_t status = SetBitRateSafe((codec_params->codec_inst).rate);
  status += (WebRtcAmrWb_EncoderInit(encoder_inst_ptr_,
                                     ((codec_params->enable_dtx) ? 1 : 0)) < 0)
                ? -1
                : 0;
  status += (WebRtcAmrWb_EncodeBitmode(encoder_inst_ptr_,
                                       encoder_packing_format_) < 0)
                ? -1
                : 0;
  return (status < 0) ? -1 : 0;
}

ACMGenericCodec* ACMAMRwb::CreateInstance(void) { return NULL; }

int16_t ACMAMRwb::InternalCreateEncoder() {
  return WebRtcAmrWb_CreateEnc(&encoder_inst_ptr_);
}

void ACMAMRwb::DestructEncoderSafe() {
  if (encoder_inst_ptr_ != NULL) {
    WebRtcAmrWb_FreeEnc(encoder_inst_ptr_);
    encoder_inst_ptr_ = NULL;
  }
  // there is no encoder set the following
  encoder_exist_ = false;
  encoder_initialized_ = false;
  encoding_mode_ = -1;  // invalid value
  encoding_rate_ = 0;
}

int16_t ACMAMRwb::SetBitRateSafe(const int32_t rate) {
  switch (rate) {
    case 7000: {
      encoding_mode_ = AMRWB_MODE_7k;
      encoding_rate_ = 7000;
      break;
    }
    case 9000: {
      encoding_mode_ = AMRWB_MODE_9k;
      encoding_rate_ = 9000;
      break;
    }
    case 12000: {
      encoding_mode_ = AMRWB_MODE_12k;
      encoding_rate_ = 12000;
      break;
    }
    case 14000: {
      encoding_mode_ = AMRWB_MODE_14k;
      encoding_rate_ = 14000;
      break;
    }
    case 16000: {
      encoding_mode_ = AMRWB_MODE_16k;
      encoding_rate_ = 16000;
      break;
    }
    case 18000: {
      encoding_mode_ = AMRWB_MODE_18k;
      encoding_rate_ = 18000;
      break;
    }
    case 20000: {
      encoding_mode_ = AMRWB_MODE_20k;
      encoding_rate_ = 20000;
      break;
    }
    case 23000: {
      encoding_mode_ = AMRWB_MODE_23k;
      encoding_rate_ = 23000;
      break;
    }
    case 24000: {
      encoding_mode_ = AMRWB_MODE_24k;
      encoding_rate_ = 24000;
      break;
    }
    default: {
      return -1;
    }
  }
  return 0;
}

void ACMAMRwb::InternalDestructEncoderInst(void* ptr_inst) {
  if (ptr_inst != NULL) {
    WebRtcAmrWb_FreeEnc(static_cast<AMRWB_encinst_t_*>(ptr_inst));
  }
  return;
}

int16_t ACMAMRwb::SetAMRwbEncoderPackingFormat(
    ACMAMRPackingFormat packing_format) {
  if ((packing_format != AMRBandwidthEfficient) &&
      (packing_format != AMROctetAlligned) &&
      (packing_format != AMRFileStorage)) {
    WEBRTC_TRACE(webrtc::kTraceError, webrtc::kTraceAudioCoding, unique_id_,
                 "Invalid AMRwb encoder packing-format.");
    return -1;
  } else {
    if (WebRtcAmrWb_EncodeBitmode(encoder_inst_ptr_, packing_format) < 0) {
      return -1;
    } else {
      encoder_packing_format_ = packing_format;
      return 0;
    }
  }
}

ACMAMRPackingFormat ACMAMRwb::AMRwbEncoderPackingFormat() const {
  return encoder_packing_format_;
}

int16_t ACMAMRwb::SetAMRwbDecoderPackingFormat(
    ACMAMRPackingFormat packing_format) {
  // Not implemented.
  return -1;
}

ACMAMRPackingFormat ACMAMRwb::AMRwbDecoderPackingFormat() const {
  // Not implemented.
  return AMRUndefined;
}

#endif

}  // namespace acm2

}  // namespace webrtc
