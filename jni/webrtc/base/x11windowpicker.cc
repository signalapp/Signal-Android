/*
 *  Copyright 2010 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/base/x11windowpicker.h"

#include <math.h>
#include <string.h>

#include <algorithm>
#include <string>

#include <X11/Xatom.h>
#include <X11/extensions/Xcomposite.h>
#include <X11/extensions/Xrender.h>
#include <X11/Xutil.h>

#include "webrtc/base/constructormagic.h"
#include "webrtc/base/logging.h"

namespace rtc {

// Convenience wrapper for XGetWindowProperty results.
template <class PropertyType>
class XWindowProperty {
 public:
  XWindowProperty(Display* display, Window window, Atom property)
      : data_(NULL) {
    const int kBitsPerByte = 8;
    Atom actual_type;
    int actual_format;
    unsigned long bytes_after;  // NOLINT: type required by XGetWindowProperty
    int status = XGetWindowProperty(display, window, property, 0L, ~0L, False,
                                    AnyPropertyType, &actual_type,
                                    &actual_format, &size_,
                                    &bytes_after, &data_);
    succeeded_ = (status == Success);
    if (!succeeded_) {
      data_ = NULL;  // Ensure nothing is freed.
    } else if (sizeof(PropertyType) * kBitsPerByte != actual_format) {
      LOG(LS_WARNING) << "Returned type size differs from "
          "requested type size.";
      succeeded_ = false;
      // We still need to call XFree in this case, so leave data_ alone.
    }
    if (!succeeded_) {
      size_ = 0;
    }
  }

  ~XWindowProperty() {
    if (data_) {
      XFree(data_);
    }
  }

  bool succeeded() const { return succeeded_; }
  size_t size() const { return size_; }
  const PropertyType* data() const {
    return reinterpret_cast<PropertyType*>(data_);
  }
  PropertyType* data() {
    return reinterpret_cast<PropertyType*>(data_);
  }

 private:
  bool succeeded_;
  unsigned long size_;  // NOLINT: type required by XGetWindowProperty
  unsigned char* data_;

  RTC_DISALLOW_COPY_AND_ASSIGN(XWindowProperty);
};

// Stupid X11.  It seems none of the synchronous returns codes from X11 calls
// are meaningful unless an asynchronous error handler is configured.  This
// RAII class registers and unregisters an X11 error handler.
class XErrorSuppressor {
 public:
  explicit XErrorSuppressor(Display* display)
      : display_(display), original_error_handler_(NULL) {
    SuppressX11Errors();
  }
  ~XErrorSuppressor() {
    UnsuppressX11Errors();
  }

 private:
  static int ErrorHandler(Display* display, XErrorEvent* e) {
    char buf[256];
    XGetErrorText(display, e->error_code, buf, sizeof buf);
    LOG(LS_WARNING) << "Received X11 error \"" << buf << "\" for request code "
                    << static_cast<unsigned int>(e->request_code);
    return 0;
  }

  void SuppressX11Errors() {
    XFlush(display_);
    XSync(display_, False);
    original_error_handler_ = XSetErrorHandler(&ErrorHandler);
  }

  void UnsuppressX11Errors() {
    XFlush(display_);
    XSync(display_, False);
    XErrorHandler handler = XSetErrorHandler(original_error_handler_);
    if (handler != &ErrorHandler) {
      LOG(LS_WARNING) << "Unbalanced XSetErrorHandler() calls detected. "
                      << "Final error handler may not be what you expect!";
    }
    original_error_handler_ = NULL;
  }

  Display* display_;
  XErrorHandler original_error_handler_;

  RTC_DISALLOW_COPY_AND_ASSIGN(XErrorSuppressor);
};

// Hiding all X11 specifics inside its own class. This to avoid
// conflicts between talk and X11 header declarations.
class XWindowEnumerator {
 public:
  XWindowEnumerator()
      : display_(NULL),
        has_composite_extension_(false),
        has_render_extension_(false) {
  }

  ~XWindowEnumerator() {
    if (display_ != NULL) {
      XCloseDisplay(display_);
    }
  }

  bool Init() {
    if (display_ != NULL) {
      // Already initialized.
      return true;
    }
    display_ = XOpenDisplay(NULL);
    if (display_ == NULL) {
      LOG(LS_ERROR) << "Failed to open display.";
      return false;
    }

    XErrorSuppressor error_suppressor(display_);

    wm_state_ = XInternAtom(display_, "WM_STATE", True);
    net_wm_icon_ = XInternAtom(display_, "_NET_WM_ICON", False);

    int event_base, error_base, major_version, minor_version;
    if (XCompositeQueryExtension(display_, &event_base, &error_base) &&
        XCompositeQueryVersion(display_, &major_version, &minor_version) &&
        // XCompositeNameWindowPixmap() requires version 0.2
        (major_version > 0 || minor_version >= 2)) {
      has_composite_extension_ = true;
    } else {
      LOG(LS_INFO) << "Xcomposite extension not available or too old.";
    }

    if (XRenderQueryExtension(display_, &event_base, &error_base) &&
        XRenderQueryVersion(display_, &major_version, &minor_version) &&
        // XRenderSetPictureTransform() requires version 0.6
        (major_version > 0 || minor_version >= 6)) {
      has_render_extension_ = true;
    } else {
      LOG(LS_INFO) << "Xrender extension not available or too old.";
    }
    return true;
  }

  bool EnumerateWindows(WindowDescriptionList* descriptions) {
    if (!Init()) {
      return false;
    }
    XErrorSuppressor error_suppressor(display_);
    int num_screens = XScreenCount(display_);
    bool result = false;
    for (int i = 0; i < num_screens; ++i) {
      if (EnumerateScreenWindows(descriptions, i)) {
        // We know we succeded on at least one screen.
        result = true;
      }
    }
    return result;
  }

  bool EnumerateDesktops(DesktopDescriptionList* descriptions) {
    if (!Init()) {
      return false;
    }
    XErrorSuppressor error_suppressor(display_);
    Window default_root_window = XDefaultRootWindow(display_);
    int num_screens = XScreenCount(display_);
    for (int i = 0; i < num_screens; ++i) {
      Window root_window = XRootWindow(display_, i);
      DesktopId id(DesktopId(root_window, i));
      // TODO: Figure out an appropriate desktop title.
      DesktopDescription desc(id, "");
      desc.set_primary(root_window == default_root_window);
      descriptions->push_back(desc);
    }
    return num_screens > 0;
  }

  bool IsVisible(const WindowId& id) {
    if (!Init()) {
      return false;
    }
    XErrorSuppressor error_suppressor(display_);
    XWindowAttributes attr;
    if (!XGetWindowAttributes(display_, id.id(), &attr)) {
      LOG(LS_ERROR) << "XGetWindowAttributes() failed";
      return false;
    }
    return attr.map_state == IsViewable;
  }

  bool MoveToFront(const WindowId& id) {
    if (!Init()) {
      return false;
    }
    XErrorSuppressor error_suppressor(display_);
    unsigned int num_children;
    Window* children;
    Window parent;
    Window root;

    // Find root window to pass event to.
    int status = XQueryTree(display_, id.id(), &root, &parent, &children,
                            &num_children);
    if (status == 0) {
      LOG(LS_WARNING) << "Failed to query for child windows.";
      return false;
    }
    if (children != NULL) {
      XFree(children);
    }

    // Move the window to front.
    XRaiseWindow(display_, id.id());

    // Some window managers (e.g., metacity in GNOME) consider it illegal to
    // raise a window without also giving it input focus with
    // _NET_ACTIVE_WINDOW, so XRaiseWindow() on its own isn't enough.
    Atom atom = XInternAtom(display_, "_NET_ACTIVE_WINDOW", True);
    if (atom != None) {
      XEvent xev;
      long event_mask;

      xev.xclient.type = ClientMessage;
      xev.xclient.serial = 0;
      xev.xclient.send_event = True;
      xev.xclient.window = id.id();
      xev.xclient.message_type = atom;

      // The format member is set to 8, 16, or 32 and specifies whether the
      // data should be viewed as a list of bytes, shorts, or longs.
      xev.xclient.format = 32;

      xev.xclient.data.l[0] = 0;
      xev.xclient.data.l[1] = 0;
      xev.xclient.data.l[2] = 0;
      xev.xclient.data.l[3] = 0;
      xev.xclient.data.l[4] = 0;

      event_mask = SubstructureRedirectMask | SubstructureNotifyMask;

      XSendEvent(display_, root, False, event_mask, &xev);
    }
    XFlush(display_);
    return true;
  }

  uint8_t* GetWindowIcon(const WindowId& id, int* width, int* height) {
    if (!Init()) {
      return NULL;
    }
    XErrorSuppressor error_suppressor(display_);
    Atom ret_type;
    int format;
    unsigned long length, bytes_after, size;
    unsigned char* data = NULL;

    // Find out the size of the icon data.
    if (XGetWindowProperty(
            display_, id.id(), net_wm_icon_, 0, 0, False, XA_CARDINAL,
            &ret_type, &format, &length, &size, &data) == Success &&
        data) {
      XFree(data);
    } else {
      LOG(LS_ERROR) << "Failed to get size of the icon.";
      return NULL;
    }
    // Get the icon data, the format is one uint32_t each for width and height,
    // followed by the actual pixel data.
    if (size >= 2 &&
        XGetWindowProperty(
            display_, id.id(), net_wm_icon_, 0, size, False, XA_CARDINAL,
            &ret_type, &format, &length, &bytes_after, &data) == Success &&
        data) {
      uint32_t* data_ptr = reinterpret_cast<uint32_t*>(data);
      int w, h;
      w = data_ptr[0];
      h = data_ptr[1];
      if (size < static_cast<unsigned long>(w * h + 2)) {
        XFree(data);
        LOG(LS_ERROR) << "Not a vaild icon.";
        return NULL;
      }
      uint8_t* rgba = ArgbToRgba(&data_ptr[2], 0, 0, w, h, w, h, true);
      XFree(data);
      *width = w;
      *height = h;
      return rgba;
    } else {
      LOG(LS_ERROR) << "Failed to get window icon data.";
      return NULL;
    }
  }

  uint8_t* GetWindowThumbnail(const WindowId& id, int width, int height) {
    if (!Init()) {
      return NULL;
    }

    if (!has_composite_extension_) {
      // Without the Xcomposite extension we would only get a good thumbnail if
      // the whole window is visible on screen and not covered by any
      // other window. This is not something we want so instead, just
      // bail out.
      LOG(LS_INFO) << "No Xcomposite extension detected.";
      return NULL;
    }
    XErrorSuppressor error_suppressor(display_);

    Window root;
    int x;
    int y;
    unsigned int src_width;
    unsigned int src_height;
    unsigned int border_width;
    unsigned int depth;

    // In addition to needing X11 server-side support for Xcomposite, it
    // actually needs to be turned on for this window in order to get a good
    // thumbnail. If the user has modern hardware/drivers but isn't using a
    // compositing window manager, that won't be the case. Here we
    // automatically turn it on for shareable windows so that we can get
    // thumbnails. We used to avoid it because the transition is visually ugly,
    // but recent window managers don't always redirect windows which led to
    // no thumbnails at all, which is a worse experience.

    // Redirect drawing to an offscreen buffer (ie, turn on compositing).
    // X11 remembers what has requested this and will turn it off for us when
    // we exit.
    XCompositeRedirectWindow(display_, id.id(), CompositeRedirectAutomatic);
    Pixmap src_pixmap = XCompositeNameWindowPixmap(display_, id.id());
    if (!src_pixmap) {
      // Even if the backing pixmap doesn't exist, this still should have
      // succeeded and returned a valid handle (it just wouldn't be a handle to
      // anything). So this is a real error path.
      LOG(LS_ERROR) << "XCompositeNameWindowPixmap() failed";
      return NULL;
    }
    if (!XGetGeometry(display_, src_pixmap, &root, &x, &y,
                      &src_width, &src_height, &border_width,
                      &depth)) {
      // If the window does not actually have a backing pixmap, this is the path
      // that will "fail", so it's a warning rather than an error.
      LOG(LS_WARNING) << "XGetGeometry() failed (probably composite is not in "
                      << "use)";
      XFreePixmap(display_, src_pixmap);
      return NULL;
    }

    // If we get to here, then composite is in use for this window and it has a
    // valid backing pixmap.

    XWindowAttributes attr;
    if (!XGetWindowAttributes(display_, id.id(), &attr)) {
      LOG(LS_ERROR) << "XGetWindowAttributes() failed";
      XFreePixmap(display_, src_pixmap);
      return NULL;
    }

    uint8_t* data = GetDrawableThumbnail(src_pixmap, attr.visual, src_width,
                                         src_height, width, height);
    XFreePixmap(display_, src_pixmap);
    return data;
  }

  int GetNumDesktops() {
    if (!Init()) {
      return -1;
    }

    return XScreenCount(display_);
  }

  uint8_t* GetDesktopThumbnail(const DesktopId& id, int width, int height) {
    if (!Init()) {
      return NULL;
    }
    XErrorSuppressor error_suppressor(display_);

    Window root_window = id.id();
    XWindowAttributes attr;
    if (!XGetWindowAttributes(display_, root_window, &attr)) {
      LOG(LS_ERROR) << "XGetWindowAttributes() failed";
      return NULL;
    }

    return GetDrawableThumbnail(root_window,
                                attr.visual,
                                attr.width,
                                attr.height,
                                width,
                                height);
  }

  bool GetDesktopDimensions(const DesktopId& id, int* width, int* height) {
    if (!Init()) {
      return false;
    }
    XErrorSuppressor error_suppressor(display_);
    XWindowAttributes attr;
    if (!XGetWindowAttributes(display_, id.id(), &attr)) {
      LOG(LS_ERROR) << "XGetWindowAttributes() failed";
      return false;
    }
    *width = attr.width;
    *height = attr.height;
    return true;
  }

 private:
  uint8_t* GetDrawableThumbnail(Drawable src_drawable,
                                Visual* visual,
                                int src_width,
                                int src_height,
                                int dst_width,
                                int dst_height) {
    if (!has_render_extension_) {
      // Without the Xrender extension we would have to read the full window and
      // scale it down in our process. Xrender is over a decade old so we aren't
      // going to expend effort to support that situation. We still need to
      // check though because probably some virtual VNC displays are in this
      // category.
      LOG(LS_INFO) << "No Xrender extension detected.";
      return NULL;
    }

    XRenderPictFormat* format = XRenderFindVisualFormat(display_,
                                                        visual);
    if (!format) {
      LOG(LS_ERROR) << "XRenderFindVisualFormat() failed";
      return NULL;
    }

    // Create a picture to reference the window pixmap.
    XRenderPictureAttributes pa;
    pa.subwindow_mode = IncludeInferiors;  // Don't clip child widgets
    Picture src = XRenderCreatePicture(display_,
                                       src_drawable,
                                       format,
                                       CPSubwindowMode,
                                       &pa);
    if (!src) {
      LOG(LS_ERROR) << "XRenderCreatePicture() failed";
      return NULL;
    }

    // Create a picture to reference the destination pixmap.
    Pixmap dst_pixmap = XCreatePixmap(display_,
                                      src_drawable,
                                      dst_width,
                                      dst_height,
                                      format->depth);
    if (!dst_pixmap) {
      LOG(LS_ERROR) << "XCreatePixmap() failed";
      XRenderFreePicture(display_, src);
      return NULL;
    }

    Picture dst = XRenderCreatePicture(display_, dst_pixmap, format, 0, NULL);
    if (!dst) {
      LOG(LS_ERROR) << "XRenderCreatePicture() failed";
      XFreePixmap(display_, dst_pixmap);
      XRenderFreePicture(display_, src);
      return NULL;
    }

    // Clear the background.
    XRenderColor transparent = {0};
    XRenderFillRectangle(display_,
                         PictOpSrc,
                         dst,
                         &transparent,
                         0,
                         0,
                         dst_width,
                         dst_height);

    // Calculate how much we need to scale the image.
    double scale_x = static_cast<double>(dst_width) /
        static_cast<double>(src_width);
    double scale_y = static_cast<double>(dst_height) /
        static_cast<double>(src_height);
    double scale = std::min(scale_y, scale_x);

    int scaled_width = round(src_width * scale);
    int scaled_height = round(src_height * scale);

    // Render the thumbnail centered on both axis.
    int centered_x = (dst_width - scaled_width) / 2;
    int centered_y = (dst_height - scaled_height) / 2;

    // Scaling matrix
    XTransform xform = { {
        { XDoubleToFixed(1), XDoubleToFixed(0), XDoubleToFixed(0) },
        { XDoubleToFixed(0), XDoubleToFixed(1), XDoubleToFixed(0) },
        { XDoubleToFixed(0), XDoubleToFixed(0), XDoubleToFixed(scale) }
        } };
    XRenderSetPictureTransform(display_, src, &xform);

    // Apply filter to smooth out the image.
    XRenderSetPictureFilter(display_, src, FilterBest, NULL, 0);

    // Render the image to the destination picture.
    XRenderComposite(display_,
                     PictOpSrc,
                     src,
                     None,
                     dst,
                     0,
                     0,
                     0,
                     0,
                     centered_x,
                     centered_y,
                     scaled_width,
                     scaled_height);

    // Get the pixel data from the X server. TODO: XGetImage
    // might be slow here, compare with ShmGetImage.
    XImage* image = XGetImage(display_,
                              dst_pixmap,
                              0,
                              0,
                              dst_width,
                              dst_height,
                              AllPlanes, ZPixmap);
    uint8_t* data = ArgbToRgba(reinterpret_cast<uint32_t*>(image->data),
                               centered_x, centered_y, scaled_width,
                               scaled_height, dst_width, dst_height, false);
    XDestroyImage(image);
    XRenderFreePicture(display_, dst);
    XFreePixmap(display_, dst_pixmap);
    XRenderFreePicture(display_, src);
    return data;
  }

  uint8_t* ArgbToRgba(uint32_t* argb_data,
                      int x,
                      int y,
                      int w,
                      int h,
                      int stride_x,
                      int stride_y,
                      bool has_alpha) {
    uint8_t* p;
    int len = stride_x * stride_y * 4;
    uint8_t* data = new uint8_t[len];
    memset(data, 0, len);
    p = data + 4 * (y * stride_x + x);
    for (int i = 0; i < h; ++i) {
      for (int j = 0; j < w; ++j) {
        uint32_t argb;
        uint32_t rgba;
        argb = argb_data[stride_x * (y + i) + x + j];
        rgba = (argb << 8) | (argb >> 24);
        *p = rgba >> 24;
        ++p;
        *p = (rgba >> 16) & 0xff;
        ++p;
        *p = (rgba >> 8) & 0xff;
        ++p;
        *p = has_alpha ? rgba & 0xFF : 0xFF;
        ++p;
      }
      p += (stride_x - w) * 4;
    }
    return data;
  }

  bool EnumerateScreenWindows(WindowDescriptionList* descriptions, int screen) {
    Window parent;
    Window *children;
    int status;
    unsigned int num_children;
    Window root_window = XRootWindow(display_, screen);
    status = XQueryTree(display_, root_window, &root_window, &parent, &children,
                        &num_children);
    if (status == 0) {
      LOG(LS_ERROR) << "Failed to query for child windows.";
      return false;
    }
    for (unsigned int i = 0; i < num_children; ++i) {
      // Iterate in reverse order to display windows from front to back.
#ifdef CHROMEOS
      // TODO(jhorwich): Short-term fix for crbug.com/120229: Don't need to
      // filter, just return all windows and let the picker scan through them.
      Window app_window = children[num_children - 1 - i];
#else
      Window app_window = GetApplicationWindow(children[num_children - 1 - i]);
#endif
      if (app_window &&
          !X11WindowPicker::IsDesktopElement(display_, app_window)) {
        std::string title;
        if (GetWindowTitle(app_window, &title)) {
          WindowId id(app_window);
          WindowDescription desc(id, title);
          descriptions->push_back(desc);
        }
      }
    }
    if (children != NULL) {
      XFree(children);
    }
    return true;
  }

  bool GetWindowTitle(Window window, std::string* title) {
    int status;
    bool result = false;
    XTextProperty window_name;
    window_name.value = NULL;
    if (window) {
      status = XGetWMName(display_, window, &window_name);
      if (status && window_name.value && window_name.nitems) {
        int cnt;
        char **list = NULL;
        status = Xutf8TextPropertyToTextList(display_, &window_name, &list,
                                             &cnt);
        if (status >= Success && cnt && *list) {
          if (cnt > 1) {
            LOG(LS_INFO) << "Window has " << cnt
                         << " text properties, only using the first one.";
          }
          *title = *list;
          result = true;
        }
        if (list != NULL) {
          XFreeStringList(list);
        }
      }
      if (window_name.value != NULL) {
        XFree(window_name.value);
      }
    }
    return result;
  }

  Window GetApplicationWindow(Window window) {
    Window root, parent;
    Window app_window = 0;
    Window *children;
    unsigned int num_children;
    Atom type = None;
    int format;
    unsigned long nitems, after;
    unsigned char *data;

    int ret = XGetWindowProperty(display_, window,
                                 wm_state_, 0L, 2,
                                 False, wm_state_, &type, &format,
                                 &nitems, &after, &data);
    if (ret != Success) {
      LOG(LS_ERROR) << "XGetWindowProperty failed with return code " << ret
                    << " for window " << window << ".";
      return 0;
    }
    if (type != None) {
      int64_t state = static_cast<int64_t>(*data);
      XFree(data);
      return state == NormalState ? window : 0;
    }
    XFree(data);
    if (!XQueryTree(display_, window, &root, &parent, &children,
                    &num_children)) {
      LOG(LS_ERROR) << "Failed to query for child windows although window"
                    << "does not have a valid WM_STATE.";
      return 0;
    }
    for (unsigned int i = 0; i < num_children; ++i) {
      app_window = GetApplicationWindow(children[i]);
      if (app_window) {
        break;
      }
    }
    if (children != NULL) {
      XFree(children);
    }
    return app_window;
  }

  Atom wm_state_;
  Atom net_wm_icon_;
  Display* display_;
  bool has_composite_extension_;
  bool has_render_extension_;
};

X11WindowPicker::X11WindowPicker() : enumerator_(new XWindowEnumerator()) {
}

X11WindowPicker::~X11WindowPicker() {
}

bool X11WindowPicker::IsDesktopElement(_XDisplay* display, Window window) {
  if (window == 0) {
    LOG(LS_WARNING) << "Zero is never a valid window.";
    return false;
  }

  // First look for _NET_WM_WINDOW_TYPE. The standard
  // (http://standards.freedesktop.org/wm-spec/latest/ar01s05.html#id2760306)
  // says this hint *should* be present on all windows, and we use the existence
  // of _NET_WM_WINDOW_TYPE_NORMAL in the property to indicate a window is not
  // a desktop element (that is, only "normal" windows should be shareable).
  Atom window_type_atom = XInternAtom(display, "_NET_WM_WINDOW_TYPE", True);
  XWindowProperty<uint32_t> window_type(display, window, window_type_atom);
  if (window_type.succeeded() && window_type.size() > 0) {
    Atom normal_window_type_atom = XInternAtom(
        display, "_NET_WM_WINDOW_TYPE_NORMAL", True);
    uint32_t* end = window_type.data() + window_type.size();
    bool is_normal = (end != std::find(
        window_type.data(), end, normal_window_type_atom));
    return !is_normal;
  }

  // Fall back on using the hint.
  XClassHint class_hint;
  Status s = XGetClassHint(display, window, &class_hint);
  bool result = false;
  if (s == 0) {
    // No hints, assume this is a normal application window.
    return result;
  }
  static const std::string gnome_panel("gnome-panel");
  static const std::string desktop_window("desktop_window");

  if (gnome_panel.compare(class_hint.res_name) == 0 ||
      desktop_window.compare(class_hint.res_name) == 0) {
    result = true;
  }
  XFree(class_hint.res_name);
  XFree(class_hint.res_class);
  return result;
}

bool X11WindowPicker::Init() {
  return enumerator_->Init();
}

bool X11WindowPicker::GetWindowList(WindowDescriptionList* descriptions) {
  return enumerator_->EnumerateWindows(descriptions);
}

bool X11WindowPicker::GetDesktopList(DesktopDescriptionList* descriptions) {
  return enumerator_->EnumerateDesktops(descriptions);
}

bool X11WindowPicker::IsVisible(const WindowId& id) {
  return enumerator_->IsVisible(id);
}

bool X11WindowPicker::MoveToFront(const WindowId& id) {
  return enumerator_->MoveToFront(id);
}

uint8_t* X11WindowPicker::GetWindowIcon(const WindowId& id,
                                        int* width,
                                        int* height) {
  return enumerator_->GetWindowIcon(id, width, height);
}

uint8_t* X11WindowPicker::GetWindowThumbnail(const WindowId& id,
                                             int width,
                                             int height) {
  return enumerator_->GetWindowThumbnail(id, width, height);
}

int X11WindowPicker::GetNumDesktops() {
  return enumerator_->GetNumDesktops();
}

uint8_t* X11WindowPicker::GetDesktopThumbnail(const DesktopId& id,
                                              int width,
                                              int height) {
  return enumerator_->GetDesktopThumbnail(id, width, height);
}

bool X11WindowPicker::GetDesktopDimensions(const DesktopId& id, int* width,
                                             int* height) {
  return enumerator_->GetDesktopDimensions(id, width, height);
}

}  // namespace rtc
