import json,re
from pathlib import Path
INFO=json.loads((Path(__file__).parent/'release-info.json').read_text())
def version_code_matches(source):
    m=re.search(r'\bversionCode\s+(\d+)\b',source)
    return m is not None and int(m.group(1))==INFO['versionCode']
def version_name_matches(source):
    m=re.search(r"\bversionName\s+['\"]([^'\"]+)['\"]",source)
    return m is not None and m.group(1)==INFO['versionName']
def apk_name_matches(source):
    return INFO['apk'] in source
