/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <math.h>
#include <string.h>

#include "testing/gtest/include/gtest/gtest.h"
#include "webrtc/base/bind.h"
#include "webrtc/test/fake_texture_frame.h"
#include "webrtc/test/frame_utils.h"
#include "webrtc/video_frame.h"

namespace webrtc {

namespace {

int ExpectedSize(int plane_stride, int image_height, PlaneType type) {
  if (type == kYPlane)
    return plane_stride * image_height;
  return plane_stride * ((image_height + 1) / 2);
}

rtc::scoped_refptr<I420Buffer> CreateGradient(int width, int height) {
  rtc::scoped_refptr<I420Buffer> buffer(
      I420Buffer::Create(width, height));
  // Initialize with gradient, Y = 128(x/w + y/h), U = 256 x/w, V = 256 y/h
  for (int x = 0; x < width; x++) {
    for (int y = 0; y < height; y++) {
      buffer->MutableDataY()[x + y * width] =
          128 * (x * height + y * width) / (width * height);
    }
  }
  int chroma_width = (width + 1) / 2;
  int chroma_height = (height + 1) / 2;
  for (int x = 0; x < chroma_width; x++) {
    for (int y = 0; y < chroma_height; y++) {
      buffer->MutableDataU()[x + y * chroma_width] =
          255 * x / (chroma_width - 1);
      buffer->MutableDataV()[x + y * chroma_width] =
          255 * y / (chroma_height - 1);
    }
  }
  return buffer;
}

// The offsets and sizes describe the rectangle extracted from the
// original (gradient) frame, in relative coordinates where the
// original frame correspond to the unit square, 0.0 <= x, y < 1.0.
void CheckCrop(webrtc::VideoFrameBuffer* frame,
               double offset_x,
               double offset_y,
               double rel_width,
               double rel_height) {
  int width = frame->width();
  int height = frame->height();
  // Check that pixel values in the corners match the gradient used
  // for initialization.
  for (int i = 0; i < 2; i++) {
    for (int j = 0; j < 2; j++) {
      // Pixel coordinates of the corner.
      int x = i * (width - 1);
      int y = j * (height - 1);
      // Relative coordinates, range 0.0 - 1.0 correspond to the
      // size of the uncropped input frame.
      double orig_x = offset_x + i * rel_width;
      double orig_y = offset_y + j * rel_height;

      EXPECT_NEAR(frame->DataY()[x + y * frame->StrideY()] / 256.0,
                  (orig_x + orig_y) / 2, 0.02);
      EXPECT_NEAR(frame->DataU()[x / 2 + (y / 2) * frame->StrideU()] / 256.0,
                  orig_x, 0.02);
      EXPECT_NEAR(frame->DataV()[x / 2 + (y / 2) * frame->StrideV()] / 256.0,
                  orig_y, 0.02);
    }
  }
}

}  // namespace

TEST(TestVideoFrame, InitialValues) {
  VideoFrame frame;
  EXPECT_TRUE(frame.IsZeroSize());
  EXPECT_EQ(kVideoRotation_0, frame.rotation());
}

TEST(TestVideoFrame, CopiesInitialFrameWithoutCrashing) {
  VideoFrame frame;
  VideoFrame frame2;
  frame2.CopyFrame(frame);
}

TEST(TestVideoFrame, WidthHeightValues) {
  VideoFrame frame;
  const int valid_value = 10;
  frame.CreateEmptyFrame(10, 10, 10, 14, 90);
  EXPECT_EQ(valid_value, frame.width());
  EXPECT_EQ(valid_value, frame.height());
  frame.set_timestamp(123u);
  EXPECT_EQ(123u, frame.timestamp());
  frame.set_ntp_time_ms(456);
  EXPECT_EQ(456, frame.ntp_time_ms());
  frame.set_render_time_ms(789);
  EXPECT_EQ(789, frame.render_time_ms());
}

TEST(TestVideoFrame, SizeAllocation) {
  VideoFrame frame;
  frame. CreateEmptyFrame(10, 10, 12, 14, 220);
  int height = frame.height();
  int stride_y = frame.video_frame_buffer()->StrideY();
  int stride_u = frame.video_frame_buffer()->StrideU();
  int stride_v = frame.video_frame_buffer()->StrideV();
  // Verify that allocated size was computed correctly.
  EXPECT_EQ(ExpectedSize(stride_y, height, kYPlane),
            frame.allocated_size(kYPlane));
  EXPECT_EQ(ExpectedSize(stride_u, height, kUPlane),
            frame.allocated_size(kUPlane));
  EXPECT_EQ(ExpectedSize(stride_v, height, kVPlane),
            frame.allocated_size(kVPlane));
}

TEST(TestVideoFrame, CopyFrame) {
  uint32_t timestamp = 1;
  int64_t ntp_time_ms = 2;
  int64_t render_time_ms = 3;
  int stride_y = 15;
  int stride_u = 10;
  int stride_v = 10;
  int width = 15;
  int height = 15;
  // Copy frame.
  VideoFrame small_frame;
  small_frame.CreateEmptyFrame(width, height,
                               stride_y, stride_u, stride_v);
  small_frame.set_timestamp(timestamp);
  small_frame.set_ntp_time_ms(ntp_time_ms);
  small_frame.set_render_time_ms(render_time_ms);
  const int kSizeY = 400;
  const int kSizeU = 100;
  const int kSizeV = 100;
  const VideoRotation kRotation = kVideoRotation_270;
  uint8_t buffer_y[kSizeY];
  uint8_t buffer_u[kSizeU];
  uint8_t buffer_v[kSizeV];
  memset(buffer_y, 16, kSizeY);
  memset(buffer_u, 8, kSizeU);
  memset(buffer_v, 4, kSizeV);
  VideoFrame big_frame;
  big_frame.CreateFrame(buffer_y, buffer_u, buffer_v,
                        width + 5, height + 5, stride_y + 5,
                        stride_u, stride_v, kRotation);
  // Frame of smaller dimensions.
  small_frame.CopyFrame(big_frame);
  EXPECT_TRUE(test::FramesEqual(small_frame, big_frame));
  EXPECT_EQ(kRotation, small_frame.rotation());

  // Frame of larger dimensions.
  small_frame.CreateEmptyFrame(width, height,
                               stride_y, stride_u, stride_v);
  memset(small_frame.video_frame_buffer()->MutableDataY(), 1,
         small_frame.allocated_size(kYPlane));
  memset(small_frame.video_frame_buffer()->MutableDataU(), 2,
         small_frame.allocated_size(kUPlane));
  memset(small_frame.video_frame_buffer()->MutableDataV(), 3,
         small_frame.allocated_size(kVPlane));
  big_frame.CopyFrame(small_frame);
  EXPECT_TRUE(test::FramesEqual(small_frame, big_frame));
}

TEST(TestVideoFrame, ShallowCopy) {
  uint32_t timestamp = 1;
  int64_t ntp_time_ms = 2;
  int64_t render_time_ms = 3;
  int stride_y = 15;
  int stride_u = 10;
  int stride_v = 10;
  int width = 15;
  int height = 15;

  const int kSizeY = 400;
  const int kSizeU = 100;
  const int kSizeV = 100;
  const VideoRotation kRotation = kVideoRotation_270;
  uint8_t buffer_y[kSizeY];
  uint8_t buffer_u[kSizeU];
  uint8_t buffer_v[kSizeV];
  memset(buffer_y, 16, kSizeY);
  memset(buffer_u, 8, kSizeU);
  memset(buffer_v, 4, kSizeV);
  VideoFrame frame1;
  frame1.CreateFrame(buffer_y, buffer_u, buffer_v, width, height,
                     stride_y, stride_u, stride_v, kRotation);
  frame1.set_timestamp(timestamp);
  frame1.set_ntp_time_ms(ntp_time_ms);
  frame1.set_render_time_ms(render_time_ms);
  VideoFrame frame2;
  frame2.ShallowCopy(frame1);

  // To be able to access the buffers, we need const pointers to the frames.
  const VideoFrame* const_frame1_ptr = &frame1;
  const VideoFrame* const_frame2_ptr = &frame2;

  EXPECT_TRUE(const_frame1_ptr->video_frame_buffer()->DataY() ==
              const_frame2_ptr->video_frame_buffer()->DataY());
  EXPECT_TRUE(const_frame1_ptr->video_frame_buffer()->DataU() ==
              const_frame2_ptr->video_frame_buffer()->DataU());
  EXPECT_TRUE(const_frame1_ptr->video_frame_buffer()->DataV() ==
              const_frame2_ptr->video_frame_buffer()->DataV());

  EXPECT_EQ(frame2.timestamp(), frame1.timestamp());
  EXPECT_EQ(frame2.ntp_time_ms(), frame1.ntp_time_ms());
  EXPECT_EQ(frame2.render_time_ms(), frame1.render_time_ms());
  EXPECT_EQ(frame2.rotation(), frame1.rotation());

  frame2.set_timestamp(timestamp + 1);
  frame2.set_ntp_time_ms(ntp_time_ms + 1);
  frame2.set_render_time_ms(render_time_ms + 1);
  frame2.set_rotation(kVideoRotation_90);

  EXPECT_NE(frame2.timestamp(), frame1.timestamp());
  EXPECT_NE(frame2.ntp_time_ms(), frame1.ntp_time_ms());
  EXPECT_NE(frame2.render_time_ms(), frame1.render_time_ms());
  EXPECT_NE(frame2.rotation(), frame1.rotation());
}

TEST(TestVideoFrame, CopyBuffer) {
  VideoFrame frame1, frame2;
  int width = 15;
  int height = 15;
  int stride_y = 15;
  int stride_uv = 10;
  const int kSizeY = 225;
  const int kSizeUv = 80;
  frame2.CreateEmptyFrame(width, height,
                          stride_y, stride_uv, stride_uv);
  uint8_t buffer_y[kSizeY];
  uint8_t buffer_u[kSizeUv];
  uint8_t buffer_v[kSizeUv];
  memset(buffer_y, 16, kSizeY);
  memset(buffer_u, 8, kSizeUv);
  memset(buffer_v, 4, kSizeUv);
  frame2.CreateFrame(buffer_y, buffer_u, buffer_v,
                     width, height, stride_y, stride_uv, stride_uv,
                     kVideoRotation_0);
  // Expect exactly the same pixel data.
  EXPECT_TRUE(test::EqualPlane(buffer_y, frame2.video_frame_buffer()->DataY(),
                               stride_y, 15, 15));
  EXPECT_TRUE(test::EqualPlane(buffer_u, frame2.video_frame_buffer()->DataU(),
                               stride_uv, 8, 8));
  EXPECT_TRUE(test::EqualPlane(buffer_v, frame2.video_frame_buffer()->DataV(),
                               stride_uv, 8, 8));

  // Compare size.
  EXPECT_LE(kSizeY, frame2.allocated_size(kYPlane));
  EXPECT_LE(kSizeUv, frame2.allocated_size(kUPlane));
  EXPECT_LE(kSizeUv, frame2.allocated_size(kVPlane));
}

TEST(TestVideoFrame, FailToReuseAllocation) {
  VideoFrame frame1;
  frame1.CreateEmptyFrame(640, 320, 640, 320, 320);
  const uint8_t* y = frame1.video_frame_buffer()->DataY();
  const uint8_t* u = frame1.video_frame_buffer()->DataU();
  const uint8_t* v = frame1.video_frame_buffer()->DataV();
  // Make a shallow copy of |frame1|.
  VideoFrame frame2(frame1.video_frame_buffer(), 0, 0, kVideoRotation_0);
  frame1.CreateEmptyFrame(640, 320, 640, 320, 320);
  EXPECT_NE(y, frame1.video_frame_buffer()->DataY());
  EXPECT_NE(u, frame1.video_frame_buffer()->DataU());
  EXPECT_NE(v, frame1.video_frame_buffer()->DataV());
}

TEST(TestVideoFrame, TextureInitialValues) {
  test::FakeNativeHandle* handle = new test::FakeNativeHandle();
  VideoFrame frame = test::FakeNativeHandle::CreateFrame(
      handle, 640, 480, 100, 10, webrtc::kVideoRotation_0);
  EXPECT_EQ(640, frame.width());
  EXPECT_EQ(480, frame.height());
  EXPECT_EQ(100u, frame.timestamp());
  EXPECT_EQ(10, frame.render_time_ms());
  ASSERT_TRUE(frame.video_frame_buffer() != nullptr);
  EXPECT_EQ(handle, frame.video_frame_buffer()->native_handle());

  frame.set_timestamp(200);
  EXPECT_EQ(200u, frame.timestamp());
  frame.set_render_time_ms(20);
  EXPECT_EQ(20, frame.render_time_ms());
}

TEST(TestI420FrameBuffer, Copy) {
  rtc::scoped_refptr<I420Buffer> buf1(
      I420Buffer::Create(20, 10));
  memset(buf1->MutableDataY(), 1, 200);
  memset(buf1->MutableDataU(), 2, 50);
  memset(buf1->MutableDataV(), 3, 50);
  rtc::scoped_refptr<I420Buffer> buf2 = I420Buffer::Copy(buf1);
  EXPECT_TRUE(test::FrameBufsEqual(buf1, buf2));
}

TEST(TestI420FrameBuffer, Scale) {
  rtc::scoped_refptr<I420Buffer> buf = CreateGradient(200, 100);

  // Pure scaling, no cropping.
  rtc::scoped_refptr<I420Buffer> scaled_buffer(
      I420Buffer::Create(150, 75));

  scaled_buffer->ScaleFrom(buf);
  CheckCrop(scaled_buffer, 0.0, 0.0, 1.0, 1.0);
}

TEST(TestI420FrameBuffer, CropXCenter) {
  rtc::scoped_refptr<I420Buffer> buf = CreateGradient(200, 100);

  // Pure center cropping, no scaling.
  rtc::scoped_refptr<I420Buffer> scaled_buffer(
      I420Buffer::Create(100, 100));

  scaled_buffer->CropAndScaleFrom(buf, 50, 0, 100, 100);
  CheckCrop(scaled_buffer, 0.25, 0.0, 0.5, 1.0);
}

TEST(TestI420FrameBuffer, CropXNotCenter) {
  rtc::scoped_refptr<I420Buffer> buf = CreateGradient(200, 100);

  // Non-center cropping, no scaling.
  rtc::scoped_refptr<I420Buffer> scaled_buffer(
      I420Buffer::Create(100, 100));

  scaled_buffer->CropAndScaleFrom(buf, 25, 0, 100, 100);
  CheckCrop(scaled_buffer, 0.125, 0.0, 0.5, 1.0);
}

TEST(TestI420FrameBuffer, CropYCenter) {
  rtc::scoped_refptr<I420Buffer> buf = CreateGradient(100, 200);

  // Pure center cropping, no scaling.
  rtc::scoped_refptr<I420Buffer> scaled_buffer(
      I420Buffer::Create(100, 100));

  scaled_buffer->CropAndScaleFrom(buf, 0, 50, 100, 100);
  CheckCrop(scaled_buffer, 0.0, 0.25, 1.0, 0.5);
}

TEST(TestI420FrameBuffer, CropYNotCenter) {
  rtc::scoped_refptr<I420Buffer> buf = CreateGradient(100, 200);

  // Non-center cropping, no scaling.
  rtc::scoped_refptr<I420Buffer> scaled_buffer(
      I420Buffer::Create(100, 100));

  scaled_buffer->CropAndScaleFrom(buf, 0, 25, 100, 100);
  CheckCrop(scaled_buffer, 0.0, 0.125, 1.0, 0.5);
}

TEST(TestI420FrameBuffer, CropAndScale16x9) {
  rtc::scoped_refptr<I420Buffer> buf = CreateGradient(640, 480);

  // Center crop to 640 x 360 (16/9 aspect), then scale down by 2.
  rtc::scoped_refptr<I420Buffer> scaled_buffer(
      I420Buffer::Create(320, 180));

  scaled_buffer->CropAndScaleFrom(buf);
  CheckCrop(scaled_buffer, 0.0, 0.125, 1.0, 0.75);
}

}  // namespace webrtc
