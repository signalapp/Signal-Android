/*
 *  Copyright 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_COMMON_VIDEO_INCLUDE_COREVIDEO_FRAME_BUFFER_H_
#define WEBRTC_COMMON_VIDEO_INCLUDE_COREVIDEO_FRAME_BUFFER_H_

#include <CoreVideo/CoreVideo.h>

#include "webrtc/common_video/include/video_frame_buffer.h"

namespace webrtc {

class CoreVideoFrameBuffer : public NativeHandleBuffer {
 public:
  explicit CoreVideoFrameBuffer(CVPixelBufferRef pixel_buffer);
  ~CoreVideoFrameBuffer() override;

  rtc::scoped_refptr<VideoFrameBuffer> NativeToI420Buffer() override;

 private:
  CVPixelBufferRef pixel_buffer_;
};

}  // namespace webrtc

#endif  // WEBRTC_COMMON_VIDEO_INCLUDE_COREVIDEO_FRAME_BUFFER_H_

