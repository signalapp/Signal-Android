/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#if defined(WEBRTC_WIN)
#if !defined(WIN32_LEAN_AND_MEAN)
#define WIN32_LEAN_AND_MEAN
#endif
#include <windows.h>
#if _MSC_VER < 1900
#define snprintf _snprintf
#endif
#undef ERROR  // wingdi.h
#endif

#if defined(WEBRTC_MAC) && !defined(WEBRTC_IOS)
#include <CoreServices/CoreServices.h>
#elif defined(WEBRTC_ANDROID)
#include <android/log.h>
// Android has a 1024 limit on log inputs. We use 60 chars as an
// approx for the header/tag portion.
// See android/system/core/liblog/logd_write.c
static const int kMaxLogLineSize = 1024 - 60;
#endif  // WEBRTC_MAC && !defined(WEBRTC_IOS) || WEBRTC_ANDROID

static const char kLibjingle[] = "libjingle";

#include <time.h>
#include <limits.h>

#include <algorithm>
#include <iomanip>
#include <ostream>
#include <vector>

#include "webrtc/base/criticalsection.h"
#include "webrtc/base/logging.h"
#include "webrtc/base/platform_thread.h"
#include "webrtc/base/stringencode.h"
#include "webrtc/base/stringutils.h"
#include "webrtc/base/timeutils.h"

