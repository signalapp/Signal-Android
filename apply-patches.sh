#!/usr/bin/env bash
set -euo pipefail
ROOT="${1:-.}"
python "$(dirname "$0")/patches/0001-hq-bluetooth.py" "$ROOT"
python "$(dirname "$0")/patches/0002-proximity-toggle.py" "$ROOT"
python "$(dirname "$0")/patches/0003-video-phone-speaker.py" "$ROOT"
