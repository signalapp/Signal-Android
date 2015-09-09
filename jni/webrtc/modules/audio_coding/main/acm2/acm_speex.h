/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_MODULES_AUDIO_CODING_MAIN_ACM2_ACM_SPEEX_H_
#define WEBRTC_MODULES_AUDIO_CODING_MAIN_ACM2_ACM_SPEEX_H_

#include "webrtc/modules/audio_coding/main/acm2/acm_generic_codec.h"

// forward declaration
struct SPEEX_encinst_t_;
struct SPEEX_decinst_t_;

namespace webrtc {

namespace acm2 {

class ACMSPEEX : public ACMGenericCodec {
 public:
  explicit ACMSPEEX(int16_t codec_id);
  ~ACMSPEEX();

  // For FEC.
  ACMGenericCodec* CreateInstance(void);

  int16_t InternalEncode(uint8_t* bitstream, int16_t* bitstream_len_byte);

  int16_t InternalInitEncoder(WebRtcACMCodecParams* codec_params);

 protected:
  void DestructEncoderSafe();

  int16_t InternalCreateEncoder();

  void InternalDestructEncoderInst(void* ptr_inst);

  int16_t SetBitRateSafe(const int32_t rate);

  int16_t EnableDTX();

  int16_t DisableDTX();

#ifdef UNUSEDSPEEX
  int16_t EnableVBR();

  int16_t DisableVBR();

  int16_t SetComplMode(int16_t mode);
#endif

  SPEEX_encinst_t_* encoder_inst_ptr_;
  int16_t compl_mode_;
  bool vbr_enabled_;
  int32_t encoding_rate_;
  int16_t sampling_frequency_;
  uint16_t samples_in_20ms_audio_;
};

}  // namespace acm2

}  // namespace webrtc

#endif  // WEBRTC_MODULES_AUDIO_CODING_MAIN_ACM2_ACM_SPEEX_H_
