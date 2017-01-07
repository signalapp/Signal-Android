/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_BASE_WINPING_H__
#define WEBRTC_BASE_WINPING_H__

#if defined(WEBRTC_WIN)

#include "webrtc/base/win32.h"
#include "webrtc/base/basictypes.h"
#include "webrtc/base/IPAddress.h"

namespace rtc {

// This class wraps a Win32 API for doing ICMP pinging.  This API, unlike the
// the normal socket APIs (as implemented on Win9x), will return an error if
// an ICMP packet with the dont-fragment bit set is too large.  This means this
// class can be used to detect the MTU to a given address.

typedef struct ip_option_information {
    UCHAR   Ttl;                // Time To Live
    UCHAR   Tos;                // Type Of Service
    UCHAR   Flags;              // IP header flags
    UCHAR   OptionsSize;        // Size in bytes of options data
    PUCHAR  OptionsData;        // Pointer to options data
} IP_OPTION_INFORMATION, * PIP_OPTION_INFORMATION;

typedef HANDLE (WINAPI *PIcmpCreateFile)();

typedef BOOL (WINAPI *PIcmpCloseHandle)(HANDLE icmp_handle);

typedef HANDLE (WINAPI *PIcmp6CreateFile)();

typedef BOOL (WINAPI *PIcmp6CloseHandle)(HANDLE icmp_handle);

typedef DWORD (WINAPI *PIcmpSendEcho)(
    HANDLE                   IcmpHandle,
    ULONG                    DestinationAddress,
    LPVOID                   RequestData,
    WORD                     RequestSize,
    PIP_OPTION_INFORMATION   RequestOptions,
    LPVOID                   ReplyBuffer,
    DWORD                    ReplySize,
    DWORD                    Timeout);

typedef DWORD (WINAPI *PIcmp6SendEcho2)(
    HANDLE IcmpHandle,
    HANDLE Event,
    FARPROC ApcRoutine,
    PVOID ApcContext,
    struct sockaddr_in6 *SourceAddress,
    struct sockaddr_in6 *DestinationAddress,
    LPVOID RequestData,
    WORD RequestSize,
    PIP_OPTION_INFORMATION RequestOptions,
    LPVOID ReplyBuffer,
    DWORD ReplySize,
    DWORD Timeout
);

class WinPing {
public:
    WinPing();
    ~WinPing();

    // Determines whether the class was initialized correctly.
    bool IsValid() { return valid_; }

    // Attempts to send a ping with the given parameters.
    enum PingResult { PING_FAIL, PING_INVALID_PARAMS,
                      PING_TOO_LARGE, PING_TIMEOUT, PING_SUCCESS };
    PingResult Ping(IPAddress ip,
                    uint32_t data_size,
                    uint32_t timeout_millis,
                    uint8_t ttl,
                    bool allow_fragments);

private:
    HMODULE dll_;
    HANDLE hping_;
    HANDLE hping6_;
    PIcmpCreateFile create_;
    PIcmpCloseHandle close_;
    PIcmpSendEcho send_;
    PIcmp6CreateFile create6_;
    PIcmp6SendEcho2 send6_;
    char* data_;
    uint32_t dlen_;
    char* reply_;
    uint32_t rlen_;
    bool valid_;
};

} // namespace rtc

#endif // WEBRTC_WIN 

#endif // WEBRTC_BASE_WINPING_H__
