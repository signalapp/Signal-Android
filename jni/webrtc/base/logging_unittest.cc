/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/base/fileutils.h"
#include "webrtc/base/gunit.h"
#include "webrtc/base/logging.h"
#include "webrtc/base/pathutils.h"
#include "webrtc/base/stream.h"
#include "webrtc/base/thread.h"

namespace rtc {

template <typename Base>
class LogSinkImpl
    : public LogSink,
      public Base {
 public:
  LogSinkImpl() {}

  template<typename P>
  explicit LogSinkImpl(P* p) : Base(p) {}

 private:
  void OnLogMessage(const std::string& message) override {
    static_cast<Base*>(this)->WriteAll(
        message.data(), message.size(), nullptr, nullptr);
  }
};

// Test basic logging operation. We should get the INFO log but not the VERBOSE.
// We should restore the correct global state at the end.
TEST(LogTest, SingleStream) {
  int sev = LogMessage::GetLogToStream(NULL);

  std::string str;
  LogSinkImpl<StringStream> stream(&str);
  LogMessage::AddLogToStream(&stream, LS_INFO);
  EXPECT_EQ(LS_INFO, LogMessage::GetLogToStream(&stream));

  LOG(LS_INFO) << "INFO";
  LOG(LS_VERBOSE) << "VERBOSE";
  EXPECT_NE(std::string::npos, str.find("INFO"));
  EXPECT_EQ(std::string::npos, str.find("VERBOSE"));

  LogMessage::RemoveLogToStream(&stream);
  EXPECT_EQ(LS_NONE, LogMessage::GetLogToStream(&stream));

  EXPECT_EQ(sev, LogMessage::GetLogToStream(NULL));
}

// Test using multiple log streams. The INFO stream should get the INFO message,
// the VERBOSE stream should get the INFO and the VERBOSE.
// We should restore the correct global state at the end.
TEST(LogTest, MultipleStreams) {
  int sev = LogMessage::GetLogToStream(NULL);

  std::string str1, str2;
  LogSinkImpl<StringStream> stream1(&str1), stream2(&str2);
  LogMessage::AddLogToStream(&stream1, LS_INFO);
  LogMessage::AddLogToStream(&stream2, LS_VERBOSE);
  EXPECT_EQ(LS_INFO, LogMessage::GetLogToStream(&stream1));
  EXPECT_EQ(LS_VERBOSE, LogMessage::GetLogToStream(&stream2));

  LOG(LS_INFO) << "INFO";
  LOG(LS_VERBOSE) << "VERBOSE";

  EXPECT_NE(std::string::npos, str1.find("INFO"));
  EXPECT_EQ(std::string::npos, str1.find("VERBOSE"));
  EXPECT_NE(std::string::npos, str2.find("INFO"));
  EXPECT_NE(std::string::npos, str2.find("VERBOSE"));

  LogMessage::RemoveLogToStream(&stream2);
  LogMessage::RemoveLogToStream(&stream1);
  EXPECT_EQ(LS_NONE, LogMessage::GetLogToStream(&stream2));
  EXPECT_EQ(LS_NONE, LogMessage::GetLogToStream(&stream1));

  EXPECT_EQ(sev, LogMessage::GetLogToStream(NULL));
}

// Ensure we don't crash when adding/removing streams while threads are going.
// We should restore the correct global state at the end.
class LogThread : public Thread {
 public:
  virtual ~LogThread() {
    Stop();
  }

 private:
  void Run() {
    // LS_SENSITIVE to avoid cluttering up any real logging going on
    LOG(LS_SENSITIVE) << "LOG";
  }
};

TEST(LogTest, MultipleThreads) {
  int sev = LogMessage::GetLogToStream(NULL);

  LogThread thread1, thread2, thread3;
  thread1.Start();
  thread2.Start();
  thread3.Start();

  LogSinkImpl<NullStream> stream1, stream2, stream3;
  for (int i = 0; i < 1000; ++i) {
    LogMessage::AddLogToStream(&stream1, LS_INFO);
    LogMessage::AddLogToStream(&stream2, LS_VERBOSE);
    LogMessage::AddLogToStream(&stream3, LS_SENSITIVE);
    LogMessage::RemoveLogToStream(&stream1);
    LogMessage::RemoveLogToStream(&stream2);
    LogMessage::RemoveLogToStream(&stream3);
  }

  EXPECT_EQ(sev, LogMessage::GetLogToStream(NULL));
}


TEST(LogTest, WallClockStartTime) {
  uint32_t time = LogMessage::WallClockStartTime();
  // Expect the time to be in a sensible range, e.g. > 2012-01-01.
  EXPECT_GT(time, 1325376000u);
}

// Test the time required to write 1000 80-character logs to an unbuffered file.
TEST(LogTest, Perf) {
  Pathname path;
  EXPECT_TRUE(Filesystem::GetTemporaryFolder(path, true, NULL));
  path.SetPathname(Filesystem::TempFilename(path, "ut"));

  LogSinkImpl<FileStream> stream;
  EXPECT_TRUE(stream.Open(path.pathname(), "wb", NULL));
  stream.DisableBuffering();
  LogMessage::AddLogToStream(&stream, LS_SENSITIVE);

  int64_t start = TimeMillis(), finish;
  std::string message('X', 80);
  for (int i = 0; i < 1000; ++i) {
    LOG(LS_SENSITIVE) << message;
  }
  finish = TimeMillis();

  LogMessage::RemoveLogToStream(&stream);
  stream.Close();
  Filesystem::DeleteFile(path);

  LOG(LS_INFO) << "Average log time: " << TimeDiff(finish, start) << " ms";
}

}  // namespace rtc
