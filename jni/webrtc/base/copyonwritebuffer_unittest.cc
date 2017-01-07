/*
 *  Copyright 2016 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/base/copyonwritebuffer.h"
#include "webrtc/base/gunit.h"

namespace rtc {

namespace {

// clang-format off
const uint8_t kTestData[] = {0x0, 0x1, 0x2, 0x3, 0x4, 0x5, 0x6, 0x7,
                             0x8, 0x9, 0xa, 0xb, 0xc, 0xd, 0xe, 0xf};
// clang-format on

}  // namespace

void EnsureBuffersShareData(const CopyOnWriteBuffer& buf1,
                            const CopyOnWriteBuffer& buf2) {
  // Data is shared between buffers.
  EXPECT_EQ(buf1.size(), buf2.size());
  EXPECT_EQ(buf1.capacity(), buf2.capacity());
  const uint8_t* data1 = buf1.data();
  const uint8_t* data2 = buf2.data();
  EXPECT_EQ(data1, data2);
  EXPECT_EQ(buf1, buf2);
}

void EnsureBuffersDontShareData(const CopyOnWriteBuffer& buf1,
                                const CopyOnWriteBuffer& buf2) {
  // Data is not shared between buffers.
  const uint8_t* data1 = buf1.cdata();
  const uint8_t* data2 = buf2.cdata();
  EXPECT_NE(data1, data2);
}

TEST(CopyOnWriteBufferTest, TestCreateEmptyData) {
  CopyOnWriteBuffer buf(static_cast<const uint8_t*>(nullptr), 0);
  EXPECT_EQ(buf.size(), 0u);
  EXPECT_EQ(buf.capacity(), 0u);
  EXPECT_EQ(buf.data(), nullptr);
}

TEST(CopyOnWriteBufferTest, TestMoveConstruct) {
  CopyOnWriteBuffer buf1(kTestData, 3, 10);
  size_t buf1_size = buf1.size();
  size_t buf1_capacity = buf1.capacity();
  const uint8_t* buf1_data = buf1.cdata();

  CopyOnWriteBuffer buf2(std::move(buf1));
  EXPECT_EQ(buf1.size(), 0u);
  EXPECT_EQ(buf1.capacity(), 0u);
  EXPECT_EQ(buf1.data(), nullptr);
  EXPECT_EQ(buf2.size(), buf1_size);
  EXPECT_EQ(buf2.capacity(), buf1_capacity);
  EXPECT_EQ(buf2.data(), buf1_data);
}

TEST(CopyOnWriteBufferTest, TestMoveAssign) {
  CopyOnWriteBuffer buf1(kTestData, 3, 10);
  size_t buf1_size = buf1.size();
  size_t buf1_capacity = buf1.capacity();
  const uint8_t* buf1_data = buf1.cdata();

  CopyOnWriteBuffer buf2;
  buf2 = std::move(buf1);
  EXPECT_EQ(buf1.size(), 0u);
  EXPECT_EQ(buf1.capacity(), 0u);
  EXPECT_EQ(buf1.data(), nullptr);
  EXPECT_EQ(buf2.size(), buf1_size);
  EXPECT_EQ(buf2.capacity(), buf1_capacity);
  EXPECT_EQ(buf2.data(), buf1_data);
}

TEST(CopyOnWriteBufferTest, TestSwap) {
  CopyOnWriteBuffer buf1(kTestData, 3, 10);
  size_t buf1_size = buf1.size();
  size_t buf1_capacity = buf1.capacity();
  const uint8_t* buf1_data = buf1.cdata();

  CopyOnWriteBuffer buf2(kTestData, 6, 20);
  size_t buf2_size = buf2.size();
  size_t buf2_capacity = buf2.capacity();
  const uint8_t* buf2_data = buf2.cdata();

  std::swap(buf1, buf2);
  EXPECT_EQ(buf1.size(), buf2_size);
  EXPECT_EQ(buf1.capacity(), buf2_capacity);
  EXPECT_EQ(buf1.data(), buf2_data);
  EXPECT_EQ(buf2.size(), buf1_size);
  EXPECT_EQ(buf2.capacity(), buf1_capacity);
  EXPECT_EQ(buf2.data(), buf1_data);
}

TEST(CopyOnWriteBufferTest, TestAppendData) {
  CopyOnWriteBuffer buf1(kTestData, 3, 10);
  CopyOnWriteBuffer buf2(buf1);

  EnsureBuffersShareData(buf1, buf2);

  // AppendData copies the underlying buffer.
  buf2.AppendData("foo");
  EXPECT_EQ(buf2.size(), buf1.size() + 4);  // "foo" + trailing 0x00
  EXPECT_EQ(buf2.capacity(), buf1.capacity());
  EXPECT_NE(buf2.data(), buf1.data());

  EXPECT_EQ(buf1, CopyOnWriteBuffer(kTestData, 3));
  const int8_t exp[] = {0x0, 0x1, 0x2, 'f', 'o', 'o', 0x0};
  EXPECT_EQ(buf2, CopyOnWriteBuffer(exp));
}

TEST(CopyOnWriteBufferTest, TestSetData) {
  CopyOnWriteBuffer buf1(kTestData, 3, 10);
  CopyOnWriteBuffer buf2;

  buf2.SetData(buf1);
  // buf2 shares the same data as buf1 now.
  EnsureBuffersShareData(buf1, buf2);

  CopyOnWriteBuffer buf3(buf1);
  // buf3 is re-allocated with new data, existing buffers are not modified.
  buf3.SetData("foo");
  EXPECT_EQ(buf1, CopyOnWriteBuffer(kTestData, 3));
  EnsureBuffersShareData(buf1, buf2);
  EnsureBuffersDontShareData(buf1, buf3);
  const int8_t exp[] = {'f', 'o', 'o', 0x0};
  EXPECT_EQ(buf3, CopyOnWriteBuffer(exp));

  buf2.SetData(static_cast<const uint8_t*>(nullptr), 0u);
  EnsureBuffersDontShareData(buf1, buf2);
  EXPECT_EQ(buf1.size(), 3u);
  EXPECT_EQ(buf1.capacity(), 10u);
  EXPECT_EQ(buf2.size(), 0u);
  EXPECT_EQ(buf2.capacity(), 0u);
}

TEST(CopyOnWriteBufferTest, TestSetDataEmpty) {
  CopyOnWriteBuffer buf;
  buf.SetData(static_cast<const uint8_t*>(nullptr), 0u);
  EXPECT_EQ(buf.size(), 0u);
  EXPECT_EQ(buf.capacity(), 0u);
  EXPECT_EQ(buf.data(), nullptr);
}

TEST(CopyOnWriteBufferTest, TestEnsureCapacity) {
  CopyOnWriteBuffer buf1(kTestData, 3, 10);
  CopyOnWriteBuffer buf2(buf1);

  // Smaller than existing capacity -> no change and still same contents.
  buf2.EnsureCapacity(8);
  EnsureBuffersShareData(buf1, buf2);
  EXPECT_EQ(buf1.size(), 3u);
  EXPECT_EQ(buf1.capacity(), 10u);
  EXPECT_EQ(buf2.size(), 3u);
  EXPECT_EQ(buf2.capacity(), 10u);

  // Lager than existing capacity -> data is cloned.
  buf2.EnsureCapacity(16);
  EnsureBuffersDontShareData(buf1, buf2);
  EXPECT_EQ(buf1.size(), 3u);
  EXPECT_EQ(buf1.capacity(), 10u);
  EXPECT_EQ(buf2.size(), 3u);
  EXPECT_EQ(buf2.capacity(), 16u);
  // The size and contents are still the same.
  EXPECT_EQ(buf1, buf2);
}

TEST(CopyOnWriteBufferTest, TestSetSize) {
  CopyOnWriteBuffer buf1(kTestData, 3, 10);
  CopyOnWriteBuffer buf2(buf1);

  buf2.SetSize(16);
  EnsureBuffersDontShareData(buf1, buf2);
  EXPECT_EQ(buf1.size(), 3u);
  EXPECT_EQ(buf1.capacity(), 10u);
  EXPECT_EQ(buf2.size(), 16u);
  EXPECT_EQ(buf2.capacity(), 16u);
  // The contents got cloned.
  EXPECT_EQ(0, memcmp(buf2.data(), kTestData, 3));
}

TEST(CopyOnWriteBufferTest, TestClear) {
  CopyOnWriteBuffer buf1(kTestData, 3, 10);
  CopyOnWriteBuffer buf2(buf1);

  buf2.Clear();
  EnsureBuffersDontShareData(buf1, buf2);
  EXPECT_EQ(buf1.size(), 3u);
  EXPECT_EQ(buf1.capacity(), 10u);
  EXPECT_EQ(0, memcmp(buf1.data(), kTestData, 3));
  EXPECT_EQ(buf2.size(), 0u);
  EXPECT_EQ(buf2.capacity(), 0u);
}

TEST(CopyOnWriteBufferTest, TestConstDataAccessor) {
  CopyOnWriteBuffer buf1(kTestData, 3, 10);
  CopyOnWriteBuffer buf2(buf1);

  // .cdata() doesn't clone data.
  const uint8_t* cdata1 = buf1.cdata();
  const uint8_t* cdata2 = buf2.cdata();
  EXPECT_EQ(cdata1, cdata2);

  // Non-const .data() clones data if shared.
  const uint8_t* data1 = buf1.data();
  const uint8_t* data2 = buf2.data();
  EXPECT_NE(data1, data2);
  // buf1 was cloned above.
  EXPECT_NE(data1, cdata1);
  // Therefore buf2 was no longer sharing data and was not cloned.
  EXPECT_EQ(data2, cdata1);
}

TEST(CopyOnWriteBufferTest, TestBacketRead) {
  CopyOnWriteBuffer buf1(kTestData, 3, 10);
  CopyOnWriteBuffer buf2(buf1);

  EnsureBuffersShareData(buf1, buf2);
  // Non-const reads clone the data if shared.
  for (size_t i = 0; i != 3u; ++i) {
    EXPECT_EQ(buf1[i], kTestData[i]);
  }
  EnsureBuffersDontShareData(buf1, buf2);
}

TEST(CopyOnWriteBufferTest, TestBacketReadConst) {
  CopyOnWriteBuffer buf1(kTestData, 3, 10);
  CopyOnWriteBuffer buf2(buf1);

  EnsureBuffersShareData(buf1, buf2);
  const CopyOnWriteBuffer& cbuf1 = buf1;
  for (size_t i = 0; i != 3u; ++i) {
    EXPECT_EQ(cbuf1[i], kTestData[i]);
  }
  EnsureBuffersShareData(buf1, buf2);
}

TEST(CopyOnWriteBufferTest, TestBacketWrite) {
  CopyOnWriteBuffer buf1(kTestData, 3, 10);
  CopyOnWriteBuffer buf2(buf1);

  EnsureBuffersShareData(buf1, buf2);
  for (size_t i = 0; i != 3u; ++i) {
    buf1[i] = kTestData[i] + 1;
  }
  EXPECT_EQ(buf1.size(), 3u);
  EXPECT_EQ(buf1.capacity(), 10u);
  EXPECT_EQ(buf2.size(), 3u);
  EXPECT_EQ(buf2.capacity(), 10u);
  EXPECT_EQ(0, memcmp(buf2.cdata(), kTestData, 3));
}

}  // namespace rtc
