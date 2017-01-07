/*
 *  Copyright 2016 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/base/gunit.h"
#include "webrtc/base/onetimeevent.h"

namespace webrtc {

TEST(OneTimeEventTest, ThreadSafe) {
  OneTimeEvent ot;

  // The one time event is expected to evaluate to true only the first time.
  EXPECT_TRUE(ot());
  EXPECT_FALSE(ot());
  EXPECT_FALSE(ot());
}

TEST(OneTimeEventTest, ThreadUnsafe) {
  ThreadUnsafeOneTimeEvent ot;

  EXPECT_TRUE(ot());
  EXPECT_FALSE(ot());
  EXPECT_FALSE(ot());
}

}  // namespace webrtc
