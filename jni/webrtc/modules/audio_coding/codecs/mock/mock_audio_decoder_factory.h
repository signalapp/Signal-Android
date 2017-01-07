/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_MODULES_AUDIO_CODING_CODECS_MOCK_MOCK_AUDIO_DECODER_FACTORY_H_
#define WEBRTC_MODULES_AUDIO_CODING_CODECS_MOCK_MOCK_AUDIO_DECODER_FACTORY_H_

#include <vector>

#include "testing/gmock/include/gmock/gmock.h"
#include "webrtc/modules/audio_coding/codecs/audio_decoder_factory.h"

namespace webrtc {

class MockAudioDecoderFactory : public AudioDecoderFactory {
 public:
  MOCK_METHOD0(GetSupportedFormats, std::vector<SdpAudioFormat>());
  std::unique_ptr<AudioDecoder> MakeAudioDecoder(
      const SdpAudioFormat& format) {
    std::unique_ptr<AudioDecoder> return_value;
    MakeAudioDecoderMock(format, &return_value);
    return return_value;
  }
  MOCK_METHOD2(MakeAudioDecoderMock,
               void(const SdpAudioFormat& format,
                    std::unique_ptr<AudioDecoder>* return_value));
};

}  // namespace webrtc

#endif  // WEBRTC_MODULES_AUDIO_CODING_CODECS_MOCK_MOCK_AUDIO_DECODER_FACTORY_H_
