/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/modules/audio_coding/main/acm2/acm_pcm16b.h"

#ifdef WEBRTC_CODEC_PCM16
#include "webrtc/modules/audio_coding/codecs/pcm16b/include/pcm16b.h"
#include "webrtc/modules/audio_coding/main/acm2/acm_codec_database.h"
#include "webrtc/modules/audio_coding/main/acm2/acm_common_defs.h"
#include "webrtc/system_wrappers/interface/trace.h"
#endif

namespace webrtc {

namespace acm2 {

#ifndef WEBRTC_CODEC_PCM16

ACMPCM16B::ACMPCM16B(int16_t /* codec_id */) { return; }

ACMPCM16B::~ACMPCM16B() { return; }

int16_t ACMPCM16B::InternalEncode(uint8_t* /* bitstream */,
                                  int16_t* /* bitstream_len_byte */) {
  return -1;
}

int16_t ACMPCM16B::InternalInitEncoder(
    WebRtcACMCodecParams* /* codec_params */) {
  return -1;
}

ACMGenericCodec* ACMPCM16B::CreateInstance(void) { return NULL; }

int16_t ACMPCM16B::InternalCreateEncoder() { return -1; }

void ACMPCM16B::InternalDestructEncoderInst(void* /* ptr_inst */) { return; }

void ACMPCM16B::DestructEncoderSafe() { return; }

#else  //===================== Actual Implementation =======================
ACMPCM16B::ACMPCM16B(int16_t codec_id) {
  codec_id_ = codec_id;
  sampling_freq_hz_ = ACMCodecDB::CodecFreq(codec_id_);
}

ACMPCM16B::~ACMPCM16B() { return; }

int16_t ACMPCM16B::InternalEncode(uint8_t* bitstream,
                                  int16_t* bitstream_len_byte) {
  *bitstream_len_byte = WebRtcPcm16b_Encode(&in_audio_[in_audio_ix_read_],
                                            frame_len_smpl_ * num_channels_,
                                            bitstream);
  // Increment the read index to tell the caller that how far
  // we have gone forward in reading the audio buffer.
  in_audio_ix_read_ += frame_len_smpl_ * num_channels_;
  return *bitstream_len_byte;
}

int16_t ACMPCM16B::InternalInitEncoder(
    WebRtcACMCodecParams* /* codec_params */) {
  // This codec does not need initialization, PCM has no instance.
  return 0;
}

ACMGenericCodec* ACMPCM16B::CreateInstance(void) { return NULL; }

int16_t ACMPCM16B::InternalCreateEncoder() {
  // PCM has no instance.
  return 0;
}

void ACMPCM16B::InternalDestructEncoderInst(void* /* ptr_inst */) {
  // PCM has no instance.
  return;
}

void ACMPCM16B::DestructEncoderSafe() {
  // PCM has no instance.
  encoder_exist_ = false;
  encoder_initialized_ = false;
  return;
}

#endif

}  // namespace acm2

}  // namespace webrtc
