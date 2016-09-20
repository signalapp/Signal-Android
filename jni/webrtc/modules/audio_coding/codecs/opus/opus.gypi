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
        ['build_opus==1', {
          'dependencies': [
            '<(opus_dir)/opus.gyp:opus'
          ],
          'export_dependent_settings': [
            '<(opus_dir)/opus.gyp:opus',
          ],
          'direct_dependent_settings': {
            'include_dirs': [  # need by Neteq audio classifier.
              '<(opus_dir)/src/src',
              '<(opus_dir)/src/celt',
            ],
          },
        }, {
          'conditions': [
            ['build_with_mozilla==1', {
              # Mozilla provides its own build of the opus library.
              'include_dirs': [
                '$(DIST)/include/opus',
              ]
            }],
          ],
        }],
      ],
      'dependencies': [
        'audio_encoder_interface',
      ],
      'sources': [
        'audio_decoder_opus.cc',
        'audio_decoder_opus.h',
        'audio_encoder_opus.cc',
        'audio_encoder_opus.h',
        'opus_inst.h',
        'opus_interface.c',
        'opus_interface.h',
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
          'sources': [
            'opus_fec_test.cc',
          ],
        },
      ],
    }],
  ],
}
