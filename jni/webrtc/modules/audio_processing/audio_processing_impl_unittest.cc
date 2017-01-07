/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/modules/audio_processing/audio_processing_impl.h"

#include "testing/gmock/include/gmock/gmock.h"
#include "testing/gtest/include/gtest/gtest.h"
#include "webrtc/config.h"
#include "webrtc/modules/audio_processing/test/test_utils.h"
#include "webrtc/modules/include/module_common_types.h"

using ::testing::Invoke;
using ::testing::Return;

namespace webrtc {

class MockInitialize : public AudioProcessingImpl {
 public:
  explicit MockInitialize(const Config& config) : AudioProcessingImpl(config) {
  }

  MOCK_METHOD0(InitializeLocked, int());
  int RealInitializeLocked() NO_THREAD_SAFETY_ANALYSIS {
    return AudioProcessingImpl::InitializeLocked();
  }
};

TEST(AudioProcessingImplTest, AudioParameterChangeTriggersInit) {
  Config config;
  MockInitialize mock(config);
  ON_CALL(mock, InitializeLocked())
      .WillByDefault(Invoke(&mock, &MockInitialize::RealInitializeLocked));

  EXPECT_CALL(mock, InitializeLocked()).Times(1);
  mock.Initialize();

  AudioFrame frame;
  // Call with the default parameters; there should be no init.
  frame.num_channels_ = 1;
  SetFrameSampleRate(&frame, 16000);
  EXPECT_CALL(mock, InitializeLocked())
      .Times(0);
  EXPECT_NOERR(mock.ProcessStream(&frame));
  EXPECT_NOERR(mock.ProcessReverseStream(&frame));

  // New sample rate. (Only impacts ProcessStream).
  SetFrameSampleRate(&frame, 32000);
  EXPECT_CALL(mock, InitializeLocked())
      .Times(1);
  EXPECT_NOERR(mock.ProcessStream(&frame));

  // New number of channels.
  frame.num_channels_ = 2;
  EXPECT_CALL(mock, InitializeLocked())
      .Times(2);
  EXPECT_NOERR(mock.ProcessStream(&frame));
  // ProcessStream sets num_channels_ == num_output_channels.
  frame.num_channels_ = 2;
  EXPECT_NOERR(mock.ProcessReverseStream(&frame));

  // A new sample rate passed to ProcessReverseStream should cause an init.
  SetFrameSampleRate(&frame, 16000);
  EXPECT_CALL(mock, InitializeLocked()).Times(1);
  EXPECT_NOERR(mock.ProcessReverseStream(&frame));
}

}  // namespace webrtc
