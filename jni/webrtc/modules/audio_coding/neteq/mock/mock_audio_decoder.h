/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_MODULES_AUDIO_CODING_NETEQ_MOCK_MOCK_AUDIO_DECODER_H_
#define WEBRTC_MODULES_AUDIO_CODING_NETEQ_MOCK_MOCK_AUDIO_DECODER_H_

#include "webrtc/modules/audio_coding/neteq/interface/audio_decoder.h"

#include "gmock/gmock.h"

namespace webrtc {

class MockAudioDecoder : public AudioDecoder {
 public:
  MockAudioDecoder() : AudioDecoder(kDecoderArbitrary) {}
  virtual ~MockAudioDecoder() { Die(); }
  MOCK_METHOD0(Die, void());
  MOCK_METHOD4(Decode, int(const uint8_t*, size_t, int16_t*,
                           AudioDecoder::SpeechType*));
  MOCK_CONST_METHOD0(HasDecodePlc, bool());
  MOCK_METHOD2(DecodePlc, int(int, int16_t*));
  MOCK_METHOD0(Init, int());
  MOCK_METHOD5(IncomingPacket, int(const uint8_t*, size_t, uint16_t, uint32_t,
                                   uint32_t));
  MOCK_METHOD0(ErrorCode, int());
  MOCK_CONST_METHOD0(codec_type, NetEqDecoder());
  MOCK_METHOD1(CodecSupported, bool(NetEqDecoder));
};

}  // namespace webrtc
#endif  // WEBRTC_MODULES_AUDIO_CODING_NETEQ_MOCK_MOCK_AUDIO_DECODER_H_
