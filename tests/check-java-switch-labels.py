#!/usr/bin/env python3
"""Fail fast for repeated Java switch labels before launching an Android build.

This is a source-level guard for repeated spelled labels. `javac` remains the
canonical checker for distinct constants that resolve to equal values.
"""
from pathlib import Path
import re
import sys

# Strip Java comments/string/char/text-block contents so embedded 'case'
# and 'switch' words cannot create false positives.
IGNORED = re.compile(r'//[^\n]*|/\*[\s\S]*?\*/|"""[\s\S]*?"""|"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'')
TOKENS = re.compile(r'[A-Za-z_$][A-Za-z_0-9$]*|\d+|->|::|[{}():,;.\[\]+\-=]')


def scan(source):
    safe = IGNORED.sub(lambda m: '\n' * m.group(0).count('\n') + ' ' * (len(m.group(0))-m.group(0).count('\n')), source)
    toks = TOKENS.findall(safe)
    blocks = []  # each brace is None or a set of case labels for a switch
    pending_switch = False
    errors = []
    i=0
    while i<len(toks):
        t=toks[i]
        if t=='switch' and i+1<len(toks) and toks[i+1]=='(':
            # Skip the entire parenthesized selector, including nested calls.
            j=i+1; level=0
            while j<len(toks):
                if toks[j]=='(': level+=1
                elif toks[j]==')':
                    level-=1
                    if level==0: break
                j+=1
            if j<len(toks):
                pending_switch=True
                i=j+1
                continue
        if t=='{':
            blocks.append(set() if pending_switch else None)
            pending_switch=False
        elif t=='}':
            if blocks: blocks.pop()
        elif t=='case':
            active=next((b for b in reversed(blocks) if b is not None),None)
            if active is not None:
                j=i+1; nesting=0; labels=[]; current=[]
                while j<len(toks):
                    v=toks[j]
                    if v in ('(', '['): nesting+=1
                    if v in (')', ']'): nesting-=1
                    if nesting==0 and v in (':','->'):
                        if current: labels.append(''.join(current))
                        break
                    if nesting==0 and v==',':
                        if current: labels.append(''.join(current))
                        current=[]
                    else: current.append(v)
                    j+=1
                for label in labels:
                    if label in active:
                        errors.append('repeated case '+label)
                    active.add(label)
                if j<len(toks): i=j
        i+=1
    return errors


def main():
    roots=[Path(p) for p in sys.argv[1:]]
    if not roots:
        roots=[Path('app/src/main/java')]
    paths=[]
    for p in roots:
        paths += sorted(p.rglob('*.java')) if p.is_dir() else [p]
    issues=[]
    for p in paths:
        for error in scan(p.read_text(encoding='utf-8')):
            issues.append(f'{p}: {error}')
    if issues:
        print('\n'.join(issues),file=sys.stderr)
        raise SystemExit('FAIL: repeated Java switch case labels')
    print(f'PASS: switch-label duplicate scan ({len(paths)} Java files)')

if __name__=='__main__': main()
