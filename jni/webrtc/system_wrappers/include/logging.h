/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

// This is a highly stripped-down version of libjingle's talk/base/logging.h.
// It is a thin wrapper around WEBRTC_TRACE, maintaining the libjingle log
// semantics to ease a transition to that format.

// NOTE: LS_INFO maps to a new trace level which should be reserved for
// infrequent, non-verbose logs. The other levels below kTraceWarning have been
// rendered essentially useless due to their verbosity. Carefully consider the
// impact of adding a new LS_INFO log. If it will be logged at anything
// approaching a frame or packet frequency, use LS_VERBOSE if necessary, or
// preferably, do not log at all.

//   LOG(...) an ostream target that can be used to send formatted
// output to a variety of logging targets, such as debugger console, stderr,
// file, or any StreamInterface.
//   The severity level passed as the first argument to the LOGging
// functions is used as a filter, to limit the verbosity of the logging.
//   Static members of LogMessage documented below are used to control the
// verbosity and target of the output.
//   There are several variations on the LOG macro which facilitate logging
// of common error conditions, detailed below.

// LOG(sev) logs the given stream at severity "sev", which must be a
//     compile-time constant of the LoggingSeverity type, without the namespace
//     prefix.
// LOG_V(sev) Like LOG(), but sev is a run-time variable of the LoggingSeverity
//     type (basically, it just doesn't prepend the namespace).
// LOG_F(sev) Like LOG(), but includes the name of the current function.

#ifndef WEBRTC_SYSTEM_WRAPPERS_INCLUDE_LOGGING_H_
#define WEBRTC_SYSTEM_WRAPPERS_INCLUDE_LOGGING_H_

#include <sstream>

namespace webrtc {

//////////////////////////////////////////////////////////////////////

// Note that the non-standard LoggingSeverity aliases exist because they are
// still in broad use.  The meanings of the levels are:
//  LS_SENSITIVE: Information which should only be logged with the consent
//   of the user, due to privacy concerns.
//  LS_VERBOSE: This level is for data which we do not want to appear in the
//   normal debug log, but should appear in diagnostic logs.
//  LS_INFO: Chatty level used in debugging for all sorts of things, the default
//   in debug builds.
//  LS_WARNING: Something that may warrant investigation.
//  LS_ERROR: Something that should not have occurred.
enum LoggingSeverity {
  LS_SENSITIVE, LS_VERBOSE, LS_INFO, LS_WARNING, LS_ERROR
};

class LogMessage {
 public:
  LogMessage(const char* file, int line, LoggingSeverity sev);
  ~LogMessage();

  static bool Loggable(LoggingSeverity sev);
  std::ostream& stream() { return print_stream_; }

 private:
  // The ostream that buffers the formatted message before output
  std::ostringstream print_stream_;

  // The severity level of this message
  LoggingSeverity severity_;
};

//////////////////////////////////////////////////////////////////////
// Macros which automatically disable logging when WEBRTC_LOGGING == 0
//////////////////////////////////////////////////////////////////////

#ifndef LOG
// The following non-obvious technique for implementation of a
// conditional log stream was stolen from google3/base/logging.h.

// This class is used to explicitly ignore values in the conditional
// logging macros.  This avoids compiler warnings like "value computed
// is not used" and "statement has no effect".

class LogMessageVoidify {
 public:
  LogMessageVoidify() { }
  // This has to be an operator with a precedence lower than << but
  // higher than ?:
  void operator&(std::ostream&) { }
};

#if defined(WEBRTC_RESTRICT_LOGGING)
// This should compile away logs matching the following condition.
#define RESTRICT_LOGGING_PRECONDITION(sev)  \
  sev < webrtc::LS_INFO ? (void) 0 :
#else
#define RESTRICT_LOGGING_PRECONDITION(sev)
#endif

#define LOG_SEVERITY_PRECONDITION(sev) \
  RESTRICT_LOGGING_PRECONDITION(sev) !(webrtc::LogMessage::Loggable(sev)) \
    ? (void) 0 \
    : webrtc::LogMessageVoidify() &

#define LOG(sev) \
  LOG_SEVERITY_PRECONDITION(webrtc::sev) \
    webrtc::LogMessage(__FILE__, __LINE__, webrtc::sev).stream()

// The _V version is for when a variable is passed in.  It doesn't do the
// namespace concatination.
#define LOG_V(sev) \
  LOG_SEVERITY_PRECONDITION(sev) \
    webrtc::LogMessage(__FILE__, __LINE__, sev).stream()

// The _F version prefixes the message with the current function name.
#if (defined(__GNUC__) && !defined(NDEBUG)) || defined(WANT_PRETTY_LOG_F)
#define LOG_F(sev) LOG(sev) << __PRETTY_FUNCTION__ << ": "
#else
#define LOG_F(sev) LOG(sev) << __FUNCTION__ << ": "
#endif

#endif  // LOG

}  // namespace webrtc

#endif  // WEBRTC_SYSTEM_WRAPPERS_INCLUDE_LOGGING_H_
