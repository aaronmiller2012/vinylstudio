from pathlib import Path
s=Path('app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
panel=s.split('private void cutPanel(){',1)[1].split('private void ',1)[0]
assert panel.count('action(a,"Artwork cut measurements",this::showArtworkCutMetrics)')==1
assert panel.index('Artwork cut measurements')<panel.index('if(alternateActive())')
assert '{"Boundary","Marks","Cut Check","Export"}' in s
standard=s.split('private void showArtworkCutMetrics(){',1)[1].split('private void ',1)[0]
specialty=s.split('private void showSpecialtyArtworkMetrics(){',1)[1].split('private void ',1)[0]
pen=s.split('private void showPenArtworkMetrics(',1)[1].split('private void ',1)[0]
assert 'if(alternateActive()){showSpecialtyArtworkMetrics();return;}' in standard
assert 'if(!standardFinalReady())' in standard
assert 'ArtworkCutMetrics.measure(' in standard
assert 'if(!specialtyFinalReady())' in specialty
assert 'SpecialtyArtworkMetrics.collectSpiral(' in specialty
assert 'SpecialtyArtworkMetrics.collect(' in specialty
assert 'SpecialtyArtworkMetrics.report(input,inspectionMinimum,inspectionGap,guard)' in specialty
assert 'if(line==null||sourceWidth<=0)' in specialty
assert 'showPenArtworkMetrics(line,settings.widthMm/(double)sourceWidth,generation,revision)' in specialty
assert 'new Path(path)' in pen
assert 'AssemblyPathMetrics.measure(scale,snapshot)' in pen
assert 'm.openPaths,m.loops,m.lengthMm' in pen
assert 'no material area or blade clearance is inferred' in pen
assert 'Toolpath measurements unavailable' in pen
assert 'Existing cut validation is unchanged' in specialty
assert 'generation==alternateGeneration&&revision==serial&&specialtyFinalReady()' in pen
assert 'generation==alternateGeneration&&revision==serial&&specialtyFinalReady()' in specialty
assert 'Detailed measurements are not yet supported for this renderer' not in s
print('PASS universal measurement entry, standard/specialty/spiral/pen routes, physical scaling, stale-result rejection and honest failure handling')
