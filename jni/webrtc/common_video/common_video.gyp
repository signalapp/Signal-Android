# Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
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
      'target_name': 'common_video',
      'type': 'static_library',
      'include_dirs': [
        '<(webrtc_root)/modules/interface/',
        'include',
        'libyuv/include',
      ],
      'dependencies': [
        '<(webrtc_root)/common.gyp:webrtc_common',
        '<(webrtc_root)/system_wrappers/system_wrappers.gyp:system_wrappers',
      ],
      'direct_dependent_settings': {
        'include_dirs': [
          'include',
          'libyuv/include',
        ],
      },
      'conditions': [
        ['build_libyuv==1', {
          'dependencies': ['<(DEPTH)/third_party/libyuv/libyuv.gyp:libyuv',],
          'export_dependent_settings': [
            '<(DEPTH)/third_party/libyuv/libyuv.gyp:libyuv',
          ],
        }, {
          # Need to add a directory normally exported by libyuv.gyp.
          'include_dirs': ['<(libyuv_dir)/include',],
        }],
        ['OS=="ios" or OS=="mac"', {
          'sources': [
            'corevideo_frame_buffer.cc',
            'include/corevideo_frame_buffer.h',
          ],
          'link_settings': {
            'xcode_settings': {
              'OTHER_LDFLAGS': [
                '-framework CoreVideo',
              ],
            },
          },
        }],
      ],
      'sources': [
        'bitrate_adjuster.cc',
        'h264/sps_vui_rewriter.cc',
        'h264/sps_vui_rewriter.h',
        'h264/h264_common.cc',
        'h264/h264_common.h',
        'h264/pps_parser.cc',
        'h264/pps_parser.h',
        'h264/sps_parser.cc',
        'h264/sps_parser.h',
        'i420_buffer_pool.cc',
        'video_frame.cc',
        'incoming_video_stream.cc',
        'include/bitrate_adjuster.h',
        'include/frame_callback.h',
        'include/i420_buffer_pool.h',
        'include/incoming_video_stream.h',
        'include/video_frame_buffer.h',
        'libyuv/include/webrtc_libyuv.h',
        'libyuv/webrtc_libyuv.cc',
        'video_frame_buffer.cc',
        'video_render_frames.cc',
        'video_render_frames.h',
      ],
    },
  ],  # targets
}
