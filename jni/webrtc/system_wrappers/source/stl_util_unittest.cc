/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

// Borrowed from Chromium's src/base/stl_util_unittest.cc
#include "webrtc/system_wrappers/include/stl_util.h"

#include <set>

#include "testing/gtest/include/gtest/gtest.h"

namespace {

// Used as test case to ensure the various base::STLXxx functions don't require
// more than operators "<" and "==" on values stored in containers.
class ComparableValue {
 public:
  explicit ComparableValue(int value) : value_(value) {}

  bool operator==(const ComparableValue& rhs) const {
    return value_ == rhs.value_;
  }

  bool operator<(const ComparableValue& rhs) const {
    return value_ < rhs.value_;
  }

 private:
  int value_;
};

}  // namespace

namespace webrtc {
namespace {

TEST(STLUtilTest, STLIsSorted) {
  {
    std::set<int> set;
    set.insert(24);
    set.insert(1);
    set.insert(12);
    EXPECT_TRUE(STLIsSorted(set));
  }

  {
    std::set<ComparableValue> set;
    set.insert(ComparableValue(24));
    set.insert(ComparableValue(1));
    set.insert(ComparableValue(12));
    EXPECT_TRUE(STLIsSorted(set));
  }

  {
    std::vector<int> vector;
    vector.push_back(1);
    vector.push_back(1);
    vector.push_back(4);
    vector.push_back(64);
    vector.push_back(12432);
    EXPECT_TRUE(STLIsSorted(vector));
    vector.back() = 1;
    EXPECT_FALSE(STLIsSorted(vector));
  }
}

TEST(STLUtilTest, STLSetDifference) {
  std::set<int> a1;
  a1.insert(1);
  a1.insert(2);
  a1.insert(3);
  a1.insert(4);

  std::set<int> a2;
  a2.insert(3);
  a2.insert(4);
  a2.insert(5);
  a2.insert(6);
  a2.insert(7);

  {
    std::set<int> difference;
    difference.insert(1);
    difference.insert(2);
    EXPECT_EQ(difference, STLSetDifference<std::set<int> >(a1, a2));
  }

  {
    std::set<int> difference;
    difference.insert(5);
    difference.insert(6);
    difference.insert(7);
    EXPECT_EQ(difference, STLSetDifference<std::set<int> >(a2, a1));
  }

  {
    std::vector<int> difference;
    difference.push_back(1);
    difference.push_back(2);
    EXPECT_EQ(difference, STLSetDifference<std::vector<int> >(a1, a2));
  }

  {
    std::vector<int> difference;
    difference.push_back(5);
    difference.push_back(6);
    difference.push_back(7);
    EXPECT_EQ(difference, STLSetDifference<std::vector<int> >(a2, a1));
  }
}

TEST(STLUtilTest, STLSetUnion) {
  std::set<int> a1;
  a1.insert(1);
  a1.insert(2);
  a1.insert(3);
  a1.insert(4);

  std::set<int> a2;
  a2.insert(3);
  a2.insert(4);
  a2.insert(5);
  a2.insert(6);
  a2.insert(7);

  {
    std::set<int> result;
    result.insert(1);
    result.insert(2);
    result.insert(3);
    result.insert(4);
    result.insert(5);
    result.insert(6);
    result.insert(7);
    EXPECT_EQ(result, STLSetUnion<std::set<int> >(a1, a2));
  }

  {
    std::set<int> result;
    result.insert(1);
    result.insert(2);
    result.insert(3);
    result.insert(4);
    result.insert(5);
    result.insert(6);
    result.insert(7);
    EXPECT_EQ(result, STLSetUnion<std::set<int> >(a2, a1));
  }

  {
    std::vector<int> result;
    result.push_back(1);
    result.push_back(2);
    result.push_back(3);
    result.push_back(4);
    result.push_back(5);
    result.push_back(6);
    result.push_back(7);
    EXPECT_EQ(result, STLSetUnion<std::vector<int> >(a1, a2));
  }

  {
    std::vector<int> result;
    result.push_back(1);
    result.push_back(2);
    result.push_back(3);
    result.push_back(4);
    result.push_back(5);
    result.push_back(6);
    result.push_back(7);
    EXPECT_EQ(result, STLSetUnion<std::vector<int> >(a2, a1));
  }
}

TEST(STLUtilTest, STLSetIntersection) {
  std::set<int> a1;
  a1.insert(1);
  a1.insert(2);
  a1.insert(3);
  a1.insert(4);

  std::set<int> a2;
  a2.insert(3);
  a2.insert(4);
  a2.insert(5);
  a2.insert(6);
  a2.insert(7);

  {
    std::set<int> result;
    result.insert(3);
    result.insert(4);
    EXPECT_EQ(result, STLSetIntersection<std::set<int> >(a1, a2));
  }

  {
    std::set<int> result;
    result.insert(3);
    result.insert(4);
    EXPECT_EQ(result, STLSetIntersection<std::set<int> >(a2, a1));
  }

  {
    std::vector<int> result;
    result.push_back(3);
    result.push_back(4);
    EXPECT_EQ(result, STLSetIntersection<std::vector<int> >(a1, a2));
  }

  {
    std::vector<int> result;
    result.push_back(3);
    result.push_back(4);
    EXPECT_EQ(result, STLSetIntersection<std::vector<int> >(a2, a1));
  }
}

TEST(STLUtilTest, STLIncludes) {
  std::set<int> a1;
  a1.insert(1);
  a1.insert(2);
  a1.insert(3);
  a1.insert(4);

  std::set<int> a2;
  a2.insert(3);
  a2.insert(4);

  std::set<int> a3;
  a3.insert(3);
  a3.insert(4);
  a3.insert(5);

  EXPECT_TRUE(STLIncludes<std::set<int> >(a1, a2));
  EXPECT_FALSE(STLIncludes<std::set<int> >(a1, a3));
  EXPECT_FALSE(STLIncludes<std::set<int> >(a2, a1));
  EXPECT_FALSE(STLIncludes<std::set<int> >(a2, a3));
  EXPECT_FALSE(STLIncludes<std::set<int> >(a3, a1));
  EXPECT_TRUE(STLIncludes<std::set<int> >(a3, a2));
}

}  // namespace
}  // namespace webrtc

