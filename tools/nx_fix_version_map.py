#!/usr/bin/env python3
"""Point version_map.json's download URLs at our own NX Launcher release
instead of FCL-Team's upstream release.

version_map.json is a shared file: every time we merge upstream, their own
edits to this file (new changelog entries) come along in the merge, but the
"url"/"netdiskUrl" fields in those edits still point at FCL-Team's GitHub
releases. This script rewrites those two fields to point at our own rolling
"nx-latest" release, keeping everything else (versionCode/versionName/date/
description) as merged from upstream.

Run after every upstream merge; see .github/workflows/nx-auto-sync.yml and
tools/nx_update.py.
"""

import json
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
VERSION_MAP = REPO_ROOT / "version_map.json"
RELEASE_TAG_URL = "https://github.com/exgg1453/NX-Launcher/releases/tag/nx-latest"
RELEASE_ASSET_BASE = "https://github.com/exgg1453/NX-Launcher/releases/download/nx-latest"
BUILD_TYPE = "fordebug"


def main():
    entries = json.loads(VERSION_MAP.read_text(encoding="utf-8"))
    for entry in entries:
        entry["url"] = f"{RELEASE_ASSET_BASE}/FCL-{BUILD_TYPE}-{entry['versionName']}-all.apk"
        entry["netdiskUrl"] = RELEASE_TAG_URL
    VERSION_MAP.write_text(
        json.dumps(entries, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    print(f"Rewrote {len(entries)} version_map.json entry(ies) to point at NX Launcher's own release.")


if __name__ == "__main__":
    main()
