/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_BASE_WIN32_H_
#define WEBRTC_BASE_WIN32_H_

#if defined(WEBRTC_WIN)

#ifndef WIN32_LEAN_AND_MEAN
#define WIN32_LEAN_AND_MEAN
#endif

// Make sure we don't get min/max macros
#ifndef NOMINMAX
#define NOMINMAX
#endif

#include <winsock2.h>
#include <windows.h>

#ifndef SECURITY_MANDATORY_LABEL_AUTHORITY
// Add defines that we use if we are compiling against older sdks
#define SECURITY_MANDATORY_MEDIUM_RID               (0x00002000L)
#define TokenIntegrityLevel static_cast<TOKEN_INFORMATION_CLASS>(0x19)
typedef struct _TOKEN_MANDATORY_LABEL {
    SID_AND_ATTRIBUTES Label;
} TOKEN_MANDATORY_LABEL, *PTOKEN_MANDATORY_LABEL;
#endif  // SECURITY_MANDATORY_LABEL_AUTHORITY

#undef SetPort

#include <string>

#include "webrtc/base/stringutils.h"
#include "webrtc/base/basictypes.h"

namespace rtc {

const char* win32_inet_ntop(int af, const void *src, char* dst, socklen_t size);
int win32_inet_pton(int af, const char* src, void *dst);

inline std::wstring ToUtf16(const char* utf8, size_t len) {
  int len16 = ::MultiByteToWideChar(CP_UTF8, 0, utf8, static_cast<int>(len),
                                    NULL, 0);
  wchar_t* ws = STACK_ARRAY(wchar_t, len16);
  ::MultiByteToWideChar(CP_UTF8, 0, utf8, static_cast<int>(len), ws, len16);
  return std::wstring(ws, len16);
}

inline std::wstring ToUtf16(const std::string& str) {
  return ToUtf16(str.data(), str.length());
}

inline std::string ToUtf8(const wchar_t* wide, size_t len) {
  int len8 = ::WideCharToMultiByte(CP_UTF8, 0, wide, static_cast<int>(len),
                                   NULL, 0, NULL, NULL);
  char* ns = STACK_ARRAY(char, len8);
  ::WideCharToMultiByte(CP_UTF8, 0, wide, static_cast<int>(len), ns, len8,
                        NULL, NULL);
  return std::string(ns, len8);
}

inline std::string ToUtf8(const wchar_t* wide) {
  return ToUtf8(wide, wcslen(wide));
}

inline std::string ToUtf8(const std::wstring& wstr) {
  return ToUtf8(wstr.data(), wstr.length());
}

// Convert FILETIME to time_t
void FileTimeToUnixTime(const FILETIME& ft, time_t* ut);

// Convert time_t to FILETIME
void UnixTimeToFileTime(const time_t& ut, FILETIME * ft);

// Convert a Utf8 path representation to a non-length-limited Unicode pathname.
bool Utf8ToWindowsFilename(const std::string& utf8, std::wstring* filename);

// Convert a FILETIME to a UInt64
inline uint64_t ToUInt64(const FILETIME& ft) {
  ULARGE_INTEGER r = {{ft.dwLowDateTime, ft.dwHighDateTime}};
  return r.QuadPart;
}

enum WindowsMajorVersions {
  kWindows2000 = 5,
  kWindowsVista = 6,
};
bool GetOsVersion(int* major, int* minor, int* build);

inline bool IsWindowsVistaOrLater() {
  int major;
  return (GetOsVersion(&major, NULL, NULL) && major >= kWindowsVista);
}

inline bool IsWindowsXpOrLater() {
  int major, minor;
  return (GetOsVersion(&major, &minor, NULL) &&
          (major >= kWindowsVista ||
          (major == kWindows2000 && minor >= 1)));
}

inline bool IsWindows8OrLater() {
  int major, minor;
  return (GetOsVersion(&major, &minor, NULL) &&
          (major > kWindowsVista ||
          (major == kWindowsVista && minor >= 2)));
}

// Determine the current integrity level of the process.
bool GetCurrentProcessIntegrityLevel(int* level);

inline bool IsCurrentProcessLowIntegrity() {
  int level;
  return (GetCurrentProcessIntegrityLevel(&level) &&
      level < SECURITY_MANDATORY_MEDIUM_RID);
}

bool AdjustCurrentProcessPrivilege(const TCHAR* privilege, bool to_enable);

}  // namespace rtc

#endif  // WEBRTC_WIN
#endif  // WEBRTC_BASE_WIN32_H_
