/*
 *  Copyright (c) 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

// MSVC++ requires this to be set before any other includes to get M_PI.
#define _USE_MATH_DEFINES

#include "webrtc/modules/audio_processing/beamformer/array_util.h"

#include <math.h>
#include <vector>

#include "testing/gtest/include/gtest/gtest.h"

namespace webrtc {

bool operator==(const Point& lhs, const Point& rhs) {
  return lhs.x() == rhs.x() && lhs.y() == rhs.y() && lhs.z() == rhs.z();
}

TEST(ArrayUtilTest, PairDirection) {
  EXPECT_EQ(Point(1.f, 2.f, 3.f),
            PairDirection(Point(0.f, 0.f, 0.f), Point(1.f, 2.f, 3.f)));
  EXPECT_EQ(Point(-1.f, -2.f, -3.f),
            PairDirection(Point(1.f, 2.f, 3.f), Point(0.f, 0.f, 0.f)));
  EXPECT_EQ(Point(0.f, 0.f, 0.f),
            PairDirection(Point(1.f, 0.f, 0.f), Point(1.f, 0.f, 0.f)));
  EXPECT_EQ(Point(-1.f, 2.f, 0.f),
            PairDirection(Point(1.f, 0.f, 0.f), Point(0.f, 2.f, 0.f)));
  EXPECT_EQ(Point(-4.f, 4.f, -4.f),
            PairDirection(Point(1.f, -2.f, 3.f), Point(-3.f, 2.f, -1.f)));
}

TEST(ArrayUtilTest, DotProduct) {
  EXPECT_FLOAT_EQ(0.f, DotProduct(Point(0.f, 0.f, 0.f), Point(1.f, 2.f, 3.f)));
  EXPECT_FLOAT_EQ(0.f, DotProduct(Point(1.f, 0.f, 2.f), Point(0.f, 3.f, 0.f)));
  EXPECT_FLOAT_EQ(0.f, DotProduct(Point(1.f, 1.f, 0.f), Point(1.f, -1.f, 0.f)));
  EXPECT_FLOAT_EQ(2.f, DotProduct(Point(1.f, 0.f, 0.f), Point(2.f, 0.f, 0.f)));
  EXPECT_FLOAT_EQ(-6.f,
                  DotProduct(Point(-2.f, 0.f, 0.f), Point(3.f, 0.f, 0.f)));
  EXPECT_FLOAT_EQ(-10.f,
                  DotProduct(Point(1.f, -2.f, 3.f), Point(-3.f, 2.f, -1.f)));
}

TEST(ArrayUtilTest, CrossProduct) {
  EXPECT_EQ(Point(0.f, 0.f, 0.f),
            CrossProduct(Point(0.f, 0.f, 0.f), Point(1.f, 2.f, 3.f)));
  EXPECT_EQ(Point(0.f, 0.f, 1.f),
            CrossProduct(Point(1.f, 0.f, 0.f), Point(0.f, 1.f, 0.f)));
  EXPECT_EQ(Point(1.f, 0.f, 0.f),
            CrossProduct(Point(0.f, 1.f, 0.f), Point(0.f, 0.f, 1.f)));
  EXPECT_EQ(Point(0.f, -1.f, 0.f),
            CrossProduct(Point(1.f, 0.f, 0.f), Point(0.f, 0.f, 1.f)));
  EXPECT_EQ(Point(-4.f, -8.f, -4.f),
            CrossProduct(Point(1.f, -2.f, 3.f), Point(-3.f, 2.f, -1.f)));
}

TEST(ArrayUtilTest, AreParallel) {
  EXPECT_TRUE(AreParallel(Point(0.f, 0.f, 0.f), Point(1.f, 2.f, 3.f)));
  EXPECT_FALSE(AreParallel(Point(1.f, 0.f, 2.f), Point(0.f, 3.f, 0.f)));
  EXPECT_FALSE(AreParallel(Point(1.f, 2.f, 0.f), Point(1.f, -0.5f, 0.f)));
  EXPECT_FALSE(AreParallel(Point(1.f, -2.f, 3.f), Point(-3.f, 2.f, -1.f)));
  EXPECT_TRUE(AreParallel(Point(1.f, 0.f, 0.f), Point(2.f, 0.f, 0.f)));
  EXPECT_TRUE(AreParallel(Point(1.f, 2.f, 3.f), Point(-2.f, -4.f, -6.f)));
}

TEST(ArrayUtilTest, ArePerpendicular) {
  EXPECT_TRUE(ArePerpendicular(Point(0.f, 0.f, 0.f), Point(1.f, 2.f, 3.f)));
  EXPECT_TRUE(ArePerpendicular(Point(1.f, 0.f, 2.f), Point(0.f, 3.f, 0.f)));
  EXPECT_TRUE(ArePerpendicular(Point(1.f, 2.f, 0.f), Point(1.f, -0.5f, 0.f)));
  EXPECT_FALSE(ArePerpendicular(Point(1.f, -2.f, 3.f), Point(-3.f, 2.f, -1.f)));
  EXPECT_FALSE(ArePerpendicular(Point(1.f, 0.f, 0.f), Point(2.f, 0.f, 0.f)));
  EXPECT_FALSE(ArePerpendicular(Point(1.f, 2.f, 3.f), Point(-2.f, -4.f, -6.f)));
}

TEST(ArrayUtilTest, GetMinimumSpacing) {
  std::vector<Point> geometry;
  geometry.push_back(Point(0.f, 0.f, 0.f));
  geometry.push_back(Point(0.1f, 0.f, 0.f));
  EXPECT_FLOAT_EQ(0.1f, GetMinimumSpacing(geometry));
  geometry.push_back(Point(0.f, 0.05f, 0.f));
  EXPECT_FLOAT_EQ(0.05f, GetMinimumSpacing(geometry));
  geometry.push_back(Point(0.f, 0.f, 0.02f));
  EXPECT_FLOAT_EQ(0.02f, GetMinimumSpacing(geometry));
  geometry.push_back(Point(-0.003f, -0.004f, 0.02f));
  EXPECT_FLOAT_EQ(0.005f, GetMinimumSpacing(geometry));
}

TEST(ArrayUtilTest, GetDirectionIfLinear) {
  std::vector<Point> geometry;
  geometry.push_back(Point(0.f, 0.f, 0.f));
  geometry.push_back(Point(0.1f, 0.f, 0.f));
  EXPECT_TRUE(
      AreParallel(Point(1.f, 0.f, 0.f), *GetDirectionIfLinear(geometry)));
  geometry.push_back(Point(0.15f, 0.f, 0.f));
  EXPECT_TRUE(
      AreParallel(Point(1.f, 0.f, 0.f), *GetDirectionIfLinear(geometry)));
  geometry.push_back(Point(-0.2f, 0.f, 0.f));
  EXPECT_TRUE(
      AreParallel(Point(1.f, 0.f, 0.f), *GetDirectionIfLinear(geometry)));
  geometry.push_back(Point(0.05f, 0.f, 0.f));
  EXPECT_TRUE(
      AreParallel(Point(1.f, 0.f, 0.f), *GetDirectionIfLinear(geometry)));
  geometry.push_back(Point(0.1f, 0.1f, 0.f));
  EXPECT_FALSE(GetDirectionIfLinear(geometry));
  geometry.push_back(Point(0.f, 0.f, -0.2f));
  EXPECT_FALSE(GetDirectionIfLinear(geometry));
}

TEST(ArrayUtilTest, GetNormalIfPlanar) {
  std::vector<Point> geometry;
  geometry.push_back(Point(0.f, 0.f, 0.f));
  geometry.push_back(Point(0.1f, 0.f, 0.f));
  EXPECT_FALSE(GetNormalIfPlanar(geometry));
  geometry.push_back(Point(0.15f, 0.f, 0.f));
  EXPECT_FALSE(GetNormalIfPlanar(geometry));
  geometry.push_back(Point(0.1f, 0.2f, 0.f));
  EXPECT_TRUE(AreParallel(Point(0.f, 0.f, 1.f), *GetNormalIfPlanar(geometry)));
  geometry.push_back(Point(0.f, -0.15f, 0.f));
  EXPECT_TRUE(AreParallel(Point(0.f, 0.f, 1.f), *GetNormalIfPlanar(geometry)));
  geometry.push_back(Point(0.f, 0.1f, 0.2f));
  EXPECT_FALSE(GetNormalIfPlanar(geometry));
  geometry.push_back(Point(0.f, 0.f, -0.15f));
  EXPECT_FALSE(GetNormalIfPlanar(geometry));
  geometry.push_back(Point(0.1f, 0.2f, 0.f));
  EXPECT_FALSE(GetNormalIfPlanar(geometry));
}

TEST(ArrayUtilTest, GetArrayNormalIfExists) {
  std::vector<Point> geometry;
  geometry.push_back(Point(0.f, 0.f, 0.f));
  geometry.push_back(Point(0.1f, 0.f, 0.f));
  EXPECT_TRUE(
      AreParallel(Point(0.f, 1.f, 0.f), *GetArrayNormalIfExists(geometry)));
  geometry.push_back(Point(0.15f, 0.f, 0.f));
  EXPECT_TRUE(
      AreParallel(Point(0.f, 1.f, 0.f), *GetArrayNormalIfExists(geometry)));
  geometry.push_back(Point(0.1f, 0.f, 0.2f));
  EXPECT_TRUE(
      AreParallel(Point(0.f, 1.f, 0.f), *GetArrayNormalIfExists(geometry)));
  geometry.push_back(Point(0.f, 0.f, -0.1f));
  EXPECT_TRUE(
      AreParallel(Point(0.f, 1.f, 0.f), *GetArrayNormalIfExists(geometry)));
  geometry.push_back(Point(0.1f, 0.2f, 0.3f));
  EXPECT_FALSE(GetArrayNormalIfExists(geometry));
  geometry.push_back(Point(0.f, -0.1f, 0.f));
  EXPECT_FALSE(GetArrayNormalIfExists(geometry));
  geometry.push_back(Point(1.f, 0.f, -0.2f));
  EXPECT_FALSE(GetArrayNormalIfExists(geometry));
}

TEST(ArrayUtilTest, DegreesToRadians) {
  EXPECT_FLOAT_EQ(0.f, DegreesToRadians(0.f));
  EXPECT_FLOAT_EQ(static_cast<float>(M_PI) / 6.f, DegreesToRadians(30.f));
  EXPECT_FLOAT_EQ(-static_cast<float>(M_PI) / 4.f, DegreesToRadians(-45.f));
  EXPECT_FLOAT_EQ(static_cast<float>(M_PI) / 3.f, DegreesToRadians(60.f));
  EXPECT_FLOAT_EQ(-static_cast<float>(M_PI) / 2.f, DegreesToRadians(-90.f));
  EXPECT_FLOAT_EQ(2.f * static_cast<float>(M_PI) / 3.f,
                  DegreesToRadians(120.f));
  EXPECT_FLOAT_EQ(-3.f * static_cast<float>(M_PI) / 4.f,
                  DegreesToRadians(-135.f));
  EXPECT_FLOAT_EQ(5.f * static_cast<float>(M_PI) / 6.f,
                  DegreesToRadians(150.f));
  EXPECT_FLOAT_EQ(-static_cast<float>(M_PI), DegreesToRadians(-180.f));
}

TEST(ArrayUtilTest, RadiansToDegrees) {
  EXPECT_FLOAT_EQ(0.f, RadiansToDegrees(0.f));
  EXPECT_FLOAT_EQ(30.f, RadiansToDegrees(M_PI / 6.f));
  EXPECT_FLOAT_EQ(-45.f, RadiansToDegrees(-M_PI / 4.f));
  EXPECT_FLOAT_EQ(60.f, RadiansToDegrees(M_PI / 3.f));
  EXPECT_FLOAT_EQ(-90.f, RadiansToDegrees(-M_PI / 2.f));
  EXPECT_FLOAT_EQ(120.f, RadiansToDegrees(2.f * M_PI / 3.f));
  EXPECT_FLOAT_EQ(-135.f, RadiansToDegrees(-3.f * M_PI / 4.f));
  EXPECT_FLOAT_EQ(150.f, RadiansToDegrees(5.f * M_PI / 6.f));
  EXPECT_FLOAT_EQ(-180.f, RadiansToDegrees(-M_PI));
}

}  // namespace webrtc
