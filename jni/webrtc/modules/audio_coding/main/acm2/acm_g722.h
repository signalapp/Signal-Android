/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_MODULES_AUDIO_CODING_MAIN_ACM2_ACM_G722_H_
#define WEBRTC_MODULES_AUDIO_CODING_MAIN_ACM2_ACM_G722_H_

#include "webrtc/modules/audio_coding/main/acm2/acm_generic_codec.h"
#include "webrtc/system_wrappers/interface/thread_annotations.h"

typedef struct WebRtcG722EncInst G722EncInst;
typedef struct WebRtcG722DecInst G722DecInst;

namespace webrtc {

namespace acm2 {

// Forward declaration.
struct ACMG722EncStr;
struct ACMG722DecStr;

class ACMG722 : public ACMGenericCodec {
 public:
  explicit ACMG722(int16_t codec_id);
  ~ACMG722();

  // For FEC.
  ACMGenericCodec* CreateInstance(void);

  int16_t InternalEncode(uint8_t* bitstream,
                         int16_t* bitstream_len_byte) OVERRIDE
      EXCLUSIVE_LOCKS_REQUIRED(codec_wrapper_lock_);

  int16_t InternalInitEncoder(WebRtcACMCodecParams* codec_params);

 protected:
  int32_t Add10MsDataSafe(const uint32_t timestamp,
                          const int16_t* data,
                          const uint16_t length_smpl,
                          const uint8_t audio_channel)
      EXCLUSIVE_LOCKS_REQUIRED(codec_wrapper_lock_);

  void DestructEncoderSafe() OVERRIDE
      EXCLUSIVE_LOCKS_REQUIRED(codec_wrapper_lock_);

  int16_t InternalCreateEncoder();

  void InternalDestructEncoderInst(void* ptr_inst);

  ACMG722EncStr* ptr_enc_str_;

  G722EncInst* encoder_inst_ptr_;
  G722EncInst* encoder_inst_ptr_right_;  // Prepared for stereo
};

}  // namespace acm2

}  // namespace webrtc

#endif  // WEBRTC_MODULES_AUDIO_CODING_MAIN_ACM2_ACM_G722_H_
