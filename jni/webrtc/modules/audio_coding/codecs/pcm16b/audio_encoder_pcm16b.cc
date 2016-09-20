/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/modules/audio_coding/codecs/pcm16b/audio_encoder_pcm16b.h"

#include "webrtc/base/checks.h"
#include "webrtc/common_types.h"
#include "webrtc/modules/audio_coding/codecs/pcm16b/pcm16b.h"

namespace webrtc {

size_t AudioEncoderPcm16B::EncodeCall(const int16_t* audio,
                                      size_t input_len,
                                      uint8_t* encoded) {
  return WebRtcPcm16b_Encode(audio, input_len, encoded);
}

size_t AudioEncoderPcm16B::BytesPerSample() const {
  return 2;
}

AudioEncoder::CodecType AudioEncoderPcm16B::GetCodecType() const {
  return CodecType::kOther;
}

namespace {
AudioEncoderPcm16B::Config CreateConfig(const CodecInst& codec_inst) {
  AudioEncoderPcm16B::Config config;
  config.num_channels = codec_inst.channels;
  config.sample_rate_hz = codec_inst.plfreq;
  config.frame_size_ms = rtc::CheckedDivExact(
      codec_inst.pacsize, rtc::CheckedDivExact(config.sample_rate_hz, 1000));
  config.payload_type = codec_inst.pltype;
  return config;
}
}  // namespace

bool AudioEncoderPcm16B::Config::IsOk() const {
  if ((sample_rate_hz != 8000) && (sample_rate_hz != 16000) &&
      (sample_rate_hz != 32000) && (sample_rate_hz != 48000))
    return false;
  return AudioEncoderPcm::Config::IsOk();
}

AudioEncoderPcm16B::AudioEncoderPcm16B(const CodecInst& codec_inst)
    : AudioEncoderPcm16B(CreateConfig(codec_inst)) {}

}  // namespace webrtc
