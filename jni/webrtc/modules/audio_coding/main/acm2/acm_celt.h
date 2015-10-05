/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_MODULES_AUDIO_CODING_MAIN_ACM2_ACM_CELT_H_
#define WEBRTC_MODULES_AUDIO_CODING_MAIN_ACM2_ACM_CELT_H_

#include "webrtc/modules/audio_coding/main/acm2/acm_generic_codec.h"

// forward declaration
struct CELT_encinst_t_;
struct CELT_decinst_t_;

namespace webrtc {

namespace acm2 {

class ACMCELT : public ACMGenericCodec {
 public:
  explicit ACMCELT(int16_t codec_id);
  ~ACMCELT();

  ACMGenericCodec* CreateInstance(void);

  int16_t InternalEncode(uint8_t* bitstream, int16_t* bitstream_len_byte);

  int16_t InternalInitEncoder(WebRtcACMCodecParams *codec_params);

 protected:
  void DestructEncoderSafe();

  int16_t InternalCreateEncoder();

  void InternalDestructEncoderInst(void* ptr_inst);

  int16_t SetBitRateSafe(const int32_t rate);

  CELT_encinst_t_* enc_inst_ptr_;
  uint16_t sampling_freq_;
  int32_t bitrate_;
  uint16_t channels_;
};

}  // namespace acm2

}  // namespace webrtc

#endif  // WEBRTC_MODULES_AUDIO_CODING_MAIN_ACM2_ACM_CELT_H_
