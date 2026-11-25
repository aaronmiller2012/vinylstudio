#!/usr/bin/env python3
import importlib.util
from pathlib import Path
p=Path(__file__).parent/'profile_summary.py'
spec=importlib.util.spec_from_file_location('profile_summary',p)
m=importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)
head='Flags,IntendedVsync,Vsync,OldestInputEvent,FrameCompleted'
text='---PROFILEDATA---\n'+head+'\n0,100000000,100000000,0,110000000\n0,130000000,130000000,0,180000000\n1,200000000,200000000,0,700000000\n---PROFILEDATA---\n'
values=m.frame_durations(text)
assert values==[10.0,50.0],values
assert m.percentile(values,50)==30.0
summary=m.summarize(text,'TOTAL PSS: 128000')
assert 'Frame records: 2' in summary
assert '125.0 MiB' in summary
assert 'Frames > 33.33 ms: 1/2' in summary
print('PASS RC57 Android frame and memory profile summary parser')

from tempfile import TemporaryDirectory
import csv
log='''D VinylPerf: styleKey=A draft=false totalMs=50
D VinylPerf: styleKey=A draft=false prepareMs=10 engineMs=20 canvasMs=30
D VinylPerf: styleKey=B draft=true totalMs=12
D VinylPerf: styleKey=C draft=false engineMs=100
'''
timings=m.render_timings(log)
assert timings=={('A','false'):[50,60],('B','true'):[12]},timings
with TemporaryDirectory() as d:
    dest=Path(d)/'style-timings.csv'
    report=m.style_report(timings,[{'style_key':k,'name':k} for k in 'ABC'],dest)
    rows=list(csv.DictReader(dest.open()))
    assert '1/3' in report and rows[0]['final_p50_ms']=='55.0'
    assert rows[1]['status']=='NOT RUN' and rows[1]['draft_count']=='1'
    assert rows[2]['status']=='NOT RUN'
print('PASS per-style final/draft timing coverage and missing case reporting')
