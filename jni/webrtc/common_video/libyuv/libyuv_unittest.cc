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

#include <memory>

#include "testing/gtest/include/gtest/gtest.h"
#include "webrtc/common_video/libyuv/include/webrtc_libyuv.h"
#include "webrtc/test/testsupport/fileutils.h"
#include "webrtc/video_frame.h"

namespace webrtc {

namespace {
void Calc16ByteAlignedStride(int width, int* stride_y, int* stride_uv) {
  *stride_y = 16 * ((width + 15) / 16);
  *stride_uv = 16 * ((width + 31) / 32);
}

}  // Anonymous namespace

class TestLibYuv : public ::testing::Test {
 protected:
  TestLibYuv();
  virtual void SetUp();
  virtual void TearDown();

  FILE* source_file_;
  VideoFrame orig_frame_;
  std::unique_ptr<uint8_t[]> orig_buffer_;
  const int width_;
  const int height_;
  const int size_y_;
  const int size_uv_;
  const size_t frame_length_;
};

TestLibYuv::TestLibYuv()
    : source_file_(NULL),
      orig_frame_(),
      width_(352),
      height_(288),
      size_y_(width_ * height_),
      size_uv_(((width_ + 1) / 2) * ((height_ + 1) / 2)),
      frame_length_(CalcBufferSize(kI420, 352, 288)) {
  orig_buffer_.reset(new uint8_t[frame_length_]);
}

void TestLibYuv::SetUp() {
  const std::string input_file_name = webrtc::test::ResourcePath("foreman_cif",
                                                                 "yuv");
  source_file_  = fopen(input_file_name.c_str(), "rb");
  ASSERT_TRUE(source_file_ != NULL) << "Cannot read file: "<<
                                       input_file_name << "\n";

  EXPECT_EQ(frame_length_,
            fread(orig_buffer_.get(), 1, frame_length_, source_file_));
  orig_frame_.CreateFrame(orig_buffer_.get(),
                          orig_buffer_.get() + size_y_,
                          orig_buffer_.get() +
                          size_y_ + size_uv_,
                          width_, height_,
                          width_, (width_ + 1) / 2,
                          (width_ + 1) / 2,
                          kVideoRotation_0);
}

void TestLibYuv::TearDown() {
  if (source_file_ != NULL) {
    ASSERT_EQ(0, fclose(source_file_));
  }
  source_file_ = NULL;
}

TEST_F(TestLibYuv, ConvertSanityTest) {
  // TODO(mikhal)
}

TEST_F(TestLibYuv, ConvertTest) {
  // Reading YUV frame - testing on the first frame of the foreman sequence
  int j = 0;
  std::string output_file_name = webrtc::test::OutputPath() +
                                 "LibYuvTest_conversion.yuv";
  FILE*  output_file = fopen(output_file_name.c_str(), "wb");
  ASSERT_TRUE(output_file != NULL);

  double psnr = 0.0;

  VideoFrame res_i420_frame;
  res_i420_frame.CreateEmptyFrame(width_, height_, width_,
                                               (width_ + 1) / 2,
                                               (width_ + 1) / 2);
  printf("\nConvert #%d I420 <-> I420 \n", j);
  std::unique_ptr<uint8_t[]> out_i420_buffer(new uint8_t[frame_length_]);
  EXPECT_EQ(0, ConvertFromI420(orig_frame_, kI420, 0,
                               out_i420_buffer.get()));
  EXPECT_EQ(0, ConvertToI420(kI420, out_i420_buffer.get(), 0, 0, width_,
                             height_, 0, kVideoRotation_0, &res_i420_frame));

  if (PrintVideoFrame(res_i420_frame, output_file) < 0) {
    return;
  }
  psnr = I420PSNR(&orig_frame_, &res_i420_frame);
  EXPECT_EQ(48.0, psnr);
  j++;

  printf("\nConvert #%d I420 <-> RGB24\n", j);
  std::unique_ptr<uint8_t[]> res_rgb_buffer2(new uint8_t[width_ * height_ * 3]);
  // Align the stride values for the output frame.
  int stride_y = 0;
  int stride_uv = 0;
  Calc16ByteAlignedStride(width_, &stride_y, &stride_uv);
  res_i420_frame.CreateEmptyFrame(width_, height_, stride_y,
                                  stride_uv, stride_uv);
  EXPECT_EQ(0, ConvertFromI420(orig_frame_, kRGB24, 0, res_rgb_buffer2.get()));

  EXPECT_EQ(0, ConvertToI420(kRGB24, res_rgb_buffer2.get(), 0, 0, width_,
                             height_, 0, kVideoRotation_0, &res_i420_frame));

  if (PrintVideoFrame(res_i420_frame, output_file) < 0) {
    return;
  }
  psnr = I420PSNR(&orig_frame_, &res_i420_frame);

  // Optimization Speed- quality trade-off => 45 dB only (platform dependant).
  EXPECT_GT(ceil(psnr), 44);
  j++;

  printf("\nConvert #%d I420 <-> UYVY\n", j);
  std::unique_ptr<uint8_t[]> out_uyvy_buffer(new uint8_t[width_ * height_ * 2]);
  EXPECT_EQ(0, ConvertFromI420(orig_frame_,  kUYVY, 0, out_uyvy_buffer.get()));
  EXPECT_EQ(0, ConvertToI420(kUYVY, out_uyvy_buffer.get(), 0, 0, width_,
                             height_, 0, kVideoRotation_0, &res_i420_frame));
  psnr = I420PSNR(&orig_frame_, &res_i420_frame);
  EXPECT_EQ(48.0, psnr);
  if (PrintVideoFrame(res_i420_frame, output_file) < 0) {
    return;
  }
  j++;

  printf("\nConvert #%d I420 <-> YUY2\n", j);
  std::unique_ptr<uint8_t[]> out_yuy2_buffer(new uint8_t[width_ * height_ * 2]);
  EXPECT_EQ(0, ConvertFromI420(orig_frame_,  kYUY2, 0, out_yuy2_buffer.get()));

  EXPECT_EQ(0, ConvertToI420(kYUY2, out_yuy2_buffer.get(), 0, 0, width_,
                             height_, 0, kVideoRotation_0, &res_i420_frame));

  if (PrintVideoFrame(res_i420_frame, output_file) < 0) {
    return;
  }

  psnr = I420PSNR(&orig_frame_, &res_i420_frame);
  EXPECT_EQ(48.0, psnr);
  printf("\nConvert #%d I420 <-> RGB565\n", j);
  std::unique_ptr<uint8_t[]> out_rgb565_buffer(
      new uint8_t[width_ * height_ * 2]);
  EXPECT_EQ(0, ConvertFromI420(orig_frame_, kRGB565, 0,
                               out_rgb565_buffer.get()));

  EXPECT_EQ(0, ConvertToI420(kRGB565, out_rgb565_buffer.get(), 0, 0, width_,
                             height_, 0, kVideoRotation_0, &res_i420_frame));

  if (PrintVideoFrame(res_i420_frame, output_file) < 0) {
    return;
  }
  j++;

  psnr = I420PSNR(&orig_frame_, &res_i420_frame);
  // TODO(leozwang) Investigate the right psnr should be set for I420ToRGB565,
  // Another example is I420ToRGB24, the psnr is 44
  // TODO(mikhal): Add psnr for RGB565, 1555, 4444, convert to ARGB.
  EXPECT_GT(ceil(psnr), 40);

  printf("\nConvert #%d I420 <-> ARGB8888\n", j);
  std::unique_ptr<uint8_t[]> out_argb8888_buffer(
      new uint8_t[width_ * height_ * 4]);
  EXPECT_EQ(0, ConvertFromI420(orig_frame_, kARGB, 0,
                               out_argb8888_buffer.get()));

  EXPECT_EQ(0, ConvertToI420(kARGB, out_argb8888_buffer.get(), 0, 0, width_,
                             height_, 0, kVideoRotation_0, &res_i420_frame));

  if (PrintVideoFrame(res_i420_frame, output_file) < 0) {
    return;
  }

  psnr = I420PSNR(&orig_frame_, &res_i420_frame);
  // TODO(leozwang) Investigate the right psnr should be set for I420ToARGB8888,
  EXPECT_GT(ceil(psnr), 42);

  ASSERT_EQ(0, fclose(output_file));
}

TEST_F(TestLibYuv, ConvertAlignedFrame) {
  // Reading YUV frame - testing on the first frame of the foreman sequence
  std::string output_file_name = webrtc::test::OutputPath() +
                                 "LibYuvTest_conversion.yuv";
  FILE*  output_file = fopen(output_file_name.c_str(), "wb");
  ASSERT_TRUE(output_file != NULL);

  double psnr = 0.0;

  VideoFrame res_i420_frame;
  int stride_y = 0;
  int stride_uv = 0;
  Calc16ByteAlignedStride(width_, &stride_y, &stride_uv);
  res_i420_frame.CreateEmptyFrame(width_, height_,
                                  stride_y, stride_uv, stride_uv);
  std::unique_ptr<uint8_t[]> out_i420_buffer(new uint8_t[frame_length_]);
  EXPECT_EQ(0, ConvertFromI420(orig_frame_, kI420, 0,
                               out_i420_buffer.get()));
  EXPECT_EQ(0, ConvertToI420(kI420, out_i420_buffer.get(), 0, 0, width_,
                             height_, 0, kVideoRotation_0, &res_i420_frame));

  if (PrintVideoFrame(res_i420_frame, output_file) < 0) {
    return;
  }
  psnr = I420PSNR(&orig_frame_, &res_i420_frame);
  EXPECT_EQ(48.0, psnr);
}


TEST_F(TestLibYuv, RotateTest) {
  // Use ConvertToI420 for multiple roatations - see that nothing breaks, all
  // memory is properly allocated and end result is equal to the starting point.
  VideoFrame rotated_res_i420_frame;
  int rotated_width = height_;
  int rotated_height = width_;
  int stride_y;
  int stride_uv;
  Calc16ByteAlignedStride(rotated_width, &stride_y, &stride_uv);
  rotated_res_i420_frame.CreateEmptyFrame(rotated_width,
                                          rotated_height,
                                          stride_y,
                                          stride_uv,
                                          stride_uv);
  EXPECT_EQ(0, ConvertToI420(kI420, orig_buffer_.get(), 0, 0, width_, height_,
                             0, kVideoRotation_90, &rotated_res_i420_frame));
  EXPECT_EQ(0, ConvertToI420(kI420, orig_buffer_.get(), 0, 0, width_, height_,
                             0, kVideoRotation_270, &rotated_res_i420_frame));
  rotated_res_i420_frame.CreateEmptyFrame(width_, height_,
                                          width_, (width_ + 1) / 2,
                                          (width_ + 1) / 2);
  EXPECT_EQ(0, ConvertToI420(kI420, orig_buffer_.get(), 0, 0, width_, height_,
                             0, kVideoRotation_180, &rotated_res_i420_frame));
}

}  // namespace webrtc
