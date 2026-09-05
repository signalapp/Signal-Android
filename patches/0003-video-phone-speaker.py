#!/usr/bin/env python3
"""Allow handset/earpiece <-> speaker selection while local video is enabled.

Signal normally hides its audio-output picker during video unless a Bluetooth or
wired headset is present, and separately marks the earpiece unavailable while
local video is on. This patch removes only those UI availability restrictions;
actual routing continues through Signal's existing audio-device selection path.

Targeted against Signal Android v8.25.2 source layout. Strict matching is
intentional: abort when upstream changes the relevant logic instead of guessing.
"""
from pathlib import Path
import sys

ROOT = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()


def edit(rel: str, replacements: list[tuple[str, str]]) -> None:
    path = ROOT / rel
    text = path.read_text(encoding="utf-8")
    original = text
    for old, new in replacements:
        count = text.count(old)
        if count != 1:
            raise RuntimeError(f"{rel}: expected exactly one match, found {count}: {old[:120]!r}")
        text = text.replace(old, new, 1)
    if text == original:
        raise RuntimeError(f"{rel}: no changes made")
    path.write_text(text, encoding="utf-8")
    print(f"patched {rel}")


edit(
    "app/src/main/java/org/thoughtcrime/securesms/components/webrtc/WebRtcControls.java",
    [
        (
            """  public boolean displayAudioToggle() {
    return (isPreJoin() || isAtLeastOutgoing()) && (!isLocalVideoEnabled || isBluetoothHeadsetAvailableForAudioToggle() || isWiredHeadsetAvailableForAudioToggle());
  }
""",
            """  public boolean displayAudioToggle() {
    return isPreJoin() || isAtLeastOutgoing();
  }
""",
        ),
        (
            """  public boolean isEarpieceAvailableForAudioToggle() {
    return !isLocalVideoEnabled;
  }
""",
            """  public boolean isEarpieceAvailableForAudioToggle() {
    return true;
  }
""",
        ),
    ],
)

print("Video handset/speaker selector patch applied successfully")
