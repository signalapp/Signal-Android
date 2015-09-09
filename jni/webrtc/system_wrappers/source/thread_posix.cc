/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

// The state of a thread is controlled by the two member variables
// alive_ and dead_.
// alive_ represents the state the thread has been ordered to achieve.
// It is set to true by the thread at startup, and is set to false by
// other threads, using SetNotAlive() and Stop().
// dead_ represents the state the thread has achieved.
// It is written by the thread encapsulated by this class only
// (except at init). It is read only by the Stop() method.
// The Run() method fires event_ when it's started; this ensures that the
// Start() method does not continue until after dead_ is false.
// This protects against premature Stop() calls from the creator thread, but
// not from other threads.

// Their transitions and states:
// alive_    dead_  Set by
// false     true   Constructor
// true      false  Run() method entry
// false     any    Run() method run_function failure
// any       false  Run() method exit (happens only with alive_ false)
// false     any    SetNotAlive
// false     any    Stop         Stop waits for dead_ to become true.
//
// Summarized a different way:
// Variable   Writer               Reader
// alive_     Constructor(false)   Run.loop
//            Run.start(true)
//            Run.fail(false)
//            SetNotAlive(false)
//            Stop(false)
//
// dead_      Constructor(true)    Stop.loop
//            Run.start(false)
//            Run.exit(true)

#include "webrtc/system_wrappers/source/thread_posix.h"

#include <algorithm>

#include <assert.h>
#include <errno.h>
#include <string.h>  // strncpy
#include <unistd.h>
#ifdef WEBRTC_LINUX
#include <linux/unistd.h>
#include <sched.h>
#include <sys/prctl.h>
#include <sys/syscall.h>
#include <sys/types.h>
#endif

#include "webrtc/system_wrappers/interface/critical_section_wrapper.h"
#include "webrtc/system_wrappers/interface/event_wrapper.h"
#include "webrtc/system_wrappers/interface/sleep.h"
#include "webrtc/system_wrappers/interface/trace.h"

