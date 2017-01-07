/*
 *  Copyright 2007 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
//
// A reuseable entry point for gunit tests.

#if defined(WEBRTC_WIN)
#include <crtdbg.h>
#endif

#include "webrtc/base/flags.h"
#include "webrtc/base/fileutils.h"
#include "webrtc/base/gunit.h"
#include "webrtc/base/logging.h"
#include "webrtc/base/ssladapter.h"
#include "webrtc/test/field_trial.h"
#include "webrtc/test/testsupport/fileutils.h"

DEFINE_bool(help, false, "prints this message");
DEFINE_string(log, "", "logging options to use");
DEFINE_string(
    force_fieldtrials,
    "",
    "Field trials control experimental feature code which can be forced. "
    "E.g. running with --force_fieldtrials=WebRTC-FooFeature/Enable/"
    " will assign the group Enable to field trial WebRTC-FooFeature.");
#if defined(WEBRTC_WIN)
DEFINE_int(crt_break_alloc, -1, "memory allocation to break on");
DEFINE_bool(default_error_handlers, false,
            "leave the default exception/dbg handler functions in place");

void TestInvalidParameterHandler(const wchar_t* expression,
                                 const wchar_t* function,
                                 const wchar_t* file,
                                 unsigned int line,
                                 uintptr_t pReserved) {
  LOG(LS_ERROR) << "InvalidParameter Handler called.  Exiting.";
  LOG(LS_ERROR) << expression << std::endl << function << std::endl << file
                << std::endl << line;
  exit(1);
}
void TestPureCallHandler() {
  LOG(LS_ERROR) << "Purecall Handler called.  Exiting.";
  exit(1);
}
int TestCrtReportHandler(int report_type, char* msg, int* retval) {
    LOG(LS_ERROR) << "CrtReport Handler called...";
    LOG(LS_ERROR) << msg;
  if (report_type == _CRT_ASSERT) {
    exit(1);
  } else {
    *retval = 0;
    return TRUE;
  }
}
#endif  // WEBRTC_WIN

int main(int argc, char** argv) {
  testing::InitGoogleTest(&argc, argv);
  rtc::FlagList::SetFlagsFromCommandLine(&argc, argv, false);
  if (FLAG_help) {
    rtc::FlagList::Print(NULL, false);
    return 0;
  }

  webrtc::test::SetExecutablePath(argv[0]);
  webrtc::test::InitFieldTrialsFromString(FLAG_force_fieldtrials);

#if defined(WEBRTC_WIN)
  if (!FLAG_default_error_handlers) {
    // Make sure any errors don't throw dialogs hanging the test run.
    _set_invalid_parameter_handler(TestInvalidParameterHandler);
    _set_purecall_handler(TestPureCallHandler);
    _CrtSetReportHook2(_CRT_RPTHOOK_INSTALL, TestCrtReportHandler);
  }

#if !defined(NDEBUG)  // Turn on memory leak checking on Windows.
  _CrtSetDbgFlag(_CRTDBG_ALLOC_MEM_DF |_CRTDBG_LEAK_CHECK_DF);
  if (FLAG_crt_break_alloc >= 0) {
    _crtBreakAlloc = FLAG_crt_break_alloc;
  }
#endif
#endif  // WEBRTC_WIN

  rtc::Filesystem::SetOrganizationName("google");
  rtc::Filesystem::SetApplicationName("unittest");

  // By default, log timestamps. Allow overrides by used of a --log flag.
  rtc::LogMessage::LogTimestamps();
  if (*FLAG_log != '\0') {
    rtc::LogMessage::ConfigureLogging(FLAG_log);
  } else if (rtc::LogMessage::GetLogToDebug() > rtc::LS_INFO) {
    // Default to LS_INFO, even for release builds to provide better test
    // logging.
    rtc::LogMessage::LogToDebug(rtc::LS_INFO);
  }

  // Initialize SSL which are used by several tests.
  rtc::InitializeSSL();

  int res = RUN_ALL_TESTS();

  rtc::CleanupSSL();

  // clean up logging so we don't appear to leak memory.
  rtc::LogMessage::ConfigureLogging("");

#if defined(WEBRTC_WIN)
  // Unhook crt function so that we don't ever log after statics have been
  // uninitialized.
  if (!FLAG_default_error_handlers)
    _CrtSetReportHook2(_CRT_RPTHOOK_REMOVE, TestCrtReportHandler);
#endif

  return res;
}
