/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

// Unit tests for Accelerate and PreemptiveExpand classes.

#include "webrtc/modules/audio_coding/neteq/accelerate.h"
#include "webrtc/modules/audio_coding/neteq/preemptive_expand.h"

#include "gtest/gtest.h"
#include "webrtc/modules/audio_coding/neteq/background_noise.h"

namespace webrtc {

TEST(TimeStretch, CreateAndDestroy) {
  const int kSampleRate = 8000;
  const size_t kNumChannels = 1;
  const int kOverlapSamples = 5 * kSampleRate / 8000;
  BackgroundNoise bgn(kNumChannels);
  Accelerate accelerate(kSampleRate, kNumChannels, bgn);
  PreemptiveExpand preemptive_expand(
      kSampleRate, kNumChannels, bgn, kOverlapSamples);
}

TEST(TimeStretch, CreateUsingFactory) {
  const int kSampleRate = 8000;
  const size_t kNumChannels = 1;
  const int kOverlapSamples = 5 * kSampleRate / 8000;
  BackgroundNoise bgn(kNumChannels);

  AccelerateFactory accelerate_factory;
  Accelerate* accelerate =
      accelerate_factory.Create(kSampleRate, kNumChannels, bgn);
  EXPECT_TRUE(accelerate != NULL);
  delete accelerate;

  PreemptiveExpandFactory preemptive_expand_factory;
  PreemptiveExpand* preemptive_expand = preemptive_expand_factory.Create(
      kSampleRate, kNumChannels, bgn, kOverlapSamples);
  EXPECT_TRUE(preemptive_expand != NULL);
  delete preemptive_expand;
}

// TODO(hlundin): Write more tests.

}  // namespace webrtc