namespace webrtc {

int ConvertToSystemPriority(ThreadPriority priority, int min_prio,
                            int max_prio) {
  assert(max_prio - min_prio > 2);
  const int top_prio = max_prio - 1;
  const int low_prio = min_prio + 1;

  switch (priority) {
    case kLowPriority:
      return low_prio;
    case kNormalPriority:
      // The -1 ensures that the kHighPriority is always greater or equal to
      // kNormalPriority.
      return (low_prio + top_prio - 1) / 2;
    case kHighPriority:
      return std::max(top_prio - 2, low_prio);
    case kHighestPriority:
      return std::max(top_prio - 1, low_prio);
    case kRealtimePriority:
      return top_prio;
  }
  assert(false);
  return low_prio;
}

extern "C"
{
  static void* StartThread(void* lp_parameter) {
    static_cast<ThreadPosix*>(lp_parameter)->Run();
    return 0;
  }
}

ThreadWrapper* ThreadPosix::Create(ThreadRunFunction func, ThreadObj obj,
                                   ThreadPriority prio,
                                   const char* thread_name) {
  ThreadPosix* ptr = new ThreadPosix(func, obj, prio, thread_name);
  if (!ptr) {
    return NULL;
  }
  const int error = ptr->Construct();
  if (error) {
    delete ptr;
    return NULL;
  }
  return ptr;
}

ThreadPosix::ThreadPosix(ThreadRunFunction func, ThreadObj obj,
                         ThreadPriority prio, const char* thread_name)
    : run_function_(func),
      obj_(obj),
      crit_state_(CriticalSectionWrapper::CreateCriticalSection()),
      alive_(false),
      dead_(true),
      prio_(prio),
      event_(EventWrapper::Create()),
      name_(),
      set_thread_name_(false),
#if (defined(WEBRTC_LINUX) || defined(WEBRTC_ANDROID))
      pid_(-1),
#endif
      attr_(),
      thread_(0) {
  if (thread_name != NULL) {
    set_thread_name_ = true;
    strncpy(name_, thread_name, kThreadMaxNameLength);
    name_[kThreadMaxNameLength - 1] = '\0';
  }
}

uint32_t ThreadWrapper::GetThreadId() {
#if defined(WEBRTC_ANDROID) || defined(WEBRTC_LINUX)
  return static_cast<uint32_t>(syscall(__NR_gettid));
#elif defined(WEBRTC_MAC) || defined(WEBRTC_IOS)
  return pthread_mach_thread_np(pthread_self());
#else
  return reinterpret_cast<uint32_t>(pthread_self());
#endif
}

int ThreadPosix::Construct() {
  int result = 0;
#if !defined(WEBRTC_ANDROID)
  // Enable immediate cancellation if requested, see Shutdown().
  result = pthread_setcancelstate(PTHREAD_CANCEL_ENABLE, NULL);
  if (result != 0) {
    return -1;
  }
  result = pthread_setcanceltype(PTHREAD_CANCEL_ASYNCHRONOUS, NULL);
  if (result != 0) {
    return -1;
  }
#endif
  result = pthread_attr_init(&attr_);
  if (result != 0) {
    return -1;
  }
  return 0;
}

ThreadPosix::~ThreadPosix() {
  pthread_attr_destroy(&attr_);
  delete event_;
  delete crit_state_;
}

#define HAS_THREAD_ID !defined(WEBRTC_IOS) && !defined(WEBRTC_MAC)

bool ThreadPosix::Start(unsigned int& thread_id)
{
  int result = pthread_attr_setdetachstate(&attr_, PTHREAD_CREATE_DETACHED);
  // Set the stack stack size to 1M.
  result |= pthread_attr_setstacksize(&attr_, 1024 * 1024);
#ifdef WEBRTC_THREAD_RR
  const int policy = SCHED_RR;
#else
  const int policy = SCHED_FIFO;
#endif
  event_->Reset();
  // If pthread_create was successful, a thread was created and is running.
  // Don't return false if it was successful since if there are any other
  // failures the state will be: thread was started but not configured as
  // asked for. However, the caller of this API will assume that a false
  // return value means that the thread never started.
  result |= pthread_create(&thread_, &attr_, &StartThread, this);
  if (result != 0) {
    return false;
  }
  {
    CriticalSectionScoped cs(crit_state_);
    dead_ = false;
  }

  // Wait up to 10 seconds for the OS to call the callback function. Prevents
  // race condition if Stop() is called too quickly after start.
  if (kEventSignaled != event_->Wait(WEBRTC_EVENT_10_SEC)) {
    WEBRTC_TRACE(kTraceError, kTraceUtility, -1,
                 "posix thread event never triggered");
    // Timed out. Something went wrong.
    return true;
  }

#if HAS_THREAD_ID
  thread_id = static_cast<unsigned int>(thread_);
#endif
  sched_param param;

  const int min_prio = sched_get_priority_min(policy);
  const int max_prio = sched_get_priority_max(policy);

  if ((min_prio == EINVAL) || (max_prio == EINVAL)) {
    WEBRTC_TRACE(kTraceError, kTraceUtility, -1,
                 "unable to retreive min or max priority for threads");
    return true;
  }
  if (max_prio - min_prio <= 2) {
    // There is no room for setting priorities with any granularity.
    return true;
  }
  param.sched_priority = ConvertToSystemPriority(prio_, min_prio, max_prio);
  result = pthread_setschedparam(thread_, policy, &param);
  if (result == EINVAL) {
    WEBRTC_TRACE(kTraceError, kTraceUtility, -1,
                 "unable to set thread priority");
  }
  return true;
}

// CPU_ZERO and CPU_SET are not available in NDK r7, so disable
// SetAffinity on Android for now.
#if (defined(WEBRTC_LINUX) && (!defined(WEBRTC_ANDROID)))
bool ThreadPosix::SetAffinity(const int* processor_numbers,
                              const unsigned int amount_of_processors) {
  if (!processor_numbers || (amount_of_processors == 0)) {
    return false;
  }
  cpu_set_t mask;
  CPU_ZERO(&mask);

  for (unsigned int processor = 0;
       processor < amount_of_processors;
       ++processor) {
    CPU_SET(processor_numbers[processor], &mask);
  }
#if defined(WEBRTC_ANDROID)
  // Android.
  const int result = syscall(__NR_sched_setaffinity,
                             pid_,
                             sizeof(mask),
                             &mask);
#else
  // "Normal" Linux.
  const int result = sched_setaffinity(pid_,
                                       sizeof(mask),
                                       &mask);
#endif
  if (result != 0) {
    return false;
  }
  return true;
}

#else
// NOTE: On Mac OS X, use the Thread affinity API in
// /usr/include/mach/thread_policy.h: thread_policy_set and mach_thread_self()
// instead of Linux gettid() syscall.
bool ThreadPosix::SetAffinity(const int* , const unsigned int) {
  return false;
}
#endif

void ThreadPosix::SetNotAlive() {
  CriticalSectionScoped cs(crit_state_);
  alive_ = false;
}

bool ThreadPosix::Stop() {
  bool dead = false;
  {
    CriticalSectionScoped cs(crit_state_);
    alive_ = false;
    dead = dead_;
  }

  // TODO(hellner) why not use an event here?
  // Wait up to 10 seconds for the thread to terminate
  for (int i = 0; i < 1000 && !dead; ++i) {
    SleepMs(10);
    {
      CriticalSectionScoped cs(crit_state_);
      dead = dead_;
    }
  }
  if (dead) {
    return true;
  } else {
    return false;
  }
}

void ThreadPosix::Run() {
  {
    CriticalSectionScoped cs(crit_state_);
    alive_ = true;
  }
#if (defined(WEBRTC_LINUX) || defined(WEBRTC_ANDROID))
  pid_ = GetThreadId();
#endif
  // The event the Start() is waiting for.
  event_->Set();

  if (set_thread_name_) {
#ifdef WEBRTC_LINUX
    prctl(PR_SET_NAME, (unsigned long)name_, 0, 0, 0);
#endif
    WEBRTC_TRACE(kTraceStateInfo, kTraceUtility, -1,
                 "Thread with name:%s started ", name_);
  } else {
    WEBRTC_TRACE(kTraceStateInfo, kTraceUtility, -1,
                 "Thread without name started");
  }
  bool alive = true;
  bool run = true;
  while (alive) {
    run = run_function_(obj_);
    CriticalSectionScoped cs(crit_state_);
    if (!run) {
      alive_ = false;
    }
    alive = alive_;
  }

  if (set_thread_name_) {
    // Don't set the name for the trace thread because it may cause a
    // deadlock. TODO(hellner) there should be a better solution than
    // coupling the thread and the trace class like this.
    if (strcmp(name_, "Trace")) {
      WEBRTC_TRACE(kTraceStateInfo, kTraceUtility, -1,
                   "Thread with name:%s stopped", name_);
    }
  } else {
    WEBRTC_TRACE(kTraceStateInfo, kTraceUtility, -1,
                 "Thread without name stopped");
  }
  {
    CriticalSectionScoped cs(crit_state_);
    dead_ = true;
  }
}

}  // namespace webrtc
