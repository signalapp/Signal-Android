/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_COMMON_VIDEO_INCLUDE_INCOMING_VIDEO_STREAM_H_
#define WEBRTC_COMMON_VIDEO_INCLUDE_INCOMING_VIDEO_STREAM_H_

#include <memory>

#include "webrtc/base/criticalsection.h"
#include "webrtc/base/platform_thread.h"
#include "webrtc/base/thread_annotations.h"
#include "webrtc/base/thread_checker.h"
#include "webrtc/common_video/video_render_frames.h"
#include "webrtc/media/base/videosinkinterface.h"

namespace webrtc {
class EventTimerWrapper;

class IncomingVideoStream : public rtc::VideoSinkInterface<VideoFrame> {
 public:
  IncomingVideoStream(int32_t delay_ms,
                      rtc::VideoSinkInterface<VideoFrame>* callback);
  ~IncomingVideoStream() override;

 protected:
  static bool IncomingVideoStreamThreadFun(void* obj);
  bool IncomingVideoStreamProcess();

 private:
  enum { kEventStartupTimeMs = 10 };
  enum { kEventMaxWaitTimeMs = 100 };
  enum { kFrameRatePeriodMs = 1000 };

  void OnFrame(const VideoFrame& video_frame) override;

  rtc::ThreadChecker main_thread_checker_;
  rtc::ThreadChecker render_thread_checker_;

  rtc::CriticalSection buffer_critsect_;
  rtc::PlatformThread incoming_render_thread_;
  std::unique_ptr<EventTimerWrapper> deliver_buffer_event_;

  rtc::VideoSinkInterface<VideoFrame>* const external_callback_;
  std::unique_ptr<VideoRenderFrames> render_buffers_
      GUARDED_BY(buffer_critsect_);
};

}  // namespace webrtc

#endif  // WEBRTC_COMMON_VIDEO_INCLUDE_INCOMING_VIDEO_STREAM_H_
