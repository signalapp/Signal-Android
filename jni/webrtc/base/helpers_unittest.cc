/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <string>

#include "webrtc/base/gunit.h"
#include "webrtc/base/helpers.h"
#include "webrtc/base/ssladapter.h"

namespace rtc {

class RandomTest : public testing::Test {};

TEST_F(RandomTest, TestCreateRandomId) {
  CreateRandomId();
}

TEST_F(RandomTest, TestCreateRandomDouble) {
  for (int i = 0; i < 100; ++i) {
    double r = CreateRandomDouble();
    EXPECT_GE(r, 0.0);
    EXPECT_LT(r, 1.0);
  }
}

TEST_F(RandomTest, TestCreateNonZeroRandomId) {
  EXPECT_NE(0U, CreateRandomNonZeroId());
}

TEST_F(RandomTest, TestCreateRandomString) {
  std::string random = CreateRandomString(256);
  EXPECT_EQ(256U, random.size());
  std::string random2;
  EXPECT_TRUE(CreateRandomString(256, &random2));
  EXPECT_NE(random, random2);
  EXPECT_EQ(256U, random2.size());
}

TEST_F(RandomTest, TestCreateRandomUuid) {
  std::string random = CreateRandomUuid();
  EXPECT_EQ(36U, random.size());
}

TEST_F(RandomTest, TestCreateRandomForTest) {
  // Make sure we get the output we expect.
  SetRandomTestMode(true);
  EXPECT_EQ(2154761789U, CreateRandomId());
  EXPECT_EQ("h0ISP4S5SJKH/9EY", CreateRandomString(16));
  EXPECT_EQ("41706e92-cdd3-46d9-a22d-8ff1737ffb11", CreateRandomUuid());

  // Reset and make sure we get the same output.
  SetRandomTestMode(true);
  EXPECT_EQ(2154761789U, CreateRandomId());
  EXPECT_EQ("h0ISP4S5SJKH/9EY", CreateRandomString(16));
  EXPECT_EQ("41706e92-cdd3-46d9-a22d-8ff1737ffb11", CreateRandomUuid());

  // Test different character sets.
  SetRandomTestMode(true);
  std::string str;
  EXPECT_TRUE(CreateRandomString(16, "a", &str));
  EXPECT_EQ("aaaaaaaaaaaaaaaa", str);
  EXPECT_TRUE(CreateRandomString(16, "abc", &str));
  EXPECT_EQ("acbccaaaabbaacbb", str);

  // Turn off test mode for other tests.
  SetRandomTestMode(false);
}

}  // namespace rtc
