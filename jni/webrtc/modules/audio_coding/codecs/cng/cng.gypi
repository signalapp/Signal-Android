# Copyright (c) 2011 The WebRTC project authors. All Rights Reserved.
#
# Use of this source code is governed by a BSD-style license
# that can be found in the LICENSE file in the root of the source
# tree. An additional intellectual property rights grant can be found
# in the file PATENTS.  All contributing project authors may
# be found in the AUTHORS file in the root of the source tree.

{
  'targets': [
    {
      'target_name': 'cng',
      'type': 'static_library',
      'dependencies': [
        '<(webrtc_root)/common_audio/common_audio.gyp:common_audio',
        'audio_encoder_interface',
      ],
      'sources': [
        'audio_encoder_cng.cc',
        'audio_encoder_cng.h',
        'webrtc_cng.cc',
        'webrtc_cng.h',
      ],
    },
  ], # targets
}
