/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_BASE_VERSIONPARSING_H_
#define WEBRTC_BASE_VERSIONPARSING_H_

#include <string>

namespace rtc {

// Parses a version string into an array. "num_expected_segments" must be the
// number of numerical segments that the version is expected to have (e.g.,
// "1.1.2.0" has 4). "version" must be an array of that length to hold the
// parsed numbers.
// Returns "true" iff successful.
bool ParseVersionString(const std::string& version_str,
                        int num_expected_segments,
                        int version[]);

// Computes the lexicographical order of two versions. The return value
// indicates the order in the standard way (e.g., see strcmp()).
int CompareVersions(const int version1[],
                    const int version2[],
                    int num_segments);

}  // namespace rtc

#endif  // WEBRTC_BASE_VERSIONPARSING_H_
