/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/modules/audio_coding/main/acm2/acm_g7291.h"

#ifdef WEBRTC_CODEC_G729_1
// NOTE! G.729.1 is not included in the open-source package. Modify this file
// or your codec API to match the function calls and names of used G.729.1 API
// file.
#include "webrtc/modules/audio_coding/main/codecs/g7291/interface/g7291_interface.h"
#include "webrtc/modules/audio_coding/main/acm2/acm_common_defs.h"
#include "webrtc/system_wrappers/interface/trace.h"
#endif

namespace webrtc {

namespace acm2 {

#ifndef WEBRTC_CODEC_G729_1

ACMG729_1::ACMG729_1(int16_t /* codec_id */)
    : encoder_inst_ptr_(NULL),
      my_rate_(32000),
      flag_8khz_(0),
      flag_g729_mode_(0) {
  return;
}

ACMG729_1::~ACMG729_1() { return; }

int16_t ACMG729_1::InternalEncode(uint8_t* /* bitstream */,
                                  int16_t* /* bitstream_len_byte */) {
  return -1;
}

int16_t ACMG729_1::InternalInitEncoder(
    WebRtcACMCodecParams* /* codec_params */) {
  return -1;
}

ACMGenericCodec* ACMG729_1::CreateInstance(void) { return NULL; }

int16_t ACMG729_1::InternalCreateEncoder() { return -1; }

void ACMG729_1::DestructEncoderSafe() { return; }

void ACMG729_1::InternalDestructEncoderInst(void* /* ptr_inst */) { return; }

int16_t ACMG729_1::SetBitRateSafe(const int32_t /*rate*/) { return -1; }

#else  //===================== Actual Implementation =======================

struct G729_1_inst_t_;

ACMG729_1::ACMG729_1(int16_t codec_id)
    : encoder_inst_ptr_(NULL),
      my_rate_(32000),  // Default rate.
      flag_8khz_(0),
      flag_g729_mode_(0) {
  // TODO(tlegrand): We should add codec_id as a input variable to the
  // constructor of ACMGenericCodec.
  codec_id_ = codec_id;
  return;
}

ACMG729_1::~ACMG729_1() {
  if (encoder_inst_ptr_ != NULL) {
    WebRtcG7291_Free(encoder_inst_ptr_);
    encoder_inst_ptr_ = NULL;
  }
  return;
}

int16_t ACMG729_1::InternalEncode(uint8_t* bitstream,
                                  int16_t* bitstream_len_byte) {
  // Initialize before entering the loop
  int16_t num_encoded_samples = 0;
  *bitstream_len_byte = 0;

  int16_t byte_length_frame = 0;

  // Derive number of 20ms frames per encoded packet.
  // [1,2,3] <=> [20,40,60]ms <=> [320,640,960] samples
  int16_t num_20ms_frames = (frame_len_smpl_ / 320);
  // Byte length for the frame. +1 is for rate information.
  byte_length_frame =
      my_rate_ / (8 * 50) * num_20ms_frames + (1 - flag_g729_mode_);

  // The following might be revised if we have G729.1 Annex C (support for DTX);
  do {
    *bitstream_len_byte = WebRtcG7291_Encode(
        encoder_inst_ptr_, &in_audio_[in_audio_ix_read_],
        reinterpret_cast<int16_t*>(bitstream), my_rate_, num_20ms_frames);

    // increment the read index this tell the caller that how far
    // we have gone forward in reading the audio buffer
    in_audio_ix_read_ += 160;

    // sanity check
    if (*bitstream_len_byte < 0) {
      // error has happened
      WEBRTC_TRACE(webrtc::kTraceError, webrtc::kTraceAudioCoding, unique_id_,
                   "InternalEncode: Encode error for G729_1");
      *bitstream_len_byte = 0;
      return -1;
    }

    num_encoded_samples += 160;
  } while (*bitstream_len_byte == 0);

  // This criteria will change if we have Annex C.
  if (*bitstream_len_byte != byte_length_frame) {
    WEBRTC_TRACE(webrtc::kTraceError, webrtc::kTraceAudioCoding, unique_id_,
                 "InternalEncode: Encode error for G729_1");
    *bitstream_len_byte = 0;
    return -1;
  }

  if (num_encoded_samples != frame_len_smpl_) {
    *bitstream_len_byte = 0;
    return -1;
  }

  return *bitstream_len_byte;
}

int16_t ACMG729_1::InternalInitEncoder(WebRtcACMCodecParams* codec_params) {
  // set the bit rate and initialize
  my_rate_ = codec_params->codec_inst.rate;
  return SetBitRateSafe((uint32_t)my_rate_);
}

ACMGenericCodec* ACMG729_1::CreateInstance(void) { return NULL; }

int16_t ACMG729_1::InternalCreateEncoder() {
  if (WebRtcG7291_Create(&encoder_inst_ptr_) < 0) {
    WEBRTC_TRACE(webrtc::kTraceError,
                 webrtc::kTraceAudioCoding,
                 unique_id_,
                 "InternalCreateEncoder: create encoder failed for G729_1");
    return -1;
  }
  return 0;
}

void ACMG729_1::DestructEncoderSafe() {
  encoder_exist_ = false;
  encoder_initialized_ = false;
  if (encoder_inst_ptr_ != NULL) {
    WebRtcG7291_Free(encoder_inst_ptr_);
    encoder_inst_ptr_ = NULL;
  }
}

void ACMG729_1::InternalDestructEncoderInst(void* ptr_inst) {
  if (ptr_inst != NULL) {
    // WebRtcG7291_Free((G729_1_inst_t*)ptrInst);
  }
  return;
}

int16_t ACMG729_1::SetBitRateSafe(const int32_t rate) {
  // allowed rates: { 8000, 12000, 14000, 16000, 18000, 20000,
  //                22000, 24000, 26000, 28000, 30000, 32000};
  // TODO(tlegrand): This check exists in one other place two. Should be
  // possible to reuse code.
  switch (rate) {
    case 8000: {
      my_rate_ = 8000;
      break;
    }
    case 12000: {
      my_rate_ = 12000;
      break;
    }
    case 14000: {
      my_rate_ = 14000;
      break;
    }
    case 16000: {
      my_rate_ = 16000;
      break;
    }
    case 18000: {
      my_rate_ = 18000;
      break;
    }
    case 20000: {
      my_rate_ = 20000;
      break;
    }
    case 22000: {
      my_rate_ = 22000;
      break;
    }
    case 24000: {
      my_rate_ = 24000;
      break;
    }
    case 26000: {
      my_rate_ = 26000;
      break;
    }
    case 28000: {
      my_rate_ = 28000;
      break;
    }
    case 30000: {
      my_rate_ = 30000;
      break;
    }
    case 32000: {
      my_rate_ = 32000;
      break;
    }
    default: {
      WEBRTC_TRACE(webrtc::kTraceError, webrtc::kTraceAudioCoding, unique_id_,
                   "SetBitRateSafe: Invalid rate G729_1");
      return -1;
    }
  }

  // Re-init with new rate
  if (WebRtcG7291_EncoderInit(encoder_inst_ptr_, my_rate_, flag_8khz_,
                              flag_g729_mode_) >= 0) {
    encoder_params_.codec_inst.rate = my_rate_;
    return 0;
  } else {
    return -1;
  }
}

#endif

}  // namespace acm2

}  // namespace webrtc
