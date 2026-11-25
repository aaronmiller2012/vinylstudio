"""Semantic release-floor validation shared by historical source-contract checks."""
import re


def at_least(actual, minimum):
    """Accept newer major/minor versions without accepting invalid release metadata."""
    if not isinstance(actual, str) or not isinstance(minimum, str):
        return False
    pattern = r"([0-9]+)\.([0-9]+)\.([0-9]+)(?:-[A-Za-z0-9][A-Za-z0-9.-]*)?"
    parsed_actual = re.fullmatch(pattern, actual)
    parsed_minimum = re.fullmatch(r"([0-9]+)\.([0-9]+)\.([0-9]+)", minimum)
    if not parsed_actual or not parsed_minimum:
        return False
    return tuple(map(int, parsed_actual.group(1, 2, 3))) >= tuple(map(int, parsed_minimum.groups()))
