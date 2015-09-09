/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "webrtc/modules/audio_coding/main/acm2/acm_ilbc.h"

#ifdef WEBRTC_CODEC_ILBC
#include "webrtc/modules/audio_coding/codecs/ilbc/interface/ilbc.h"
#include "webrtc/modules/audio_coding/main/acm2/acm_common_defs.h"
#include "webrtc/system_wrappers/interface/trace.h"
#endif

namespace webrtc {

namespace acm2 {

#ifndef WEBRTC_CODEC_ILBC

ACMILBC::ACMILBC(int16_t /* codec_id */) : encoder_inst_ptr_(NULL) {}

ACMILBC::~ACMILBC() { return; }

int16_t ACMILBC::InternalEncode(uint8_t* /* bitstream */,
                                int16_t* /* bitstream_len_byte */) {
  return -1;
}

int16_t ACMILBC::InternalInitEncoder(WebRtcACMCodecParams* /* codec_params */) {
  return -1;
}

ACMGenericCodec* ACMILBC::CreateInstance(void) { return NULL; }

int16_t ACMILBC::InternalCreateEncoder() { return -1; }

void ACMILBC::DestructEncoderSafe() { return; }

void ACMILBC::InternalDestructEncoderInst(void* /* ptr_inst */) { return; }

int16_t ACMILBC::SetBitRateSafe(const int32_t /* rate */) { return -1; }

#else  //===================== Actual Implementation =======================

ACMILBC::ACMILBC(int16_t codec_id) : encoder_inst_ptr_(NULL) {
  codec_id_ = codec_id;
  return;
}

ACMILBC::~ACMILBC() {
  if (encoder_inst_ptr_ != NULL) {
    WebRtcIlbcfix_EncoderFree(encoder_inst_ptr_);
    encoder_inst_ptr_ = NULL;
  }
  return;
}

int16_t ACMILBC::InternalEncode(uint8_t* bitstream,
                                int16_t* bitstream_len_byte) {
  *bitstream_len_byte = WebRtcIlbcfix_Encode(
      encoder_inst_ptr_, &in_audio_[in_audio_ix_read_], frame_len_smpl_,
      reinterpret_cast<int16_t*>(bitstream));
  if (*bitstream_len_byte < 0) {
    WEBRTC_TRACE(webrtc::kTraceError,
                 webrtc::kTraceAudioCoding,
                 unique_id_,
                 "InternalEncode: error in encode for ILBC");
    return -1;
  }
  // increment the read index this tell the caller that how far
  // we have gone forward in reading the audio buffer
  in_audio_ix_read_ += frame_len_smpl_;
  return *bitstream_len_byte;
}

int16_t ACMILBC::InternalInitEncoder(WebRtcACMCodecParams* codec_params) {
  // initialize with a correct processing block length
  if ((160 == (codec_params->codec_inst).pacsize) ||
      (320 == (codec_params->codec_inst).pacsize)) {
    // processing block of 20ms
    return WebRtcIlbcfix_EncoderInit(encoder_inst_ptr_, 20);
  } else if ((240 == (codec_params->codec_inst).pacsize) ||
      (480 == (codec_params->codec_inst).pacsize)) {
    // processing block of 30ms
    return WebRtcIlbcfix_EncoderInit(encoder_inst_ptr_, 30);
  } else {
    WEBRTC_TRACE(webrtc::kTraceError, webrtc::kTraceAudioCoding, unique_id_,
                 "InternalInitEncoder: invalid processing block");
    return -1;
  }
}

ACMGenericCodec* ACMILBC::CreateInstance(void) { return NULL; }

int16_t ACMILBC::InternalCreateEncoder() {
  if (WebRtcIlbcfix_EncoderCreate(&encoder_inst_ptr_) < 0) {
    WEBRTC_TRACE(webrtc::kTraceError,
                 webrtc::kTraceAudioCoding,
                 unique_id_,
                 "InternalCreateEncoder: cannot create instance for ILBC "
                 "encoder");
    return -1;
  }
  return 0;
}

void ACMILBC::DestructEncoderSafe() {
  encoder_initialized_ = false;
  encoder_exist_ = false;
  if (encoder_inst_ptr_ != NULL) {
    WebRtcIlbcfix_EncoderFree(encoder_inst_ptr_);
    encoder_inst_ptr_ = NULL;
  }
}

void ACMILBC::InternalDestructEncoderInst(void* ptr_inst) {
  if (ptr_inst != NULL) {
    WebRtcIlbcfix_EncoderFree(static_cast<iLBC_encinst_t_*>(ptr_inst));
  }
  return;
}

int16_t ACMILBC::SetBitRateSafe(const int32_t rate) {
  // Check that rate is valid. No need to store the value
  if (rate == 13300) {
    WebRtcIlbcfix_EncoderInit(encoder_inst_ptr_, 30);
  } else if (rate == 15200) {
    WebRtcIlbcfix_EncoderInit(encoder_inst_ptr_, 20);
  } else {
    return -1;
  }
  encoder_params_.codec_inst.rate = rate;

  return 0;
}

#endif

}  // namespace acm2

}  // namespace webrtc