namespace rtc {
namespace {

// Return the filename portion of the string (that following the last slash).
const char* FilenameFromPath(const char* file) {
  const char* end1 = ::strrchr(file, '/');
  const char* end2 = ::strrchr(file, '\\');
  if (!end1 && !end2)
    return file;
  else
    return (end1 > end2) ? end1 + 1 : end2 + 1;
}

}  // namespace

/////////////////////////////////////////////////////////////////////////////
// Constant Labels
/////////////////////////////////////////////////////////////////////////////

const char* FindLabel(int value, const ConstantLabel entries[]) {
  for (int i = 0; entries[i].label; ++i) {
    if (value == entries[i].value) {
      return entries[i].label;
    }
  }
  return 0;
}

std::string ErrorName(int err, const ConstantLabel* err_table) {
  if (err == 0)
    return "No error";

  if (err_table != 0) {
    if (const char* value = FindLabel(err, err_table))
      return value;
  }

  char buffer[16];
  snprintf(buffer, sizeof(buffer), "0x%08x", err);
  return buffer;
}

/////////////////////////////////////////////////////////////////////////////
// LogMessage
/////////////////////////////////////////////////////////////////////////////

// By default, release builds don't log, debug builds at info level
#if !defined(NDEBUG)
LoggingSeverity LogMessage::min_sev_ = LS_INFO;
LoggingSeverity LogMessage::dbg_sev_ = LS_INFO;
#else
LoggingSeverity LogMessage::min_sev_ = LS_NONE;
LoggingSeverity LogMessage::dbg_sev_ = LS_NONE;
#endif
bool LogMessage::log_to_stderr_ = true;

namespace {
// Global lock for log subsystem, only needed to serialize access to streams_.
CriticalSection g_log_crit;
}  // namespace

// The list of logging streams currently configured.
// Note: we explicitly do not clean this up, because of the uncertain ordering
// of destructors at program exit.  Let the person who sets the stream trigger
// cleanup by setting to NULL, or let it leak (safe at program exit).
LogMessage::StreamList LogMessage::streams_ GUARDED_BY(g_log_crit);

// Boolean options default to false (0)
bool LogMessage::thread_, LogMessage::timestamp_;

LogMessage::LogMessage(const char* file,
                       int line,
                       LoggingSeverity sev,
                       LogErrorContext err_ctx,
                       int err,
                       const char* module)
    : severity_(sev), tag_(kLibjingle) {
  if (timestamp_) {
    // Use SystemTimeMillis so that even if tests use fake clocks, the timestamp
    // in log messages represents the real system time.
    int64_t time = TimeDiff(SystemTimeMillis(), LogStartTime());
    // Also ensure WallClockStartTime is initialized, so that it matches
    // LogStartTime.
    WallClockStartTime();
    print_stream_ << "[" << std::setfill('0') << std::setw(3) << (time / 1000)
                  << ":" << std::setw(3) << (time % 1000) << std::setfill(' ')
                  << "] ";
  }

  if (thread_) {
    PlatformThreadId id = CurrentThreadId();
    print_stream_ << "[" << std::dec << id << "] ";
  }

  if (file != NULL)
    print_stream_ << "(" << FilenameFromPath(file)  << ":" << line << "): ";

  if (err_ctx != ERRCTX_NONE) {
    std::ostringstream tmp;
    tmp << "[0x" << std::setfill('0') << std::hex << std::setw(8) << err << "]";
    switch (err_ctx) {
      case ERRCTX_ERRNO:
        tmp << " " << strerror(err);
        break;
#if WEBRTC_WIN
      case ERRCTX_HRESULT: {
        char msgbuf[256];
        DWORD flags = FORMAT_MESSAGE_FROM_SYSTEM;
        HMODULE hmod = GetModuleHandleA(module);
        if (hmod)
          flags |= FORMAT_MESSAGE_FROM_HMODULE;
        if (DWORD len = FormatMessageA(
            flags, hmod, err,
            MAKELANGID(LANG_NEUTRAL, SUBLANG_DEFAULT),
            msgbuf, sizeof(msgbuf) / sizeof(msgbuf[0]), NULL)) {
          while ((len > 0) &&
              isspace(static_cast<unsigned char>(msgbuf[len-1]))) {
            msgbuf[--len] = 0;
          }
          tmp << " " << msgbuf;
        }
        break;
      }
#endif  // WEBRTC_WIN
#if defined(WEBRTC_MAC) && !defined(WEBRTC_IOS)
      case ERRCTX_OSSTATUS: {
        std::string desc(DescriptionFromOSStatus(err));
        tmp << " " << (desc.empty() ? "Unknown error" : desc.c_str());
        break;
      }
#endif  // WEBRTC_MAC && !defined(WEBRTC_IOS)
      default:
        break;
    }
    extra_ = tmp.str();
  }
}

LogMessage::LogMessage(const char* file,
                       int line,
                       LoggingSeverity sev,
                       const std::string& tag)
    : LogMessage(file, line, sev, ERRCTX_NONE, 0 /* err */, NULL /* module */) {
  tag_ = tag;
  print_stream_ << tag << ": ";
}

LogMessage::~LogMessage() {
  if (!extra_.empty())
    print_stream_ << " : " << extra_;
  print_stream_ << std::endl;

  const std::string& str = print_stream_.str();
  if (severity_ >= dbg_sev_) {
    OutputToDebug(str, severity_, tag_);
  }

  CritScope cs(&g_log_crit);
  for (auto& kv : streams_) {
    if (severity_ >= kv.second) {
      kv.first->OnLogMessage(str);
    }
  }
}

int64_t LogMessage::LogStartTime() {
  static const int64_t g_start = SystemTimeMillis();
  return g_start;
}

uint32_t LogMessage::WallClockStartTime() {
  static const uint32_t g_start_wallclock = time(NULL);
  return g_start_wallclock;
}

void LogMessage::LogThreads(bool on) {
  thread_ = on;
}

void LogMessage::LogTimestamps(bool on) {
  timestamp_ = on;
}

void LogMessage::LogToDebug(LoggingSeverity min_sev) {
  dbg_sev_ = min_sev;
  CritScope cs(&g_log_crit);
  UpdateMinLogSeverity();
}

void LogMessage::SetLogToStderr(bool log_to_stderr) {
  log_to_stderr_ = log_to_stderr;
}

int LogMessage::GetLogToStream(LogSink* stream) {
  CritScope cs(&g_log_crit);
  LoggingSeverity sev = LS_NONE;
  for (auto& kv : streams_) {
    if (!stream || stream == kv.first) {
      sev = std::min(sev, kv.second);
    }
  }
  return sev;
}

void LogMessage::AddLogToStream(LogSink* stream, LoggingSeverity min_sev) {
  CritScope cs(&g_log_crit);
  streams_.push_back(std::make_pair(stream, min_sev));
  UpdateMinLogSeverity();
}

void LogMessage::RemoveLogToStream(LogSink* stream) {
  CritScope cs(&g_log_crit);
  for (StreamList::iterator it = streams_.begin(); it != streams_.end(); ++it) {
    if (stream == it->first) {
      streams_.erase(it);
      break;
    }
  }
  UpdateMinLogSeverity();
}

void LogMessage::ConfigureLogging(const char* params) {
  LoggingSeverity current_level = LS_VERBOSE;
  LoggingSeverity debug_level = GetLogToDebug();

  std::vector<std::string> tokens;
  tokenize(params, ' ', &tokens);

  for (const std::string& token : tokens) {
    if (token.empty())
      continue;

    // Logging features
    if (token == "tstamp") {
      LogTimestamps();
    } else if (token == "thread") {
      LogThreads();

    // Logging levels
    } else if (token == "sensitive") {
      current_level = LS_SENSITIVE;
    } else if (token == "verbose") {
      current_level = LS_VERBOSE;
    } else if (token == "info") {
      current_level = LS_INFO;
    } else if (token == "warning") {
      current_level = LS_WARNING;
    } else if (token == "error") {
      current_level = LS_ERROR;
    } else if (token == "none") {
      current_level = LS_NONE;

    // Logging targets
    } else if (token == "debug") {
      debug_level = current_level;
    }
  }

#if defined(WEBRTC_WIN)
  if ((LS_NONE != debug_level) && !::IsDebuggerPresent()) {
    // First, attempt to attach to our parent's console... so if you invoke
    // from the command line, we'll see the output there.  Otherwise, create
    // our own console window.
    // Note: These methods fail if a console already exists, which is fine.
    bool success = false;
    typedef BOOL (WINAPI* PFN_AttachConsole)(DWORD);
    if (HINSTANCE kernel32 = ::LoadLibrary(L"kernel32.dll")) {
      // AttachConsole is defined on WinXP+.
      if (PFN_AttachConsole attach_console = reinterpret_cast<PFN_AttachConsole>
            (::GetProcAddress(kernel32, "AttachConsole"))) {
        success = (FALSE != attach_console(ATTACH_PARENT_PROCESS));
      }
      ::FreeLibrary(kernel32);
    }
    if (!success) {
      ::AllocConsole();
    }
  }
#endif  // WEBRTC_WIN

  LogToDebug(debug_level);
}

void LogMessage::UpdateMinLogSeverity() EXCLUSIVE_LOCKS_REQUIRED(g_log_crit) {
  LoggingSeverity min_sev = dbg_sev_;
  for (auto& kv : streams_) {
    min_sev = std::min(dbg_sev_, kv.second);
  }
  min_sev_ = min_sev;
}

void LogMessage::OutputToDebug(const std::string& str,
                               LoggingSeverity severity,
                               const std::string& tag) {
  bool log_to_stderr = log_to_stderr_;
#if defined(WEBRTC_MAC) && !defined(WEBRTC_IOS) && defined(NDEBUG)
  // On the Mac, all stderr output goes to the Console log and causes clutter.
  // So in opt builds, don't log to stderr unless the user specifically sets
  // a preference to do so.
  CFStringRef key = CFStringCreateWithCString(kCFAllocatorDefault,
                                              "logToStdErr",
                                              kCFStringEncodingUTF8);
  CFStringRef domain = CFBundleGetIdentifier(CFBundleGetMainBundle());
  if (key != NULL && domain != NULL) {
    Boolean exists_and_is_valid;
    Boolean should_log =
        CFPreferencesGetAppBooleanValue(key, domain, &exists_and_is_valid);
    // If the key doesn't exist or is invalid or is false, we will not log to
    // stderr.
    log_to_stderr = exists_and_is_valid && should_log;
  }
  if (key != NULL) {
    CFRelease(key);
  }
#endif
#if defined(WEBRTC_WIN)
  // Always log to the debugger.
  // Perhaps stderr should be controlled by a preference, as on Mac?
  OutputDebugStringA(str.c_str());
  if (log_to_stderr) {
    // This handles dynamically allocated consoles, too.
    if (HANDLE error_handle = ::GetStdHandle(STD_ERROR_HANDLE)) {
      log_to_stderr = false;
      DWORD written = 0;
      ::WriteFile(error_handle, str.data(), static_cast<DWORD>(str.size()),
                  &written, 0);
    }
  }
#endif  // WEBRTC_WIN
#if defined(WEBRTC_ANDROID)
  // Android's logging facility uses severity to log messages but we
  // need to map libjingle's severity levels to Android ones first.
  // Also write to stderr which maybe available to executable started
  // from the shell.
  int prio;
  switch (severity) {
    case LS_SENSITIVE:
      __android_log_write(ANDROID_LOG_INFO, tag.c_str(), "SENSITIVE");
      if (log_to_stderr) {
        fprintf(stderr, "SENSITIVE");
        fflush(stderr);
      }
      return;
    case LS_VERBOSE:
      prio = ANDROID_LOG_VERBOSE;
      break;
    case LS_INFO:
      prio = ANDROID_LOG_INFO;
      break;
    case LS_WARNING:
      prio = ANDROID_LOG_WARN;
      break;
    case LS_ERROR:
      prio = ANDROID_LOG_ERROR;
      break;
    default:
      prio = ANDROID_LOG_UNKNOWN;
  }

  int size = str.size();
  int line = 0;
  int idx = 0;
  const int max_lines = size / kMaxLogLineSize + 1;
  if (max_lines == 1) {
    __android_log_print(prio, tag.c_str(), "%.*s", size, str.c_str());
  } else {
    while (size > 0) {
      const int len = std::min(size, kMaxLogLineSize);
      // Use the size of the string in the format (str may have \0 in the
      // middle).
      __android_log_print(prio, tag.c_str(), "[%d/%d] %.*s",
                          line + 1, max_lines,
                          len, str.c_str() + idx);
      idx += len;
      size -= len;
      ++line;
    }
  }
#endif  // WEBRTC_ANDROID
  if (log_to_stderr) {
    fprintf(stderr, "%s", str.c_str());
    fflush(stderr);
  }
}

//////////////////////////////////////////////////////////////////////
// Logging Helpers
//////////////////////////////////////////////////////////////////////

void LogMultiline(LoggingSeverity level, const char* label, bool input,
                  const void* data, size_t len, bool hex_mode,
                  LogMultilineState* state) {
  if (!LOG_CHECK_LEVEL_V(level))
    return;

  const char * direction = (input ? " << " : " >> ");

  // NULL data means to flush our count of unprintable characters.
  if (!data) {
    if (state && state->unprintable_count_[input]) {
      LOG_V(level) << label << direction << "## "
                   << state->unprintable_count_[input]
                   << " consecutive unprintable ##";
      state->unprintable_count_[input] = 0;
    }
    return;
  }

  // The ctype classification functions want unsigned chars.
  const unsigned char* udata = static_cast<const unsigned char*>(data);

  if (hex_mode) {
    const size_t LINE_SIZE = 24;
    char hex_line[LINE_SIZE * 9 / 4 + 2], asc_line[LINE_SIZE + 1];
    while (len > 0) {
      memset(asc_line, ' ', sizeof(asc_line));
      memset(hex_line, ' ', sizeof(hex_line));
      size_t line_len = std::min(len, LINE_SIZE);
      for (size_t i = 0; i < line_len; ++i) {
        unsigned char ch = udata[i];
        asc_line[i] = isprint(ch) ? ch : '.';
        hex_line[i*2 + i/4] = hex_encode(ch >> 4);
        hex_line[i*2 + i/4 + 1] = hex_encode(ch & 0xf);
      }
      asc_line[sizeof(asc_line)-1] = 0;
      hex_line[sizeof(hex_line)-1] = 0;
      LOG_V(level) << label << direction
                   << asc_line << " " << hex_line << " ";
      udata += line_len;
      len -= line_len;
    }
    return;
  }

  size_t consecutive_unprintable = state ? state->unprintable_count_[input] : 0;

  const unsigned char* end = udata + len;
  while (udata < end) {
    const unsigned char* line = udata;
    const unsigned char* end_of_line = strchrn<unsigned char>(udata,
                                                              end - udata,
                                                              '\n');
    if (!end_of_line) {
      udata = end_of_line = end;
    } else {
      udata = end_of_line + 1;
    }

    bool is_printable = true;

    // If we are in unprintable mode, we need to see a line of at least
    // kMinPrintableLine characters before we'll switch back.
    const ptrdiff_t kMinPrintableLine = 4;
    if (consecutive_unprintable && ((end_of_line - line) < kMinPrintableLine)) {
      is_printable = false;
    } else {
      // Determine if the line contains only whitespace and printable
      // characters.
      bool is_entirely_whitespace = true;
      for (const unsigned char* pos = line; pos < end_of_line; ++pos) {
        if (isspace(*pos))
          continue;
        is_entirely_whitespace = false;
        if (!isprint(*pos)) {
          is_printable = false;
          break;
        }
      }
      // Treat an empty line following unprintable data as unprintable.
      if (consecutive_unprintable && is_entirely_whitespace) {
        is_printable = false;
      }
    }
    if (!is_printable) {
      consecutive_unprintable += (udata - line);
      continue;
    }
    // Print out the current line, but prefix with a count of prior unprintable
    // characters.
    if (consecutive_unprintable) {
      LOG_V(level) << label << direction << "## " << consecutive_unprintable
                  << " consecutive unprintable ##";
      consecutive_unprintable = 0;
    }
    // Strip off trailing whitespace.
    while ((end_of_line > line) && isspace(*(end_of_line-1))) {
      --end_of_line;
    }
    // Filter out any private data
    std::string substr(reinterpret_cast<const char*>(line), end_of_line - line);
    std::string::size_type pos_private = substr.find("Email");
    if (pos_private == std::string::npos) {
      pos_private = substr.find("Passwd");
    }
    if (pos_private == std::string::npos) {
      LOG_V(level) << label << direction << substr;
    } else {
      LOG_V(level) << label << direction << "## omitted for privacy ##";
    }
  }

  if (state) {
    state->unprintable_count_[input] = consecutive_unprintable;
  }
}

//////////////////////////////////////////////////////////////////////

}  // namespace rtc
