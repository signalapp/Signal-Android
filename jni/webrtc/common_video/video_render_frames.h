/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_COMMON_VIDEO_VIDEO_RENDER_FRAMES_H_
#define WEBRTC_COMMON_VIDEO_VIDEO_RENDER_FRAMES_H_

#include <stdint.h>

#include <list>

#include "webrtc/base/optional.h"
#include "webrtc/video_frame.h"

namespace webrtc {

// Class definitions
class VideoRenderFrames {
 public:
  explicit VideoRenderFrames(uint32_t render_delay_ms);
  VideoRenderFrames(const VideoRenderFrames&) = delete;

  // Add a frame to the render queue
  int32_t AddFrame(const VideoFrame& new_frame);

  // Get a frame for rendering, or false if it's not time to render.
  rtc::Optional<VideoFrame> FrameToRender();

  // Returns the number of ms to next frame to render
  uint32_t TimeToNextFrameRelease();

 private:
  // 10 seconds for 30 fps.
  enum { KMaxNumberOfFrames = 300 };
  // Don't render frames with timestamp older than 500ms from now.
  enum { KOldRenderTimestampMS = 500 };
  // Don't render frames with timestamp more than 10s into the future.
  enum { KFutureRenderTimestampMS = 10000 };

  // Sorted list with framed to be rendered, oldest first.
  std::list<VideoFrame> incoming_frames_;

  // Estimated delay from a frame is released until it's rendered.
  const uint32_t render_delay_ms_;
};

}  // namespace webrtc

#endif  // WEBRTC_COMMON_VIDEO_VIDEO_RENDER_FRAMES_H_
