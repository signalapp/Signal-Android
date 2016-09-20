/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/system_wrappers/source/event_timer_posix.h"

#include "testing/gtest/include/gtest/gtest.h"
#include "webrtc/base/event.h"
#include "webrtc/base/criticalsection.h"

namespace webrtc {

enum class ThreadState {
  kNotStarted,
  kWaiting,
  kRequestProcessCall,
  kCallingProcess,
  kProcessDone,
  kContinue,
  kExiting,
  kDead
};

class EventTimerPosixTest : public testing::Test, public EventTimerPosix {
 public:
  EventTimerPosixTest()
      : thread_state_(ThreadState::kNotStarted),
        process_event_(false, true),
        main_event_(false, true),
        process_thread_id_(0),
        process_thread_(nullptr) {}
  virtual ~EventTimerPosixTest() {}

  rtc::PlatformThread* CreateThread() override {
    EXPECT_TRUE(process_thread_ == nullptr);
    process_thread_ =
        new rtc::PlatformThread(Run, this, "EventTimerPosixTestThread");
    return process_thread_;
  }

  static bool Run(void* obj) {
    return static_cast<EventTimerPosixTest*>(obj)->Process();
  }

  bool Process() {
    bool res = ProcessInternal();
    if (!res) {
      rtc::CritScope cs(&lock_);
      thread_state_ = ThreadState::kDead;
      main_event_.Set();
    }
    return res;
  }

  bool ProcessInternal() {
    {
      rtc::CritScope cs(&lock_);
      if (thread_state_ == ThreadState::kNotStarted) {
        if (!ChangeThreadState(ThreadState::kNotStarted,
                               ThreadState::kContinue)) {
          ADD_FAILURE() << "Unable to start process thread";
          return false;
        }
        process_thread_id_ = rtc::CurrentThreadId();
      }
    }

    if (!ChangeThreadState(ThreadState::kContinue, ThreadState::kWaiting))
      return false;

    if (!AwaitThreadState(ThreadState::kRequestProcessCall,
                          rtc::Event::kForever))
      return false;

    if (!ChangeThreadState(ThreadState::kRequestProcessCall,
                           ThreadState::kCallingProcess))
      return false;

    EventTimerPosix::Process();

    if (!ChangeThreadState(ThreadState::kCallingProcess,
                           ThreadState::kProcessDone))
      return false;

    if (!AwaitThreadState(ThreadState::kContinue, rtc::Event::kForever))
      return false;

    return true;
  }

  bool IsProcessThread() {
    rtc::CritScope cs(&lock_);
    return process_thread_id_ == rtc::CurrentThreadId();
  }

  bool ChangeThreadState(ThreadState prev_state, ThreadState new_state) {
    rtc::CritScope cs(&lock_);
    if (thread_state_ != prev_state)
      return false;
    thread_state_ = new_state;
    if (IsProcessThread()) {
      main_event_.Set();
    } else {
      process_event_.Set();
    }
    return true;
  }

  bool AwaitThreadState(ThreadState state, int timeout) {
    rtc::Event* event = IsProcessThread() ? &process_event_ : &main_event_;
    do {
      rtc::CritScope cs(&lock_);
      if (state != ThreadState::kDead && thread_state_ == ThreadState::kExiting)
        return false;
      if (thread_state_ == state)
        return true;
    } while (event->Wait(timeout));
    return false;
  }

  bool CallProcess(int timeout_ms) {
    return AwaitThreadState(ThreadState::kWaiting, timeout_ms) &&
           ChangeThreadState(ThreadState::kWaiting,
                             ThreadState::kRequestProcessCall);
  }

  bool AwaitProcessDone(int timeout_ms) {
    return AwaitThreadState(ThreadState::kProcessDone, timeout_ms) &&
           ChangeThreadState(ThreadState::kProcessDone, ThreadState::kContinue);
  }

  void TearDown() override {
    if (process_thread_) {
      {
        rtc::CritScope cs(&lock_);
        if (thread_state_ != ThreadState::kDead) {
          thread_state_ = ThreadState::kExiting;
          process_event_.Set();
        }
      }
      ASSERT_TRUE(AwaitThreadState(ThreadState::kDead, 5000));
    }
  }

  ThreadState thread_state_;
  rtc::CriticalSection lock_;
  rtc::Event process_event_;
  rtc::Event main_event_;
  rtc::PlatformThreadId process_thread_id_;
  rtc::PlatformThread* process_thread_;
};

TEST_F(EventTimerPosixTest, WaiterBlocksUntilTimeout) {
  const int kTimerIntervalMs = 100;
  const int kTimeoutMs = 5000;
  ASSERT_TRUE(StartTimer(false, kTimerIntervalMs));
  ASSERT_TRUE(CallProcess(kTimeoutMs));
  EventTypeWrapper res = Wait(kTimeoutMs);
  EXPECT_EQ(kEventSignaled, res);
  ASSERT_TRUE(AwaitProcessDone(kTimeoutMs));
}

TEST_F(EventTimerPosixTest, WaiterWakesImmediatelyAfterTimeout) {
  const int kTimerIntervalMs = 100;
  const int kTimeoutMs = 5000;
  ASSERT_TRUE(StartTimer(false, kTimerIntervalMs));
  ASSERT_TRUE(CallProcess(kTimeoutMs));
  ASSERT_TRUE(AwaitProcessDone(kTimeoutMs));
  EventTypeWrapper res = Wait(0);
  EXPECT_EQ(kEventSignaled, res);
}

TEST_F(EventTimerPosixTest, WaiterBlocksUntilTimeoutProcessInactiveOnStart) {
  const int kTimerIntervalMs = 100;
  const int kTimeoutMs = 5000;
  // First call to StartTimer initializes thread.
  ASSERT_TRUE(StartTimer(false, kTimerIntervalMs));

  // Process thread currently _not_ blocking on Process() call.
  ASSERT_TRUE(AwaitThreadState(ThreadState::kWaiting, kTimeoutMs));

  // Start new one-off timer, then call Process().
  ASSERT_TRUE(StartTimer(false, kTimerIntervalMs));
  ASSERT_TRUE(CallProcess(kTimeoutMs));

  EventTypeWrapper res = Wait(kTimeoutMs);
  EXPECT_EQ(kEventSignaled, res);

  ASSERT_TRUE(AwaitProcessDone(kTimeoutMs));
}

}  // namespace webrtc
