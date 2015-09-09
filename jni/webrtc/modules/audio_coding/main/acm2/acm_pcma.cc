/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/modules/audio_coding/main/acm2/acm_pcma.h"

#include "webrtc/modules/audio_coding/codecs/g711/include/g711_interface.h"
#include "webrtc/modules/audio_coding/main/acm2/acm_common_defs.h"
#include "webrtc/system_wrappers/interface/trace.h"

// Codec interface

namespace webrtc {

namespace acm2 {

ACMPCMA::ACMPCMA(int16_t codec_id) { codec_id_ = codec_id; }

ACMPCMA::~ACMPCMA() { return; }

int16_t ACMPCMA::InternalEncode(uint8_t* bitstream,
                                int16_t* bitstream_len_byte) {
  *bitstream_len_byte = WebRtcG711_EncodeA(
      NULL, &in_audio_[in_audio_ix_read_], frame_len_smpl_ * num_channels_,
      reinterpret_cast<int16_t*>(bitstream));
  // Increment the read index this tell the caller that how far
  // we have gone forward in reading the audio buffer.
  in_audio_ix_read_ += frame_len_smpl_ * num_channels_;
  return *bitstream_len_byte;
}

int16_t ACMPCMA::InternalInitEncoder(WebRtcACMCodecParams* /* codec_params */) {
  // This codec does not need initialization, PCM has no instance.
  return 0;
}

ACMGenericCodec* ACMPCMA::CreateInstance(void) { return NULL; }

int16_t ACMPCMA::InternalCreateEncoder() {
  // PCM has no instance.
  return 0;
}

void ACMPCMA::InternalDestructEncoderInst(void* /* ptr_inst */) {
  // PCM has no instance.
  return;
}

void ACMPCMA::DestructEncoderSafe() {
  // PCM has no instance.
  return;
}

}  // namespace acm2

}  // namespace webrtc
