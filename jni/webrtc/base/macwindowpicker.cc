/*
 *  Copyright 2010 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "webrtc/base/macwindowpicker.h"

#include <ApplicationServices/ApplicationServices.h>
#include <CoreFoundation/CoreFoundation.h>
#include <dlfcn.h>

#include "webrtc/base/logging.h"
#include "webrtc/base/macutils.h"

namespace rtc {

static const char* kCoreGraphicsName =
    "/System/Library/Frameworks/ApplicationServices.framework/Frameworks/"
    "CoreGraphics.framework/CoreGraphics";

static const char* kWindowListCopyWindowInfo = "CGWindowListCopyWindowInfo";
static const char* kWindowListCreateDescriptionFromArray =
    "CGWindowListCreateDescriptionFromArray";

// Function pointer for holding the CGWindowListCopyWindowInfo function.
typedef CFArrayRef(*CGWindowListCopyWindowInfoProc)(CGWindowListOption,
                                                    CGWindowID);

// Function pointer for holding the CGWindowListCreateDescriptionFromArray
// function.
typedef CFArrayRef(*CGWindowListCreateDescriptionFromArrayProc)(CFArrayRef);

MacWindowPicker::MacWindowPicker() : lib_handle_(NULL), get_window_list_(NULL),
                                     get_window_list_desc_(NULL) {
}

MacWindowPicker::~MacWindowPicker() {
  if (lib_handle_ != NULL) {
    dlclose(lib_handle_);
  }
}

bool MacWindowPicker::Init() {
  // TODO: If this class grows to use more dynamically functions
  // from the CoreGraphics framework, consider using
  // webrtc/base/latebindingsymboltable.h.
  lib_handle_ = dlopen(kCoreGraphicsName, RTLD_NOW);
  if (lib_handle_ == NULL) {
    LOG(LS_ERROR) << "Could not load CoreGraphics";
    return false;
  }

  get_window_list_ = dlsym(lib_handle_, kWindowListCopyWindowInfo);
  get_window_list_desc_ =
      dlsym(lib_handle_, kWindowListCreateDescriptionFromArray);
  if (get_window_list_ == NULL || get_window_list_desc_ == NULL) {
    // The CGWindowListCopyWindowInfo and the
    // CGWindowListCreateDescriptionFromArray functions was introduced
    // in Leopard(10.5) so this is a normal failure on Tiger.
    LOG(LS_INFO) << "Failed to load Core Graphics symbols";
    dlclose(lib_handle_);
    lib_handle_ = NULL;
    return false;
  }

  return true;
}

bool MacWindowPicker::IsVisible(const WindowId& id) {
  // Init if we're not already inited.
  if (get_window_list_desc_ == NULL && !Init()) {
    return false;
  }
  CGWindowID ids[1];
  ids[0] = id.id();
  CFArrayRef window_id_array =
      CFArrayCreate(NULL, reinterpret_cast<const void **>(&ids), 1, NULL);

  CFArrayRef window_array =
      reinterpret_cast<CGWindowListCreateDescriptionFromArrayProc>(
          get_window_list_desc_)(window_id_array);
  if (window_array == NULL || 0 == CFArrayGetCount(window_array)) {
    // Could not find the window. It might have been closed.
    LOG(LS_INFO) << "Window not found";
    CFRelease(window_id_array);
    return false;
  }

  CFDictionaryRef window = reinterpret_cast<CFDictionaryRef>(
      CFArrayGetValueAtIndex(window_array, 0));
  CFBooleanRef is_visible = reinterpret_cast<CFBooleanRef>(
      CFDictionaryGetValue(window, kCGWindowIsOnscreen));

  // Check that the window is visible. If not we might crash.
  bool visible = false;
  if (is_visible != NULL) {
    visible = CFBooleanGetValue(is_visible);
  }
  CFRelease(window_id_array);
  CFRelease(window_array);
  return visible;
}

bool MacWindowPicker::MoveToFront(const WindowId& id) {
  // Init if we're not already initialized.
  if (get_window_list_desc_ == NULL && !Init()) {
    return false;
  }
  CGWindowID ids[1];
  ids[0] = id.id();
  CFArrayRef window_id_array =
      CFArrayCreate(NULL, reinterpret_cast<const void **>(&ids), 1, NULL);

  CFArrayRef window_array =
      reinterpret_cast<CGWindowListCreateDescriptionFromArrayProc>(
          get_window_list_desc_)(window_id_array);
  if (window_array == NULL || 0 == CFArrayGetCount(window_array)) {
    // Could not find the window. It might have been closed.
    LOG(LS_INFO) << "Window not found";
    CFRelease(window_id_array);
    return false;
  }

  CFDictionaryRef window = reinterpret_cast<CFDictionaryRef>(
      CFArrayGetValueAtIndex(window_array, 0));
  CFStringRef window_name_ref = reinterpret_cast<CFStringRef>(
      CFDictionaryGetValue(window, kCGWindowName));
  CFNumberRef application_pid = reinterpret_cast<CFNumberRef>(
      CFDictionaryGetValue(window, kCGWindowOwnerPID));

  int pid_val;
  CFNumberGetValue(application_pid, kCFNumberIntType, &pid_val);
  std::string window_name;
  ToUtf8(window_name_ref, &window_name);

  // Build an applescript that sets the selected window to front
  // within the application. Then set the application to front.
  bool result = true;
  std::stringstream ss;
  ss << "tell application \"System Events\"\n"
     << "set proc to the first item of (every process whose unix id is "
     << pid_val
     << ")\n"
     << "tell proc to perform action \"AXRaise\" of window \""
     << window_name
     << "\"\n"
     << "set the frontmost of proc to true\n"
     << "end tell";
  if (!RunAppleScript(ss.str())) {
    // This might happen to for example X applications where the X
    // server spawns of processes with their own PID but the X server
    // is still registered as owner to the application windows. As a
    // workaround, we put the X server process to front, meaning that
    // all X applications will show up. The drawback with this
    // workaround is that the application that we really wanted to set
    // to front might be behind another X application.
    ProcessSerialNumber psn;
    pid_t pid = pid_val;
    int res = GetProcessForPID(pid, &psn);
    if (res != 0) {
      LOG(LS_ERROR) << "Failed getting process for pid";
      result = false;
    }
    res = SetFrontProcess(&psn);
    if (res != 0) {
      LOG(LS_ERROR) << "Failed setting process to front";
      result = false;
    }
  }
  CFRelease(window_id_array);
  CFRelease(window_array);
  return result;
}

bool MacWindowPicker::GetDesktopList(DesktopDescriptionList* descriptions) {
  const uint32_t kMaxDisplays = 128;
  CGDirectDisplayID active_displays[kMaxDisplays];
  uint32_t display_count = 0;

  CGError err = CGGetActiveDisplayList(kMaxDisplays,
                                       active_displays,
                                       &display_count);
  if (err != kCGErrorSuccess) {
    LOG_E(LS_ERROR, OS, err) << "Failed to enumerate the active displays.";
    return false;
  }
  for (uint32_t i = 0; i < display_count; ++i) {
    DesktopId id(active_displays[i], static_cast<int>(i));
    // TODO: Figure out an appropriate desktop title.
    DesktopDescription desc(id, "");
    desc.set_primary(CGDisplayIsMain(id.id()));
    descriptions->push_back(desc);
  }
  return display_count > 0;
}

bool MacWindowPicker::GetDesktopDimensions(const DesktopId& id,
                                           int* width,
                                           int* height) {
  *width = CGDisplayPixelsWide(id.id());
  *height = CGDisplayPixelsHigh(id.id());
  return true;
}

bool MacWindowPicker::GetWindowList(WindowDescriptionList* descriptions) {
  // Init if we're not already inited.
  if (get_window_list_ == NULL && !Init()) {
    return false;
  }

  // Only get onscreen, non-desktop windows.
  CFArrayRef window_array =
      reinterpret_cast<CGWindowListCopyWindowInfoProc>(get_window_list_)(
          kCGWindowListOptionOnScreenOnly | kCGWindowListExcludeDesktopElements,
          kCGNullWindowID);
  if (window_array == NULL) {
    return false;
  }

  // Check windows to make sure they have an id, title, and use window layer 0.
  CFIndex i;
  CFIndex count = CFArrayGetCount(window_array);
  for (i = 0; i < count; ++i) {
    CFDictionaryRef window = reinterpret_cast<CFDictionaryRef>(
        CFArrayGetValueAtIndex(window_array, i));
    CFStringRef window_title = reinterpret_cast<CFStringRef>(
        CFDictionaryGetValue(window, kCGWindowName));
    CFNumberRef window_id = reinterpret_cast<CFNumberRef>(
        CFDictionaryGetValue(window, kCGWindowNumber));
    CFNumberRef window_layer = reinterpret_cast<CFNumberRef>(
        CFDictionaryGetValue(window, kCGWindowLayer));
    if (window_title != NULL && window_id != NULL && window_layer != NULL) {
      std::string title_str;
      int id_val, layer_val;
      ToUtf8(window_title, &title_str);
      CFNumberGetValue(window_id, kCFNumberIntType, &id_val);
      CFNumberGetValue(window_layer, kCFNumberIntType, &layer_val);

      // Discard windows without a title.
      if (layer_val == 0 && title_str.length() > 0) {
        WindowId id(static_cast<CGWindowID>(id_val));
        WindowDescription desc(id, title_str);
        descriptions->push_back(desc);
      }
    }
  }

  CFRelease(window_array);
  return true;
}

}  // namespace rtc
