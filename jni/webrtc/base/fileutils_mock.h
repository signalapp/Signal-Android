/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_BASE_FILEUTILS_MOCK_H_
#define WEBRTC_BASE_FILEUTILS_MOCK_H_

#include <string>
#include <utility>
#include <vector>

#include "webrtc/base/fileutils.h"
#include "webrtc/base/gunit.h"
#include "webrtc/base/pathutils.h"
#include "webrtc/base/stream.h"

namespace rtc {

class FakeFileStream : public FileStream {
  public:
    explicit FakeFileStream(const std::string & contents) :
      string_stream_(contents)
    {}

    virtual StreamResult Read(void* buffer, size_t buffer_len,
                              size_t* read, int* error) {
      return string_stream_.Read(buffer, buffer_len, read, error);
    }

    virtual void Close() {
      return string_stream_.Close();
    }
    virtual bool GetSize(size_t* size) const {
      return string_stream_.GetSize(size);
    }

  private:
    StringStream string_stream_;
};

class FakeDirectoryIterator : public DirectoryIterator {
  public:
    typedef std::pair<std::string, std::string> File;

    /*
     * files should be sorted by directory
     * put '/' at the end of file if you want it to be a directory
     *
     * Sample list:
     *  /var/dir/file1
     *  /var/dir/file2
     *  /var/dir/subdir1/
     *  /var/dir/subdir2/
     *  /var/dir2/file2
     *  /var/dir3/
     *
     *  you can call Iterate for any path: /var, /var/dir, /var/dir2
     *  unrelated files will be ignored
     */
    explicit FakeDirectoryIterator(const std::vector<File>& all_files) :
      all_files_(all_files) {}

    virtual bool Iterate(const Pathname& path) {
      path_iterator_ = all_files_.begin();
      path_ = path.pathname();

      // make sure path ends end with '/'
      if (path_.rfind(Pathname::DefaultFolderDelimiter()) != path_.size() - 1)
        path_ += Pathname::DefaultFolderDelimiter();

      return  FakeDirectoryIterator::Search(std::string(""));
    }

    virtual bool Next() {
      std::string current_name = Name();
      path_iterator_++;
      return FakeDirectoryIterator::Search(current_name);
    }

    bool Search(const std::string& current_name) {
      for (; path_iterator_ != all_files_.end(); path_iterator_++) {
        if (path_iterator_->first.find(path_) == 0
            && Name().compare(current_name) != 0) {
          return true;
        }
      }

      return false;
    }

    virtual bool IsDirectory() const {
      std::string sub_path = path_iterator_->first;

      return std::string::npos !=
        sub_path.find(Pathname::DefaultFolderDelimiter(), path_.size());
    }

    virtual std::string Name() const {
      std::string sub_path = path_iterator_->first;

      // path     - top level path  (ex. /var/lib)
      // sub_path - subpath under top level path (ex. /var/lib/dir/dir/file )
      // find shortest non-trivial common path. (ex. /var/lib/dir)
      size_t start  = path_.size();
      size_t end    = sub_path.find(Pathname::DefaultFolderDelimiter(), start);

      if (end != std::string::npos) {
        return sub_path.substr(start, end - start);
      } else {
        return sub_path.substr(start);
      }
    }

  private:
    const std::vector<File> all_files_;

    std::string path_;
    std::vector<File>::const_iterator path_iterator_;
};

class FakeFileSystem : public FilesystemInterface {
  public:
    typedef std::pair<std::string, std::string> File;

    explicit FakeFileSystem(const std::vector<File>& all_files) :
     all_files_(all_files) {}

    virtual DirectoryIterator *IterateDirectory() {
     return new FakeDirectoryIterator(all_files_);
    }

    virtual FileStream * OpenFile(
       const Pathname &filename,
       const std::string &mode) {
     std::vector<File>::const_iterator i_files = all_files_.begin();
     std::string path = filename.pathname();

     for (; i_files != all_files_.end(); i_files++) {
       if (i_files->first.compare(path) == 0) {
         return new FakeFileStream(i_files->second);
       }
     }

     return NULL;
    }

    bool CreatePrivateFile(const Pathname &filename) {
      EXPECT_TRUE(false) << "Unsupported operation";
      return false;
    }
    bool DeleteFile(const Pathname &filename) {
      EXPECT_TRUE(false) << "Unsupported operation";
      return false;
    }
    bool DeleteEmptyFolder(const Pathname &folder) {
      EXPECT_TRUE(false) << "Unsupported operation";
      return false;
    }
    bool DeleteFolderContents(const Pathname &folder) {
      EXPECT_TRUE(false) << "Unsupported operation";
      return false;
    }
    bool DeleteFolderAndContents(const Pathname &folder) {
      EXPECT_TRUE(false) << "Unsupported operation";
      return false;
    }
    bool CreateFolder(const Pathname &pathname) {
      EXPECT_TRUE(false) << "Unsupported operation";
      return false;
    }
    bool MoveFolder(const Pathname &old_path, const Pathname &new_path) {
      EXPECT_TRUE(false) << "Unsupported operation";
      return false;
    }
    bool MoveFile(const Pathname &old_path, const Pathname &new_path) {
      EXPECT_TRUE(false) << "Unsupported operation";
      return false;
    }
    bool CopyFile(const Pathname &old_path, const Pathname &new_path) {
      EXPECT_TRUE(false) << "Unsupported operation";
      return false;
    }
    bool IsFolder(const Pathname &pathname) {
      EXPECT_TRUE(false) << "Unsupported operation";
      return false;
    }
    bool IsFile(const Pathname &pathname) {
      EXPECT_TRUE(false) << "Unsupported operation";
      return false;
    }
    bool IsAbsent(const Pathname &pathname) {
      EXPECT_TRUE(false) << "Unsupported operation";
      return false;
    }
    bool IsTemporaryPath(const Pathname &pathname) {
      EXPECT_TRUE(false) << "Unsupported operation";
      return false;
    }
    bool GetTemporaryFolder(Pathname &path, bool create,
                            const std::string *append) {
      EXPECT_TRUE(false) << "Unsupported operation";
      return false;
    }
    std::string TempFilename(const Pathname &dir, const std::string &prefix) {
      EXPECT_TRUE(false) << "Unsupported operation";
      return std::string();
    }
    bool GetFileSize(const Pathname &path, size_t *size) {
      EXPECT_TRUE(false) << "Unsupported operation";
      return false;
    }
    bool GetFileTime(const Pathname &path, FileTimeType which,
                     time_t* time) {
      EXPECT_TRUE(false) << "Unsupported operation";
      return false;
    }
    bool GetAppPathname(Pathname *path) {
      EXPECT_TRUE(false) << "Unsupported operation";
      return false;
    }
    bool GetAppDataFolder(Pathname *path, bool per_user) {
      EXPECT_TRUE(per_user) << "Unsupported operation";
#if defined(WEBRTC_WIN)
      path->SetPathname("c:\\Users\\test_user", "");
#else
      path->SetPathname("/home/user/test_user", "");
#endif
      return true;
    }
    bool GetAppTempFolder(Pathname *path) {
      EXPECT_TRUE(false) << "Unsupported operation";
      return false;
    }
    bool GetDiskFreeSpace(const Pathname& path, int64_t* freebytes) {
      EXPECT_TRUE(false) << "Unsupported operation";
      return false;
    }
    Pathname GetCurrentDirectory() {
      return Pathname();
    }

  private:
    const std::vector<File> all_files_;
};
}  // namespace rtc

#endif  // WEBRTC_BASE_FILEUTILS_MOCK_H_
