/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/common_video/libyuv/include/webrtc_libyuv.h"

#include <assert.h>
#include <string.h>

// NOTE(ajm): Path provided by gyp.
#include "libyuv.h"  // NOLINT

namespace webrtc {

VideoType RawVideoTypeToCommonVideoVideoType(RawVideoType type) {
  switch (type) {
    case kVideoI420:
      return kI420;
    case kVideoIYUV:
      return kIYUV;
    case kVideoRGB24:
      return kRGB24;
    case kVideoARGB:
      return kARGB;
    case kVideoARGB4444:
      return kARGB4444;
    case kVideoRGB565:
      return kRGB565;
    case kVideoARGB1555:
      return kARGB1555;
    case kVideoYUY2:
      return kYUY2;
    case kVideoYV12:
      return kYV12;
    case kVideoUYVY:
      return kUYVY;
    case kVideoNV21:
      return kNV21;
    case kVideoNV12:
      return kNV12;
    case kVideoBGRA:
      return kBGRA;
    case kVideoMJPEG:
      return kMJPG;
    default:
      assert(false);
  }
  return kUnknown;
}

size_t CalcBufferSize(VideoType type, int width, int height) {
  assert(width >= 0);
  assert(height >= 0);
  size_t buffer_size = 0;
  switch (type) {
    case kI420:
    case kNV12:
    case kNV21:
    case kIYUV:
    case kYV12: {
      int half_width = (width + 1) >> 1;
      int half_height = (height + 1) >> 1;
      buffer_size = width * height + half_width * half_height * 2;
      break;
    }
    case kARGB4444:
    case kRGB565:
    case kARGB1555:
    case kYUY2:
    case kUYVY:
      buffer_size = width * height * 2;
      break;
    case kRGB24:
      buffer_size = width * height * 3;
      break;
    case kBGRA:
    case kARGB:
      buffer_size = width * height * 4;
      break;
    default:
      assert(false);
      break;
  }
  return buffer_size;
}

static int PrintPlane(const uint8_t* buf,
                      int width,
                      int height,
                      int stride,
                      FILE* file) {
  for (int i = 0; i < height; i++, buf += stride) {
    if (fwrite(buf, 1, width, file) != static_cast<unsigned int>(width))
      return -1;
  }
  return 0;
}

// TODO(nisse): Belongs with the test code?
int PrintVideoFrame(const VideoFrame& frame, FILE* file) {
  if (file == NULL)
    return -1;
  if (frame.IsZeroSize())
    return -1;
  int width = frame.video_frame_buffer()->width();
  int height = frame.video_frame_buffer()->height();
  int chroma_width = (width + 1) / 2;
  int chroma_height = (height + 1) / 2;

  if (PrintPlane(frame.video_frame_buffer()->DataY(), width, height,
                 frame.video_frame_buffer()->StrideY(), file) < 0) {
    return -1;
  }
  if (PrintPlane(frame.video_frame_buffer()->DataU(),
                 chroma_width, chroma_height,
                 frame.video_frame_buffer()->StrideU(), file) < 0) {
    return -1;
  }
  if (PrintPlane(frame.video_frame_buffer()->DataV(),
                 chroma_width, chroma_height,
                 frame.video_frame_buffer()->StrideV(), file) < 0) {
    return -1;
  }
  return 0;
}

int ExtractBuffer(const rtc::scoped_refptr<VideoFrameBuffer>& input_frame,
                  size_t size,
                  uint8_t* buffer) {
  assert(buffer);
  if (!input_frame)
    return -1;
  int width = input_frame->width();
  int height = input_frame->height();
  size_t length = CalcBufferSize(kI420, width, height);
  if (size < length) {
     return -1;
  }

  int chroma_width = (width + 1) / 2;
  int chroma_height = (height + 1) / 2;

  libyuv::I420Copy(input_frame->DataY(),
                   input_frame->StrideY(),
                   input_frame->DataU(),
                   input_frame->StrideU(),
                   input_frame->DataV(),
                   input_frame->StrideV(),
                   buffer, width,
                   buffer + width*height, chroma_width,
                   buffer + width*height + chroma_width*chroma_height,
                   chroma_width,
                   width, height);

  return static_cast<int>(length);
}

int ExtractBuffer(const VideoFrame& input_frame, size_t size, uint8_t* buffer) {
  return ExtractBuffer(input_frame.video_frame_buffer(), size, buffer);
}

int ConvertNV12ToRGB565(const uint8_t* src_frame,
                        uint8_t* dst_frame,
                        int width, int height) {
  int abs_height = (height < 0) ? -height : height;
  const uint8_t* yplane = src_frame;
  const uint8_t* uvInterlaced = src_frame + (width * abs_height);

  return libyuv::NV12ToRGB565(yplane, width,
                              uvInterlaced, (width + 1) >> 1,
                              dst_frame, width,
                              width, height);
}

int ConvertRGB24ToARGB(const uint8_t* src_frame, uint8_t* dst_frame,
                       int width, int height, int dst_stride) {
  if (dst_stride == 0)
    dst_stride = width;
  return libyuv::RGB24ToARGB(src_frame, width,
                             dst_frame, dst_stride,
                             width, height);
}

libyuv::RotationMode ConvertRotationMode(VideoRotation rotation) {
  switch (rotation) {
    case kVideoRotation_0:
      return libyuv::kRotate0;
    case kVideoRotation_90:
      return libyuv::kRotate90;
    case kVideoRotation_180:
      return libyuv::kRotate180;
    case kVideoRotation_270:
      return libyuv::kRotate270;
  }
  assert(false);
  return libyuv::kRotate0;
}

int ConvertVideoType(VideoType video_type) {
  switch (video_type) {
    case kUnknown:
      return libyuv::FOURCC_ANY;
    case  kI420:
      return libyuv::FOURCC_I420;
    case kIYUV:  // same as KYV12
    case kYV12:
      return libyuv::FOURCC_YV12;
    case kRGB24:
      return libyuv::FOURCC_24BG;
    case kABGR:
      return libyuv::FOURCC_ABGR;
    case kRGB565:
      return libyuv::FOURCC_RGBP;
    case kYUY2:
      return libyuv::FOURCC_YUY2;
    case kUYVY:
      return libyuv::FOURCC_UYVY;
    case kMJPG:
      return libyuv::FOURCC_MJPG;
    case kNV21:
      return libyuv::FOURCC_NV21;
    case kNV12:
      return libyuv::FOURCC_NV12;
    case kARGB:
      return libyuv::FOURCC_ARGB;
    case kBGRA:
      return libyuv::FOURCC_BGRA;
    case kARGB4444:
      return libyuv::FOURCC_R444;
    case kARGB1555:
      return libyuv::FOURCC_RGBO;
  }
  assert(false);
  return libyuv::FOURCC_ANY;
}

// TODO(nisse): Delete this wrapper, let callers use libyuv directly.
int ConvertToI420(VideoType src_video_type,
                  const uint8_t* src_frame,
                  int crop_x,
                  int crop_y,
                  int src_width,
                  int src_height,
                  size_t sample_size,
                  VideoRotation rotation,
                  VideoFrame* dst_frame) {
  int dst_width = dst_frame->width();
  int dst_height = dst_frame->height();
  // LibYuv expects pre-rotation values for dst.
  // Stride values should correspond to the destination values.
  if (rotation == kVideoRotation_90 || rotation == kVideoRotation_270) {
    dst_width = dst_frame->height();
    dst_height = dst_frame->width();
  }
  return libyuv::ConvertToI420(
      src_frame, sample_size,
      dst_frame->video_frame_buffer()->MutableDataY(),
      dst_frame->video_frame_buffer()->StrideY(),
      dst_frame->video_frame_buffer()->MutableDataU(),
      dst_frame->video_frame_buffer()->StrideU(),
      dst_frame->video_frame_buffer()->MutableDataV(),
      dst_frame->video_frame_buffer()->StrideV(),
      crop_x, crop_y,
      src_width, src_height,
      dst_width, dst_height,
      ConvertRotationMode(rotation),
      ConvertVideoType(src_video_type));
}

int ConvertFromI420(const VideoFrame& src_frame,
                    VideoType dst_video_type,
                    int dst_sample_size,
                    uint8_t* dst_frame) {
  return libyuv::ConvertFromI420(
      src_frame.video_frame_buffer()->DataY(),
      src_frame.video_frame_buffer()->StrideY(),
      src_frame.video_frame_buffer()->DataU(),
      src_frame.video_frame_buffer()->StrideU(),
      src_frame.video_frame_buffer()->DataV(),
      src_frame.video_frame_buffer()->StrideV(),
      dst_frame, dst_sample_size,
      src_frame.width(), src_frame.height(),
      ConvertVideoType(dst_video_type));
}

// Compute PSNR for an I420 frame (all planes)
double I420PSNR(const VideoFrame* ref_frame, const VideoFrame* test_frame) {
  if (!ref_frame || !test_frame)
    return -1;
  else if ((ref_frame->width() !=  test_frame->width()) ||
          (ref_frame->height() !=  test_frame->height()))
    return -1;
  else if (ref_frame->width() < 0 || ref_frame->height() < 0)
    return -1;

  double psnr = libyuv::I420Psnr(ref_frame->video_frame_buffer()->DataY(),
                                 ref_frame->video_frame_buffer()->StrideY(),
                                 ref_frame->video_frame_buffer()->DataU(),
                                 ref_frame->video_frame_buffer()->StrideU(),
                                 ref_frame->video_frame_buffer()->DataV(),
                                 ref_frame->video_frame_buffer()->StrideV(),
                                 test_frame->video_frame_buffer()->DataY(),
                                 test_frame->video_frame_buffer()->StrideY(),
                                 test_frame->video_frame_buffer()->DataU(),
                                 test_frame->video_frame_buffer()->StrideU(),
                                 test_frame->video_frame_buffer()->DataV(),
                                 test_frame->video_frame_buffer()->StrideV(),
                                 test_frame->width(), test_frame->height());
  // LibYuv sets the max psnr value to 128, we restrict it here.
  // In case of 0 mse in one frame, 128 can skew the results significantly.
  return (psnr > kPerfectPSNR) ? kPerfectPSNR : psnr;
}

// Compute SSIM for an I420 frame (all planes)
double I420SSIM(const VideoFrame* ref_frame, const VideoFrame* test_frame) {
  if (!ref_frame || !test_frame)
    return -1;
  else if ((ref_frame->width() !=  test_frame->width()) ||
          (ref_frame->height() !=  test_frame->height()))
    return -1;
  else if (ref_frame->width() < 0 || ref_frame->height()  < 0)
    return -1;

  return libyuv::I420Ssim(ref_frame->video_frame_buffer()->DataY(),
                          ref_frame->video_frame_buffer()->StrideY(),
                          ref_frame->video_frame_buffer()->DataU(),
                          ref_frame->video_frame_buffer()->StrideU(),
                          ref_frame->video_frame_buffer()->DataV(),
                          ref_frame->video_frame_buffer()->StrideV(),
                          test_frame->video_frame_buffer()->DataY(),
                          test_frame->video_frame_buffer()->StrideY(),
                          test_frame->video_frame_buffer()->DataU(),
                          test_frame->video_frame_buffer()->StrideU(),
                          test_frame->video_frame_buffer()->DataV(),
                          test_frame->video_frame_buffer()->StrideV(),
                          test_frame->width(), test_frame->height());
}
}  // namespace webrtc
