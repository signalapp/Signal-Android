/*
 *  Copyright 2010 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "webrtc/base/win32windowpicker.h"

#include <string>
#include <vector>

#include "webrtc/base/arraysize.h"
#include "webrtc/base/common.h"
#include "webrtc/base/logging.h"

namespace rtc {

namespace {

// Window class names that we want to filter out.
const char kProgramManagerClass[] = "Progman";
const char kButtonClass[] = "Button";

}  // namespace

BOOL CALLBACK Win32WindowPicker::EnumProc(HWND hwnd, LPARAM l_param) {
  WindowDescriptionList* descriptions =
      reinterpret_cast<WindowDescriptionList*>(l_param);

  // Skip windows that are invisible, minimized, have no title, or are owned,
  // unless they have the app window style set. Except for minimized windows,
  // this is what Alt-Tab does.
  // TODO: Figure out how to grab a thumbnail of a minimized window and
  // include them in the list.
  int len = GetWindowTextLength(hwnd);
  HWND owner = GetWindow(hwnd, GW_OWNER);
  LONG exstyle = GetWindowLong(hwnd, GWL_EXSTYLE);
  if (len == 0 || IsIconic(hwnd) || !IsWindowVisible(hwnd) ||
      (owner && !(exstyle & WS_EX_APPWINDOW))) {
    // TODO: Investigate if windows without title still could be
    // interesting to share. We could use the name of the process as title:
    //
    // GetWindowThreadProcessId()
    // OpenProcess()
    // QueryFullProcessImageName()
    return TRUE;
  }

  // Skip the Program Manager window and the Start button.
  TCHAR class_name_w[500];
  ::GetClassName(hwnd, class_name_w, 500);
  std::string class_name = ToUtf8(class_name_w);
  if (class_name == kProgramManagerClass || class_name == kButtonClass) {
    // We don't want the Program Manager window nor the Start button.
    return TRUE;
  }

  TCHAR window_title[500];
  GetWindowText(hwnd, window_title, arraysize(window_title));
  std::string title = ToUtf8(window_title);

  WindowId id(hwnd);
  WindowDescription desc(id, title);
  descriptions->push_back(desc);
  return TRUE;
}

BOOL CALLBACK Win32WindowPicker::MonitorEnumProc(HMONITOR h_monitor,
                                                 HDC hdc_monitor,
                                                 LPRECT lprc_monitor,
                                                 LPARAM l_param) {
  DesktopDescriptionList* desktop_desc =
      reinterpret_cast<DesktopDescriptionList*>(l_param);

  DesktopId id(h_monitor, static_cast<int>(desktop_desc->size()));
  // TODO: Figure out an appropriate desktop title.
  DesktopDescription desc(id, "");

  // Determine whether it's the primary monitor.
  MONITORINFO monitor_info = {0};
  monitor_info.cbSize = sizeof(monitor_info);
  bool primary = (GetMonitorInfo(h_monitor, &monitor_info) &&
      (monitor_info.dwFlags & MONITORINFOF_PRIMARY) != 0);
  desc.set_primary(primary);

  desktop_desc->push_back(desc);
  return TRUE;
}

Win32WindowPicker::Win32WindowPicker() {
}

bool Win32WindowPicker::Init() {
  return true;
}
// TODO: Consider changing enumeration to clear() descriptions
// before append().
bool Win32WindowPicker::GetWindowList(WindowDescriptionList* descriptions) {
  LPARAM desc = reinterpret_cast<LPARAM>(descriptions);
  return EnumWindows(Win32WindowPicker::EnumProc, desc) != FALSE;
}

bool Win32WindowPicker::GetDesktopList(DesktopDescriptionList* descriptions) {
  // Create a fresh WindowDescriptionList so that we can use desktop_desc.size()
  // in MonitorEnumProc to compute the desktop index.
  DesktopDescriptionList desktop_desc;
  HDC hdc = GetDC(NULL);
  bool success = false;
  if (EnumDisplayMonitors(hdc, NULL, Win32WindowPicker::MonitorEnumProc,
      reinterpret_cast<LPARAM>(&desktop_desc)) != FALSE) {
    // Append the desktop descriptions to the end of the returned descriptions.
    descriptions->insert(descriptions->end(), desktop_desc.begin(),
                         desktop_desc.end());
    success = true;
  }
  ReleaseDC(NULL, hdc);
  return success;
}

bool Win32WindowPicker::GetDesktopDimensions(const DesktopId& id,
                                             int* width,
                                             int* height) {
  MONITORINFOEX monitor_info;
  monitor_info.cbSize = sizeof(MONITORINFOEX);
  if (!GetMonitorInfo(id.id(), &monitor_info)) {
    return false;
  }
  *width = monitor_info.rcMonitor.right - monitor_info.rcMonitor.left;
  *height = monitor_info.rcMonitor.bottom - monitor_info.rcMonitor.top;
  return true;
}

bool Win32WindowPicker::IsVisible(const WindowId& id) {
  return (::IsWindow(id.id()) != FALSE && ::IsWindowVisible(id.id()) != FALSE);
}

bool Win32WindowPicker::MoveToFront(const WindowId& id) {
  return SetForegroundWindow(id.id()) != FALSE;
}

}  // namespace rtc
