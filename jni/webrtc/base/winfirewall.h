/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_BASE_WINFIREWALL_H_
#define WEBRTC_BASE_WINFIREWALL_H_

#ifndef _HRESULT_DEFINED
#define _HRESULT_DEFINED
typedef long HRESULT;  // Can't forward declare typedef, but don't need all win
#endif // !_HRESULT_DEFINED

struct INetFwMgr;
struct INetFwPolicy;
struct INetFwProfile;

namespace rtc {

//////////////////////////////////////////////////////////////////////
// WinFirewall
//////////////////////////////////////////////////////////////////////

class WinFirewall {
 public:
  WinFirewall();
  ~WinFirewall();

  bool Initialize(HRESULT* result);
  void Shutdown();

  bool Enabled() const;
  bool QueryAuthorized(const char* filename, bool* authorized) const;
  bool QueryAuthorizedW(const wchar_t* filename, bool* authorized) const;

  bool AddApplication(const char* filename, const char* friendly_name,
                      bool authorized, HRESULT* result);
  bool AddApplicationW(const wchar_t* filename, const wchar_t* friendly_name,
                       bool authorized, HRESULT* result);

 private:
  INetFwMgr* mgr_;
  INetFwPolicy* policy_;
  INetFwProfile* profile_;
};

//////////////////////////////////////////////////////////////////////

}  // namespace rtc

#endif  // WEBRTC_BASE_WINFIREWALL_H_
