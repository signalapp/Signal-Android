/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_MODULES_AUDIO_PROCESSING_AGC_MOCK_AGC_H_
#define WEBRTC_MODULES_AUDIO_PROCESSING_AGC_MOCK_AGC_H_

#include "webrtc/modules/audio_processing/agc/agc.h"

#include "testing/gmock/include/gmock/gmock.h"
#include "webrtc/modules/include/module_common_types.h"

namespace webrtc {

class MockAgc : public Agc {
 public:
  MOCK_METHOD2(AnalyzePreproc, float(const int16_t* audio, size_t length));
  MOCK_METHOD3(Process, int(const int16_t* audio, size_t length,
                            int sample_rate_hz));
  MOCK_METHOD1(GetRmsErrorDb, bool(int* error));
  MOCK_METHOD0(Reset, void());
  MOCK_METHOD1(set_target_level_dbfs, int(int level));
  MOCK_CONST_METHOD0(target_level_dbfs, int());
  MOCK_METHOD1(EnableStandaloneVad, void(bool enable));
  MOCK_CONST_METHOD0(standalone_vad_enabled, bool());
};

}  // namespace webrtc

#endif  // WEBRTC_MODULES_AUDIO_PROCESSING_AGC_MOCK_AGC_H_
