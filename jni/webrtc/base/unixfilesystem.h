/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_BASE_UNIXFILESYSTEM_H_
#define WEBRTC_BASE_UNIXFILESYSTEM_H_

#include <sys/types.h>

#include "webrtc/base/fileutils.h"

namespace rtc {

class UnixFilesystem : public FilesystemInterface {
 public:
  UnixFilesystem();
  ~UnixFilesystem() override;

#if defined(WEBRTC_ANDROID) || defined(WEBRTC_IOS)
  // Android does not have a native code API to fetch the app data or temp
  // folders. That needs to be passed into this class from Java. Similarly, iOS
  // only supports an Objective-C API for fetching the folder locations, so that
  // needs to be passed in here from Objective-C.  Or at least that used to be
  // the case; now the ctor will do the work if necessary and possible.
  // TODO(fischman): add an Android version that uses JNI and drop the
  // SetApp*Folder() APIs once external users stop using them.
  static void SetAppDataFolder(const std::string& folder);
  static void SetAppTempFolder(const std::string& folder);
#endif

  // Opens a file. Returns an open StreamInterface if function succeeds.
  // Otherwise, returns NULL.
  FileStream* OpenFile(const Pathname& filename,
                       const std::string& mode) override;

  // Atomically creates an empty file accessible only to the current user if one
  // does not already exist at the given path, otherwise fails.
  bool CreatePrivateFile(const Pathname& filename) override;

  // This will attempt to delete the file located at filename.
  // It will fail with VERIY if you pass it a non-existant file, or a directory.
  bool DeleteFile(const Pathname& filename) override;

  // This will attempt to delete the folder located at 'folder'
  // It ASSERTs and returns false if you pass it a non-existant folder or a
  // plain file.
  bool DeleteEmptyFolder(const Pathname& folder) override;

  // Creates a directory. This will call itself recursively to create /foo/bar
  // even if /foo does not exist. All created directories are created with the
  // given mode.
  // Returns TRUE if function succeeds
  virtual bool CreateFolder(const Pathname &pathname, mode_t mode);

  // As above, with mode = 0755.
  bool CreateFolder(const Pathname& pathname) override;

  // This moves a file from old_path to new_path, where "file" can be a plain
  // file or directory, which will be moved recursively.
  // Returns true if function succeeds.
  bool MoveFile(const Pathname& old_path, const Pathname& new_path) override;
  bool MoveFolder(const Pathname& old_path, const Pathname& new_path) override;

  // This copies a file from old_path to _new_path where "file" can be a plain
  // file or directory, which will be copied recursively.
  // Returns true if function succeeds
  bool CopyFile(const Pathname& old_path, const Pathname& new_path) override;

  // Returns true if a pathname is a directory
  bool IsFolder(const Pathname& pathname) override;

  // Returns true if pathname represents a temporary location on the system.
  bool IsTemporaryPath(const Pathname& pathname) override;

  // Returns true of pathname represents an existing file
  bool IsFile(const Pathname& pathname) override;

  // Returns true if pathname refers to no filesystem object, every parent
  // directory either exists, or is also absent.
  bool IsAbsent(const Pathname& pathname) override;

  std::string TempFilename(const Pathname& dir,
                           const std::string& prefix) override;

  // A folder appropriate for storing temporary files (Contents are
  // automatically deleted when the program exists)
  bool GetTemporaryFolder(Pathname& path,
                          bool create,
                          const std::string* append) override;

  bool GetFileSize(const Pathname& path, size_t* size) override;
  bool GetFileTime(const Pathname& path,
                   FileTimeType which,
                   time_t* time) override;

  // Returns the path to the running application.
  bool GetAppPathname(Pathname* path) override;

  bool GetAppDataFolder(Pathname* path, bool per_user) override;

  // Get a temporary folder that is unique to the current user and application.
  bool GetAppTempFolder(Pathname* path) override;

  bool GetDiskFreeSpace(const Pathname& path, int64_t* freebytes) override;

  // Returns the absolute path of the current directory.
  Pathname GetCurrentDirectory() override;

 private:
#if defined(WEBRTC_ANDROID) || defined(WEBRTC_IOS)
  static char* provided_app_data_folder_;
  static char* provided_app_temp_folder_;
#else
  static char* app_temp_path_;
#endif

  static char* CopyString(const std::string& str);
};

}  // namespace rtc

#endif  // WEBRTC_BASE_UNIXFILESYSTEM_H_
