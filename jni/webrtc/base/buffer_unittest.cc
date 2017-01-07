/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/base/buffer.h"
#include "webrtc/base/gunit.h"

#include <type_traits>
#include <utility>

namespace rtc {

namespace {

// clang-format off
const uint8_t kTestData[] = {0x0, 0x1, 0x2, 0x3, 0x4, 0x5, 0x6, 0x7,
                             0x8, 0x9, 0xa, 0xb, 0xc, 0xd, 0xe, 0xf};
// clang-format on

void TestBuf(const Buffer& b1, size_t size, size_t capacity) {
  EXPECT_EQ(b1.size(), size);
  EXPECT_EQ(b1.capacity(), capacity);
}

}  // namespace

TEST(BufferTest, TestConstructEmpty) {
  TestBuf(Buffer(), 0, 0);
  TestBuf(Buffer(Buffer()), 0, 0);
  TestBuf(Buffer(0), 0, 0);

  // We can't use a literal 0 for the first argument, because C++ will allow
  // that to be considered a null pointer, which makes the call ambiguous.
  TestBuf(Buffer(0 + 0, 10), 0, 10);

  TestBuf(Buffer(kTestData, 0), 0, 0);
  TestBuf(Buffer(kTestData, 0, 20), 0, 20);
}

TEST(BufferTest, TestConstructData) {
  Buffer buf(kTestData, 7);
  EXPECT_EQ(buf.size(), 7u);
  EXPECT_EQ(buf.capacity(), 7u);
  EXPECT_EQ(0, memcmp(buf.data(), kTestData, 7));
}

TEST(BufferTest, TestConstructDataWithCapacity) {
  Buffer buf(kTestData, 7, 14);
  EXPECT_EQ(buf.size(), 7u);
  EXPECT_EQ(buf.capacity(), 14u);
  EXPECT_EQ(0, memcmp(buf.data(), kTestData, 7));
}

TEST(BufferTest, TestConstructArray) {
  Buffer buf(kTestData);
  EXPECT_EQ(buf.size(), 16u);
  EXPECT_EQ(buf.capacity(), 16u);
  EXPECT_EQ(0, memcmp(buf.data(), kTestData, 16));
}

TEST(BufferTest, TestSetData) {
  Buffer buf(kTestData + 4, 7);
  buf.SetData(kTestData, 9);
  EXPECT_EQ(buf.size(), 9u);
  EXPECT_EQ(buf.capacity(), 7u * 3 / 2);
  EXPECT_EQ(0, memcmp(buf.data(), kTestData, 9));
}

TEST(BufferTest, TestAppendData) {
  Buffer buf(kTestData + 4, 3);
  buf.AppendData(kTestData + 10, 2);
  const int8_t exp[] = {0x4, 0x5, 0x6, 0xa, 0xb};
  EXPECT_EQ(buf, Buffer(exp));
}

TEST(BufferTest, TestSetSizeSmaller) {
  Buffer buf;
  buf.SetData(kTestData, 15);
  buf.SetSize(10);
  EXPECT_EQ(buf.size(), 10u);
  EXPECT_EQ(buf.capacity(), 15u);  // Hasn't shrunk.
  EXPECT_EQ(buf, Buffer(kTestData, 10));
}

TEST(BufferTest, TestSetSizeLarger) {
  Buffer buf;
  buf.SetData(kTestData, 15);
  EXPECT_EQ(buf.size(), 15u);
  EXPECT_EQ(buf.capacity(), 15u);
  buf.SetSize(20);
  EXPECT_EQ(buf.size(), 20u);
  EXPECT_EQ(buf.capacity(), 15u * 3 / 2);  // Has grown.
  EXPECT_EQ(0, memcmp(buf.data(), kTestData, 15));
}

TEST(BufferTest, TestEnsureCapacitySmaller) {
  Buffer buf(kTestData);
  const char* data = buf.data<char>();
  buf.EnsureCapacity(4);
  EXPECT_EQ(buf.capacity(), 16u);     // Hasn't shrunk.
  EXPECT_EQ(buf.data<char>(), data);  // No reallocation.
  EXPECT_EQ(buf, Buffer(kTestData));
}

TEST(BufferTest, TestEnsureCapacityLarger) {
  Buffer buf(kTestData, 5);
  buf.EnsureCapacity(10);
  const int8_t* data = buf.data<int8_t>();
  EXPECT_EQ(buf.capacity(), 10u);
  buf.AppendData(kTestData + 5, 5);
  EXPECT_EQ(buf.data<int8_t>(), data);  // No reallocation.
  EXPECT_EQ(buf, Buffer(kTestData, 10));
}

TEST(BufferTest, TestMoveConstruct) {
  Buffer buf1(kTestData, 3, 40);
  const uint8_t* data = buf1.data();
  Buffer buf2(std::move(buf1));
  EXPECT_EQ(buf2.size(), 3u);
  EXPECT_EQ(buf2.capacity(), 40u);
  EXPECT_EQ(buf2.data(), data);
  buf1.Clear();
  EXPECT_EQ(buf1.size(), 0u);
  EXPECT_EQ(buf1.capacity(), 0u);
  EXPECT_EQ(buf1.data(), nullptr);
}

TEST(BufferTest, TestMoveAssign) {
  Buffer buf1(kTestData, 3, 40);
  const uint8_t* data = buf1.data();
  Buffer buf2(kTestData);
  buf2 = std::move(buf1);
  EXPECT_EQ(buf2.size(), 3u);
  EXPECT_EQ(buf2.capacity(), 40u);
  EXPECT_EQ(buf2.data(), data);
  buf1.Clear();
  EXPECT_EQ(buf1.size(), 0u);
  EXPECT_EQ(buf1.capacity(), 0u);
  EXPECT_EQ(buf1.data(), nullptr);
}

TEST(BufferTest, TestSwap) {
  Buffer buf1(kTestData, 3);
  Buffer buf2(kTestData, 6, 40);
  uint8_t* data1 = buf1.data();
  uint8_t* data2 = buf2.data();
  using std::swap;
  swap(buf1, buf2);
  EXPECT_EQ(buf1.size(), 6u);
  EXPECT_EQ(buf1.capacity(), 40u);
  EXPECT_EQ(buf1.data(), data2);
  EXPECT_EQ(buf2.size(), 3u);
  EXPECT_EQ(buf2.capacity(), 3u);
  EXPECT_EQ(buf2.data(), data1);
}

TEST(BufferTest, TestClear) {
  Buffer buf;
  buf.SetData(kTestData, 15);
  EXPECT_EQ(buf.size(), 15u);
  EXPECT_EQ(buf.capacity(), 15u);
  const char *data = buf.data<char>();
  buf.Clear();
  EXPECT_EQ(buf.size(), 0u);
  EXPECT_EQ(buf.capacity(), 15u);  // Hasn't shrunk.
  EXPECT_EQ(buf.data<char>(), data); // No reallocation.
}

TEST(BufferTest, TestLambdaSetAppend) {
  auto setter = [] (rtc::ArrayView<uint8_t> av) {
    for (int i = 0; i != 15; ++i)
      av[i] = kTestData[i];
    return 15;
  };

  Buffer buf1;
  buf1.SetData(kTestData, 15);
  buf1.AppendData(kTestData, 15);

  Buffer buf2;
  EXPECT_EQ(buf2.SetData(15, setter), 15u);
  EXPECT_EQ(buf2.AppendData(15, setter), 15u);
  EXPECT_EQ(buf1, buf2);
  EXPECT_EQ(buf1.capacity(), buf2.capacity());
}

TEST(BufferTest, TestLambdaSetAppendSigned) {
  auto setter = [] (rtc::ArrayView<int8_t> av) {
    for (int i = 0; i != 15; ++i)
      av[i] = kTestData[i];
    return 15;
  };

  Buffer buf1;
  buf1.SetData(kTestData, 15);
  buf1.AppendData(kTestData, 15);

  Buffer buf2;
  EXPECT_EQ(buf2.SetData<int8_t>(15, setter), 15u);
  EXPECT_EQ(buf2.AppendData<int8_t>(15, setter), 15u);
  EXPECT_EQ(buf1, buf2);
  EXPECT_EQ(buf1.capacity(), buf2.capacity());
}

TEST(BufferTest, TestLambdaAppendEmpty) {
  auto setter = [] (rtc::ArrayView<uint8_t> av) {
    for (int i = 0; i != 15; ++i)
      av[i] = kTestData[i];
    return 15;
  };

  Buffer buf1;
  buf1.SetData(kTestData, 15);

  Buffer buf2;
  EXPECT_EQ(buf2.AppendData(15, setter), 15u);
  EXPECT_EQ(buf1, buf2);
  EXPECT_EQ(buf1.capacity(), buf2.capacity());
}

TEST(BufferTest, TestLambdaAppendPartial) {
  auto setter = [] (rtc::ArrayView<uint8_t> av) {
    for (int i = 0; i != 7; ++i)
      av[i] = kTestData[i];
    return 7;
  };

  Buffer buf;
  EXPECT_EQ(buf.AppendData(15, setter), 7u);
  EXPECT_EQ(buf.size(), 7u);            // Size is exactly what we wrote.
  EXPECT_GE(buf.capacity(), 7u);        // Capacity is valid.
  EXPECT_NE(buf.data<char>(), nullptr); // Data is actually stored.
}

TEST(BufferTest, TestMutableLambdaSetAppend) {
  uint8_t magic_number = 17;
  auto setter = [magic_number] (rtc::ArrayView<uint8_t> av) mutable {
    for (int i = 0; i != 15; ++i) {
      av[i] = magic_number;
      ++magic_number;
    }
    return 15;
  };

  EXPECT_EQ(magic_number, 17);

  Buffer buf;
  EXPECT_EQ(buf.SetData(15, setter), 15u);
  EXPECT_EQ(buf.AppendData(15, setter), 15u);
  EXPECT_EQ(buf.size(), 30u);           // Size is exactly what we wrote.
  EXPECT_GE(buf.capacity(), 30u);       // Capacity is valid.
  EXPECT_NE(buf.data<char>(), nullptr); // Data is actually stored.

  for (uint8_t i = 0; i != buf.size(); ++i) {
    EXPECT_EQ(buf.data()[i], magic_number + i);
  }
}

TEST(BufferTest, TestBracketRead) {
  Buffer buf(kTestData, 7);
  EXPECT_EQ(buf.size(), 7u);
  EXPECT_EQ(buf.capacity(), 7u);
  EXPECT_NE(buf.data(), nullptr);

  for (size_t i = 0; i != 7u; ++i) {
    EXPECT_EQ(buf[i], kTestData[i]);
  }
}

TEST(BufferTest, TestBracketReadConst) {
  Buffer buf(kTestData, 7);
  EXPECT_EQ(buf.size(), 7u);
  EXPECT_EQ(buf.capacity(), 7u);
  EXPECT_NE(buf.data(), nullptr);

  const Buffer& cbuf = buf;

  for (size_t i = 0; i != 7u; ++i) {
    EXPECT_EQ(cbuf[i], kTestData[i]);
  }
}

TEST(BufferTest, TestBracketWrite) {
  Buffer buf(7);
  EXPECT_EQ(buf.size(), 7u);
  EXPECT_EQ(buf.capacity(), 7u);
  EXPECT_NE(buf.data(), nullptr);

  for (size_t i = 0; i != 7u; ++i) {
    buf[i] = kTestData[i];
  }

  for (size_t i = 0; i != 7u; ++i) {
    EXPECT_EQ(buf[i], kTestData[i]);
  }
}

TEST(BufferTest, TestInt16) {
  static constexpr int16_t test_data[] = {14, 15, 16, 17, 18};
  BufferT<int16_t> buf(test_data);
  EXPECT_EQ(buf.size(), 5u);
  EXPECT_EQ(buf.capacity(), 5u);
  EXPECT_NE(buf.data(), nullptr);
  for (size_t i = 0; i != buf.size(); ++i) {
    EXPECT_EQ(test_data[i], buf[i]);
  }
  BufferT<int16_t> buf2(test_data);
  EXPECT_EQ(buf, buf2);
  buf2[0] = 9;
  EXPECT_NE(buf, buf2);
}

TEST(BufferTest, TestFloat) {
  static constexpr float test_data[] = {14, 15, 16, 17, 18};
  BufferT<float> buf;
  EXPECT_EQ(buf.size(), 0u);
  EXPECT_EQ(buf.capacity(), 0u);
  EXPECT_EQ(buf.data(), nullptr);
  buf.SetData(test_data);
  EXPECT_EQ(buf.size(), 5u);
  EXPECT_EQ(buf.capacity(), 5u);
  EXPECT_NE(buf.data(), nullptr);
  float* p1 = buf.data();
  while (buf.data() == p1) {
    buf.AppendData(test_data);
  }
  EXPECT_EQ(buf.size(), buf.capacity());
  EXPECT_GT(buf.size(), 5u);
  EXPECT_EQ(buf.size() % 5, 0u);
  EXPECT_NE(buf.data(), nullptr);
  for (size_t i = 0; i != buf.size(); ++i) {
    EXPECT_EQ(test_data[i % 5], buf[i]);
  }
}

TEST(BufferTest, TestStruct) {
  struct BloodStone {
    bool blood;
    const char* stone;
  };
  BufferT<BloodStone> buf(4);
  EXPECT_EQ(buf.size(), 4u);
  EXPECT_EQ(buf.capacity(), 4u);
  EXPECT_NE(buf.data(), nullptr);
  BufferT<BloodStone*> buf2(4);
  for (size_t i = 0; i < buf2.size(); ++i) {
    buf2[i] = &buf[i];
  }
  static const char kObsidian[] = "obsidian";
  buf2[2]->stone = kObsidian;
  EXPECT_EQ(kObsidian, buf[2].stone);
}

}  // namespace rtc
