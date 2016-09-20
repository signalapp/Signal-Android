/*
 *  Copyright 2010 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#ifndef WEBRTC_BASE_WIN32WINDOWPICKER_H_
#define WEBRTC_BASE_WIN32WINDOWPICKER_H_

#include "webrtc/base/win32.h"
#include "webrtc/base/windowpicker.h"

namespace rtc {

class Win32WindowPicker : public WindowPicker {
 public:
  Win32WindowPicker();
  virtual bool Init();
  virtual bool IsVisible(const WindowId& id);
  virtual bool MoveToFront(const WindowId& id);
  virtual bool GetWindowList(WindowDescriptionList* descriptions);
  virtual bool GetDesktopList(DesktopDescriptionList* descriptions);
  virtual bool GetDesktopDimensions(const DesktopId& id, int* width,
                                    int* height);

 protected:
  static BOOL CALLBACK EnumProc(HWND hwnd, LPARAM l_param);
  static BOOL CALLBACK MonitorEnumProc(HMONITOR h_monitor,
                                       HDC hdc_monitor,
                                       LPRECT lprc_monitor,
                                       LPARAM l_param);
};

}  // namespace rtc

#endif  // WEBRTC_BASE_WIN32WINDOWPICKER_H_
