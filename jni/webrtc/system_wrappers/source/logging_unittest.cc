/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/system_wrappers/interface/logging.h"

#include "testing/gtest/include/gtest/gtest.h"
#include "webrtc/system_wrappers/interface/condition_variable_wrapper.h"
#include "webrtc/system_wrappers/interface/critical_section_wrapper.h"
#include "webrtc/system_wrappers/interface/scoped_ptr.h"
#include "webrtc/system_wrappers/interface/sleep.h"
#include "webrtc/system_wrappers/interface/trace.h"

namespace webrtc {
namespace {

class LoggingTest : public ::testing::Test, public TraceCallback {
 public:
  virtual void Print(TraceLevel level, const char* msg, int length) {
    CriticalSectionScoped cs(crit_.get());
    // We test the length here to ensure (with high likelihood) that only our
    // traces will be tested.
    if (level_ != kTraceNone && static_cast<int>(expected_log_.str().size()) ==
        length - Trace::kBoilerplateLength - 1) {
      EXPECT_EQ(level_, level);
      EXPECT_EQ(expected_log_.str(), &msg[Trace::kBoilerplateLength]);
      level_ = kTraceNone;
      cv_->Wake();
    }
  }

 protected:
  LoggingTest()
    : crit_(CriticalSectionWrapper::CreateCriticalSection()),
      cv_(ConditionVariableWrapper::CreateConditionVariable()),
      level_(kTraceNone),
      expected_log_() {
  }

  void SetUp() {
    Trace::CreateTrace();
    Trace::SetTraceCallback(this);
  }

  void TearDown() {
    Trace::SetTraceCallback(NULL);
    Trace::ReturnTrace();
    CriticalSectionScoped cs(crit_.get());
    ASSERT_EQ(kTraceNone, level_) << "Print() was not called";
  }

  scoped_ptr<CriticalSectionWrapper> crit_;
  scoped_ptr<ConditionVariableWrapper> cv_;
  TraceLevel level_ GUARDED_BY(crit_);
  std::ostringstream expected_log_ GUARDED_BY(crit_);
};

TEST_F(LoggingTest, LogStream) {
  {
    CriticalSectionScoped cs(crit_.get());
    level_ = kTraceWarning;
    std::string msg = "Important message";
    expected_log_ << "(logging_unittest.cc:" << __LINE__ + 1 << "): " << msg;
    LOG(LS_WARNING) << msg;
    cv_->SleepCS(*crit_.get(), 2000);
  }
}

TEST_F(LoggingTest, LogFunctionError) {
  {
    CriticalSectionScoped cs(crit_.get());
    int bar = 42;
    int baz = 99;
    level_ = kTraceError;
    expected_log_ << "(logging_unittest.cc:" << __LINE__ + 2
                  << "): Foo failed: bar=" << bar << ", baz=" << baz;
    LOG_FERR2(LS_ERROR, Foo, bar, baz);
    cv_->SleepCS(*crit_.get(), 2000);
  }
}

}  // namespace
}  // namespace webrtc
