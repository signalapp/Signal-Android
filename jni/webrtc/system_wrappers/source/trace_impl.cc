/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/system_wrappers/source/trace_impl.h"

#include <assert.h>
#include <stdarg.h>
#include <stdio.h>
#include <string.h>

#include "webrtc/base/atomicops.h"
#include "webrtc/base/platform_thread.h"
#ifdef _WIN32
#include "webrtc/system_wrappers/source/trace_win.h"
#else
#include "webrtc/system_wrappers/source/trace_posix.h"
#endif  // _WIN32

#define KEY_LEN_CHARS 31

#ifdef _WIN32
#pragma warning(disable:4355)
#endif  // _WIN32

namespace webrtc {

const int Trace::kBoilerplateLength = 71;
const int Trace::kTimestampPosition = 13;
const int Trace::kTimestampLength = 12;
volatile int Trace::level_filter_ = kTraceDefault;

// Construct On First Use idiom. Avoids "static initialization order fiasco".
TraceImpl* TraceImpl::StaticInstance(CountOperation count_operation,
                                     const TraceLevel level) {
  // Sanities to avoid taking lock unless absolutely necessary (for
  // performance reasons). count_operation == kAddRefNoCreate implies that a
  // message will be written to file.
  if ((level != kTraceAll) && (count_operation == kAddRefNoCreate)) {
    if (!(level & level_filter())) {
      return NULL;
    }
  }
  TraceImpl* impl =
    GetStaticInstance<TraceImpl>(count_operation);
  return impl;
}

TraceImpl* TraceImpl::GetTrace(const TraceLevel level) {
  return StaticInstance(kAddRefNoCreate, level);
}

TraceImpl* TraceImpl::CreateInstance() {
#if defined(_WIN32)
  return new TraceWindows();
#else
  return new TracePosix();
#endif
}

TraceImpl::TraceImpl()
    : callback_(NULL),
      row_count_text_(0),
      file_count_text_(0),
      trace_file_(FileWrapper::Create()) {
}

TraceImpl::~TraceImpl() {
  trace_file_->CloseFile();
}

int32_t TraceImpl::AddThreadId(char* trace_message) const {
  uint32_t thread_id = rtc::CurrentThreadId();
  // Messages is 12 characters.
  return sprintf(trace_message, "%10u; ", thread_id);
}

int32_t TraceImpl::AddLevel(char* sz_message, const TraceLevel level) const {
  const int kMessageLength = 12;
  switch (level) {
    case kTraceTerseInfo:
      // Add the appropriate amount of whitespace.
      memset(sz_message, ' ', kMessageLength);
      sz_message[kMessageLength] = '\0';
      break;
    case kTraceStateInfo:
      sprintf(sz_message, "STATEINFO ; ");
      break;
    case kTraceWarning:
      sprintf(sz_message, "WARNING   ; ");
      break;
    case kTraceError:
      sprintf(sz_message, "ERROR     ; ");
      break;
    case kTraceCritical:
      sprintf(sz_message, "CRITICAL  ; ");
      break;
    case kTraceInfo:
      sprintf(sz_message, "DEBUGINFO ; ");
      break;
    case kTraceModuleCall:
      sprintf(sz_message, "MODULECALL; ");
      break;
    case kTraceMemory:
      sprintf(sz_message, "MEMORY    ; ");
      break;
    case kTraceTimer:
      sprintf(sz_message, "TIMER     ; ");
      break;
    case kTraceStream:
      sprintf(sz_message, "STREAM    ; ");
      break;
    case kTraceApiCall:
      sprintf(sz_message, "APICALL   ; ");
      break;
    case kTraceDebug:
      sprintf(sz_message, "DEBUG     ; ");
      break;
    default:
      assert(false);
      return 0;
  }
  // All messages are 12 characters.
  return kMessageLength;
}

int32_t TraceImpl::AddModuleAndId(char* trace_message,
                                  const TraceModule module,
                                  const int32_t id) const {
  // Use long int to prevent problems with different definitions of
  // int32_t.
  // TODO(hellner): is this actually a problem? If so, it should be better to
  //                clean up int32_t
  const long int idl = id;
  const int kMessageLength = 25;
  if (idl != -1) {
    const unsigned long int id_engine = id >> 16;
    const unsigned long int id_channel = id & 0xffff;

    switch (module) {
      case kTraceUndefined:
        // Add the appropriate amount of whitespace.
        memset(trace_message, ' ', kMessageLength);
        trace_message[kMessageLength] = '\0';
        break;
      case kTraceVoice:
        sprintf(trace_message, "       VOICE:%5ld %5ld;", id_engine,
                id_channel);
        break;
      case kTraceVideo:
        sprintf(trace_message, "       VIDEO:%5ld %5ld;", id_engine,
                id_channel);
        break;
      case kTraceUtility:
        sprintf(trace_message, "     UTILITY:%5ld %5ld;", id_engine,
                id_channel);
        break;
      case kTraceRtpRtcp:
        sprintf(trace_message, "    RTP/RTCP:%5ld %5ld;", id_engine,
                id_channel);
        break;
      case kTraceTransport:
        sprintf(trace_message, "   TRANSPORT:%5ld %5ld;", id_engine,
                id_channel);
        break;
      case kTraceAudioCoding:
        sprintf(trace_message, "AUDIO CODING:%5ld %5ld;", id_engine,
                id_channel);
        break;
      case kTraceSrtp:
        sprintf(trace_message, "        SRTP:%5ld %5ld;", id_engine,
                id_channel);
        break;
      case kTraceAudioMixerServer:
        sprintf(trace_message, " AUDIO MIX/S:%5ld %5ld;", id_engine,
                id_channel);
        break;
      case kTraceAudioMixerClient:
        sprintf(trace_message, " AUDIO MIX/C:%5ld %5ld;", id_engine,
                id_channel);
        break;
      case kTraceVideoCoding:
        sprintf(trace_message, "VIDEO CODING:%5ld %5ld;", id_engine,
                id_channel);
        break;
      case kTraceVideoMixer:
        // Print sleep time and API call
        sprintf(trace_message, "   VIDEO MIX:%5ld %5ld;", id_engine,
                id_channel);
        break;
      case kTraceFile:
        sprintf(trace_message, "        FILE:%5ld %5ld;", id_engine,
                id_channel);
        break;
      case kTraceAudioProcessing:
        sprintf(trace_message, "  AUDIO PROC:%5ld %5ld;", id_engine,
                id_channel);
        break;
      case kTraceAudioDevice:
        sprintf(trace_message, "AUDIO DEVICE:%5ld %5ld;", id_engine,
                id_channel);
        break;
      case kTraceVideoRenderer:
        sprintf(trace_message, "VIDEO RENDER:%5ld %5ld;", id_engine,
                id_channel);
        break;
      case kTraceVideoCapture:
        sprintf(trace_message, "VIDEO CAPTUR:%5ld %5ld;", id_engine,
                id_channel);
        break;
      case kTraceRemoteBitrateEstimator:
        sprintf(trace_message, "     BWE RBE:%5ld %5ld;", id_engine,
                id_channel);
        break;
    }
  } else {
    switch (module) {
      case kTraceUndefined:
        // Add the appropriate amount of whitespace.
        memset(trace_message, ' ', kMessageLength);
        trace_message[kMessageLength] = '\0';
        break;
      case kTraceVoice:
        sprintf(trace_message, "       VOICE:%11ld;", idl);
        break;
      case kTraceVideo:
        sprintf(trace_message, "       VIDEO:%11ld;", idl);
        break;
      case kTraceUtility:
        sprintf(trace_message, "     UTILITY:%11ld;", idl);
        break;
      case kTraceRtpRtcp:
        sprintf(trace_message, "    RTP/RTCP:%11ld;", idl);
        break;
      case kTraceTransport:
        sprintf(trace_message, "   TRANSPORT:%11ld;", idl);
        break;
      case kTraceAudioCoding:
        sprintf(trace_message, "AUDIO CODING:%11ld;", idl);
        break;
      case kTraceSrtp:
        sprintf(trace_message, "        SRTP:%11ld;", idl);
        break;
      case kTraceAudioMixerServer:
        sprintf(trace_message, " AUDIO MIX/S:%11ld;", idl);
        break;
      case kTraceAudioMixerClient:
        sprintf(trace_message, " AUDIO MIX/C:%11ld;", idl);
        break;
      case kTraceVideoCoding:
        sprintf(trace_message, "VIDEO CODING:%11ld;", idl);
        break;
      case kTraceVideoMixer:
        sprintf(trace_message, "   VIDEO MIX:%11ld;", idl);
        break;
      case kTraceFile:
        sprintf(trace_message, "        FILE:%11ld;", idl);
        break;
      case kTraceAudioProcessing:
        sprintf(trace_message, "  AUDIO PROC:%11ld;", idl);
        break;
      case kTraceAudioDevice:
        sprintf(trace_message, "AUDIO DEVICE:%11ld;", idl);
        break;
      case kTraceVideoRenderer:
        sprintf(trace_message, "VIDEO RENDER:%11ld;", idl);
        break;
      case kTraceVideoCapture:
        sprintf(trace_message, "VIDEO CAPTUR:%11ld;", idl);
        break;
      case kTraceRemoteBitrateEstimator:
        sprintf(trace_message, "     BWE RBE:%11ld;", idl);
        break;
    }
  }
  return kMessageLength;
}

int32_t TraceImpl::SetTraceFileImpl(const char* file_name_utf8,
                                    const bool add_file_counter) {
  rtc::CritScope lock(&crit_);

  trace_file_->CloseFile();
  trace_file_path_.clear();

  if (file_name_utf8) {
    if (add_file_counter) {
      file_count_text_ = 1;

      char file_name_with_counter_utf8[FileWrapper::kMaxFileNameSize];
      CreateFileName(file_name_utf8, file_name_with_counter_utf8,
                     file_count_text_);
      if (!trace_file_->OpenFile(file_name_with_counter_utf8, false)) {
        return -1;
      }
      trace_file_path_ = file_name_with_counter_utf8;
    } else {
      file_count_text_ = 0;
      if (!trace_file_->OpenFile(file_name_utf8, false)) {
        return -1;
      }
      trace_file_path_ = file_name_utf8;
    }
  }
  row_count_text_ = 0;
  return 0;
}

int32_t TraceImpl::SetTraceCallbackImpl(TraceCallback* callback) {
  rtc::CritScope lock(&crit_);
  callback_ = callback;
  return 0;
}

int32_t TraceImpl::AddMessage(
    char* trace_message,
    const char msg[WEBRTC_TRACE_MAX_MESSAGE_SIZE],
    const uint16_t written_so_far) const {
  int length = 0;
  if (written_so_far >= WEBRTC_TRACE_MAX_MESSAGE_SIZE) {
    return -1;
  }
  // - 2 to leave room for newline and NULL termination.
#ifdef _WIN32
  length = _snprintf(trace_message,
                     WEBRTC_TRACE_MAX_MESSAGE_SIZE - written_so_far - 2,
                     "%s", msg);
  if (length < 0) {
    length = WEBRTC_TRACE_MAX_MESSAGE_SIZE - written_so_far - 2;
    trace_message[length] = 0;
  }
#else
  length = snprintf(trace_message,
                    WEBRTC_TRACE_MAX_MESSAGE_SIZE - written_so_far - 2,
                    "%s", msg);
  if (length < 0 ||
      length > WEBRTC_TRACE_MAX_MESSAGE_SIZE - written_so_far - 2) {
    length = WEBRTC_TRACE_MAX_MESSAGE_SIZE - written_so_far - 2;
    trace_message[length] = 0;
  }
#endif
  // Length with NULL termination.
  return length + 1;
}

void TraceImpl::AddMessageToList(
    const char trace_message[WEBRTC_TRACE_MAX_MESSAGE_SIZE],
    const uint16_t length,
    const TraceLevel level) {
  rtc::CritScope lock(&crit_);
  if (callback_)
    callback_->Print(level, trace_message, length);
  WriteToFile(trace_message, length);
}

void TraceImpl::WriteToFile(const char* msg, uint16_t length) {
  if (!trace_file_->is_open())
    return;

  if (row_count_text_ > WEBRTC_TRACE_MAX_FILE_SIZE) {
    // wrap file
    row_count_text_ = 0;
    trace_file_->Flush();

    if (file_count_text_ == 0) {
      trace_file_->Rewind();
    } else {
      char new_file_name[FileWrapper::kMaxFileNameSize];

      // get current name
      file_count_text_++;
      UpdateFileName(new_file_name, file_count_text_);

      trace_file_->CloseFile();
      trace_file_path_.clear();

      if (!trace_file_->OpenFile(new_file_name, false)) {
        return;
      }
      trace_file_path_ = new_file_name;
    }
  }
  if (row_count_text_ == 0) {
    char message[WEBRTC_TRACE_MAX_MESSAGE_SIZE + 1];
    int32_t length = AddDateTimeInfo(message);
    if (length != -1) {
      message[length] = 0;
      message[length - 1] = '\n';
      trace_file_->Write(message, length);
      row_count_text_++;
    }
  }

  char trace_message[WEBRTC_TRACE_MAX_MESSAGE_SIZE];
  memcpy(trace_message, msg, length);
  trace_message[length] = 0;
  trace_message[length - 1] = '\n';
  trace_file_->Write(trace_message, length);
  row_count_text_++;
}

void TraceImpl::AddImpl(const TraceLevel level,
                        const TraceModule module,
                        const int32_t id,
                        const char msg[WEBRTC_TRACE_MAX_MESSAGE_SIZE]) {
  if (!TraceCheck(level))
    return;

  char trace_message[WEBRTC_TRACE_MAX_MESSAGE_SIZE];
  char* message_ptr = &trace_message[0];
  int32_t len = AddLevel(message_ptr, level);
  if (len == -1)
    return;

  message_ptr += len;
  int32_t ack_len = len;

  len = AddTime(message_ptr, level);
  if (len == -1)
    return;

  message_ptr += len;
  ack_len += len;

  len = AddModuleAndId(message_ptr, module, id);
  if (len == -1)
    return;

  message_ptr += len;
  ack_len += len;

  len = AddThreadId(message_ptr);
  if (len < 0)
    return;

  message_ptr += len;
  ack_len += len;

  len = AddMessage(message_ptr, msg, static_cast<uint16_t>(ack_len));
  if (len == -1)
    return;

  ack_len += len;
  AddMessageToList(trace_message, static_cast<uint16_t>(ack_len), level);
}

bool TraceImpl::TraceCheck(const TraceLevel level) const {
  return (level & level_filter()) ? true : false;
}

bool TraceImpl::UpdateFileName(
    char file_name_with_counter_utf8[FileWrapper::kMaxFileNameSize],
    const uint32_t new_count) const {
  int32_t length = static_cast<int32_t>(trace_file_path_.length());

  int32_t length_without_file_ending = length - 1;
  while (length_without_file_ending > 0) {
    if (trace_file_path_[length_without_file_ending] == '.') {
      break;
    } else {
      length_without_file_ending--;
    }
  }
  if (length_without_file_ending == 0) {
    length_without_file_ending = length;
  }
  int32_t length_to_ = length_without_file_ending - 1;
  while (length_to_ > 0) {
    if (trace_file_path_[length_to_] == '_') {
      break;
    } else {
      length_to_--;
    }
  }

  memcpy(file_name_with_counter_utf8, &trace_file_path_[0], length_to_);
  sprintf(file_name_with_counter_utf8 + length_to_, "_%lu%s",
          static_cast<long unsigned int>(new_count),
          &trace_file_path_[length_without_file_ending]);
  return true;
}

bool TraceImpl::CreateFileName(
    const char file_name_utf8[FileWrapper::kMaxFileNameSize],
    char file_name_with_counter_utf8[FileWrapper::kMaxFileNameSize],
    const uint32_t new_count) const {
  int32_t length = (int32_t)strlen(file_name_utf8);
  if (length < 0) {
    return false;
  }

  int32_t length_without_file_ending = length - 1;
  while (length_without_file_ending > 0) {
    if (file_name_utf8[length_without_file_ending] == '.') {
      break;
    } else {
      length_without_file_ending--;
    }
  }
  if (length_without_file_ending == 0) {
    length_without_file_ending = length;
  }
  memcpy(file_name_with_counter_utf8, file_name_utf8,
         length_without_file_ending);
  sprintf(file_name_with_counter_utf8 + length_without_file_ending, "_%lu%s",
          static_cast<long unsigned int>(new_count),
          file_name_utf8 + length_without_file_ending);
  return true;
}

// static
void Trace::CreateTrace() {
  TraceImpl::StaticInstance(kAddRef);
}

// static
void Trace::ReturnTrace() {
  TraceImpl::StaticInstance(kRelease);
}

// static
void Trace::set_level_filter(int filter) {
  rtc::AtomicOps::ReleaseStore(&level_filter_, filter);
}

// static
int Trace::level_filter() {
  return rtc::AtomicOps::AcquireLoad(&level_filter_);
}

// static
int32_t Trace::SetTraceFile(const char* file_name,
                            const bool add_file_counter) {
  TraceImpl* trace = TraceImpl::GetTrace();
  if (trace) {
    int ret_val = trace->SetTraceFileImpl(file_name, add_file_counter);
    ReturnTrace();
    return ret_val;
  }
  return -1;
}

int32_t Trace::SetTraceCallback(TraceCallback* callback) {
  TraceImpl* trace = TraceImpl::GetTrace();
  if (trace) {
    int ret_val = trace->SetTraceCallbackImpl(callback);
    ReturnTrace();
    return ret_val;
  }
  return -1;
}

void Trace::Add(const TraceLevel level, const TraceModule module,
                const int32_t id, const char* msg, ...) {
  TraceImpl* trace = TraceImpl::GetTrace(level);
  if (trace) {
    if (trace->TraceCheck(level)) {
      char temp_buff[WEBRTC_TRACE_MAX_MESSAGE_SIZE];
      char* buff = 0;
      if (msg) {
        va_list args;
        va_start(args, msg);
#ifdef _WIN32
        _vsnprintf(temp_buff, WEBRTC_TRACE_MAX_MESSAGE_SIZE - 1, msg, args);
#else
        vsnprintf(temp_buff, WEBRTC_TRACE_MAX_MESSAGE_SIZE - 1, msg, args);
#endif
        va_end(args);
        buff = temp_buff;
      }
      trace->AddImpl(level, module, id, buff);
    }
    ReturnTrace();
  }
}

}  // namespace webrtc
