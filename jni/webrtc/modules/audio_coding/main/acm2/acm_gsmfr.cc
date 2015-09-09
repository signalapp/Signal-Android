/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/modules/audio_coding/main/acm2/acm_gsmfr.h"

#ifdef WEBRTC_CODEC_GSMFR
// NOTE! GSM-FR is not included in the open-source package. Modify this file
// or your codec API to match the function calls and names of used GSM-FR API
// file.
#include "webrtc/modules/audio_coding/main/codecs/gsmfr/interface/gsmfr_interface.h"
#include "webrtc/modules/audio_coding/main/acm2/acm_common_defs.h"
#include "webrtc/system_wrappers/interface/trace.h"
#endif

namespace webrtc {

namespace acm2 {

#ifndef WEBRTC_CODEC_GSMFR

ACMGSMFR::ACMGSMFR(int16_t /* codec_id */) : encoder_inst_ptr_(NULL) {}

ACMGSMFR::~ACMGSMFR() { return; }

int16_t ACMGSMFR::InternalEncode(uint8_t* /* bitstream */,
                                 int16_t* /* bitstream_len_byte */) {
  return -1;
}

int16_t ACMGSMFR::EnableDTX() { return -1; }

int16_t ACMGSMFR::DisableDTX() { return -1; }

int16_t ACMGSMFR::InternalInitEncoder(
    WebRtcACMCodecParams* /* codec_params */) {
  return -1;
}

ACMGenericCodec* ACMGSMFR::CreateInstance(void) { return NULL; }

int16_t ACMGSMFR::InternalCreateEncoder() { return -1; }

void ACMGSMFR::DestructEncoderSafe() { return; }

void ACMGSMFR::InternalDestructEncoderInst(void* /* ptr_inst */) {
  return;
}

#else  //===================== Actual Implementation =======================

ACMGSMFR::ACMGSMFR(int16_t codec_id)
    : codec_id_(codec_id),
      has_internal_dtx_(true),
      encoder_inst_ptr_(NULL) {}

ACMGSMFR::~ACMGSMFR() {
  if (encoder_inst_ptr_ != NULL) {
    WebRtcGSMFR_FreeEnc(encoder_inst_ptr_);
    encoder_inst_ptr_ = NULL;
  }
  return;
}

int16_t ACMGSMFR::InternalEncode(uint8_t* bitstream,
                                 int16_t* bitstream_len_byte) {
  *bitstream_len_byte = WebRtcGSMFR_Encode(
      encoder_inst_ptr_, &in_audio_[in_audio_ix_read_], frame_len_smpl_,
      reinterpret_cast<int16_t*>(bitstream));

  // increment the read index this tell the caller that how far
  // we have gone forward in reading the audio buffer
  in_audio_ix_read_ += frame_len_smpl_;
  return *bitstream_len_byte;
}

int16_t ACMGSMFR::EnableDTX() {
  if (dtx_enabled_) {
    return 0;
  } else if (encoder_exist_) {
    if (WebRtcGSMFR_EncoderInit(encoder_inst_ptr_, 1) < 0) {
      WEBRTC_TRACE(webrtc::kTraceError, webrtc::kTraceAudioCoding, unique_id_,
                   "EnableDTX: cannot init encoder for GSMFR");
      return -1;
    }
    dtx_enabled_ = true;
    return 0;
  } else {
    return -1;
  }
}

int16_t ACMGSMFR::DisableDTX() {
  if (!dtx_enabled_) {
    return 0;
  } else if (encoder_exist_) {
    if (WebRtcGSMFR_EncoderInit(encoder_inst_ptr_, 0) < 0) {
      WEBRTC_TRACE(webrtc::kTraceError, webrtc::kTraceAudioCoding, unique_id_,
                   "DisableDTX: cannot init encoder for GSMFR");
      return -1;
    }
    dtx_enabled_ = false;
    return 0;
  } else {
    // encoder doesn't exists, therefore disabling is harmless
    return 0;
  }
}

int16_t ACMGSMFR::InternalInitEncoder(WebRtcACMCodecParams* codec_params) {
  if (WebRtcGSMFR_EncoderInit(encoder_inst_ptr_,
                              ((codec_params->enable_dtx) ? 1 : 0)) < 0) {
    WEBRTC_TRACE(webrtc::kTraceError,
                 webrtc::kTraceAudioCoding,
                 unique_id_,
                 "InternalInitEncoder: cannot init encoder for GSMFR");
  }
  return 0;
}

ACMGenericCodec* ACMGSMFR::CreateInstance(void) { return NULL; }

int16_t ACMGSMFR::InternalCreateEncoder() {
  if (WebRtcGSMFR_CreateEnc(&encoder_inst_ptr_) < 0) {
    WEBRTC_TRACE(webrtc::kTraceError,
                 webrtc::kTraceAudioCoding,
                 unique_id_,
                 "InternalCreateEncoder: cannot create instance for GSMFR "
                 "encoder");
    return -1;
  }
  return 0;
}

void ACMGSMFR::DestructEncoderSafe() {
  if (encoder_inst_ptr_ != NULL) {
    WebRtcGSMFR_FreeEnc(encoder_inst_ptr_);
    encoder_inst_ptr_ = NULL;
  }
  encoder_exist_ = false;
  encoder_initialized_ = false;
}

void ACMGSMFR::InternalDestructEncoderInst(void* ptr_inst) {
  if (ptr_inst != NULL) {
    WebRtcGSMFR_FreeEnc(static_cast<GSMFR_encinst_t_*>(ptr_inst));
  }
  return;
}

#endif

}  // namespace acm2

}  // namespace webrtc
