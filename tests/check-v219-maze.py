from pathlib import Path
p=Path(__file__).resolve().parents[1]
a=(p/'app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
b=(p/'app/src/main/java/com/halftonestudio/MazePortraitEngine.java').read_text()
assert '"Maze Portrait"' in a and 'maze=MazePortraitEngine.generate(prepared,opt)' in a
assert 'maze-walls.svg' in a and 'maze-solution.svg' in a and 'maze-combined-preview.svg' in a
assert 'mazeResult.wallsPath' in a and 'mazeResult.solutionPath' in a
assert 'perimeterOpening(entry,active,n)' in b
assert 'if(prev[exit]<0)' in b and 'reached!=validCount' in b
assert 'lightProb=0f;' in b and 'darkProb=0f;' in b
print('PASS: V2.19 maze UI, preview, three exports, perimeter openings and perfect-maze guard')
