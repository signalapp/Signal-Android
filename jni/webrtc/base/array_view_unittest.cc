/*
 *  Copyright 2015 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <algorithm>
#include <string>
#include <vector>

#include "webrtc/base/array_view.h"
#include "webrtc/base/buffer.h"
#include "webrtc/base/checks.h"
#include "webrtc/base/gunit.h"

namespace rtc {

namespace {
template <typename T>
void Call(ArrayView<T>) {}
}  // namespace

TEST(ArrayViewTest, TestConstructFromPtrAndArray) {
  char arr[] = "Arrr!";
  const char carr[] = "Carrr!";
  Call<const char>(arr);
  Call<const char>(carr);
  Call<char>(arr);
  // Call<char>(carr);  // Compile error, because can't drop const.
  // Call<int>(arr);  // Compile error, because incompatible types.
  ArrayView<int*> x;
  EXPECT_EQ(0u, x.size());
  EXPECT_EQ(nullptr, x.data());
  ArrayView<char> y = arr;
  EXPECT_EQ(6u, y.size());
  EXPECT_EQ(arr, y.data());
  ArrayView<const char> z(arr + 1, 3);
  EXPECT_EQ(3u, z.size());
  EXPECT_EQ(arr + 1, z.data());
  ArrayView<const char> w(arr, 2);
  EXPECT_EQ(2u, w.size());
  EXPECT_EQ(arr, w.data());
  ArrayView<char> q(arr, 0);
  EXPECT_EQ(0u, q.size());
  EXPECT_EQ(nullptr, q.data());
#if RTC_DCHECK_IS_ON && GTEST_HAS_DEATH_TEST && !defined(WEBRTC_ANDROID)
  // DCHECK error (nullptr with nonzero size).
  EXPECT_DEATH(ArrayView<int>(static_cast<int*>(nullptr), 5), "");
#endif
  // These are compile errors, because incompatible types.
  // ArrayView<int> m = arr;
  // ArrayView<float> n(arr + 2, 2);
}

TEST(ArrayViewTest, TestCopyConstructor) {
  char arr[] = "Arrr!";
  ArrayView<char> x = arr;
  EXPECT_EQ(6u, x.size());
  EXPECT_EQ(arr, x.data());
  ArrayView<char> y = x;  // Copy non-const -> non-const.
  EXPECT_EQ(6u, y.size());
  EXPECT_EQ(arr, y.data());
  ArrayView<const char> z = x;  // Copy non-const -> const.
  EXPECT_EQ(6u, z.size());
  EXPECT_EQ(arr, z.data());
  ArrayView<const char> w = z;  // Copy const -> const.
  EXPECT_EQ(6u, w.size());
  EXPECT_EQ(arr, w.data());
  // ArrayView<char> v = z;  // Compile error, because can't drop const.
}

TEST(ArrayViewTest, TestCopyAssignment) {
  char arr[] = "Arrr!";
  ArrayView<char> x(arr);
  EXPECT_EQ(6u, x.size());
  EXPECT_EQ(arr, x.data());
  ArrayView<char> y;
  y = x;  // Copy non-const -> non-const.
  EXPECT_EQ(6u, y.size());
  EXPECT_EQ(arr, y.data());
  ArrayView<const char> z;
  z = x;  // Copy non-const -> const.
  EXPECT_EQ(6u, z.size());
  EXPECT_EQ(arr, z.data());
  ArrayView<const char> w;
  w = z;  // Copy const -> const.
  EXPECT_EQ(6u, w.size());
  EXPECT_EQ(arr, w.data());
  // ArrayView<char> v;
  // v = z;  // Compile error, because can't drop const.
}

TEST(ArrayViewTest, TestStdVector) {
  std::vector<int> v;
  v.push_back(3);
  v.push_back(11);
  Call<const int>(v);
  Call<int>(v);
  // Call<unsigned int>(v);  // Compile error, because incompatible types.
  ArrayView<int> x = v;
  EXPECT_EQ(2u, x.size());
  EXPECT_EQ(v.data(), x.data());
  ArrayView<const int> y;
  y = v;
  EXPECT_EQ(2u, y.size());
  EXPECT_EQ(v.data(), y.data());
  // ArrayView<double> d = v;  // Compile error, because incompatible types.
  const std::vector<int> cv;
  Call<const int>(cv);
  // Call<int>(cv);  // Compile error, because can't drop const.
  ArrayView<const int> z = cv;
  EXPECT_EQ(0u, z.size());
  EXPECT_EQ(nullptr, z.data());
  // ArrayView<int> w = cv;  // Compile error, because can't drop const.
}

TEST(ArrayViewTest, TestRtcBuffer) {
  rtc::Buffer b = "so buffer";
  Call<const uint8_t>(b);
  Call<uint8_t>(b);
  // Call<int8_t>(b);  // Compile error, because incompatible types.
  ArrayView<uint8_t> x = b;
  EXPECT_EQ(10u, x.size());
  EXPECT_EQ(b.data(), x.data());
  ArrayView<const uint8_t> y;
  y = b;
  EXPECT_EQ(10u, y.size());
  EXPECT_EQ(b.data(), y.data());
  // ArrayView<char> d = b;  // Compile error, because incompatible types.
  const rtc::Buffer cb = "very const";
  Call<const uint8_t>(cb);
  // Call<uint8_t>(cb);  // Compile error, because can't drop const.
  ArrayView<const uint8_t> z = cb;
  EXPECT_EQ(11u, z.size());
  EXPECT_EQ(cb.data(), z.data());
  // ArrayView<uint8_t> w = cb;  // Compile error, because can't drop const.
}

TEST(ArrayViewTest, TestSwap) {
  const char arr[] = "Arrr!";
  const char aye[] = "Aye, Cap'n!";
  ArrayView<const char> x(arr);
  EXPECT_EQ(6u, x.size());
  EXPECT_EQ(arr, x.data());
  ArrayView<const char> y(aye);
  EXPECT_EQ(12u, y.size());
  EXPECT_EQ(aye, y.data());
  using std::swap;
  swap(x, y);
  EXPECT_EQ(12u, x.size());
  EXPECT_EQ(aye, x.data());
  EXPECT_EQ(6u, y.size());
  EXPECT_EQ(arr, y.data());
  // ArrayView<char> z;
  // swap(x, z);  // Compile error, because can't drop const.
}

TEST(ArrayViewTest, TestIndexing) {
  char arr[] = "abcdefg";
  ArrayView<char> x(arr);
  const ArrayView<char> y(arr);
  ArrayView<const char> z(arr);
  EXPECT_EQ(8u, x.size());
  EXPECT_EQ(8u, y.size());
  EXPECT_EQ(8u, z.size());
  EXPECT_EQ('b', x[1]);
  EXPECT_EQ('c', y[2]);
  EXPECT_EQ('d', z[3]);
  x[3] = 'X';
  y[2] = 'Y';
  // z[1] = 'Z';  // Compile error, because z's element type is const char.
  EXPECT_EQ('b', x[1]);
  EXPECT_EQ('Y', y[2]);
  EXPECT_EQ('X', z[3]);
#if RTC_DCHECK_IS_ON && GTEST_HAS_DEATH_TEST && !defined(WEBRTC_ANDROID)
  EXPECT_DEATH(z[8], "");  // DCHECK error (index out of bounds).
#endif
}

TEST(ArrayViewTest, TestIterationEmpty) {
  ArrayView<std::vector<std::vector<std::vector<std::string>>>> av;
  EXPECT_FALSE(av.begin());
  EXPECT_FALSE(av.cbegin());
  EXPECT_FALSE(av.end());
  EXPECT_FALSE(av.cend());
  for (auto& e : av) {
    EXPECT_TRUE(false);
    EXPECT_EQ(42u, e.size());  // Dummy use of e to prevent unused var warning.
  }
}

TEST(ArrayViewTest, TestIteration) {
  char arr[] = "Arrr!";
  ArrayView<char> av(arr);
  EXPECT_EQ('A', *av.begin());
  EXPECT_EQ('A', *av.cbegin());
  EXPECT_EQ('\0', *(av.end() - 1));
  EXPECT_EQ('\0', *(av.cend() - 1));
  char i = 0;
  for (auto& e : av) {
    EXPECT_EQ(arr + i, &e);
    e = 's' + i;
    ++i;
  }
  i = 0;
  for (auto& e : ArrayView<const char>(av)) {
    EXPECT_EQ(arr + i, &e);
    // e = 'q' + i;  // Compile error, because e is a const char&.
    ++i;
  }
}

TEST(ArrayViewTest, TestEmpty) {
  EXPECT_TRUE(ArrayView<int>().empty());
  const int a[] = {1, 2, 3};
  EXPECT_FALSE(ArrayView<const int>(a).empty());
}

TEST(ArrayViewTest, TestCompare) {
  int a[] = {1, 2, 3};
  int b[] = {1, 2, 3};
  EXPECT_EQ(ArrayView<int>(a), ArrayView<int>(a));
  EXPECT_EQ(ArrayView<int>(), ArrayView<int>());
  EXPECT_NE(ArrayView<int>(a), ArrayView<int>(b));
  EXPECT_NE(ArrayView<int>(a), ArrayView<int>());
  EXPECT_NE(ArrayView<int>(a), ArrayView<int>(a, 2));
}

}  // namespace rtc
