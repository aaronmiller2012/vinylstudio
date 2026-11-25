"""Static transitive host-gate coverage for NAS/Play shell entry points.

A release may invoke its current style gate and inherited v3/RC173 gates as
siblings rather than nesting the entire historical test suite inside the new
style-specific gate. Verify the executable bash-call graph instead of assuming
one particular release-gate filename or nesting pattern.
"""

from pathlib import Path
import re

_DIRECT_BASH_GATE = re.compile(r"^\s*bash\s+(tests/[A-Za-z0-9_.-]+\.sh)(?=\s|$)", re.MULTILINE)


def gate_reachable(root: Path, entry: str, target: str) -> bool:
    if not (target.startswith("tests/") and target.endswith(".sh")):
        raise ValueError("Target must be a tests/*.sh gate")
    pending = [entry]
    visited = set()
    while pending:
        script = pending.pop()
        if script == target:
            return True
        if script in visited:
            continue
        visited.add(script)
        path = root / script
        if not path.is_file():
            continue
        pending.extend(_DIRECT_BASH_GATE.findall(path.read_text()))
    return False
