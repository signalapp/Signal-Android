/*
 *  Copyright 2010 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <memory>
#include <string>

#include "webrtc/base/gunit.h"
#include "webrtc/base/helpers.h"
#include "webrtc/base/logging.h"
#include "webrtc/base/pathutils.h"
#include "webrtc/base/multipart.h"

namespace rtc {

static const std::string kTestMultipartBoundary = "123456789987654321";
static const std::string kTestContentType =
    "multipart/form-data; boundary=123456789987654321";
static const char kTestData[] = "This is a test.";
static const char kTestStreamContent[] = "This is a test stream.";

TEST(MultipartTest, TestBasicOperations) {
  MultipartStream multipart("multipart/form-data", kTestMultipartBoundary);
  std::string content_type;
  multipart.GetContentType(&content_type);
  EXPECT_EQ(kTestContentType, content_type);

  EXPECT_EQ(rtc::SS_OPENING, multipart.GetState());

  // The multipart stream contains only --boundary--\r\n
  size_t end_part_size = multipart.GetEndPartSize();
  multipart.EndParts();
  EXPECT_EQ(rtc::SS_OPEN, multipart.GetState());
  size_t size;
  EXPECT_TRUE(multipart.GetSize(&size));
  EXPECT_EQ(end_part_size, size);

  // Write is not supported.
  EXPECT_EQ(rtc::SR_ERROR,
            multipart.Write(kTestData, sizeof(kTestData), NULL, NULL));

  multipart.Close();
  EXPECT_EQ(rtc::SS_CLOSED, multipart.GetState());
  EXPECT_TRUE(multipart.GetSize(&size));
  EXPECT_EQ(0U, size);
}

TEST(MultipartTest, TestAddAndRead) {
  MultipartStream multipart("multipart/form-data", kTestMultipartBoundary);

  size_t part_size =
      multipart.GetPartSize(kTestData, "form-data; name=\"text\"", "text");
  EXPECT_TRUE(multipart.AddPart(kTestData, "form-data; name=\"text\"", "text"));
  size_t size;
  EXPECT_TRUE(multipart.GetSize(&size));
  EXPECT_EQ(part_size, size);

  std::unique_ptr<rtc::MemoryStream> stream(
      new rtc::MemoryStream(kTestStreamContent));
  size_t stream_size = 0;
  EXPECT_TRUE(stream->GetSize(&stream_size));
  part_size +=
      multipart.GetPartSize("", "form-data; name=\"stream\"", "stream");
  part_size += stream_size;

  EXPECT_TRUE(multipart.AddPart(
      new rtc::MemoryStream(kTestStreamContent),
      "form-data; name=\"stream\"",
      "stream"));
  EXPECT_TRUE(multipart.GetSize(&size));
  EXPECT_EQ(part_size, size);

  // In adding state, block read.
  char buffer[1024];
  EXPECT_EQ(rtc::SR_BLOCK,
            multipart.Read(buffer, sizeof(buffer), NULL, NULL));
  // Write is not supported.
  EXPECT_EQ(rtc::SR_ERROR,
            multipart.Write(buffer, sizeof(buffer), NULL, NULL));

  part_size += multipart.GetEndPartSize();
  multipart.EndParts();
  EXPECT_TRUE(multipart.GetSize(&size));
  EXPECT_EQ(part_size, size);

  // Read the multipart stream into StringStream
  std::string str;
  rtc::StringStream str_stream(&str);
  EXPECT_EQ(rtc::SR_SUCCESS,
            Flow(&multipart, buffer, sizeof(buffer), &str_stream));
  EXPECT_EQ(size, str.length());

  // Search three boundaries and two parts in the order.
  size_t pos = 0;
  pos = str.find(kTestMultipartBoundary);
  EXPECT_NE(std::string::npos, pos);
  pos += kTestMultipartBoundary.length();

  pos = str.find(kTestData, pos);
  EXPECT_NE(std::string::npos, pos);
  pos += sizeof(kTestData);

  pos = str.find(kTestMultipartBoundary, pos);
  EXPECT_NE(std::string::npos, pos);
  pos += kTestMultipartBoundary.length();

  pos = str.find(kTestStreamContent, pos);
  EXPECT_NE(std::string::npos, pos);
  pos += sizeof(kTestStreamContent);

  pos = str.find(kTestMultipartBoundary, pos);
  EXPECT_NE(std::string::npos, pos);
  pos += kTestMultipartBoundary.length();

  pos = str.find(kTestMultipartBoundary, pos);
  EXPECT_EQ(std::string::npos, pos);
}

}  // namespace rtc
