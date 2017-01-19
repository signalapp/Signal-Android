/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/modules/audio_coding/main/acm2/acm_red.h"

#include "webrtc/modules/audio_coding/main/acm2/acm_common_defs.h"
#include "webrtc/system_wrappers/interface/trace.h"

namespace webrtc {

namespace acm2 {

ACMRED::ACMRED(int16_t codec_id) { codec_id_ = codec_id; }

ACMRED::~ACMRED() {}

int16_t ACMRED::InternalEncode(uint8_t* /* bitstream */,
                               int16_t* /* bitstream_len_byte */) {
  // RED is never used as an encoder
  // RED has no instance
  return 0;
}

int16_t ACMRED::InternalInitEncoder(WebRtcACMCodecParams* /* codec_params */) {
  // This codec does not need initialization,
  // RED has no instance
  return 0;
}

ACMGenericCodec* ACMRED::CreateInstance(void) { return NULL; }

int16_t ACMRED::InternalCreateEncoder() {
  // RED has no instance
  return 0;
}

void ACMRED::InternalDestructEncoderInst(void* /* ptr_inst */) {
  // RED has no instance
}

void ACMRED::DestructEncoderSafe() {
  // RED has no instance
}

}  // namespace acm2

}  // namespace webrtc
