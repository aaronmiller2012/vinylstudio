#!/usr/bin/env python3
from pathlib import Path
r=Path(__file__).resolve().parents[1]
s=(r/'app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
assert 'String.format(Locale.US,"%,d cuts",cutCounts[i])' in s
assert 'LayerCutCounts.standard(out)' in s
assert 'standardCountSerial==renderedSerial?standardLayerCutCounts:null' in s
assert 'LayerCutCounts.layeredCell(layeredResult)' in s
assert 'LayerCutCounts.mosaic(tessellationResult)' in s
assert 'LayerCutCounts.tonal(layers)' in s
assert 'if(activeWorkspace==4&&!draft)updateVisibleLayerCutLabels();' in s
assert 'if(activeWorkspace==4)updateVisibleLayerCutLabels();' in s
assert 'tonalCutCountGeneration==alternateReadyGeneration' in s
assert 'String.format(Locale.US,"%,d separate pieces' in s
print('PASS v2.49.2 UI: cuts per layer with stale/draft protections and async tonal count')
