/*
 *  Copyright 2010 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_BASE_LINUXWINDOWPICKER_H_
#define WEBRTC_BASE_LINUXWINDOWPICKER_H_

#include <memory>

#include "webrtc/base/basictypes.h"
#include "webrtc/base/windowpicker.h"

// Avoid include <X11/Xlib.h>.
struct _XDisplay;
typedef unsigned long Window;

namespace rtc {

class XWindowEnumerator;

class X11WindowPicker : public WindowPicker {
 public:
  X11WindowPicker();
  ~X11WindowPicker() override;

  static bool IsDesktopElement(_XDisplay* display, Window window);

  bool Init() override;
  bool IsVisible(const WindowId& id) override;
  bool MoveToFront(const WindowId& id) override;
  bool GetWindowList(WindowDescriptionList* descriptions) override;
  bool GetDesktopList(DesktopDescriptionList* descriptions) override;
  bool GetDesktopDimensions(const DesktopId& id,
                            int* width,
                            int* height) override;
  uint8_t* GetWindowIcon(const WindowId& id, int* width, int* height);
  uint8_t* GetWindowThumbnail(const WindowId& id, int width, int height);
  int GetNumDesktops();
  uint8_t* GetDesktopThumbnail(const DesktopId& id, int width, int height);

 private:
  std::unique_ptr<XWindowEnumerator> enumerator_;
};

}  // namespace rtc

#endif  // WEBRTC_BASE_LINUXWINDOWPICKER_H_
