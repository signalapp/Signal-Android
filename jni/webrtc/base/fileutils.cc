/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <assert.h>

#include "webrtc/base/arraysize.h"
#include "webrtc/base/pathutils.h"
#include "webrtc/base/fileutils.h"
#include "webrtc/base/stringutils.h"
#include "webrtc/base/stream.h"

#if defined(WEBRTC_WIN)
#include "webrtc/base/win32filesystem.h"
#else
#include "webrtc/base/unixfilesystem.h"
#endif

#if !defined(WEBRTC_WIN)
#define MAX_PATH 260
#endif

namespace rtc {

//////////////////////////
// Directory Iterator   //
//////////////////////////

// A DirectoryIterator is created with a given directory. It originally points
// to the first file in the directory, and can be advanecd with Next(). This
// allows you to get information about each file.

  // Constructor
DirectoryIterator::DirectoryIterator()
#ifdef WEBRTC_WIN
    : handle_(INVALID_HANDLE_VALUE) {
#else
    : dir_(NULL), dirent_(NULL) {
#endif
}

  // Destructor
DirectoryIterator::~DirectoryIterator() {
#if defined(WEBRTC_WIN)
  if (handle_ != INVALID_HANDLE_VALUE)
    ::FindClose(handle_);
#else
  if (dir_)
    closedir(dir_);
#endif
}

  // Starts traversing a directory.
  // dir is the directory to traverse
  // returns true if the directory exists and is valid
bool DirectoryIterator::Iterate(const Pathname &dir) {
  directory_ = dir.pathname();
#if defined(WEBRTC_WIN)
  if (handle_ != INVALID_HANDLE_VALUE)
    ::FindClose(handle_);
  std::string d = dir.pathname() + '*';
  handle_ = ::FindFirstFile(ToUtf16(d).c_str(), &data_);
  if (handle_ == INVALID_HANDLE_VALUE)
    return false;
#else
  if (dir_ != NULL)
    closedir(dir_);
  dir_ = ::opendir(directory_.c_str());
  if (dir_ == NULL)
    return false;
  dirent_ = readdir(dir_);
  if (dirent_ == NULL)
    return false;

  if (::stat(std::string(directory_ + Name()).c_str(), &stat_) != 0)
    return false;
#endif
  return true;
}

  // Advances to the next file
  // returns true if there were more files in the directory.
bool DirectoryIterator::Next() {
#if defined(WEBRTC_WIN)
  return ::FindNextFile(handle_, &data_) == TRUE;
#else
  dirent_ = ::readdir(dir_);
  if (dirent_ == NULL)
    return false;

  return ::stat(std::string(directory_ + Name()).c_str(), &stat_) == 0;
#endif
}

  // returns true if the file currently pointed to is a directory
bool DirectoryIterator::IsDirectory() const {
#if defined(WEBRTC_WIN)
  return (data_.dwFileAttributes & FILE_ATTRIBUTE_DIRECTORY) != FALSE;
#else
  return S_ISDIR(stat_.st_mode);
#endif
}

  // returns the name of the file currently pointed to
std::string DirectoryIterator::Name() const {
#if defined(WEBRTC_WIN)
  return ToUtf8(data_.cFileName);
#else
  assert(dirent_ != NULL);
  return dirent_->d_name;
#endif
}

  // returns the size of the file currently pointed to
size_t DirectoryIterator::FileSize() const {
#if !defined(WEBRTC_WIN)
  return stat_.st_size;
#else
  return data_.nFileSizeLow;
#endif
}

bool DirectoryIterator::OlderThan(int seconds) const {
  time_t file_modify_time;
#if defined(WEBRTC_WIN)
  FileTimeToUnixTime(data_.ftLastWriteTime, &file_modify_time);
#else
  file_modify_time = stat_.st_mtime;
#endif
  return time(NULL) - file_modify_time >= seconds;
}

FilesystemInterface* Filesystem::default_filesystem_ = NULL;

FilesystemInterface *Filesystem::EnsureDefaultFilesystem() {
  if (!default_filesystem_) {
#if defined(WEBRTC_WIN)
    default_filesystem_ = new Win32Filesystem();
#else
    default_filesystem_ = new UnixFilesystem();
#endif
  }
  return default_filesystem_;
}

DirectoryIterator* FilesystemInterface::IterateDirectory() {
  return new DirectoryIterator();
}

bool FilesystemInterface::CopyFolder(const Pathname &old_path,
                                     const Pathname &new_path) {
  bool success = true;
  VERIFY(IsFolder(old_path));
  Pathname new_dir;
  new_dir.SetFolder(new_path.pathname());
  Pathname old_dir;
  old_dir.SetFolder(old_path.pathname());
  if (!CreateFolder(new_dir))
    return false;
  DirectoryIterator *di = IterateDirectory();
  if (!di)
    return false;
  if (di->Iterate(old_dir.pathname())) {
    do {
      if (di->Name() == "." || di->Name() == "..")
        continue;
      Pathname source;
      Pathname dest;
      source.SetFolder(old_dir.pathname());
      dest.SetFolder(new_path.pathname());
      source.SetFilename(di->Name());
      dest.SetFilename(di->Name());
      if (!CopyFileOrFolder(source, dest))
        success = false;
    } while (di->Next());
  }
  delete di;
  return success;
}

bool FilesystemInterface::DeleteFolderContents(const Pathname &folder) {
  bool success = true;
  VERIFY(IsFolder(folder));
  DirectoryIterator *di = IterateDirectory();
  if (!di)
    return false;
  if (di->Iterate(folder)) {
    do {
      if (di->Name() == "." || di->Name() == "..")
        continue;
      Pathname subdir;
      subdir.SetFolder(folder.pathname());
      if (di->IsDirectory()) {
        subdir.AppendFolder(di->Name());
        if (!DeleteFolderAndContents(subdir)) {
          success = false;
        }
      } else {
        subdir.SetFilename(di->Name());
        if (!DeleteFile(subdir)) {
          success = false;
        }
      }
    } while (di->Next());
  }
  delete di;
  return success;
}

bool FilesystemInterface::DeleteFolderAndContents(const Pathname& folder) {
  return DeleteFolderContents(folder) && DeleteEmptyFolder(folder);
}

bool FilesystemInterface::CleanAppTempFolder() {
  Pathname path;
  if (!GetAppTempFolder(&path))
    return false;
  if (IsAbsent(path))
    return true;
  if (!IsTemporaryPath(path)) {
    ASSERT(false);
    return false;
  }
  return DeleteFolderContents(path);
}

Pathname Filesystem::GetCurrentDirectory() {
  return EnsureDefaultFilesystem()->GetCurrentDirectory();
}

bool CreateUniqueFile(Pathname& path, bool create_empty) {
  LOG(LS_INFO) << "Path " << path.pathname() << std::endl;
  // If no folder is supplied, use the temporary folder
  if (path.folder().empty()) {
    Pathname temporary_path;
    if (!Filesystem::GetTemporaryFolder(temporary_path, true, NULL)) {
      printf("Get temp failed\n");
      return false;
    }
    path.SetFolder(temporary_path.pathname());
  }

  // If no filename is supplied, use a temporary name
  if (path.filename().empty()) {
    std::string folder(path.folder());
    std::string filename = Filesystem::TempFilename(folder, "gt");
    path.SetPathname(filename);
    if (!create_empty) {
      Filesystem::DeleteFile(path.pathname());
    }
    return true;
  }

  // Otherwise, create a unique name based on the given filename
  // foo.txt -> foo-N.txt
  const std::string basename = path.basename();
  const size_t MAX_VERSION = 100;
  size_t version = 0;
  while (version < MAX_VERSION) {
    std::string pathname = path.pathname();

    if (!Filesystem::IsFile(pathname)) {
      if (create_empty) {
        FileStream* fs = Filesystem::OpenFile(pathname, "w");
        delete fs;
      }
      return true;
    }
    version += 1;
    char version_base[MAX_PATH];
    sprintfn(version_base, arraysize(version_base), "%s-%u", basename.c_str(),
             version);
    path.SetBasename(version_base);
  }
  return true;
}

}  // namespace rtc
