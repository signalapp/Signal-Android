/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/modules/audio_coding/main/acm2/acm_dtmf_playout.h"

#ifdef WEBRTC_CODEC_AVT
#include "webrtc/modules/audio_coding/main/acm2/acm_common_defs.h"
#include "webrtc/modules/audio_coding/main/acm2/acm_receiver.h"
#include "webrtc/system_wrappers/interface/trace.h"
#endif

namespace webrtc {

namespace acm2 {

#ifndef WEBRTC_CODEC_AVT

ACMDTMFPlayout::ACMDTMFPlayout(int16_t /* codec_id */) { return; }

ACMDTMFPlayout::~ACMDTMFPlayout() { return; }

int16_t ACMDTMFPlayout::InternalEncode(uint8_t* /* bitstream */,
                                       int16_t* /* bitstream_len_byte */) {
  return -1;
}

int16_t ACMDTMFPlayout::InternalInitEncoder(
    WebRtcACMCodecParams* /* codec_params */) {
  return -1;
}

ACMGenericCodec* ACMDTMFPlayout::CreateInstance(void) { return NULL; }

int16_t ACMDTMFPlayout::InternalCreateEncoder() { return -1; }

void ACMDTMFPlayout::InternalDestructEncoderInst(void* /* ptr_inst */) {
  return;
}

void ACMDTMFPlayout::DestructEncoderSafe() {
  return;
}

#else  //===================== Actual Implementation =======================

ACMDTMFPlayout::ACMDTMFPlayout(int16_t codec_id) { codec_id_ = codec_id; }

ACMDTMFPlayout::~ACMDTMFPlayout() { return; }

int16_t ACMDTMFPlayout::InternalEncode(uint8_t* /* bitstream */,
                                       int16_t* /* bitstream_len_byte */) {
  return 0;
}

int16_t ACMDTMFPlayout::InternalInitEncoder(
    WebRtcACMCodecParams* /* codec_params */) {
  // This codec does not need initialization,
  // DTMFPlayout has no instance
  return 0;
}

ACMGenericCodec* ACMDTMFPlayout::CreateInstance(void) { return NULL; }

int16_t ACMDTMFPlayout::InternalCreateEncoder() {
  // DTMFPlayout has no instance
  return 0;
}

void ACMDTMFPlayout::InternalDestructEncoderInst(void* /* ptr_inst */) {
  // DTMFPlayout has no instance
  return;
}

void ACMDTMFPlayout::DestructEncoderSafe() {
  // DTMFPlayout has no instance
  return;
}

#endif

}  // namespace acm2

}  // namespace webrtc
