/*
 *  Copyright (c) 2011 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

// System independant wrapper for spawning threads
// Note: the spawned thread will loop over the callback function until stopped.
// Note: The callback function is expected to return every 2 seconds or more
// often.

#ifndef WEBRTC_SYSTEM_WRAPPERS_INTERFACE_THREAD_WRAPPER_H_
#define WEBRTC_SYSTEM_WRAPPERS_INTERFACE_THREAD_WRAPPER_H_

#include "webrtc/common_types.h"
#include "webrtc/typedefs.h"

namespace webrtc {

// Object that will be passed by the spawned thread when it enters the callback
// function.
#define ThreadObj void*

// Callback function that the spawned thread will enter once spawned.
// A return value of false is interpreted as that the function has no
// more work to do and that the thread can be released.
typedef bool(*ThreadRunFunction)(ThreadObj);

enum ThreadPriority {
  kLowPriority = 1,
  kNormalPriority = 2,
  kHighPriority = 3,
  kHighestPriority = 4,
  kRealtimePriority = 5
};

class ThreadWrapper {
 public:
  enum {kThreadMaxNameLength = 64};

  virtual ~ThreadWrapper() {};

  // Factory method. Constructor disabled.
  //
  // func        Pointer to a, by user, specified callback function.
  // obj         Object associated with the thread. Passed in the callback
  //             function.
  // prio        Thread priority. May require root/admin rights.
  // thread_name  NULL terminated thread name, will be visable in the Windows
  //             debugger.
  static ThreadWrapper* CreateThread(ThreadRunFunction func,
                                     ThreadObj obj,
                                     ThreadPriority prio = kNormalPriority,
                                     const char* thread_name = 0);

  // Get the current thread's kernel thread ID.
  static uint32_t GetThreadId();

  // Non blocking termination of the spawned thread. Note that it is not safe
  // to delete this class until the spawned thread has been reclaimed.
  virtual void SetNotAlive() = 0;

  // Tries to spawns a thread and returns true if that was successful.
  // Additionally, it tries to set thread priority according to the priority
  // from when CreateThread was called. However, failure to set priority will
  // not result in a false return value.
  // TODO(henrike): add a function for polling whether priority was set or
  //                not.
  virtual bool Start(unsigned int& id) = 0;

  // Sets the threads CPU affinity. CPUs are listed 0 - (number of CPUs - 1).
  // The numbers in processor_numbers specify which CPUs are allowed to run the
  // thread. processor_numbers should not contain any duplicates and elements
  // should be lower than (number of CPUs - 1). amount_of_processors should be
  // equal to the number of processors listed in processor_numbers.
  virtual bool SetAffinity(const int* processor_numbers,
                           const unsigned int amount_of_processors);

  // Stops the spawned thread and waits for it to be reclaimed with a timeout
  // of two seconds. Will return false if the thread was not reclaimed.
  // Multiple tries to Stop are allowed (e.g. to wait longer than 2 seconds).
  // It's ok to call Stop() even if the spawned thread has been reclaimed.
  virtual bool Stop() = 0;
};

}  // namespace webrtc

#endif  // WEBRTC_SYSTEM_WRAPPERS_INTERFACE_THREAD_WRAPPER_H_
