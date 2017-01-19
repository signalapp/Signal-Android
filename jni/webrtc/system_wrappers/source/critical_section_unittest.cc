/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/system_wrappers/interface/critical_section_wrapper.h"

#include "testing/gtest/include/gtest/gtest.h"
#include "webrtc/system_wrappers/interface/sleep.h"
#include "webrtc/system_wrappers/interface/thread_wrapper.h"
#include "webrtc/system_wrappers/interface/trace.h"

namespace webrtc {

namespace {

// Cause a process switch. Needed to avoid depending on
// busy-wait in tests.
static void SwitchProcess() {
  // Note - sched_yield has been tried as process switch. This does
  // not cause a process switch enough of the time for reliability.
  SleepMs(1);
}

class ProtectedCount {
public:
  explicit ProtectedCount(CriticalSectionWrapper* crit_sect)
    : crit_sect_(crit_sect),
      count_(0) {
  }

  void Increment() {
    CriticalSectionScoped cs(crit_sect_);
    ++count_;
  }

  int Count() const {
    CriticalSectionScoped cs(crit_sect_);
    return count_;
  }

private:
  CriticalSectionWrapper* crit_sect_;
  int count_;
};

class CritSectTest : public ::testing::Test {
public:
  CritSectTest() {}

  // Waits a number of cycles for the count to reach a given value.
  // Returns true if the target is reached or passed.
  bool WaitForCount(int target, ProtectedCount* count) {
    int loop_counter = 0;
    // On Posix, this SwitchProcess() needs to be in a loop to make the
    // test both fast and non-flaky.
    // With 1 us wait as the switch, up to 7 rounds have been observed.
    while (count->Count() < target && loop_counter < 100 * target) {
      ++loop_counter;
      SwitchProcess();
    }
    return (count->Count() >= target);
  }
};

bool LockUnlockThenStopRunFunction(void* obj) {
  ProtectedCount* the_count = static_cast<ProtectedCount*>(obj);
  the_count->Increment();
  return false;
}

TEST_F(CritSectTest, ThreadWakesOnce) NO_THREAD_SAFETY_ANALYSIS {
  CriticalSectionWrapper* crit_sect =
      CriticalSectionWrapper::CreateCriticalSection();
  ProtectedCount count(crit_sect);
  ThreadWrapper* thread = ThreadWrapper::CreateThread(
      &LockUnlockThenStopRunFunction, &count);
  unsigned int id = 42;
  crit_sect->Enter();
  ASSERT_TRUE(thread->Start(id));
  SwitchProcess();
  // The critical section is of reentrant mode, so this should not release
  // the lock, even though count.Count() locks and unlocks the critical section
  // again.
  // Thus, the thread should not be able to increment the count
  ASSERT_EQ(0, count.Count());
  crit_sect->Leave();  // This frees the thread to act.
  EXPECT_TRUE(WaitForCount(1, &count));
  EXPECT_TRUE(thread->Stop());
  delete thread;
  delete crit_sect;
}

bool LockUnlockRunFunction(void* obj) {
  ProtectedCount* the_count = static_cast<ProtectedCount*>(obj);
  the_count->Increment();
  SwitchProcess();
  return true;
}

TEST_F(CritSectTest, ThreadWakesTwice) NO_THREAD_SAFETY_ANALYSIS {
  CriticalSectionWrapper* crit_sect =
      CriticalSectionWrapper::CreateCriticalSection();
  ProtectedCount count(crit_sect);
  ThreadWrapper* thread = ThreadWrapper::CreateThread(&LockUnlockRunFunction,
                                                      &count);
  unsigned int id = 42;
  crit_sect->Enter();  // Make sure counter stays 0 until we wait for it.
  ASSERT_TRUE(thread->Start(id));
  crit_sect->Leave();

  // The thread is capable of grabbing the lock multiple times,
  // incrementing counter once each time.
  // It's possible for the count to be incremented by more than 2.
  EXPECT_TRUE(WaitForCount(2, &count));
  EXPECT_LE(2, count.Count());

  // The thread does not increment while lock is held.
  crit_sect->Enter();
  int count_before = count.Count();
  for (int i = 0; i < 10; i++) {
    SwitchProcess();
  }
  EXPECT_EQ(count_before, count.Count());
  crit_sect->Leave();

  thread->SetNotAlive();  // Tell thread to exit once run function finishes.
  SwitchProcess();
  EXPECT_TRUE(WaitForCount(count_before + 1, &count));
  EXPECT_TRUE(thread->Stop());
  delete thread;
  delete crit_sect;
}

}  // anonymous namespace

}  // namespace webrtc
