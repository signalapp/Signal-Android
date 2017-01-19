# Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
#
# Use of this source code is governed by a BSD-style license
# that can be found in the LICENSE file in the root of the source
# tree. An additional intellectual property rights grant can be found
# in the file PATENTS.  All contributing project authors may
# be found in the AUTHORS file in the root of the source tree.

{
  'targets': [
    {
      'target_name': 'webrtc_opus',
      'type': 'static_library',
      'conditions': [
        ['build_with_mozilla==1', {
          # Mozilla provides its own build of the opus library.
          'include_dirs': [
            '$(DIST)/include/opus',
           ]
        }, {
          'dependencies': [
            '<(DEPTH)/third_party/opus/opus.gyp:opus'
          ],
        }],
      ],
      'include_dirs': [
        '<(webrtc_root)',
      ],
      'sources': [
        'interface/opus_interface.h',
        'opus_inst.h',
        'opus_interface.c',
      ],
    },
  ],
  'conditions': [
    ['include_tests==1', {
      'targets': [
        {
          'target_name': 'webrtc_opus_fec_test',
          'type': 'executable',
          'dependencies': [
            'webrtc_opus',
            '<(webrtc_root)/common_audio/common_audio.gyp:common_audio',
            '<(webrtc_root)/test/test.gyp:test_support_main',
            '<(DEPTH)/testing/gtest.gyp:gtest',
          ],
          'include_dirs': [
            '<(webrtc_root)',
          ],
          'sources': [
            'opus_fec_test.cc',
          ],
        },
      ],
    }],
  ],
}
