#!/usr/bin/env python3
"""Structural safety checks complement JVM cancellation and draft-behavior tests."""
from pathlib import Path
import json,re
root=Path(__file__).resolve().parents[1]
src=root/'app/src/main/java/com/halftonestudio'
info=json.loads((root/'tests/release-info.json').read_text())
assert info['versionCode']>=439, 'Render responsiveness release baseline regressed'
assert (root/info['gate']).is_file(), 'Configured release gate missing'
ui=(src/'StudioActivity.java').read_text()
assert 'int maxSide=draft?480:850;' in ui
assert 'config.draft=draft;' in ui
assert 'supportsSpecialtyDraft(mazePortraitMode' in ui
assert 'alternateReadyGeneration=-1;' in ui and 'renderedSerial=-1' in ui
assert 'specialtySourceKey(photoRevision,source,settings.snips,0,settings.circleCrop,160)' in ui
assert 'if(largest>160)' in ui
policy=(src/'RenderDraftPolicy.java').read_text()
assert 'HEAVY_DEBOUNCE_MS=120' in policy and 'DEBOUNCE_MS=60' in policy and 'MAX_SIDE_PX=480' in policy
assert 'draft?460:880' in (src/'PatternPortraitTrioEngine.java').read_text()
assert 'o.draft?460f:840f' in (src/'PrismaticDiagonalPortraitEngine.java').read_text()
files='FallenConfettiEngine InterlockingChainmailEngine PatternPortraitTrioEngine PrismaticDiagonalPortraitEngine LinocutEngine LowPolyDelaunayEngine BanknoteEngravingEngine StainedGlassEngine RidgeLinesEngine TruchetTilesEngine TspArtEngine TextHalftoneEngine TopographicLayersEngine ExpandedPortraitStylesEngine MazePortraitEngine MultiColorStippleEngine LayeredCellPortraitEngine TessellationEngine TessellationMotifEngine VinylEngine AsciiPortraitEngine StencilSeparationEngine WordPortraitEngine WordKnockoutEngine SingleLineSwirlEngine SpaceFillingRibbon LineArtEngine ContinuousLineModes MultilayerStencilEngine'.split()
coverage={name:(src/(name+'.java')).read_text().count('CancellationCheckpoint.check()') for name in files}
assert all(value>0 for value in coverage.values()),('Engine checkpoints missing',coverage)
count=sum(coverage.values())
assert 'Future<?> future=workers[i].submit(()->memoryBudget.run(lane,task));' in (src/'StudioRenderController.java').read_text()
print('PASS RC162 source checks: draft budgets, latest-result guards, cancellation in 29 engine modules ('+str(count)+' checkpoints)')
