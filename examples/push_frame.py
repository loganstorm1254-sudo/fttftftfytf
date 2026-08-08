#!/usr/bin/env python3
"""Push an image to MineDoom's 16:9 Display screen.

Usage:
  python push_frame.py /path/to/image.png

The plugin watches: <server>/plugins/MineDoom/display/frame.png
While the Display Terminal lever is ON, that image appears on the linked screen.

You can also animate:
  for i in range(100):
      # draw frame...
      img.save(out)
      time.sleep(0.1)
"""

from __future__ import annotations

import argparse
import shutil
import sys
from pathlib import Path


def main() -> int:
    p = argparse.ArgumentParser(description="Push a PNG/JPG to a MineDoom display screen")
    p.add_argument("image", type=Path, help="Source image (.png / .jpg)")
    p.add_argument(
        "--out",
        type=Path,
        default=None,
        help="Destination frame path (default: ./plugins/MineDoom/display/frame.png)",
    )
    args = p.parse_args()

    if not args.image.is_file():
        print(f"Missing image: {args.image}", file=sys.stderr)
        return 1

    out = args.out
    if out is None:
        # Try common server layouts relative to cwd
        candidates = [
            Path("plugins/MineDoom/display/frame.png"),
            Path("../plugins/MineDoom/display/frame.png"),
        ]
        out = next((c for c in candidates if c.parent.exists()), candidates[0])

    out.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(args.image, out)
    print(f"Wrote {out.resolve()}")
    print("Flick the Display Terminal lever ON to show it on the 16:9 screen.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
