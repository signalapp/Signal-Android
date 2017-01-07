/*
 *  Copyright (c) 2011 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/system_wrappers/include/data_log.h"

#include <string>

#include "testing/gtest/include/gtest/gtest.h"

using ::webrtc::DataLog;

TEST(TestDataLog, IntContainers) {
  int c = 5;
  webrtc::ValueContainer<int> v1(c);
  c = 10;
  webrtc::ValueContainer<int> v2(c);
  std::string s1, s2;
  v1.ToString(&s1);
  v2.ToString(&s2);
  ASSERT_EQ(s1, "5,");
  ASSERT_EQ(s2, "10,");
  v1 = v2;
  v1.ToString(&s1);
  ASSERT_EQ(s1, s2);
}

TEST(TestDataLog, DoubleContainers) {
  double c = 3.5;
  webrtc::ValueContainer<double> v1(c);
  c = 10.3;
  webrtc::ValueContainer<double> v2(c);
  std::string s1, s2;
  v1.ToString(&s1);
  v2.ToString(&s2);
  ASSERT_EQ(s1, "3.5,");
  ASSERT_EQ(s2, "10.3,");
  v1 = v2;
  v1.ToString(&s1);
  ASSERT_EQ(s1, s2);
}

TEST(TestDataLog, MultiValueContainers) {
  int a[3] = {1, 2, 3};
  int b[3] = {4, 5, 6};
  webrtc::MultiValueContainer<int> m1(a, 3);
  webrtc::MultiValueContainer<int> m2(b, 3);
  webrtc::MultiValueContainer<int> m3(a, 3);
  std::string s1, s2, s3;
  m1.ToString(&s1);
  m2.ToString(&s2);
  ASSERT_EQ(s1, "1,2,3,");
  ASSERT_EQ(s2, "4,5,6,");
  m1 = m2;
  m1.ToString(&s1);
  ASSERT_EQ(s1, s2);
  m3.ToString(&s3);
  ASSERT_EQ(s3, "1,2,3,");
}
