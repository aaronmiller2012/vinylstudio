from pathlib import Path
s=Path('app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
for word in ['StyleLayerOperations.bySlot(result,','newStyleOperations=operations','continuousBladeContours=completedBladeContours','newStyleSelectedLayer!=slot','bySlot(', 'blade cuts','pen/engrave paths','mazeWallSegments']:
    assert word in s,word
assert 'continuousPenPaths=completedPenPaths' in s
assert '"Single toolpath"' not in s
print('PASS: all new art styles and continuous modes use actual layer/toolpath labels')
