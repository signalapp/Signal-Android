/*
 *  Copyright 2010 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#ifndef WEBRTC_BASE_MACWINDOWPICKER_H_
#define WEBRTC_BASE_MACWINDOWPICKER_H_

#include "webrtc/base/windowpicker.h"

namespace rtc {

class MacWindowPicker : public WindowPicker {
 public:
  MacWindowPicker();
  ~MacWindowPicker() override;
  bool Init() override;
  bool IsVisible(const WindowId& id) override;
  bool MoveToFront(const WindowId& id) override;
  bool GetWindowList(WindowDescriptionList* descriptions) override;
  bool GetDesktopList(DesktopDescriptionList* descriptions) override;
  bool GetDesktopDimensions(const DesktopId& id,
                            int* width,
                            int* height) override;

 private:
  void* lib_handle_;
  void* get_window_list_;
  void* get_window_list_desc_;
};

}  // namespace rtc

#endif  // WEBRTC_BASE_MACWINDOWPICKER_H_
