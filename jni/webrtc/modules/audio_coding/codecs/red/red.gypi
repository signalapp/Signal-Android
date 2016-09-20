# Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
#
# Use of this source code is governed by a BSD-style license
# that can be found in the LICENSE file in the root of the source
# tree. An additional intellectual property rights grant can be found
# in the file PATENTS.  All contributing project authors may
# be found in the AUTHORS file in the root of the source tree.

{
  'targets': [
    {
      'target_name': 'red',
      'type': 'static_library',
      'dependencies': [
        'audio_encoder_interface',
      ],
      'include_dirs': [
        'include',
        '<(webrtc_root)',
      ],
      'direct_dependent_settings': {
        'include_dirs': [
          'include',
          '<(webrtc_root)',
        ],
      },
      'sources': [
        'audio_encoder_copy_red.h',
        'audio_encoder_copy_red.cc',
      ],
    },
  ], # targets
}
