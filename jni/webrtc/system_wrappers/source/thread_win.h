/*
 *  Copyright (c) 2011 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_SYSTEM_WRAPPERS_SOURCE_THREAD_WIN_H_
#define WEBRTC_SYSTEM_WRAPPERS_SOURCE_THREAD_WIN_H_

#include "webrtc/system_wrappers/interface/thread_wrapper.h"

#include <windows.h>

#include "webrtc/system_wrappers/interface/critical_section_wrapper.h"
#include "webrtc/system_wrappers/interface/event_wrapper.h"

namespace webrtc {

class ThreadWindows : public ThreadWrapper {
 public:
  ThreadWindows(ThreadRunFunction func, ThreadObj obj, ThreadPriority prio,
                const char* thread_name);
  virtual ~ThreadWindows();

  virtual bool Start(unsigned int& id);
  bool SetAffinity(const int* processor_numbers,
                   const unsigned int amount_of_processors);
  virtual bool Stop();
  virtual void SetNotAlive();

  static unsigned int WINAPI StartThread(LPVOID lp_parameter);

 protected:
  virtual void Run();

 private:
  ThreadRunFunction    run_function_;
  ThreadObj            obj_;

  bool                    alive_;
  bool                    dead_;

  // TODO(hellner)
  // do_not_close_handle_ member seem pretty redundant. Should be able to remove
  // it. Basically it should be fine to reclaim the handle when calling stop
  // and in the destructor.
  bool                    do_not_close_handle_;
  ThreadPriority          prio_;
  EventWrapper*           event_;
  CriticalSectionWrapper* critsect_stop_;

  HANDLE                  thread_;
  unsigned int            id_;
  char                    name_[kThreadMaxNameLength];
  bool                    set_thread_name_;

};

}  // namespace webrtc

#endif  // WEBRTC_SYSTEM_WRAPPERS_SOURCE_THREAD_WIN_H_
