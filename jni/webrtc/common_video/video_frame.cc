/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/video_frame.h"

#include <string.h>

#include <algorithm>  // swap

#include "webrtc/base/bind.h"
#include "webrtc/base/checks.h"

namespace webrtc {

// FFmpeg's decoder, used by H264DecoderImpl, requires up to 8 bytes padding due
// to optimized bitstream readers. See avcodec_decode_video2.
const size_t EncodedImage::kBufferPaddingBytesH264 = 8;

VideoFrame::VideoFrame()
    : video_frame_buffer_(nullptr),
      timestamp_(0),
      ntp_time_ms_(0),
      render_time_ms_(0),
      rotation_(kVideoRotation_0) {}

VideoFrame::VideoFrame(const rtc::scoped_refptr<VideoFrameBuffer>& buffer,
                       uint32_t timestamp,
                       int64_t render_time_ms,
                       VideoRotation rotation)
    : video_frame_buffer_(buffer),
      timestamp_(timestamp),
      ntp_time_ms_(0),
      render_time_ms_(render_time_ms),
      rotation_(rotation) {
  RTC_DCHECK(buffer);
}

void VideoFrame::CreateEmptyFrame(int width,
                                  int height,
                                  int stride_y,
                                  int stride_u,
                                  int stride_v) {
  const int half_width = (width + 1) / 2;
  RTC_DCHECK_GT(width, 0);
  RTC_DCHECK_GT(height, 0);
  RTC_DCHECK_GE(stride_y, width);
  RTC_DCHECK_GE(stride_u, half_width);
  RTC_DCHECK_GE(stride_v, half_width);

  // Creating empty frame - reset all values.
  timestamp_ = 0;
  ntp_time_ms_ = 0;
  render_time_ms_ = 0;
  rotation_ = kVideoRotation_0;

  // Allocate a new buffer.
  video_frame_buffer_ = I420Buffer::Create(
      width, height, stride_y, stride_u, stride_v);
}

void VideoFrame::CreateFrame(const uint8_t* buffer_y,
                             const uint8_t* buffer_u,
                             const uint8_t* buffer_v,
                             int width,
                             int height,
                             int stride_y,
                             int stride_u,
                             int stride_v,
                             VideoRotation rotation) {
  const int half_height = (height + 1) / 2;
  const int expected_size_y = height * stride_y;
  const int expected_size_u = half_height * stride_u;
  const int expected_size_v = half_height * stride_v;
  CreateEmptyFrame(width, height, stride_y, stride_u, stride_v);
  memcpy(video_frame_buffer_->MutableDataY(), buffer_y, expected_size_y);
  memcpy(video_frame_buffer_->MutableDataU(), buffer_u, expected_size_u);
  memcpy(video_frame_buffer_->MutableDataV(), buffer_v, expected_size_v);
  rotation_ = rotation;
}

void VideoFrame::CreateFrame(const uint8_t* buffer,
                             int width,
                             int height,
                             VideoRotation rotation) {
  const int stride_y = width;
  const int stride_uv = (width + 1) / 2;

  const uint8_t* buffer_y = buffer;
  const uint8_t* buffer_u = buffer_y + stride_y * height;
  const uint8_t* buffer_v = buffer_u + stride_uv * ((height + 1) / 2);
  CreateFrame(buffer_y, buffer_u, buffer_v, width, height, stride_y,
              stride_uv, stride_uv, rotation);
}

void VideoFrame::CopyFrame(const VideoFrame& videoFrame) {
  ShallowCopy(videoFrame);

  // If backed by a plain memory buffer, create a new, non-shared, copy.
  if (video_frame_buffer_ && !video_frame_buffer_->native_handle()) {
    video_frame_buffer_ = I420Buffer::Copy(video_frame_buffer_);
  }
}

void VideoFrame::ShallowCopy(const VideoFrame& videoFrame) {
  video_frame_buffer_ = videoFrame.video_frame_buffer();
  timestamp_ = videoFrame.timestamp_;
  ntp_time_ms_ = videoFrame.ntp_time_ms_;
  render_time_ms_ = videoFrame.render_time_ms_;
  rotation_ = videoFrame.rotation_;
}

// TODO(nisse): Delete. Besides test code, only one use, in
// webrtcvideoengine2.cc:CreateBlackFrame.
int VideoFrame::allocated_size(PlaneType type) const {
  const int plane_height = (type == kYPlane) ? height() : (height() + 1) / 2;
  int stride;
  switch (type) {
    case kYPlane:
      stride = video_frame_buffer_->StrideY();
      break;
    case kUPlane:
      stride = video_frame_buffer_->StrideU();
      break;
    case kVPlane:
      stride = video_frame_buffer_->StrideV();
      break;
    default:
      RTC_NOTREACHED();
      return 0;
  }
  return plane_height * stride;
}

int VideoFrame::width() const {
  return video_frame_buffer_ ? video_frame_buffer_->width() : 0;
}

int VideoFrame::height() const {
  return video_frame_buffer_ ? video_frame_buffer_->height() : 0;
}

bool VideoFrame::IsZeroSize() const {
  return !video_frame_buffer_;
}

const rtc::scoped_refptr<VideoFrameBuffer>& VideoFrame::video_frame_buffer()
    const {
  return video_frame_buffer_;
}

size_t EncodedImage::GetBufferPaddingBytes(VideoCodecType codec_type) {
  switch (codec_type) {
    case kVideoCodecVP8:
    case kVideoCodecVP9:
      return 0;
    case kVideoCodecH264:
      return kBufferPaddingBytesH264;
    case kVideoCodecI420:
    case kVideoCodecRED:
    case kVideoCodecULPFEC:
    case kVideoCodecGeneric:
    case kVideoCodecUnknown:
      return 0;
  }
  RTC_NOTREACHED();
  return 0;
}

}  // namespace webrtc
