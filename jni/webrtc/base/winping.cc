/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/base/winping.h"

#include <assert.h>
#include <Iphlpapi.h>

#include <algorithm>

#include "webrtc/base/byteorder.h"
#include "webrtc/base/common.h"
#include "webrtc/base/ipaddress.h"
#include "webrtc/base/logging.h"
#include "webrtc/base/nethelpers.h"
#include "webrtc/base/socketaddress.h"

namespace rtc {

//////////////////////////////////////////////////////////////////////
// Found in IPExport.h
//////////////////////////////////////////////////////////////////////

typedef struct icmp_echo_reply {
    ULONG   Address;            // Replying address
    ULONG   Status;             // Reply IP_STATUS
    ULONG   RoundTripTime;      // RTT in milliseconds
    USHORT  DataSize;           // Reply data size in bytes
    USHORT  Reserved;           // Reserved for system use
    PVOID   Data;               // Pointer to the reply data
    struct ip_option_information Options; // Reply options
} ICMP_ECHO_REPLY, * PICMP_ECHO_REPLY;

typedef struct icmpv6_echo_reply_lh {
  sockaddr_in6    Address;
  ULONG           Status;
  unsigned int    RoundTripTime;
} ICMPV6_ECHO_REPLY, *PICMPV6_ECHO_REPLY;

//
// IP_STATUS codes returned from IP APIs
//

#define IP_STATUS_BASE              11000

#define IP_SUCCESS                  0
#define IP_BUF_TOO_SMALL            (IP_STATUS_BASE + 1)
#define IP_DEST_NET_UNREACHABLE     (IP_STATUS_BASE + 2)
#define IP_DEST_HOST_UNREACHABLE    (IP_STATUS_BASE + 3)
#define IP_DEST_PROT_UNREACHABLE    (IP_STATUS_BASE + 4)
#define IP_DEST_PORT_UNREACHABLE    (IP_STATUS_BASE + 5)
#define IP_NO_RESOURCES             (IP_STATUS_BASE + 6)
#define IP_BAD_OPTION               (IP_STATUS_BASE + 7)
#define IP_HW_ERROR                 (IP_STATUS_BASE + 8)
#define IP_PACKET_TOO_BIG           (IP_STATUS_BASE + 9)
#define IP_REQ_TIMED_OUT            (IP_STATUS_BASE + 10)
#define IP_BAD_REQ                  (IP_STATUS_BASE + 11)
#define IP_BAD_ROUTE                (IP_STATUS_BASE + 12)
#define IP_TTL_EXPIRED_TRANSIT      (IP_STATUS_BASE + 13)
#define IP_TTL_EXPIRED_REASSEM      (IP_STATUS_BASE + 14)
#define IP_PARAM_PROBLEM            (IP_STATUS_BASE + 15)
#define IP_SOURCE_QUENCH            (IP_STATUS_BASE + 16)
#define IP_OPTION_TOO_BIG           (IP_STATUS_BASE + 17)
#define IP_BAD_DESTINATION          (IP_STATUS_BASE + 18)

#define IP_ADDR_DELETED             (IP_STATUS_BASE + 19)
#define IP_SPEC_MTU_CHANGE          (IP_STATUS_BASE + 20)
#define IP_MTU_CHANGE               (IP_STATUS_BASE + 21)
#define IP_UNLOAD                   (IP_STATUS_BASE + 22)
#define IP_ADDR_ADDED               (IP_STATUS_BASE + 23)
#define IP_MEDIA_CONNECT            (IP_STATUS_BASE + 24)
#define IP_MEDIA_DISCONNECT         (IP_STATUS_BASE + 25)
#define IP_BIND_ADAPTER             (IP_STATUS_BASE + 26)
#define IP_UNBIND_ADAPTER           (IP_STATUS_BASE + 27)
#define IP_DEVICE_DOES_NOT_EXIST    (IP_STATUS_BASE + 28)
#define IP_DUPLICATE_ADDRESS        (IP_STATUS_BASE + 29)
#define IP_INTERFACE_METRIC_CHANGE  (IP_STATUS_BASE + 30)
#define IP_RECONFIG_SECFLTR         (IP_STATUS_BASE + 31)
#define IP_NEGOTIATING_IPSEC        (IP_STATUS_BASE + 32)
#define IP_INTERFACE_WOL_CAPABILITY_CHANGE  (IP_STATUS_BASE + 33)
#define IP_DUPLICATE_IPADD          (IP_STATUS_BASE + 34)

#define IP_GENERAL_FAILURE          (IP_STATUS_BASE + 50)
#define MAX_IP_STATUS               IP_GENERAL_FAILURE
#define IP_PENDING                  (IP_STATUS_BASE + 255)

//
// Values used in the IP header Flags field.
//
#define IP_FLAG_DF      0x2         // Don't fragment this packet.

//
// Supported IP Option Types.
//
// These types define the options which may be used in the OptionsData field
// of the ip_option_information structure.  See RFC 791 for a complete
// description of each.
//
#define IP_OPT_EOL      0          // End of list option
#define IP_OPT_NOP      1          // No operation
#define IP_OPT_SECURITY 0x82       // Security option
#define IP_OPT_LSRR     0x83       // Loose source route
#define IP_OPT_SSRR     0x89       // Strict source route
#define IP_OPT_RR       0x7        // Record route
#define IP_OPT_TS       0x44       // Timestamp
#define IP_OPT_SID      0x88       // Stream ID (obsolete)
#define IP_OPT_ROUTER_ALERT 0x94  // Router Alert Option

#define MAX_OPT_SIZE    40         // Maximum length of IP options in bytes

//////////////////////////////////////////////////////////////////////
// Global Constants and Types
//////////////////////////////////////////////////////////////////////

const char * const ICMP_DLL_NAME = "Iphlpapi.dll";
const char * const ICMP_CREATE_FUNC = "IcmpCreateFile";
const char * const ICMP_CLOSE_FUNC = "IcmpCloseHandle";
const char * const ICMP_SEND_FUNC = "IcmpSendEcho";
const char * const ICMP6_CREATE_FUNC = "Icmp6CreateFile";
const char * const ICMP6_SEND_FUNC = "Icmp6SendEcho2";

inline uint32_t ReplySize(uint32_t data_size, int family) {
  if (family == AF_INET) {
    // A ping error message is 8 bytes long, so make sure we allow for at least
    // 8 bytes of reply data.
    return sizeof(ICMP_ECHO_REPLY) + std::max<uint32_t>(8, data_size);
  } else if (family == AF_INET6) {
    // Per MSDN, Send6IcmpEcho2 needs at least one ICMPV6_ECHO_REPLY,
    // 8 bytes for ICMP header, _and_ an IO_BLOCK_STATUS (2 pointers),
    // in addition to the data size.
    return sizeof(ICMPV6_ECHO_REPLY) + data_size + 8 + (2 * sizeof(DWORD*));
  } else {
    return 0;
  }
}

//////////////////////////////////////////////////////////////////////
// WinPing
//////////////////////////////////////////////////////////////////////

WinPing::WinPing()
    : dll_(0), hping_(INVALID_HANDLE_VALUE), create_(0), close_(0), send_(0),
      create6_(0), send6_(0), data_(0), dlen_(0), reply_(0),
      rlen_(0), valid_(false) {

  dll_ = LoadLibraryA(ICMP_DLL_NAME);
  if (!dll_) {
    LOG(LERROR) << "LoadLibrary: " << GetLastError();
    return;
  }

  create_ = (PIcmpCreateFile) GetProcAddress(dll_, ICMP_CREATE_FUNC);
  close_ = (PIcmpCloseHandle) GetProcAddress(dll_, ICMP_CLOSE_FUNC);
  send_ = (PIcmpSendEcho) GetProcAddress(dll_, ICMP_SEND_FUNC);
  if (!create_ || !close_ || !send_) {
    LOG(LERROR) << "GetProcAddress(ICMP_*): " << GetLastError();
    return;
  }
  hping_ = create_();
  if (hping_ == INVALID_HANDLE_VALUE) {
    LOG(LERROR) << "IcmpCreateFile: " << GetLastError();
    return;
  }

  if (HasIPv6Enabled()) {
    create6_ = (PIcmp6CreateFile) GetProcAddress(dll_, ICMP6_CREATE_FUNC);
    send6_ = (PIcmp6SendEcho2) GetProcAddress(dll_, ICMP6_SEND_FUNC);
    if (!create6_ || !send6_) {
      LOG(LERROR) << "GetProcAddress(ICMP6_*): " << GetLastError();
      return;
    }
    hping6_ = create6_();
    if (hping6_ == INVALID_HANDLE_VALUE) {
      LOG(LERROR) << "Icmp6CreateFile: " << GetLastError();
    }
  }

  dlen_ = 0;
  rlen_ = ReplySize(dlen_, AF_INET);
  data_ = new char[dlen_];
  reply_ = new char[rlen_];

  valid_ = true;
}

WinPing::~WinPing() {
  if ((hping_ != INVALID_HANDLE_VALUE) && close_) {
    if (!close_(hping_))
      LOG(WARNING) << "IcmpCloseHandle: " << GetLastError();
  }
  if ((hping6_ != INVALID_HANDLE_VALUE) && close_) {
    if (!close_(hping6_)) {
      LOG(WARNING) << "Icmp6CloseHandle: " << GetLastError();
    }
  }

  if (dll_)
    FreeLibrary(dll_);

  delete[] data_;
  delete[] reply_;
}

WinPing::PingResult WinPing::Ping(IPAddress ip,
                                  uint32_t data_size,
                                  uint32_t timeout,
                                  uint8_t ttl,
                                  bool allow_fragments) {
  if (data_size == 0 || timeout == 0 || ttl == 0) {
    LOG(LERROR) << "IcmpSendEcho: data_size/timeout/ttl is 0.";
    return PING_INVALID_PARAMS;
  }

  assert(IsValid());

  IP_OPTION_INFORMATION ipopt;
  memset(&ipopt, 0, sizeof(ipopt));
  if (!allow_fragments)
    ipopt.Flags |= IP_FLAG_DF;
  ipopt.Ttl = ttl;

  uint32_t reply_size = ReplySize(data_size, ip.family());

  if (data_size > dlen_) {
    delete [] data_;
    dlen_ = data_size;
    data_ = new char[dlen_];
    memset(data_, 'z', dlen_);
  }

  if (reply_size > rlen_) {
    delete [] reply_;
    rlen_ = reply_size;
    reply_ = new char[rlen_];
  }
  DWORD result = 0;
  if (ip.family() == AF_INET) {
    result = send_(hping_, ip.ipv4_address().S_un.S_addr, data_,
                   uint16_t(data_size), &ipopt, reply_, reply_size, timeout);
  } else if (ip.family() == AF_INET6) {
    sockaddr_in6 src = {0};
    sockaddr_in6 dst = {0};
    src.sin6_family = AF_INET6;
    dst.sin6_family = AF_INET6;
    dst.sin6_addr = ip.ipv6_address();
    result = send6_(hping6_, NULL, NULL, NULL, &src, &dst, data_,
                    int16_t(data_size), &ipopt, reply_, reply_size, timeout);
  }
  if (result == 0) {
    DWORD error = GetLastError();
    if (error == IP_PACKET_TOO_BIG)
      return PING_TOO_LARGE;
    if (error == IP_REQ_TIMED_OUT)
      return PING_TIMEOUT;
    LOG(LERROR) << "IcmpSendEcho(" << ip.ToSensitiveString()
                << ", " << data_size << "): " << error;
    return PING_FAIL;
  }

  return PING_SUCCESS;
}

//////////////////////////////////////////////////////////////////////
// Microsoft Documenation
//////////////////////////////////////////////////////////////////////
//
// Routine Name:
//
//     IcmpCreateFile
//
// Routine Description:
//
//     Opens a handle on which ICMP Echo Requests can be issued.
//
// Arguments:
//
//     None.
//
// Return Value:
//
//     An open file handle or INVALID_HANDLE_VALUE. Extended error information
//     is available by calling GetLastError().
//
//////////////////////////////////////////////////////////////////////
//
// Routine Name:
//
//     IcmpCloseHandle
//
// Routine Description:
//
//     Closes a handle opened by ICMPOpenFile.
//
// Arguments:
//
//     IcmpHandle  - The handle to close.
//
// Return Value:
//
//     TRUE if the handle was closed successfully, otherwise FALSE. Extended
//     error information is available by calling GetLastError().
//
//////////////////////////////////////////////////////////////////////
//
// Routine Name:
//
//     IcmpSendEcho
//
// Routine Description:
//
//     Sends an ICMP Echo request and returns any replies. The
//     call returns when the timeout has expired or the reply buffer
//     is filled.
//
// Arguments:
//
//     IcmpHandle           - An open handle returned by ICMPCreateFile.
//
//     DestinationAddress   - The destination of the echo request.
//
//     RequestData          - A buffer containing the data to send in the
//                            request.
//
//     RequestSize          - The number of bytes in the request data buffer.
//
//     RequestOptions       - Pointer to the IP header options for the request.
//                            May be NULL.
//
//     ReplyBuffer          - A buffer to hold any replies to the request.
//                            On return, the buffer will contain an array of
//                            ICMP_ECHO_REPLY structures followed by the
//                            options and data for the replies. The buffer
//                            should be large enough to hold at least one
//                            ICMP_ECHO_REPLY structure plus
//                            MAX(RequestSize, 8) bytes of data since an ICMP
//                            error message contains 8 bytes of data.
//
//     ReplySize            - The size in bytes of the reply buffer.
//
//     Timeout              - The time in milliseconds to wait for replies.
//
// Return Value:
//
//     Returns the number of ICMP_ECHO_REPLY structures stored in ReplyBuffer.
//     The status of each reply is contained in the structure. If the return
//     value is zero, extended error information is available via
//     GetLastError().
//
//////////////////////////////////////////////////////////////////////

} // namespace rtc
