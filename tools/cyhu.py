#!/usr/bin/env python3
"""CleanYourHeadUnit command line. Run `tools/cyhu.py --help`. Needs Python 3.9+ and adb, nothing else."""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from cyhu.cli import main  # noqa: E402

if __name__ == "__main__":
    sys.exit(main())
