/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/modules/audio_processing/transient/wpd_node.h"

#include <string.h>

#include "testing/gtest/include/gtest/gtest.h"

namespace webrtc {

static const size_t kDataLength = 5;
static const float kTolerance = 0.0001f;

static const size_t kParentDataLength = kDataLength * 2;
static const float kParentData[kParentDataLength] =
    {1.f, 2.f, 3.f, 4.f, 5.f, 6.f, 7.f, 8.f, 9.f, 10.f};

static const float kCoefficients[] = {0.2f, -0.3f, 0.5f, -0.7f, 0.11f};
static const size_t kCoefficientsLength = sizeof(kCoefficients) /
                                       sizeof(kCoefficients[0]);

TEST(WPDNodeTest, Accessors) {
  WPDNode node(kDataLength, kCoefficients, kCoefficientsLength);
  EXPECT_EQ(0, node.set_data(kParentData, kDataLength));
  EXPECT_EQ(0, memcmp(node.data(),
                      kParentData,
                      kDataLength * sizeof(node.data()[0])));
}

TEST(WPDNodeTest, UpdateThatOnlyDecimates) {
  const float kIndentyCoefficient = 1.f;
  WPDNode node(kDataLength, &kIndentyCoefficient, 1);
  EXPECT_EQ(0, node.Update(kParentData, kParentDataLength));
  for (size_t i = 0; i < kDataLength; ++i) {
    EXPECT_FLOAT_EQ(kParentData[i * 2 + 1], node.data()[i]);
  }
}

TEST(WPDNodeTest, UpdateWithArbitraryDataAndArbitraryFilter) {
  WPDNode node(kDataLength, kCoefficients, kCoefficientsLength);
  EXPECT_EQ(0, node.Update(kParentData, kParentDataLength));
  EXPECT_NEAR(0.1f, node.data()[0], kTolerance);
  EXPECT_NEAR(0.2f, node.data()[1], kTolerance);
  EXPECT_NEAR(0.18f, node.data()[2], kTolerance);
  EXPECT_NEAR(0.56f, node.data()[3], kTolerance);
  EXPECT_NEAR(0.94f, node.data()[4], kTolerance);
}

TEST(WPDNodeTest, ExpectedErrorReturnValue) {
  WPDNode node(kDataLength, kCoefficients, kCoefficientsLength);
  EXPECT_EQ(-1, node.Update(kParentData, kParentDataLength - 1));
  EXPECT_EQ(-1, node.Update(NULL, kParentDataLength));
  EXPECT_EQ(-1, node.set_data(kParentData, kDataLength - 1));
  EXPECT_EQ(-1, node.set_data(NULL, kDataLength));
}

}  // namespace webrtc
