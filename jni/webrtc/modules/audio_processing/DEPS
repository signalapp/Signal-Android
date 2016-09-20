include_rules = [
  "+webrtc/base",
  "+webrtc/common_audio",
  "+webrtc/system_wrappers",
]

specific_include_rules = {
  ".*test\.cc": [
    "+webrtc/tools",
    # Android platform build has different paths.
    "+gtest",
    "+external/webrtc",
  ],
}
