/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/common_video/video_render_frames.h"

#include <assert.h>

#include "webrtc/base/timeutils.h"
#include "webrtc/modules/include/module_common_types.h"
#include "webrtc/system_wrappers/include/trace.h"

namespace webrtc {
namespace {

const uint32_t kEventMaxWaitTimeMs = 200;
const uint32_t kMinRenderDelayMs = 10;
const uint32_t kMaxRenderDelayMs = 500;

uint32_t EnsureValidRenderDelay(uint32_t render_delay) {
  return (render_delay < kMinRenderDelayMs || render_delay > kMaxRenderDelayMs)
             ? kMinRenderDelayMs
             : render_delay;
}
}  // namespace

VideoRenderFrames::VideoRenderFrames(uint32_t render_delay_ms)
    : render_delay_ms_(EnsureValidRenderDelay(render_delay_ms)) {}

int32_t VideoRenderFrames::AddFrame(const VideoFrame& new_frame) {
  const int64_t time_now = rtc::TimeMillis();

  // Drop old frames only when there are other frames in the queue, otherwise, a
  // really slow system never renders any frames.
  if (!incoming_frames_.empty() &&
      new_frame.render_time_ms() + KOldRenderTimestampMS < time_now) {
    WEBRTC_TRACE(kTraceWarning,
                 kTraceVideoRenderer,
                 -1,
                 "%s: too old frame, timestamp=%u.",
                 __FUNCTION__,
                 new_frame.timestamp());
    return -1;
  }

  if (new_frame.render_time_ms() > time_now + KFutureRenderTimestampMS) {
    WEBRTC_TRACE(kTraceWarning, kTraceVideoRenderer, -1,
                 "%s: frame too long into the future, timestamp=%u.",
                 __FUNCTION__, new_frame.timestamp());
    return -1;
  }

  incoming_frames_.push_back(new_frame);
  return static_cast<int32_t>(incoming_frames_.size());
}

rtc::Optional<VideoFrame> VideoRenderFrames::FrameToRender() {
  rtc::Optional<VideoFrame> render_frame;
  // Get the newest frame that can be released for rendering.
  while (!incoming_frames_.empty() && TimeToNextFrameRelease() <= 0) {
    render_frame = rtc::Optional<VideoFrame>(incoming_frames_.front());
    incoming_frames_.pop_front();
  }
  return render_frame;
}

uint32_t VideoRenderFrames::TimeToNextFrameRelease() {
  if (incoming_frames_.empty()) {
    return kEventMaxWaitTimeMs;
  }
  const int64_t time_to_release = incoming_frames_.front().render_time_ms() -
                                  render_delay_ms_ -
                                  rtc::TimeMillis();
  return time_to_release < 0 ? 0u : static_cast<uint32_t>(time_to_release);
}

}  // namespace webrtc
