/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

// Conversion functions for UTF-8 and UTF-16 strings on Windows.
// Duplicated from talk/base/win32.h.
#ifndef WEBRTC_SYSTEM_WRAPPERS_INTERFACE_UTF_UTIL_H_
#define WEBRTC_SYSTEM_WRAPPERS_INTERFACE_UTF_UTIL_H_

#ifdef WIN32
#include <windows.h>
#include <string>

#include "webrtc/system_wrappers/interface/scoped_ptr.h"

namespace webrtc {

inline std::wstring ToUtf16(const char* utf8, size_t len) {
  int len16 = ::MultiByteToWideChar(CP_UTF8, 0, utf8, static_cast<int>(len),
                                    NULL, 0);
  scoped_ptr<wchar_t[]> ws(new wchar_t[len16]);
  ::MultiByteToWideChar(CP_UTF8, 0, utf8, static_cast<int>(len), ws.get(),
                        len16);
  return std::wstring(ws.get(), len16);
}

inline std::wstring ToUtf16(const std::string& str) {
  return ToUtf16(str.data(), str.length());
}

inline std::string ToUtf8(const wchar_t* wide, size_t len) {
  int len8 = ::WideCharToMultiByte(CP_UTF8, 0, wide, static_cast<int>(len),
                                   NULL, 0, NULL, NULL);
  scoped_ptr<char[]> ns(new char[len8]);
  ::WideCharToMultiByte(CP_UTF8, 0, wide, static_cast<int>(len), ns.get(), len8,
                        NULL, NULL);
  return std::string(ns.get(), len8);
}

inline std::string ToUtf8(const wchar_t* wide) {
  return ToUtf8(wide, wcslen(wide));
}

inline std::string ToUtf8(const std::wstring& wstr) {
  return ToUtf8(wstr.data(), wstr.length());
}

}  // namespace webrtc

#endif  // WIN32
#endif  // WEBRTC_SYSTEM_WRAPPERS_INTERFACE_UTF_UTIL_H_
