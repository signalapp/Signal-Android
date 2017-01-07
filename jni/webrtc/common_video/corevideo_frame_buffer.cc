/*
 *  Copyright 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/common_video/include/corevideo_frame_buffer.h"

#include "libyuv/convert.h"
#include "webrtc/base/checks.h"
#include "webrtc/base/logging.h"

namespace webrtc {

CoreVideoFrameBuffer::CoreVideoFrameBuffer(CVPixelBufferRef pixel_buffer)
    : NativeHandleBuffer(pixel_buffer,
                         CVPixelBufferGetWidth(pixel_buffer),
                         CVPixelBufferGetHeight(pixel_buffer)),
      pixel_buffer_(pixel_buffer) {
  CVBufferRetain(pixel_buffer_);
}

CoreVideoFrameBuffer::~CoreVideoFrameBuffer() {
  CVBufferRelease(pixel_buffer_);
}

rtc::scoped_refptr<VideoFrameBuffer>
CoreVideoFrameBuffer::NativeToI420Buffer() {
  RTC_DCHECK(CVPixelBufferGetPixelFormatType(pixel_buffer_) ==
             kCVPixelFormatType_420YpCbCr8BiPlanarFullRange);
  size_t width = CVPixelBufferGetWidthOfPlane(pixel_buffer_, 0);
  size_t height = CVPixelBufferGetHeightOfPlane(pixel_buffer_, 0);
  // TODO(tkchin): Use a frame buffer pool.
  rtc::scoped_refptr<webrtc::VideoFrameBuffer> buffer =
      new rtc::RefCountedObject<webrtc::I420Buffer>(width, height);
  CVPixelBufferLockBaseAddress(pixel_buffer_, kCVPixelBufferLock_ReadOnly);
  const uint8_t* src_y = static_cast<const uint8_t*>(
      CVPixelBufferGetBaseAddressOfPlane(pixel_buffer_, 0));
  int src_y_stride = CVPixelBufferGetBytesPerRowOfPlane(pixel_buffer_, 0);
  const uint8_t* src_uv = static_cast<const uint8_t*>(
      CVPixelBufferGetBaseAddressOfPlane(pixel_buffer_, 1));
  int src_uv_stride = CVPixelBufferGetBytesPerRowOfPlane(pixel_buffer_, 1);
  int ret = libyuv::NV12ToI420(
      src_y, src_y_stride, src_uv, src_uv_stride,
      buffer->MutableDataY(), buffer->StrideY(),
      buffer->MutableDataU(), buffer->StrideU(),
      buffer->MutableDataV(), buffer->StrideV(),
      width, height);
  CVPixelBufferUnlockBaseAddress(pixel_buffer_, kCVPixelBufferLock_ReadOnly);
  if (ret) {
    LOG(LS_ERROR) << "Error converting NV12 to I420: " << ret;
    return nullptr;
  }
  return buffer;
}

}  // namespace webrtc
