/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/system_wrappers/interface/condition_variable_wrapper.h"

#include "testing/gtest/include/gtest/gtest.h"
#include "webrtc/system_wrappers/interface/critical_section_wrapper.h"
#include "webrtc/system_wrappers/interface/thread_wrapper.h"
#include "webrtc/system_wrappers/interface/trace.h"

namespace webrtc {

namespace {

const int kLongWaitMs = 100 * 1000; // A long time in testing terms
const int kShortWaitMs = 2 * 1000; // Long enough for process switches to happen

// A Baton is one possible control structure one can build using
// conditional variables.
// A Baton is always held by one and only one active thread - unlike
// a lock, it can never be free.
// One can pass it or grab it - both calls have timeouts.
// Note - a production tool would guard against passing it without
// grabbing it first. This one is for testing, so it doesn't.
class Baton {
 public:
  Baton()
    : giver_sect_(CriticalSectionWrapper::CreateCriticalSection()),
      crit_sect_(CriticalSectionWrapper::CreateCriticalSection()),
      cond_var_(ConditionVariableWrapper::CreateConditionVariable()),
      being_passed_(false),
      pass_count_(0) {
  }

  ~Baton() {
    delete giver_sect_;
    delete crit_sect_;
    delete cond_var_;
  }

  // Pass the baton. Returns false if baton is not picked up in |max_msecs|.
  // Only one process can pass at the same time; this property is
  // ensured by the |giver_sect_| lock.
  bool Pass(uint32_t max_msecs) {
    CriticalSectionScoped cs_giver(giver_sect_);
    CriticalSectionScoped cs(crit_sect_);
    SignalBatonAvailable();
    const bool result = TakeBatonIfStillFree(max_msecs);
    if (result) {
      ++pass_count_;
    }
    return result;
  }

  // Grab the baton. Returns false if baton is not passed.
  bool Grab(uint32_t max_msecs) {
    CriticalSectionScoped cs(crit_sect_);
    return WaitUntilBatonOffered(max_msecs);
  }

  int PassCount() {
    // We don't allow polling PassCount() during a Pass()-call since there is
    // no guarantee that |pass_count_| is incremented until the Pass()-call
    // finishes. I.e. the Grab()-call may finish before |pass_count_| has been
    // incremented.
    // Thus, this function waits on giver_sect_.
    CriticalSectionScoped cs(giver_sect_);
    return pass_count_;
  }

 private:
  // Wait/Signal forms a classical semaphore on |being_passed_|.
  // These functions must be called with crit_sect_ held.
  bool WaitUntilBatonOffered(int timeout_ms) {
    while (!being_passed_) {
      if (!cond_var_->SleepCS(*crit_sect_, timeout_ms)) {
        return false;
      }
    }
    being_passed_ = false;
    cond_var_->Wake();
    return true;
  }

  void SignalBatonAvailable() {
    assert(!being_passed_);
    being_passed_ = true;
    cond_var_->Wake();
  }

  // Timeout extension: Wait for a limited time for someone else to
  // take it, and take it if it's not taken.
  // Returns true if resource is taken by someone else, false
  // if it is taken back by the caller.
  // This function must be called with both |giver_sect_| and
  // |crit_sect_| held.
  bool TakeBatonIfStillFree(int timeout_ms) {
    bool not_timeout = true;
    while (being_passed_ && not_timeout) {
      not_timeout = cond_var_->SleepCS(*crit_sect_, timeout_ms);
      // If we're woken up while variable is still held, we may have
      // gotten a wakeup destined for a grabber thread.
      // This situation is not treated specially here.
    }
    if (!being_passed_) {
      return true;
    } else {
      assert(!not_timeout);
      being_passed_ = false;
      return false;
    }
  }

  // Lock that ensures that there is only one thread in the active
  // part of Pass() at a time.
  // |giver_sect_| must always be acquired before |cond_var_|.
  CriticalSectionWrapper* giver_sect_;
  // Lock that protects |being_passed_|.
  CriticalSectionWrapper* crit_sect_;
  ConditionVariableWrapper* cond_var_;
  bool being_passed_;
  // Statistics information: Number of successfull passes.
  int pass_count_;
};

// Function that waits on a Baton, and passes it right back.
// We expect these calls never to time out.
bool WaitingRunFunction(void* obj) {
  Baton* the_baton = static_cast<Baton*> (obj);
  EXPECT_TRUE(the_baton->Grab(kLongWaitMs));
  EXPECT_TRUE(the_baton->Pass(kLongWaitMs));
  return true;
}

class CondVarTest : public ::testing::Test {
 public:
  CondVarTest() {}

  virtual void SetUp() {
    thread_ = ThreadWrapper::CreateThread(&WaitingRunFunction,
                                          &baton_);
    unsigned int id = 42;
    ASSERT_TRUE(thread_->Start(id));
  }

  virtual void TearDown() {
    // We have to wake the thread in order to make it obey the stop order.
    // But we don't know if the thread has completed the run function, so
    // we don't know if it will exit before or after the Pass.
    // Thus, we need to pin it down inside its Run function (between Grab
    // and Pass).
    ASSERT_TRUE(baton_.Pass(kShortWaitMs));
    thread_->SetNotAlive();
    ASSERT_TRUE(baton_.Grab(kShortWaitMs));
    ASSERT_TRUE(thread_->Stop());
    delete thread_;
  }

 protected:
  Baton baton_;

 private:
  ThreadWrapper* thread_;
};

// The SetUp and TearDown functions use condition variables.
// This test verifies those pieces in isolation.
TEST_F(CondVarTest, InitFunctionsWork) {
  // All relevant asserts are in the SetUp and TearDown functions.
}

// This test verifies that one can use the baton multiple times.
TEST_F(CondVarTest, PassBatonMultipleTimes) {
  const int kNumberOfRounds = 2;
  for (int i = 0; i < kNumberOfRounds; ++i) {
    ASSERT_TRUE(baton_.Pass(kShortWaitMs));
    ASSERT_TRUE(baton_.Grab(kShortWaitMs));
  }
  EXPECT_EQ(2 * kNumberOfRounds, baton_.PassCount());
}

}  // anonymous namespace

}  // namespace webrtc
