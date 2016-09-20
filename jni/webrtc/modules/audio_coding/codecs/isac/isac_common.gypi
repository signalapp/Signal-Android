# Copyright (c) 2015 The WebRTC project authors. All Rights Reserved.
#
# Use of this source code is governed by a BSD-style license
# that can be found in the LICENSE file in the root of the source
# tree. An additional intellectual property rights grant can be found
# in the file PATENTS.  All contributing project authors may
# be found in the AUTHORS file in the root of the source tree.

{
  'targets': [
    {
      'target_name': 'isac_common',
      'type': 'static_library',
      'sources': [
        'audio_encoder_isac_t.h',
        'audio_encoder_isac_t_impl.h',
        'locked_bandwidth_info.cc',
        'locked_bandwidth_info.h',
      ],
    },
  ],
}
