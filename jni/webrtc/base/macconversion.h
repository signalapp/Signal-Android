/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_BASE_MACCONVERSION_H_
#define WEBRTC_BASE_MACCONVERSION_H_

#if defined(WEBRTC_MAC) || defined(WEBRTC_IOS)

#include <CoreFoundation/CoreFoundation.h>

#include <string>

// given a CFStringRef, attempt to convert it to a C++ string.
// returns true if it succeeds, false otherwise.
// We can safely assume, given our context, that the string is
// going to be in ASCII, because it will either be an IP address,
// or a domain name, which is guaranteed to be ASCII-representable.
bool p_convertHostCFStringRefToCPPString(const CFStringRef cfstr,
                                         std::string& cppstr);

// Convert the CFNumber to an integer, putting the integer in the location
// given, and returhing true, if the conversion succeeds.
// If given a NULL or a non-CFNumber, returns false.
// This is pretty aggresive about trying to convert to int.
bool p_convertCFNumberToInt(CFNumberRef cfn, int* i);

// given a CFNumberRef, determine if it represents a true value.
bool p_isCFNumberTrue(CFNumberRef cfn);

#endif  // WEBRTC_MAC || WEBRTC_IOS

#endif  // WEBRTC_BASE_MACCONVERSION_H_
