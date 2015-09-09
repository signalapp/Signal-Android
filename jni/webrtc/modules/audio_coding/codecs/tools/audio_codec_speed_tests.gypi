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
    'target_name': 'audio_codec_speed_tests',
    'type': '<(gtest_target_type)',
    'dependencies': [
      'audio_processing',
      'iSACFix',
      'webrtc_opus',
      '<(DEPTH)/testing/gtest.gyp:gtest',
      '<(webrtc_root)/system_wrappers/source/system_wrappers.gyp:system_wrappers',
      '<(webrtc_root)/test/test.gyp:test_support_main',
    ],
    'sources': [
      'audio_codec_speed_test.h',
      'audio_codec_speed_test.cc',
      '<(webrtc_root)/modules/audio_coding/codecs/opus/opus_speed_test.cc',
      '<(webrtc_root)/modules/audio_coding/codecs/isac/fix/test/isac_speed_test.cc',
    ],
    'conditions': [
      # TODO(henrike): remove build_with_chromium==1 when the bots are
      # using Chromium's buildbots.
      ['build_with_chromium==1 and OS=="android"', {
        'dependencies': [
          '<(DEPTH)/testing/android/native_test.gyp:native_test_native_code',
        ],
      }],
    ],
  }],
  'conditions': [
    # TODO(henrike): remove build_with_chromium==1 when the bots are using
    # Chromium's buildbots.
    ['build_with_chromium==1 and OS=="android"', {
      'targets': [
        {
          'target_name': 'audio_codec_speed_tests_apk_target',
          'type': 'none',
          'dependencies': [
            '<(apk_tests_path):audio_codec_speed_tests_apk',
          ],
        },
      ],
    }],
    ['test_isolation_mode != "noop"', {
      'targets': [
        {
          'target_name': 'audio_codec_speed_tests_run',
          'type': 'none',
          'dependencies': [
            'audio_codec_speed_tests',
          ],
          'includes': [
            '../../../../build/isolate.gypi',
            'audio_codec_speed_tests.isolate',
          ],
          'sources': [
            'audio_codec_speed_tests.isolate',
          ],
        },
      ],
    }],
  ],
}
