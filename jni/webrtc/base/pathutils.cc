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
#include "webrtc/base/win32.h"
#include <shellapi.h>
#include <shlobj.h>
#include <tchar.h>
#endif  // WEBRTC_WIN

#include "webrtc/base/common.h"
#include "webrtc/base/fileutils.h"
#include "webrtc/base/logging.h"
#include "webrtc/base/pathutils.h"
#include "webrtc/base/stringutils.h"
#include "webrtc/base/urlencode.h"

namespace rtc {

static const char EMPTY_STR[] = "";

// EXT_DELIM separates a file basename from extension
const char EXT_DELIM = '.';

// FOLDER_DELIMS separate folder segments and the filename
const char* const FOLDER_DELIMS = "/\\";

// DEFAULT_FOLDER_DELIM is the preferred delimiter for this platform
#if WEBRTC_WIN
const char DEFAULT_FOLDER_DELIM = '\\';
#else  // !WEBRTC_WIN
const char DEFAULT_FOLDER_DELIM = '/';
#endif  // !WEBRTC_WIN

///////////////////////////////////////////////////////////////////////////////
// Pathname - parsing of pathnames into components, and vice versa
///////////////////////////////////////////////////////////////////////////////

bool Pathname::IsFolderDelimiter(char ch) {
  return (NULL != ::strchr(FOLDER_DELIMS, ch));
}

char Pathname::DefaultFolderDelimiter() {
  return DEFAULT_FOLDER_DELIM;
}

Pathname::Pathname()
    : folder_delimiter_(DEFAULT_FOLDER_DELIM) {
}

Pathname::Pathname(const Pathname&) = default;
Pathname::Pathname(Pathname&&) = default;

Pathname::Pathname(const std::string& pathname)
    : folder_delimiter_(DEFAULT_FOLDER_DELIM) {
  SetPathname(pathname);
}

Pathname::Pathname(const std::string& folder, const std::string& filename)
    : folder_delimiter_(DEFAULT_FOLDER_DELIM) {
  SetPathname(folder, filename);
}

Pathname& Pathname::operator=(const Pathname&) = default;
Pathname& Pathname::operator=(Pathname&&) = default;

void Pathname::SetFolderDelimiter(char delimiter) {
  ASSERT(IsFolderDelimiter(delimiter));
  folder_delimiter_ = delimiter;
}

void Pathname::Normalize() {
  for (size_t i=0; i<folder_.length(); ++i) {
    if (IsFolderDelimiter(folder_[i])) {
      folder_[i] = folder_delimiter_;
    }
  }
}

void Pathname::clear() {
  folder_.clear();
  basename_.clear();
  extension_.clear();
}

bool Pathname::empty() const {
  return folder_.empty() && basename_.empty() && extension_.empty();
}

std::string Pathname::pathname() const {
  std::string pathname(folder_);
  pathname.append(basename_);
  pathname.append(extension_);
  if (pathname.empty()) {
    // Instead of the empty pathname, return the current working directory.
    pathname.push_back('.');
    pathname.push_back(folder_delimiter_);
  }
  return pathname;
}

std::string Pathname::url() const {
  std::string s = "file:///";
  for (size_t i=0; i<folder_.length(); ++i) {
    if (IsFolderDelimiter(folder_[i]))
      s += '/';
    else
      s += folder_[i];
  }
  s += basename_;
  s += extension_;
  return UrlEncodeStringForOnlyUnsafeChars(s);
}

void Pathname::SetPathname(const std::string& pathname) {
  std::string::size_type pos = pathname.find_last_of(FOLDER_DELIMS);
  if (pos != std::string::npos) {
    SetFolder(pathname.substr(0, pos + 1));
    SetFilename(pathname.substr(pos + 1));
  } else {
    SetFolder(EMPTY_STR);
    SetFilename(pathname);
  }
}

void Pathname::SetPathname(const std::string& folder,
                           const std::string& filename) {
  SetFolder(folder);
  SetFilename(filename);
}

void Pathname::AppendPathname(const std::string& pathname) {
  std::string full_pathname(folder_);
  full_pathname.append(pathname);
  SetPathname(full_pathname);
}

std::string Pathname::folder() const {
  return folder_;
}

std::string Pathname::folder_name() const {
  std::string::size_type pos = std::string::npos;
  if (folder_.size() >= 2) {
    pos = folder_.find_last_of(FOLDER_DELIMS, folder_.length() - 2);
  }
  if (pos != std::string::npos) {
    return folder_.substr(pos + 1);
  } else {
    return folder_;
  }
}

std::string Pathname::parent_folder() const {
  std::string::size_type pos = std::string::npos;
  if (folder_.size() >= 2) {
    pos = folder_.find_last_of(FOLDER_DELIMS, folder_.length() - 2);
  }
  if (pos != std::string::npos) {
    return folder_.substr(0, pos + 1);
  } else {
    return EMPTY_STR;
  }
}

void Pathname::SetFolder(const std::string& folder) {
  folder_.assign(folder);
  // Ensure folder ends in a path delimiter
  if (!folder_.empty() && !IsFolderDelimiter(folder_[folder_.length()-1])) {
    folder_.push_back(folder_delimiter_);
  }
}

void Pathname::AppendFolder(const std::string& folder) {
  folder_.append(folder);
  // Ensure folder ends in a path delimiter
  if (!folder_.empty() && !IsFolderDelimiter(folder_[folder_.length()-1])) {
    folder_.push_back(folder_delimiter_);
  }
}

std::string Pathname::basename() const {
  return basename_;
}

bool Pathname::SetBasename(const std::string& basename) {
  if(basename.find_first_of(FOLDER_DELIMS) != std::string::npos) {
    return false;
  }
  basename_.assign(basename);
  return true;
}

std::string Pathname::extension() const {
  return extension_;
}

bool Pathname::SetExtension(const std::string& extension) {
  if (extension.find_first_of(FOLDER_DELIMS) != std::string::npos ||
    extension.find_first_of(EXT_DELIM, 1) != std::string::npos) {
      return false;
  }
  extension_.assign(extension);
  // Ensure extension begins with the extension delimiter
  if (!extension_.empty() && (extension_[0] != EXT_DELIM)) {
    extension_.insert(extension_.begin(), EXT_DELIM);
  }
  return true;
}

std::string Pathname::filename() const {
  std::string filename(basename_);
  filename.append(extension_);
  return filename;
}

bool Pathname::SetFilename(const std::string& filename) {
  std::string::size_type pos = filename.rfind(EXT_DELIM);
  if ((pos == std::string::npos) || (pos == 0)) {
    return SetExtension(EMPTY_STR) && SetBasename(filename);
  } else {
    return SetExtension(filename.substr(pos)) && SetBasename(filename.substr(0, pos));
  }
}

#if defined(WEBRTC_WIN)
bool Pathname::GetDrive(char* drive, uint32_t bytes) const {
  return GetDrive(drive, bytes, folder_);
}

// static
bool Pathname::GetDrive(char* drive,
                        uint32_t bytes,
                        const std::string& pathname) {
  // need at lease 4 bytes to save c:
  if (bytes < 4 || pathname.size() < 3) {
    return false;
  }

  memcpy(drive, pathname.c_str(), 3);
  drive[3] = 0;
  // sanity checking
  return (isalpha(drive[0]) &&
          drive[1] == ':' &&
          drive[2] == '\\');
}
#endif

///////////////////////////////////////////////////////////////////////////////

} // namespace rtc
