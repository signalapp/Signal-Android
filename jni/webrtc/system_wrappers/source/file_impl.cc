/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/system_wrappers/source/file_impl.h"

#include <assert.h>

#ifdef _WIN32
#include <Windows.h>
#else
#include <stdarg.h>
#include <string.h>
#endif

#include "webrtc/system_wrappers/interface/rw_lock_wrapper.h"

namespace webrtc {

FileWrapper* FileWrapper::Create() {
  return new FileWrapperImpl();
}

FileWrapperImpl::FileWrapperImpl()
    : rw_lock_(RWLockWrapper::CreateRWLock()),
      id_(NULL),
      managed_file_handle_(true),
      open_(false),
      looping_(false),
      read_only_(false),
      max_size_in_bytes_(0),
      size_in_bytes_(0) {
  memset(file_name_utf8_, 0, kMaxFileNameSize);
}

FileWrapperImpl::~FileWrapperImpl() {
  if (id_ != NULL && managed_file_handle_) {
    fclose(id_);
  }
}

int FileWrapperImpl::CloseFile() {
  WriteLockScoped write(*rw_lock_);
  return CloseFileImpl();
}

int FileWrapperImpl::Rewind() {
  WriteLockScoped write(*rw_lock_);
  if (looping_ || !read_only_) {
    if (id_ != NULL) {
      size_in_bytes_ = 0;
      return fseek(id_, 0, SEEK_SET);
    }
  }
  return -1;
}

int FileWrapperImpl::SetMaxFileSize(size_t bytes) {
  WriteLockScoped write(*rw_lock_);
  max_size_in_bytes_ = bytes;
  return 0;
}

int FileWrapperImpl::Flush() {
  WriteLockScoped write(*rw_lock_);
  return FlushImpl();
}

int FileWrapperImpl::FileName(char* file_name_utf8, size_t size) const {
  ReadLockScoped read(*rw_lock_);
  size_t length = strlen(file_name_utf8_);
  if (length > kMaxFileNameSize) {
    assert(false);
    return -1;
  }
  if (length < 1) {
    return -1;
  }

  // Make sure to NULL terminate
  if (size < length) {
    length = size - 1;
  }
  memcpy(file_name_utf8, file_name_utf8_, length);
  file_name_utf8[length] = 0;
  return 0;
}

bool FileWrapperImpl::Open() const {
  ReadLockScoped read(*rw_lock_);
  return open_;
}

int FileWrapperImpl::OpenFile(const char* file_name_utf8, bool read_only,
                              bool loop, bool text) {
  WriteLockScoped write(*rw_lock_);
  if (id_ != NULL && !managed_file_handle_)
    return -1;
  size_t length = strlen(file_name_utf8);
  if (length > kMaxFileNameSize - 1) {
    return -1;
  }

  read_only_ = read_only;

  FILE* tmp_id = NULL;
#if defined _WIN32
  wchar_t wide_file_name[kMaxFileNameSize];
  wide_file_name[0] = 0;

  MultiByteToWideChar(CP_UTF8,
                      0,  // UTF8 flag
                      file_name_utf8,
                      -1,  // Null terminated string
                      wide_file_name,
                      kMaxFileNameSize);
  if (text) {
    if (read_only) {
      tmp_id = _wfopen(wide_file_name, L"rt");
    } else {
      tmp_id = _wfopen(wide_file_name, L"wt");
    }
  } else {
    if (read_only) {
      tmp_id = _wfopen(wide_file_name, L"rb");
    } else {
      tmp_id = _wfopen(wide_file_name, L"wb");
    }
  }
#else
  if (text) {
    if (read_only) {
      tmp_id = fopen(file_name_utf8, "rt");
    } else {
      tmp_id = fopen(file_name_utf8, "wt");
    }
  } else {
    if (read_only) {
      tmp_id = fopen(file_name_utf8, "rb");
    } else {
      tmp_id = fopen(file_name_utf8, "wb");
    }
  }
#endif

  if (tmp_id != NULL) {
    // +1 comes from copying the NULL termination character.
    memcpy(file_name_utf8_, file_name_utf8, length + 1);
    if (id_ != NULL) {
      fclose(id_);
    }
    id_ = tmp_id;
    managed_file_handle_ = true;
    looping_ = loop;
    open_ = true;
    return 0;
  }
  return -1;
}

int FileWrapperImpl::OpenFromFileHandle(FILE* handle,
                                        bool manage_file,
                                        bool read_only,
                                        bool loop) {
  WriteLockScoped write(*rw_lock_);
  if (!handle)
    return -1;

  if (id_ != NULL) {
    if (managed_file_handle_)
      fclose(id_);
    else
      return -1;
  }

  id_ = handle;
  managed_file_handle_ = manage_file;
  read_only_ = read_only;
  looping_ = loop;
  open_ = true;
  return 0;
}

int FileWrapperImpl::Read(void* buf, int length) {
  WriteLockScoped write(*rw_lock_);
  if (length < 0)
    return -1;

  if (id_ == NULL)
    return -1;

  int bytes_read = static_cast<int>(fread(buf, 1, length, id_));
  if (bytes_read != length && !looping_) {
    CloseFileImpl();
  }
  return bytes_read;
}

int FileWrapperImpl::WriteText(const char* format, ...) {
  WriteLockScoped write(*rw_lock_);
  if (format == NULL)
    return -1;

  if (read_only_)
    return -1;

  if (id_ == NULL)
    return -1;

  va_list args;
  va_start(args, format);
  int num_chars = vfprintf(id_, format, args);
  va_end(args);

  if (num_chars >= 0) {
    return num_chars;
  } else {
    CloseFileImpl();
    return -1;
  }
}

bool FileWrapperImpl::Write(const void* buf, int length) {
  WriteLockScoped write(*rw_lock_);
  if (buf == NULL)
    return false;

  if (length < 0)
    return false;

  if (read_only_)
    return false;

  if (id_ == NULL)
    return false;

  // Check if it's time to stop writing.
  if (max_size_in_bytes_ > 0 &&
      (size_in_bytes_ + length) > max_size_in_bytes_) {
    FlushImpl();
    return false;
  }

  size_t num_bytes = fwrite(buf, 1, length, id_);
  if (num_bytes > 0) {
    size_in_bytes_ += num_bytes;
    return true;
  }

  CloseFileImpl();
  return false;
}

int FileWrapperImpl::CloseFileImpl() {
  if (id_ != NULL) {
    if (managed_file_handle_)
      fclose(id_);
    id_ = NULL;
  }
  memset(file_name_utf8_, 0, kMaxFileNameSize);
  open_ = false;
  return 0;
}

int FileWrapperImpl::FlushImpl() {
  if (id_ != NULL) {
    return fflush(id_);
  }
  return -1;
}

}  // namespace webrtc
