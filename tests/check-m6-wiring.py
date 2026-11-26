from pathlib import Path
import xml.etree.ElementTree as ET
p=Path('app/src/main/java/com/halftonestudio')
g=(p/'CutGeometryConditioner.java').read_text();s=(p/'VinylStyleSuite.java').read_text();u=(p/'StudioActivity.java').read_text();f=(p/'StudioProductFlows.java').read_text()
assert 'CutRepairGrid.plan(spanX,spanY,minMm,gapMm,MAX_PIXELS)' in g and 'if(grid==null)throw new BudgetExceededException' in g
assert s.count('repairBySeparatedGroups(result,layer,minimumMm,gapMm);return;')==2
assert 'CutRepairPartitions.group' in s and 'CutRepairGrid.openingRadius(minMm,p)' in g
assert 'SharedExportFiles.extractCutShare' in f and f.index('if(cutShare.reviewOnly())')<f.index('for(File sheet:cutShare.sheets)')
assert 'shareReviewZip(written)' in f and 'reportSummary(cutShare.matNote)' in f
assert 'ParallelGeometry.configureMemoryBudget' in u and 'onTrimMemory(int level)' in u
assert 'StylePickerFamilies.matches(category,choice.category,choice.previewKind)' in u and 'StylePickerFamilies.tabs()' in u
assert 'paletteRequest!=layeredPhotoPaletteRequest' in u and 'sourceRevision!=photoRevision' in u and 'R.string.photo_suggestion_skipped' in u
strings={e.attrib['name'] for e in ET.parse('app/src/main/res/values/strings.xml').getroot().findall('string')}
assert {'share_review_only_title','share_review_only_message','action_share_review_zip','share_review_zip_unavailable','photo_suggestion_skipped','photo_suggestion_memory','photo_suggestion_busy'}<=strings
assert 'bash tests/run-nas-source-preflight.sh' in Path('BUILD_ON_NAS.sh').read_text()
print('PASS M6 production call-site and resource wiring')
