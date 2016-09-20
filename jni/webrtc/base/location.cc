/*
 *  Copyright 2016 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/base/location.h"

#include "webrtc/base/stringutils.h"

namespace rtc {

Location::Location(const char* function_name, const char* file_and_line)
    : function_name_(function_name), file_and_line_(file_and_line) {}

Location::Location() : function_name_("Unknown"), file_and_line_("Unknown") {}

Location::Location(const Location& other)
    : function_name_(other.function_name_),
      file_and_line_(other.file_and_line_) {}

Location& Location::operator=(const Location& other) {
  function_name_ = other.function_name_;
  file_and_line_ = other.file_and_line_;
  return *this;
}

std::string Location::ToString() const {
  char buf[256];
  sprintfn(buf, sizeof(buf), "%s@%s", function_name_, file_and_line_);
  return buf;
}

}  // namespace rtc
