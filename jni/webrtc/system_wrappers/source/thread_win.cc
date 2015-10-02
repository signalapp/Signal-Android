/*
 *  Copyright (c) 2011 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/system_wrappers/source/thread_win.h"

#include <assert.h>
#include <process.h>
#include <stdio.h>
#include <windows.h>

#include "webrtc/system_wrappers/interface/trace.h"
#include "webrtc/system_wrappers/source/set_thread_name_win.h"

namespace webrtc {

ThreadWindows::ThreadWindows(ThreadRunFunction func, ThreadObj obj,
                             ThreadPriority prio, const char* thread_name)
    : ThreadWrapper(),
      run_function_(func),
      obj_(obj),
      alive_(false),
      dead_(true),
      do_not_close_handle_(false),
      prio_(prio),
      event_(NULL),
      thread_(NULL),
      id_(0),
      name_(),
      set_thread_name_(false) {
  event_ = EventWrapper::Create();
  critsect_stop_ = CriticalSectionWrapper::CreateCriticalSection();
  if (thread_name != NULL) {
    // Set the thread name to appear in the VS debugger.
    set_thread_name_ = true;
    strncpy(name_, thread_name, kThreadMaxNameLength);
  }
}

ThreadWindows::~ThreadWindows() {
#ifdef _DEBUG
  assert(!alive_);
#endif
  if (thread_) {
    CloseHandle(thread_);
  }
  if (event_) {
    delete event_;
  }
  if (critsect_stop_) {
    delete critsect_stop_;
  }
}

uint32_t ThreadWrapper::GetThreadId() {
  return GetCurrentThreadId();
}

unsigned int WINAPI ThreadWindows::StartThread(LPVOID lp_parameter) {
  static_cast<ThreadWindows*>(lp_parameter)->Run();
  return 0;
}

bool ThreadWindows::Start(unsigned int& thread_id) {
  if (!run_function_) {
    return false;
  }
  do_not_close_handle_ = false;

  // Set stack size to 1M
  thread_ = (HANDLE)_beginthreadex(NULL, 1024 * 1024, StartThread, (void*)this,
                                   0, &thread_id);
  if (thread_ == NULL) {
    return false;
  }
  id_ = thread_id;
  event_->Wait(INFINITE);

  switch (prio_) {
    case kLowPriority:
      SetThreadPriority(thread_, THREAD_PRIORITY_BELOW_NORMAL);
      break;
    case kNormalPriority:
      SetThreadPriority(thread_, THREAD_PRIORITY_NORMAL);
      break;
    case kHighPriority:
      SetThreadPriority(thread_, THREAD_PRIORITY_ABOVE_NORMAL);
      break;
    case kHighestPriority:
      SetThreadPriority(thread_, THREAD_PRIORITY_HIGHEST);
      break;
    case kRealtimePriority:
      SetThreadPriority(thread_, THREAD_PRIORITY_TIME_CRITICAL);
      break;
  };
  return true;
}

bool ThreadWindows::SetAffinity(const int* processor_numbers,
                                const unsigned int amount_of_processors) {
  DWORD_PTR processor_bit_mask = 0;
  for (unsigned int processor_index = 0;
       processor_index < amount_of_processors;
       ++processor_index) {
    // Convert from an array with processor numbers to a bitmask
    // Processor numbers start at zero.
    // TODO(hellner): this looks like a bug. Shouldn't the '=' be a '+='?
    // Or even better |=
    processor_bit_mask = 1 << processor_numbers[processor_index];
  }
  return SetThreadAffinityMask(thread_, processor_bit_mask) != 0;
}

void ThreadWindows::SetNotAlive() {
  alive_ = false;
}

bool ThreadWindows::Stop() {
  critsect_stop_->Enter();

  // Prevents the handle from being closed in ThreadWindows::Run()
  do_not_close_handle_ = true;
  alive_ = false;
  bool signaled = false;
  if (thread_ && !dead_) {
    critsect_stop_->Leave();

    // Wait up to 2 seconds for the thread to complete.
    if (WAIT_OBJECT_0 == WaitForSingleObject(thread_, 2000)) {
      signaled = true;
    }
    critsect_stop_->Enter();
  }
  if (thread_) {
    CloseHandle(thread_);
    thread_ = NULL;
  }
  critsect_stop_->Leave();

  if (dead_ || signaled) {
    return true;
  } else {
    return false;
  }
}

void ThreadWindows::Run() {
  alive_ = true;
  dead_ = false;
  event_->Set();

  // All tracing must be after event_->Set to avoid deadlock in Trace.
  if (set_thread_name_) {
    WEBRTC_TRACE(kTraceStateInfo, kTraceUtility, id_,
                 "Thread with name:%s started ", name_);
    SetThreadName(static_cast<DWORD>(-1), name_); // -1 == caller thread.
  } else {
    WEBRTC_TRACE(kTraceStateInfo, kTraceUtility, id_,
                 "Thread without name started");
  }

  do {
    if (run_function_) {
      if (!run_function_(obj_)) {
        alive_ = false;
      }
    } else {
      alive_ = false;
    }
  } while (alive_);

  if (set_thread_name_) {
    WEBRTC_TRACE(kTraceStateInfo, kTraceUtility, id_,
                 "Thread with name:%s stopped", name_);
  } else {
    WEBRTC_TRACE(kTraceStateInfo, kTraceUtility, id_,
                 "Thread without name stopped");
  }

  critsect_stop_->Enter();

  if (thread_ && !do_not_close_handle_) {
    HANDLE thread = thread_;
    thread_ = NULL;
    CloseHandle(thread);
  }
  dead_ = true;

  critsect_stop_->Leave();
};

}  // namespace webrtc
