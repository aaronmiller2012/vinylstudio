from pathlib import Path
p=Path(__file__).resolve().parents[1]
s=(p/'app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
e=(p/'app/src/main/java/com/halftonestudio/LayeredCellPortraitEngine.java').read_text()
for phrase in ['"Prepare","Shapes","Adjust","Colors","Layers","Export"','private void showUnifiedStyleDrawer()', 'private void halftonePanel()',
  'case 2:colorPaletteOnlyPanel();break;', 'case 3:unifiedLayersPanel();break;',
  'private void unifiedLayersPanel()', 'private void selectUnifiedLayer(int index)',
  'colorSwatchGrid(overview,colors,labels,populated,selected','"Color wheel"',
  '"Screen angle · °"', '"Line / dot spacing"','"Coverage"','"Contrast"',
  '"Shape / line: "','"Cell motif: "', 'private void chooseLayeredLayerFamily(int index)',
  'out.put("layeredLayerFamilies",families)', 'json.optJSONArray("layeredLayerFamilies")',
  'cells.layerFamilies=mosaicLayerFamilies']:
 assert phrase in s, 'Missing workspace wiring: '+phrase
assert 'public int[] layerFamilies;' in e and 'validFamilies(o.layerFamilies,o.layerCount)' in e
assert 'if(o.layerFamilies!=null)' in e
assert 'case 3:if(workflowStep==WorkflowFlow.LAYERS' not in s
print('PASS: V2.49 six-tab navigation, centralized active layer controls, per-channel screens, per-layer shape/state binding')
