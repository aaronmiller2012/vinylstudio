#!/usr/bin/env python3
"""RC83 onboarding copy and navigation contract. Independent of Android SDK."""
from pathlib import Path
from xml.etree import ElementTree as ET
import re
root=Path(__file__).resolve().parents[1]
res=root/'app/src/main/res/values/strings.xml'
strings={node.attrib['name']:node.text or '' for node in ET.parse(res).getroot().findall('string')}
java=(root/'app/src/main/java/com/halftonestudio/VinylQuickStartGuide.java').read_text()
activity=(root/'app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
assert 'FIRST_RUN_PAGES=VinylGuidePolicy.GUIDE_PAGES' in java
policy=(root/'app/src/main/java/com/halftonestudio/VinylGuidePolicy.java').read_text()
assert 'GUIDE_PAGES=5, TIP_PAGES=11' in policy
assert '(fullHelp?TITLES.length:FIRST_RUN_PAGES)-firstPage' in java
assert 'showQuickStartGuide(false)' in activity
assert 'showQuickStartGuide(true)' in activity
assert 'fullHelp?R.string.guide_done:R.string.guide_start' in java
assert 'KEY_SEEN="quickStartGuideSeenV2494"' in java, 'Existing users must not be re-onboarded'
assert 'KEY_TIPS="quickStartTipsEnabledV2494"' in java, 'Do not reset tip preferences'
for kind in ('title','body','tip'):
    ids=re.findall(r'R.string.guide_'+kind+r'_(\d+)',java)
    assert ids==[str(i) for i in range(16)], f'{kind}: guide must have 16 ordered pages: {ids}'
    assert 'guide_'+kind+'_16' not in strings, 'stale guide resource left behind'
    for i in range(16):
        txt=strings['guide_'+kind+'_'+str(i)]
        assert txt and len(txt)> (8 if kind=='title' else 40), (kind,i,txt)
        if kind=='body': assert len(txt)<180, ('keep it simple',i,len(txt))
        if kind=='tip': assert len(txt)<110, ('keep it simple',i,len(txt))
for name in ('guide_snip_tip','guide_layers_tip','guide_export_tip','guide_menu','guide_close','guide_done','guide_sample'):
    assert name in strings and strings[name].strip()
assert 'eight populated cut layers' in strings['guide_tip_3']
assert 'test cut' in strings['guide_tip_4']
assert 'firstPage=fullHelp?FIRST_RUN_PAGES:0' in java
assert 'TITLES[topic]' in java and 'DESCRIPTIONS[topic]' in java and 'TIPS[topic]' in java
assert 'guide_user_menu' in activity and 'guide_topics' in java
assert 'setItems(names' in java
assert 'scroller.scrollTo(0,0)' in java
assert 'dimensions-dxf.txt' in strings['guide_body_13']
assert '11.5-inch' in strings['guide_body_7']
assert 'Raised edge accent sheet' in strings['guide_body_10']
assert 'Final render pending' in strings['guide_tip_8']
assert 'Export project ZIP' in strings['guide_body_15']
assert 'Apply snip' in strings['guide_body_1']
assert 'Try sample photo' in strings['guide_tip_0']
assert 'assembly guide PDF' in strings['guide_body_4']
assert 'Heat-transfer vinyl' in strings['guide_tip_14']
assert 'guide_kicker_help' in java
# Smart routing: contextual start page, Open shortcut, first-run tour unaffected
for name in ('guide_go','guide_suggested'): assert '%1$s' in strings[name]
assert 'VinylGuidePolicy.suggestedPage(fullHelp,workspace,outputDevice)' in java
assert 'index={contextual?suggested:0}' in java
assert 'VinylGuidePolicy.mayOfferShortcut' in java
assert 'showQuickStartGuide(false,false)' in activity, 'first-run tour must start at page 1'
assert 'fromMenu&&photo!=null?activeWorkspace:-1' in activity
assert 'photo!=null,WORKSPACE_LABELS,this::selectWorkspace' in activity
assert 'mayOfferShortcut(target,hasPhoto)' in java
assert 'next.setContentDescription(next.getText())' in java
assert 'if(fullHelp||contextual)root.addView(topics' in java
print('Guide PASS: 5 first-run pages, 11 question-style tips, contextual start + Open shortcut, short copy, preferences and cutting safety')
