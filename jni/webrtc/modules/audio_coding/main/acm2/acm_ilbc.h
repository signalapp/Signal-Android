/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_MODULES_AUDIO_CODING_MAIN_ACM2_ACM_ILBC_H_
#define WEBRTC_MODULES_AUDIO_CODING_MAIN_ACM2_ACM_ILBC_H_

#include "webrtc/modules/audio_coding/main/acm2/acm_generic_codec.h"

// forward declaration
struct iLBC_encinst_t_;
struct iLBC_decinst_t_;

namespace webrtc {

namespace acm2 {

class ACMILBC : public ACMGenericCodec {
 public:
  explicit ACMILBC(int16_t codec_id);
  ~ACMILBC();

  // for FEC
  ACMGenericCodec* CreateInstance(void);

  int16_t InternalEncode(uint8_t* bitstream,
                         int16_t* bitstream_len_byte) OVERRIDE
      EXCLUSIVE_LOCKS_REQUIRED(codec_wrapper_lock_);

  int16_t InternalInitEncoder(WebRtcACMCodecParams* codec_params);

 protected:
  int16_t SetBitRateSafe(const int32_t rate) OVERRIDE
      EXCLUSIVE_LOCKS_REQUIRED(codec_wrapper_lock_);

  void DestructEncoderSafe() OVERRIDE
      EXCLUSIVE_LOCKS_REQUIRED(codec_wrapper_lock_);

  int16_t InternalCreateEncoder();

  void InternalDestructEncoderInst(void* ptr_inst);

  iLBC_encinst_t_* encoder_inst_ptr_;
};

}  // namespace acm2

}  // namespace webrtc

#endif  // WEBRTC_MODULES_AUDIO_CODING_MAIN_ACM2_ACM_ILBC_H_
