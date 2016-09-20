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
      'target_name': 'audio_decoder_interface',
      'type': 'static_library',
      'sources': [
        'audio_decoder.cc',
        'audio_decoder.h',
      ],
      'dependencies': [
        '<(webrtc_root)/base/base.gyp:rtc_base_approved',
        '<(webrtc_root)/common.gyp:webrtc_common',
      ],
    },

    {
      'target_name': 'audio_encoder_interface',
      'type': 'static_library',
      'sources': [
        'audio_encoder.cc',
        'audio_encoder.h',
      ],
      'dependencies': [
        '<(webrtc_root)/base/base.gyp:rtc_base_approved',
        '<(webrtc_root)/common.gyp:webrtc_common',
      ],
    },
  ],
}
