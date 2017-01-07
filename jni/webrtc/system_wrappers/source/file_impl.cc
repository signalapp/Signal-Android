/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/system_wrappers/include/file_wrapper.h"

#ifdef _WIN32
#include <Windows.h>
#else
#include <stdarg.h>
#include <string.h>
#endif

#include "webrtc/base/checks.h"

namespace webrtc {
namespace {
FILE* FileOpen(const char* file_name_utf8, bool read_only) {
#if defined(_WIN32)
  int len = MultiByteToWideChar(CP_UTF8, 0, file_name_utf8, -1, nullptr, 0);
  std::wstring wstr(len, 0);
  MultiByteToWideChar(CP_UTF8, 0, file_name_utf8, -1, &wstr[0], len);
  FILE* file = _wfopen(wstr.c_str(), read_only ? L"rb" : L"wb");
#else
  FILE* file = fopen(file_name_utf8, read_only ? "rb" : "wb");
#endif
  return file;
}
}  // namespace

// static
FileWrapper* FileWrapper::Create() {
  return new FileWrapper();
}

// static
FileWrapper FileWrapper::Open(const char* file_name_utf8, bool read_only) {
  return FileWrapper(FileOpen(file_name_utf8, read_only), 0);
}

FileWrapper::FileWrapper() {}

FileWrapper::FileWrapper(FILE* file, size_t max_size)
    : file_(file), max_size_in_bytes_(max_size) {}

FileWrapper::~FileWrapper() {
  CloseFileImpl();
}

FileWrapper::FileWrapper(FileWrapper&& other) {
  operator=(std::move(other));
}

FileWrapper& FileWrapper::operator=(FileWrapper&& other) {
  file_ = other.file_;
  max_size_in_bytes_ = other.max_size_in_bytes_;
  position_ = other.position_;
  other.file_ = nullptr;
  return *this;
}

void FileWrapper::CloseFile() {
  rtc::CritScope lock(&lock_);
  CloseFileImpl();
}

int FileWrapper::Rewind() {
  rtc::CritScope lock(&lock_);
  if (file_ != nullptr) {
    position_ = 0;
    return fseek(file_, 0, SEEK_SET);
  }
  return -1;
}

void FileWrapper::SetMaxFileSize(size_t bytes) {
  rtc::CritScope lock(&lock_);
  max_size_in_bytes_ = bytes;
}

int FileWrapper::Flush() {
  rtc::CritScope lock(&lock_);
  return FlushImpl();
}

bool FileWrapper::OpenFile(const char* file_name_utf8, bool read_only) {
  size_t length = strlen(file_name_utf8);
  if (length > kMaxFileNameSize - 1)
    return false;

  rtc::CritScope lock(&lock_);
  if (file_ != nullptr)
    return false;

  file_ = FileOpen(file_name_utf8, read_only);
  return file_ != nullptr;
}

bool FileWrapper::OpenFromFileHandle(FILE* handle) {
  if (!handle)
    return false;
  rtc::CritScope lock(&lock_);
  CloseFileImpl();
  file_ = handle;
  return true;
}

int FileWrapper::Read(void* buf, size_t length) {
  rtc::CritScope lock(&lock_);
  if (file_ == nullptr)
    return -1;

  size_t bytes_read = fread(buf, 1, length, file_);
  return static_cast<int>(bytes_read);
}

bool FileWrapper::Write(const void* buf, size_t length) {
  if (buf == nullptr)
    return false;

  rtc::CritScope lock(&lock_);

  if (file_ == nullptr)
    return false;

  // Check if it's time to stop writing.
  if (max_size_in_bytes_ > 0 && (position_ + length) > max_size_in_bytes_)
    return false;

  size_t num_bytes = fwrite(buf, 1, length, file_);
  position_ += num_bytes;

  return num_bytes == length;
}

void FileWrapper::CloseFileImpl() {
  if (file_ != nullptr)
    fclose(file_);
  file_ = nullptr;
}

int FileWrapper::FlushImpl() {
  return (file_ != nullptr) ? fflush(file_) : -1;
}

}  // namespace webrtc
