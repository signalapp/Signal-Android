/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_MODULES_AUDIO_CODING_NETEQ_MOCK_MOCK_DTMF_TONE_GENERATOR_H_
#define WEBRTC_MODULES_AUDIO_CODING_NETEQ_MOCK_MOCK_DTMF_TONE_GENERATOR_H_

#include "webrtc/modules/audio_coding/neteq/dtmf_tone_generator.h"

#include "testing/gmock/include/gmock/gmock.h"

namespace webrtc {

class MockDtmfToneGenerator : public DtmfToneGenerator {
 public:
  virtual ~MockDtmfToneGenerator() { Die(); }
  MOCK_METHOD0(Die, void());
  MOCK_METHOD3(Init,
      int(int fs, int event, int attenuation));
  MOCK_METHOD0(Reset,
      void());
  MOCK_METHOD2(Generate,
      int(size_t num_samples, AudioMultiVector* output));
  MOCK_CONST_METHOD0(initialized,
      bool());
};

}  // namespace webrtc
#endif  // WEBRTC_MODULES_AUDIO_CODING_NETEQ_MOCK_MOCK_DTMF_TONE_GENERATOR_H_
