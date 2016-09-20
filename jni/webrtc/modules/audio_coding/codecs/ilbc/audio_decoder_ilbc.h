/*
 *  Copyright (c) 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_MODULES_AUDIO_CODING_CODECS_ILBC_AUDIO_DECODER_ILBC_H_
#define WEBRTC_MODULES_AUDIO_CODING_CODECS_ILBC_AUDIO_DECODER_ILBC_H_

#include "webrtc/base/constructormagic.h"
#include "webrtc/modules/audio_coding/codecs/audio_decoder.h"

typedef struct iLBC_decinst_t_ IlbcDecoderInstance;

namespace webrtc {

class AudioDecoderIlbc final : public AudioDecoder {
 public:
  AudioDecoderIlbc();
  ~AudioDecoderIlbc() override;
  bool HasDecodePlc() const override;
  size_t DecodePlc(size_t num_frames, int16_t* decoded) override;
  void Reset() override;
  int SampleRateHz() const override;
  size_t Channels() const override;

 protected:
  int DecodeInternal(const uint8_t* encoded,
                     size_t encoded_len,
                     int sample_rate_hz,
                     int16_t* decoded,
                     SpeechType* speech_type) override;

 private:
  IlbcDecoderInstance* dec_state_;
  RTC_DISALLOW_COPY_AND_ASSIGN(AudioDecoderIlbc);
};

}  // namespace webrtc
#endif  // WEBRTC_MODULES_AUDIO_CODING_CODECS_ILBC_AUDIO_DECODER_ILBC_H_
