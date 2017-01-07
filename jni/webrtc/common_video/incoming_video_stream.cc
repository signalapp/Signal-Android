/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/common_video/include/incoming_video_stream.h"

#include "webrtc/base/timeutils.h"
#include "webrtc/common_video/video_render_frames.h"
#include "webrtc/system_wrappers/include/critical_section_wrapper.h"
#include "webrtc/system_wrappers/include/event_wrapper.h"

namespace webrtc {

IncomingVideoStream::IncomingVideoStream(
    int32_t delay_ms,
    rtc::VideoSinkInterface<VideoFrame>* callback)
    : incoming_render_thread_(&IncomingVideoStreamThreadFun,
                              this,
                              "IncomingVideoStreamThread"),
      deliver_buffer_event_(EventTimerWrapper::Create()),
      external_callback_(callback),
      render_buffers_(new VideoRenderFrames(delay_ms)) {
  RTC_DCHECK(external_callback_);

  render_thread_checker_.DetachFromThread();

  deliver_buffer_event_->StartTimer(false, kEventStartupTimeMs);
  incoming_render_thread_.Start();
  incoming_render_thread_.SetPriority(rtc::kRealtimePriority);
}

IncomingVideoStream::~IncomingVideoStream() {
  RTC_DCHECK(main_thread_checker_.CalledOnValidThread());

  {
    rtc::CritScope cs(&buffer_critsect_);
    render_buffers_.reset();
  }

  deliver_buffer_event_->Set();
  incoming_render_thread_.Stop();
  deliver_buffer_event_->StopTimer();
}

void IncomingVideoStream::OnFrame(const VideoFrame& video_frame) {
  // Most of the time we'll be on a decoder thread here, but when using
  // VideoToolbox on iOS, we'll get called on a thread from a thread pool.

  // Hand over or insert frame.
  rtc::CritScope csB(&buffer_critsect_);
  if (render_buffers_->AddFrame(video_frame) == 1) {
    deliver_buffer_event_->Set();
  }
}

bool IncomingVideoStream::IncomingVideoStreamThreadFun(void* obj) {
  return static_cast<IncomingVideoStream*>(obj)->IncomingVideoStreamProcess();
}

bool IncomingVideoStream::IncomingVideoStreamProcess() {
  RTC_DCHECK_RUN_ON(&render_thread_checker_);

  if (kEventError != deliver_buffer_event_->Wait(kEventMaxWaitTimeMs)) {
    // Get a new frame to render and the time for the frame after this one.
    rtc::Optional<VideoFrame> frame_to_render;
    uint32_t wait_time;
    {
      rtc::CritScope cs(&buffer_critsect_);
      if (!render_buffers_.get()) {
        // Terminating
        return false;
      }
      frame_to_render = render_buffers_->FrameToRender();
      wait_time = render_buffers_->TimeToNextFrameRelease();
    }

    // Set timer for next frame to render.
    if (wait_time > kEventMaxWaitTimeMs) {
      wait_time = kEventMaxWaitTimeMs;
    }

    deliver_buffer_event_->StartTimer(false, wait_time);

    if (frame_to_render) {
      external_callback_->OnFrame(*frame_to_render);
    }
  }
  return true;
}

}  // namespace webrtc
