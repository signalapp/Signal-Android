/*
 *  Copyright (c) 2011 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_SYSTEM_WRAPPERS_INCLUDE_FILE_WRAPPER_H_
#define WEBRTC_SYSTEM_WRAPPERS_INCLUDE_FILE_WRAPPER_H_

#include <stddef.h>
#include <stdio.h>

#include "webrtc/base/criticalsection.h"
#include "webrtc/common_types.h"
#include "webrtc/typedefs.h"

// Implementation of an InStream and OutStream that can read (exclusive) or
// write from/to a file.

namespace webrtc {

// TODO(tommi): Remove the base classes, rename to rtc::File and move to base.
class FileWrapper : public InStream, public OutStream {
 public:
  static const size_t kMaxFileNameSize = 1024;

  // Factory methods.
  // TODO(tommi): Remove Create().
  static FileWrapper* Create();
  static FileWrapper Open(const char* file_name_utf8, bool read_only);

  FileWrapper(FILE* file, size_t max_size);
  ~FileWrapper() override;

  // Support for move semantics.
  FileWrapper(FileWrapper&& other);
  FileWrapper& operator=(FileWrapper&& other);

  // Returns true if a file has been opened.
  bool is_open() const { return file_ != nullptr; }

  // Opens a file in read or write mode, decided by the read_only parameter.
  bool OpenFile(const char* file_name_utf8, bool read_only);

  // Initializes the wrapper from an existing handle.  The wrapper
  // takes ownership of |handle| and closes it in CloseFile().
  bool OpenFromFileHandle(FILE* handle);

  void CloseFile();

  // Limits the file size to |bytes|. Writing will fail after the cap
  // is hit. Pass zero to use an unlimited size.
  // TODO(tommi): Could we move this out into a separate class?
  void SetMaxFileSize(size_t bytes);

  // Flush any pending writes.  Note: Flushing when closing, is not required.
  int Flush();

  // Rewinds the file to the start.
  int Rewind() override;
  int Read(void* buf, size_t length) override;
  bool Write(const void* buf, size_t length) override;

 private:
  FileWrapper();

  void CloseFileImpl();
  int FlushImpl();

  // TODO(tommi): Remove the lock.
  rtc::CriticalSection lock_;

  FILE* file_ = nullptr;
  size_t position_ = 0;
  size_t max_size_in_bytes_ = 0;

  // Copying is not supported.
  FileWrapper(const FileWrapper&) = delete;
  FileWrapper& operator=(const FileWrapper&) = delete;
};

}  // namespace webrtc

#endif  // WEBRTC_SYSTEM_WRAPPERS_INCLUDE_FILE_WRAPPER_H_
