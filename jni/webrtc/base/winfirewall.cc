/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/base/winfirewall.h"

#include "webrtc/base/win32.h"

#include <comdef.h>
#include <netfw.h>

#define RELEASE(lpUnk) do { \
  if ((lpUnk) != NULL) { \
    (lpUnk)->Release(); \
    (lpUnk) = NULL; \
  } \
} while (0)

namespace rtc {

//////////////////////////////////////////////////////////////////////
// WinFirewall
//////////////////////////////////////////////////////////////////////

WinFirewall::WinFirewall() : mgr_(NULL), policy_(NULL), profile_(NULL) {
}

WinFirewall::~WinFirewall() {
  Shutdown();
}

bool WinFirewall::Initialize(HRESULT* result) {
  if (mgr_) {
    if (result) {
      *result = S_OK;
    }
    return true;
  }

  HRESULT hr = CoCreateInstance(__uuidof(NetFwMgr),
                                0, CLSCTX_INPROC_SERVER,
                                __uuidof(INetFwMgr),
                                reinterpret_cast<void **>(&mgr_));
  if (SUCCEEDED(hr) && (mgr_ != NULL))
    hr = mgr_->get_LocalPolicy(&policy_);
  if (SUCCEEDED(hr) && (policy_ != NULL))
    hr = policy_->get_CurrentProfile(&profile_);

  if (result)
    *result = hr;
  return SUCCEEDED(hr) && (profile_ != NULL);
}

void WinFirewall::Shutdown() {
  RELEASE(profile_);
  RELEASE(policy_);
  RELEASE(mgr_);
}

bool WinFirewall::Enabled() const {
  if (!profile_)
    return false;

  VARIANT_BOOL fwEnabled = VARIANT_FALSE;
  profile_->get_FirewallEnabled(&fwEnabled);
  return (fwEnabled != VARIANT_FALSE);
}

bool WinFirewall::QueryAuthorized(const char* filename, bool* authorized)
    const {
  return QueryAuthorizedW(ToUtf16(filename).c_str(), authorized);
}

bool WinFirewall::QueryAuthorizedW(const wchar_t* filename, bool* authorized)
    const {
  *authorized = false;
  bool success = false;

  if (!profile_)
    return false;

  _bstr_t bfilename = filename;

  INetFwAuthorizedApplications* apps = NULL;
  HRESULT hr = profile_->get_AuthorizedApplications(&apps);
  if (SUCCEEDED(hr) && (apps != NULL)) {
    INetFwAuthorizedApplication* app = NULL;
    hr = apps->Item(bfilename, &app);
    if (SUCCEEDED(hr) && (app != NULL)) {
      VARIANT_BOOL fwEnabled = VARIANT_FALSE;
      hr = app->get_Enabled(&fwEnabled);
      app->Release();

      if (SUCCEEDED(hr)) {
        success = true;
        *authorized = (fwEnabled != VARIANT_FALSE);
      }
    } else if (hr == HRESULT_FROM_WIN32(ERROR_FILE_NOT_FOUND)) {
      // No entry in list of authorized apps
      success = true;
    } else {
      // Unexpected error
    }
    apps->Release();
  }

  return success;
}

bool WinFirewall::AddApplication(const char* filename,
                                 const char* friendly_name,
                                 bool authorized,
                                 HRESULT* result) {
  return AddApplicationW(ToUtf16(filename).c_str(),
      ToUtf16(friendly_name).c_str(), authorized, result);
}

bool WinFirewall::AddApplicationW(const wchar_t* filename,
                                  const wchar_t* friendly_name,
                                  bool authorized,
                                  HRESULT* result) {
  INetFwAuthorizedApplications* apps = NULL;
  HRESULT hr = profile_->get_AuthorizedApplications(&apps);
  if (SUCCEEDED(hr) && (apps != NULL)) {
    INetFwAuthorizedApplication* app = NULL;
    hr = CoCreateInstance(__uuidof(NetFwAuthorizedApplication),
                          0, CLSCTX_INPROC_SERVER,
                          __uuidof(INetFwAuthorizedApplication),
                          reinterpret_cast<void **>(&app));
    if (SUCCEEDED(hr) && (app != NULL)) {
      _bstr_t bstr = filename;
      hr = app->put_ProcessImageFileName(bstr);
      bstr = friendly_name;
      if (SUCCEEDED(hr))
        hr = app->put_Name(bstr);
      if (SUCCEEDED(hr))
        hr = app->put_Enabled(authorized ? VARIANT_TRUE : VARIANT_FALSE);
      if (SUCCEEDED(hr))
        hr = apps->Add(app);
      app->Release();
    }
    apps->Release();
  }
  if (result)
    *result = hr;
  return SUCCEEDED(hr);
}

}  // namespace rtc
