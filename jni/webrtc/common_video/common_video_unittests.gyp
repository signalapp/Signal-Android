# Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
#
# Use of this source code is governed by a BSD-style license
# that can be found in the LICENSE file in the root of the source
# tree. An additional intellectual property rights grant can be found
# in the file PATENTS.  All contributing project authors may
# be found in the AUTHORS file in the root of the source tree.

{
  'includes': ['../build/common.gypi'],
  'targets': [
    {
      'target_name': 'common_video_unittests',
      'type': '<(gtest_target_type)',
      'dependencies': [
         '<(webrtc_root)/common_video/common_video.gyp:common_video',
         '<(DEPTH)/testing/gtest.gyp:gtest',
         '<(webrtc_root)/system_wrappers/system_wrappers.gyp:system_wrappers',
         '<(webrtc_root)/test/test.gyp:test_support_main',
         '<(webrtc_root)/test/test.gyp:video_test_common',
      ],
      'sources': [
        'bitrate_adjuster_unittest.cc',
        'h264/pps_parser_unittest.cc',
        'h264/sps_parser_unittest.cc',
        'h264/sps_vui_rewriter_unittest.cc',
        'i420_buffer_pool_unittest.cc',
        'i420_video_frame_unittest.cc',
        'libyuv/libyuv_unittest.cc',
      ],
      # Disable warnings to enable Win64 build, issue 1323.
      'msvs_disabled_warnings': [
        4267,  # size_t to int truncation.
      ],
      'conditions': [
        ['OS=="android"', {
          'dependencies': [
            '<(DEPTH)/testing/android/native_test.gyp:native_test_native_code',
          ],
        }],
        ['OS=="ios"', {
          'mac_bundle_resources': [
            '<(DEPTH)/resources/foreman_cif.yuv',
          ],
        }],
      ],
    },
  ],  # targets
  'conditions': [
    ['OS=="android"', {
      'targets': [
        {
          'target_name': 'common_video_unittests_apk_target',
          'type': 'none',
          'dependencies': [
            '<(android_tests_path):common_video_unittests_apk',
          ],
        },
      ],
      'conditions': [
        ['test_isolation_mode != "noop"',
          {
            'targets': [
              {
                'target_name': 'common_video_unittests_apk_run',
                'type': 'none',
                'dependencies': [
                  '<(android_tests_path):common_video_unittests_apk',
                ],
                'includes': [
                  '../build/isolate.gypi',
                ],
                'sources': [
                  'common_video_unittests_apk.isolate',
                ],
              },
            ],
          },
        ],
      ],
    }],  # OS=="android"
    ['test_isolation_mode != "noop"', {
      'targets': [
        {
          'target_name': 'common_video_unittests_run',
          'type': 'none',
          'dependencies': [
            'common_video_unittests',
          ],
          'includes': [
            '../build/isolate.gypi',
          ],
          'sources': [
            'common_video_unittests.isolate',
          ],
        },
      ],
    }],
  ],
}
