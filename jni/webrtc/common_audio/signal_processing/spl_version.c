/*
 *  Copyright (c) 2011 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */


/*
 * This file contains the function WebRtcSpl_get_version().
 * The description header can be found in signal_processing_library.h
 *
 */

#include <string.h>
#include "webrtc/common_audio/signal_processing/include/signal_processing_library.h"

int16_t WebRtcSpl_get_version(char* version, int16_t length_in_bytes)
{
    strncpy(version, "1.2.0", length_in_bytes);
    return 0;
}
