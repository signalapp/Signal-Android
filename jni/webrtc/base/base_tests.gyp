# Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
#
# Use of this source code is governed by a BSD-style license
# that can be found in the LICENSE file in the root of the source
# tree. An additional intellectual property rights grant can be found
# in the file PATENTS.  All contributing project authors may
# be found in the AUTHORS file in the root of the source tree.
{
  'includes': [ '../build/common.gypi', ],
  'targets': [
    {
      'target_name': 'rtc_base_tests_utils',
      'type': 'static_library',
      'sources': [
        'unittest_main.cc',
        # Also use this as a convenient dumping ground for misc files that are
        # included by multiple targets below.
        'fakeclock.cc',
        'fakeclock.h',
        'fakenetwork.h',
        'fakesslidentity.h',
        'faketaskrunner.h',
        'gunit.h',
        'testbase64.h',
        'testechoserver.h',
        'testutils.h',
        'timedelta.h',
      ],
      'defines': [
        'GTEST_RELATIVE_PATH',
      ],
      'dependencies': [
        'base.gyp:rtc_base',
        '<(DEPTH)/testing/gtest.gyp:gtest',
        '<(webrtc_root)/test/test.gyp:field_trial',
        '<(webrtc_root)/test/test.gyp:test_support',
      ],
      'direct_dependent_settings': {
        'defines': [
          'GTEST_RELATIVE_PATH',
        ],
      },
      'export_dependent_settings': [
        '<(DEPTH)/testing/gtest.gyp:gtest',
      ],
    },
  ],
}
