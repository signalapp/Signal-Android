/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_MODULES_AUDIO_CODING_MAIN_ACM2_ACM_G7221C_H_
#define WEBRTC_MODULES_AUDIO_CODING_MAIN_ACM2_ACM_G7221C_H_

#include "webrtc/modules/audio_coding/main/acm2/acm_generic_codec.h"

// forward declaration
struct G722_1C_24_encinst_t_;
struct G722_1C_24_decinst_t_;
struct G722_1C_32_encinst_t_;
struct G722_1C_32_decinst_t_;
struct G722_1C_48_encinst_t_;
struct G722_1C_48_decinst_t_;
struct G722_1_Inst_t_;

namespace webrtc {

namespace acm2 {

class ACMG722_1C : public ACMGenericCodec {
 public:
  explicit ACMG722_1C(int16_t codec_id);
  ~ACMG722_1C();

  // for FEC
  ACMGenericCodec* CreateInstance(void);

  int16_t InternalEncode(uint8_t* bitstream, int16_t* bitstream_len_byte);

  int16_t InternalInitEncoder(WebRtcACMCodecParams* codec_params);

 protected:
  void DestructEncoderSafe();

  int16_t InternalCreateEncoder();

  void InternalDestructEncoderInst(void* ptr_inst);

  int32_t operational_rate_;

  G722_1_Inst_t_* encoder_inst_ptr_;
  G722_1_Inst_t_* encoder_inst_ptr_right_;  // Used in stereo mode

  // Only one set of these pointer is valid at any instance
  G722_1C_24_encinst_t_* encoder_inst24_ptr_;
  G722_1C_24_encinst_t_* encoder_inst24_ptr_right_;
  G722_1C_32_encinst_t_* encoder_inst32_ptr_;
  G722_1C_32_encinst_t_* encoder_inst32_ptr_right_;
  G722_1C_48_encinst_t_* encoder_inst48_ptr_;
  G722_1C_48_encinst_t_* encoder_inst48_ptr_right_;
};

}  // namespace acm2

}  // namespace webrtc

#endif  // WEBRTC_MODULES_AUDIO_CODING_MAIN_ACM2_ACM_G7221C_H_
