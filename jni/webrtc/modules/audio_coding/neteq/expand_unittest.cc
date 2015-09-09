/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

// Unit tests for Expand class.

#include "webrtc/modules/audio_coding/neteq/expand.h"

#include "gtest/gtest.h"
#include "webrtc/modules/audio_coding/neteq/background_noise.h"
#include "webrtc/modules/audio_coding/neteq/random_vector.h"
#include "webrtc/modules/audio_coding/neteq/sync_buffer.h"

namespace webrtc {

TEST(Expand, CreateAndDestroy) {
  int fs = 8000;
  size_t channels = 1;
  BackgroundNoise bgn(channels);
  SyncBuffer sync_buffer(1, 1000);
  RandomVector random_vector;
  Expand expand(&bgn, &sync_buffer, &random_vector, fs, channels);
}

TEST(Expand, CreateUsingFactory) {
  int fs = 8000;
  size_t channels = 1;
  BackgroundNoise bgn(channels);
  SyncBuffer sync_buffer(1, 1000);
  RandomVector random_vector;
  ExpandFactory expand_factory;
  Expand* expand =
      expand_factory.Create(&bgn, &sync_buffer, &random_vector, fs, channels);
  EXPECT_TRUE(expand != NULL);
  delete expand;
}

// TODO(hlundin): Write more tests.

}  // namespace webrtc
