/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/modules/audio_coding/main/acm2/acm_g729.h"

#ifdef WEBRTC_CODEC_G729
// NOTE! G.729 is not included in the open-source package. Modify this file
// or your codec API to match the function calls and names of used G.729 API
// file.
#include "webrtc/modules/audio_coding/main/codecs/g729/interface/g729_interface.h"
#include "webrtc/modules/audio_coding/main/acm2/acm_common_defs.h"
#include "webrtc/modules/audio_coding/main/acm2/acm_receiver.h"
#include "webrtc/system_wrappers/interface/trace.h"
#endif

namespace webrtc {

namespace acm2 {

#ifndef WEBRTC_CODEC_G729

ACMG729::ACMG729(int16_t /* codec_id */) : encoder_inst_ptr_(NULL) {}

ACMG729::~ACMG729() { return; }

int16_t ACMG729::InternalEncode(uint8_t* /* bitstream */,
                                int16_t* /* bitstream_len_byte */) {
  return -1;
}

int16_t ACMG729::EnableDTX() { return -1; }

int16_t ACMG729::DisableDTX() { return -1; }

int32_t ACMG729::ReplaceInternalDTXSafe(const bool /*replace_internal_dtx */) {
  return -1;
}

int32_t ACMG729::IsInternalDTXReplacedSafe(bool* /* internal_dtx_replaced */) {
  return -1;
}

int16_t ACMG729::InternalInitEncoder(WebRtcACMCodecParams* /* codec_params */) {
  return -1;
}

ACMGenericCodec* ACMG729::CreateInstance(void) { return NULL; }

int16_t ACMG729::InternalCreateEncoder() { return -1; }

void ACMG729::DestructEncoderSafe() { return; }

void ACMG729::InternalDestructEncoderInst(void* /* ptr_inst */) { return; }

#else  //===================== Actual Implementation =======================
ACMG729::ACMG729(int16_t codec_id)
    : codec_id_(codec_id),
      has_internal_dtx_(),
      encoder_inst_ptr_(NULL) {}

ACMG729::~ACMG729() {
  if (encoder_inst_ptr_ != NULL) {
    // Delete encoder memory
    WebRtcG729_FreeEnc(encoder_inst_ptr_);
    encoder_inst_ptr_ = NULL;
  }
  return;
}

int16_t ACMG729::InternalEncode(uint8_t* bitstream,
                                int16_t* bitstream_len_byte) {
  // Initialize before entering the loop
  int16_t num_encoded_samples = 0;
  int16_t tmp_len_byte = 0;
  int16_t vad_decision = 0;
  *bitstream_len_byte = 0;
  while (num_encoded_samples < frame_len_smpl_) {
    // Call G.729 encoder with pointer to encoder memory, input
    // audio, number of samples and bitsream
    tmp_len_byte = WebRtcG729_Encode(
        encoder_inst_ptr_, &in_audio_[in_audio_ix_read_], 80,
        reinterpret_cast<int16_t*>(&(bitstream[*bitstream_len_byte])));

    // increment the read index this tell the caller that how far
    // we have gone forward in reading the audio buffer
    in_audio_ix_read_ += 80;

    // sanity check
    if (tmp_len_byte < 0) {
      // error has happened
      *bitstream_len_byte = 0;
      return -1;
    }

    // increment number of written bytes
    *bitstream_len_byte += tmp_len_byte;
    switch (tmp_len_byte) {
      case 0: {
        if (0 == num_encoded_samples) {
          // this is the first 10 ms in this packet and there is
          // no data generated, perhaps DTX is enabled and the
          // codec is not generating any bit-stream for this 10 ms.
          // we do not continue encoding this frame.
          return 0;
        }
        break;
      }
      case 2: {
        // check if G.729 internal DTX is enabled
        if (has_internal_dtx_ && dtx_enabled_) {
          vad_decision = 0;
          for (int16_t n = 0; n < MAX_FRAME_SIZE_10MSEC; n++) {
            vad_label_[n] = vad_decision;
          }
        }
        // we got a SID and have to send out this packet no matter
        // how much audio we have encoded
        return *bitstream_len_byte;
      }
      case 10: {
        vad_decision = 1;
        // this is a valid length just continue encoding
        break;
      }
      default: {
        return -1;
      }
    }

    // update number of encoded samples
    num_encoded_samples += 80;
  }

  // update VAD decision vector
  if (has_internal_dtx_ && !vad_decision && dtx_enabled_) {
    for (int16_t n = 0; n < MAX_FRAME_SIZE_10MSEC; n++) {
      vad_label_[n] = vad_decision;
    }
  }

  // done encoding, return number of encoded bytes
  return *bitstream_len_byte;
}

int16_t ACMG729::EnableDTX() {
  if (dtx_enabled_) {
    // DTX already enabled, do nothing
    return 0;
  } else if (encoder_exist_) {
    // Re-init the G.729 encoder to turn on DTX
    if (WebRtcG729_EncoderInit(encoder_inst_ptr_, 1) < 0) {
      return -1;
    }
    dtx_enabled_ = true;
    return 0;
  } else {
    return -1;
  }
}

int16_t ACMG729::DisableDTX() {
  if (!dtx_enabled_) {
    // DTX already dissabled, do nothing
    return 0;
  } else if (encoder_exist_) {
    // Re-init the G.729 decoder to turn off DTX
    if (WebRtcG729_EncoderInit(encoder_inst_ptr_, 0) < 0) {
      return -1;
    }
    dtx_enabled_ = false;
    return 0;
  } else {
    // encoder doesn't exists, therefore disabling is harmless
    return 0;
  }
}

int32_t ACMG729::ReplaceInternalDTXSafe(const bool replace_internal_dtx) {
  // This function is used to disable the G.729 built in DTX and use an
  // external instead.

  if (replace_internal_dtx == has_internal_dtx_) {
    // Make sure we keep the DTX/VAD setting if possible
    bool old_enable_dtx = dtx_enabled_;
    bool old_enable_vad = vad_enabled_;
    ACMVADMode old_mode = vad_mode_;
    if (replace_internal_dtx) {
      // Disable internal DTX before enabling external DTX
      DisableDTX();
    } else {
      // Disable external DTX before enabling internal
      ACMGenericCodec::DisableDTX();
    }
    has_internal_dtx_ = !replace_internal_dtx;
    int16_t status = SetVADSafe(old_enable_dtx, old_enable_vad, old_mode);
    // Check if VAD status has changed from inactive to active, or if error was
    // reported
    if (status == 1) {
      vad_enabled_ = true;
      return status;
    } else if (status < 0) {
      has_internal_dtx_ = replace_internal_dtx;
      return -1;
    }
  }
  return 0;
}

int32_t ACMG729::IsInternalDTXReplacedSafe(bool* internal_dtx_replaced) {
  // Get status of wether DTX is replaced or not
  *internal_dtx_replaced = !has_internal_dtx_;
  return 0;
}

int16_t ACMG729::InternalInitEncoder(WebRtcACMCodecParams* codec_params) {
  // Init G.729 encoder
  return WebRtcG729_EncoderInit(encoder_inst_ptr_,
                                ((codec_params->enable_dtx) ? 1 : 0));
}

ACMGenericCodec* ACMG729::CreateInstance(void) {
  // Function not used
  return NULL;
}

int16_t ACMG729::InternalCreateEncoder() {
  // Create encoder memory
  return WebRtcG729_CreateEnc(&encoder_inst_ptr_);
}

void ACMG729::DestructEncoderSafe() {
  // Free encoder memory
  encoder_exist_ = false;
  encoder_initialized_ = false;
  if (encoder_inst_ptr_ != NULL) {
    WebRtcG729_FreeEnc(encoder_inst_ptr_);
    encoder_inst_ptr_ = NULL;
  }
}

void ACMG729::InternalDestructEncoderInst(void* ptr_inst) {
  if (ptr_inst != NULL) {
    WebRtcG729_FreeEnc(static_cast<G729_encinst_t_*>(ptr_inst));
  }
  return;
}

#endif

}  // namespace acm2

}  // namespace webrtc
