/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_MODULES_AUDIO_CODING_NETEQ_MOCK_MOCK_EXPAND_H_
#define WEBRTC_MODULES_AUDIO_CODING_NETEQ_MOCK_MOCK_EXPAND_H_

#include "webrtc/modules/audio_coding/neteq/expand.h"

#include "testing/gmock/include/gmock/gmock.h"

namespace webrtc {

class MockExpand : public Expand {
 public:
  MockExpand(BackgroundNoise* background_noise,
             SyncBuffer* sync_buffer,
             RandomVector* random_vector,
             StatisticsCalculator* statistics,
             int fs,
             size_t num_channels)
      : Expand(background_noise,
               sync_buffer,
               random_vector,
               statistics,
               fs,
               num_channels) {}
  virtual ~MockExpand() { Die(); }
  MOCK_METHOD0(Die, void());
  MOCK_METHOD0(Reset,
      void());
  MOCK_METHOD1(Process,
      int(AudioMultiVector* output));
  MOCK_METHOD0(SetParametersForNormalAfterExpand,
      void());
  MOCK_METHOD0(SetParametersForMergeAfterExpand,
      void());
  MOCK_CONST_METHOD0(overlap_length,
      size_t());
};

}  // namespace webrtc

namespace webrtc {

class MockExpandFactory : public ExpandFactory {
 public:
  MOCK_CONST_METHOD6(Create,
                     Expand*(BackgroundNoise* background_noise,
                             SyncBuffer* sync_buffer,
                             RandomVector* random_vector,
                             StatisticsCalculator* statistics,
                             int fs,
                             size_t num_channels));
};

}  // namespace webrtc
#endif  // WEBRTC_MODULES_AUDIO_CODING_NETEQ_MOCK_MOCK_EXPAND_H_
