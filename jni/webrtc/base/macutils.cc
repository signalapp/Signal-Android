/*
 *  Copyright 2007 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <memory>
#include <sstream>

#include "webrtc/base/common.h"
#include "webrtc/base/logging.h"
#include "webrtc/base/macutils.h"
#include "webrtc/base/stringutils.h"

namespace rtc {

///////////////////////////////////////////////////////////////////////////////

bool ToUtf8(const CFStringRef str16, std::string* str8) {
  if ((NULL == str16) || (NULL == str8)) {
    return false;
  }
  size_t maxlen = CFStringGetMaximumSizeForEncoding(CFStringGetLength(str16),
                                                    kCFStringEncodingUTF8) + 1;
  std::unique_ptr<char[]> buffer(new char[maxlen]);
  if (!buffer || !CFStringGetCString(str16, buffer.get(), maxlen,
                                     kCFStringEncodingUTF8)) {
    return false;
  }
  str8->assign(buffer.get());
  return true;
}

bool ToUtf16(const std::string& str8, CFStringRef* str16) {
  if (NULL == str16) {
    return false;
  }
  *str16 = CFStringCreateWithBytes(kCFAllocatorDefault,
                                   reinterpret_cast<const UInt8*>(str8.data()),
                                   str8.length(), kCFStringEncodingUTF8,
                                   false);
  return NULL != *str16;
}

#if defined(WEBRTC_MAC) && !defined(WEBRTC_IOS)
void DecodeFourChar(UInt32 fc, std::string* out) {
  std::stringstream ss;
  ss << '\'';
  bool printable = true;
  for (int i = 3; i >= 0; --i) {
    char ch = (fc >> (8 * i)) & 0xFF;
    if (isprint(static_cast<unsigned char>(ch))) {
      ss << ch;
    } else {
      printable = false;
      break;
    }
  }
  if (printable) {
    ss << '\'';
  } else {
    ss.str("");
    ss << "0x" << std::hex << fc;
  }
  out->append(ss.str());
}

static bool GetGestalt(OSType ostype, int* value) {
  ASSERT(NULL != value);
  SInt32 native_value;
  OSStatus result = Gestalt(ostype, &native_value);
  if (noErr == result) {
    *value = native_value;
    return true;
  }
  std::string str;
  DecodeFourChar(ostype, &str);
  LOG_E(LS_ERROR, OS, result) << "Gestalt(" << str << ")";
  return false;
}

bool GetOSVersion(int* major, int* minor, int* bugfix) {
  ASSERT(major && minor && bugfix);
  if (!GetGestalt(gestaltSystemVersion, major)) {
    return false;
  }
  if (*major < 0x1040) {
    *bugfix = *major & 0xF;
    *minor = (*major >> 4) & 0xF;
    *major = (*major >> 8);
    return true;
  }
  return GetGestalt(gestaltSystemVersionMajor, major) &&
         GetGestalt(gestaltSystemVersionMinor, minor) &&
         GetGestalt(gestaltSystemVersionBugFix, bugfix);
}

MacOSVersionName GetOSVersionName() {
  int major = 0, minor = 0, bugfix = 0;
  if (!GetOSVersion(&major, &minor, &bugfix)) {
    return kMacOSUnknown;
  }
  if (major > 10) {
    return kMacOSNewer;
  }
  if ((major < 10) || (minor < 3)) {
    return kMacOSOlder;
  }
  switch (minor) {
    case 3:
      return kMacOSPanther;
    case 4:
      return kMacOSTiger;
    case 5:
      return kMacOSLeopard;
    case 6:
      return kMacOSSnowLeopard;
    case 7:
      return kMacOSLion;
    case 8:
      return kMacOSMountainLion;
    case 9:
      return kMacOSMavericks;
  }
  return kMacOSNewer;
}

bool GetQuickTimeVersion(std::string* out) {
  int ver;
  if (!GetGestalt(gestaltQuickTimeVersion, &ver)) {
    return false;
  }

  std::stringstream ss;
  ss << std::hex << ver;
  *out = ss.str();
  return true;
}

bool RunAppleScript(const std::string& script) {
  // TODO(thaloun): Add a .mm file that contains something like this:
  // NSString source from script
  // NSAppleScript* appleScript = [[NSAppleScript alloc] initWithSource:&source]
  // if (appleScript != nil) {
  //   [appleScript executeAndReturnError:nil]
  //   [appleScript release]
#ifndef CARBON_DEPRECATED
  ComponentInstance component = NULL;
  AEDesc script_desc;
  AEDesc result_data;
  OSStatus err;
  OSAID script_id, result_id;

  AECreateDesc(typeNull, NULL, 0, &script_desc);
  AECreateDesc(typeNull, NULL, 0, &result_data);
  script_id = kOSANullScript;
  result_id = kOSANullScript;

  component = OpenDefaultComponent(kOSAComponentType, typeAppleScript);
  if (component == NULL) {
    LOG(LS_ERROR) << "Failed opening Apple Script component";
    return false;
  }
  err = AECreateDesc(typeUTF8Text, script.data(), script.size(), &script_desc);
  if (err != noErr) {
    CloseComponent(component);
    LOG(LS_ERROR) << "Failed creating Apple Script description";
    return false;
  }

  err = OSACompile(component, &script_desc, kOSAModeCanInteract, &script_id);
  if (err != noErr) {
    AEDisposeDesc(&script_desc);
    if (script_id != kOSANullScript) {
      OSADispose(component, script_id);
    }
    CloseComponent(component);
    LOG(LS_ERROR) << "Error compiling Apple Script";
    return false;
  }

  err = OSAExecute(component, script_id, kOSANullScript, kOSAModeCanInteract,
                   &result_id);

  if (err == errOSAScriptError) {
    LOG(LS_ERROR) << "Error when executing Apple Script: " << script;
    AECreateDesc(typeNull, NULL, 0, &result_data);
    OSAScriptError(component, kOSAErrorMessage, typeChar, &result_data);
    int len = AEGetDescDataSize(&result_data);
    char* data = (char*)malloc(len);
    if (data != NULL) {
      err = AEGetDescData(&result_data, data, len);
      LOG(LS_ERROR) << "Script error: " << std::string(data, len);
    }
    AEDisposeDesc(&script_desc);
    AEDisposeDesc(&result_data);
    return false;
  }
  AEDisposeDesc(&script_desc);
  if (script_id != kOSANullScript) {
    OSADispose(component, script_id);
  }
  if (result_id != kOSANullScript) {
    OSADispose(component, result_id);
  }
  CloseComponent(component);
  return true;
#else
  // TODO(thaloun): Support applescripts with the NSAppleScript API.
  return false;
#endif  // CARBON_DEPRECATED
}
#endif  // WEBRTC_MAC && !defined(WEBRTC_IOS)

///////////////////////////////////////////////////////////////////////////////

}  // namespace rtc
