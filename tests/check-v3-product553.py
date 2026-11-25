#!/usr/bin/env python3
"""Build 553 integration: version identity, manifest share wiring, transfer rules, call sites."""
import json, re
import xml.etree.ElementTree as ET
from pathlib import Path
root=Path(__file__).resolve().parents[1]
src=root/'app/src/main/java/com/halftonestudio'
A='{http://schemas.android.com/apk/res/android}'
info=json.loads((root/'tests/release-info.json').read_text())
gradle=(root/'app/build.gradle').read_text()
code=int(re.search(r'versionCode (\d+);',gradle)[1])
assert code==info['versionCode'], 'release-info / gradle versionCode'
assert f'static final int CURRENT={code};' in (src/'EngineBuild.java').read_text(), 'EngineBuild.CURRENT must equal versionCode'
name=re.search(r"versionName '([^']+)'",gradle)[1]
assert name!='3.0.0' and info['apk']==f'VinylHalftoneStudio-v{name}-Product{code}.apk' or code!=553, 'version name must change with releases'

m=ET.parse(root/'app/src/main/AndroidManifest.xml').getroot()
app=m.find('application')
act=app.find('activity')
assert act.get(A+'launchMode')=='singleTask', 'shared photos must reach the single editor instance'
filters=[(f.find('action').get(A+'name'),[d.get(A+'mimeType') for d in f.findall('data') if d.get(A+'mimeType')]) for f in act.findall('intent-filter')]
assert ('android.intent.action.SEND',['image/*']) in filters and ('android.intent.action.VIEW',['image/*']) in filters, filters
assert app.get(A+'allowBackup')=='false', 'cloud backup stays disabled'

rules=ET.parse(root/'app/src/main/res/xml/data_extraction_rules.xml').getroot()
assert [(e.tag,e.get('domain'),e.get('path')) for e in rules.find('cloud-backup')]==[('exclude','root','.')]
incl={(e.get('domain'),e.get('path')) for e in rules.find('device-transfer') if e.tag=='include'}
assert ('file','project-library/') in incl, 'project library must move to a new phone'
s=(src/'StudioActivity.java').read_text()
assert 'PALETTE_LIBRARY_PREFS="vinylstudio-palettes"' in s and ('sharedpref','vinylstudio-palettes.xml') in incl
assert 'getSharedPreferences("scrap_calibration_v1"' in s and ('sharedpref','scrap_calibration_v1.xml') in incl
assert 'StudioProjectLibrary.shared(getFilesDir())' in s and '"project-library"' in (src/'StudioProjectLibrary.java').read_text()

# Call-site wiring that host tests cannot reach.
flows=(src/'StudioProductFlows.java').read_text()
assert 'productFlows.maybeShowBackupReminder(entries.size())' in s and 'productFlows.recordLibraryBackup();' in s
assert 'BackupReminderPolicy.PREF_LAST_BACKUP,System.currentTimeMillis()' in flows
assert 'out.put(EngineBuild.KEY,EngineBuild.CURRENT)' in s and 'productFlows.maybeShowEngineChangeNotice(projectEngineBuild,currentLibraryId)' in s
assert s.count('productFlows.offerShareCutSheets(uri)')==2 and 'if(!ready.diagnostic)productFlows.offerShareCutSheets(uri);' in s, 'diagnostic ZIPs must never be offered as cut files'
assert 'productFlows.acceptSharedImage(getIntent())' in s and 'protected void onNewIntent(Intent intent)' in s
assert 'requestProjectSwitch(()->{cancelStartupRestore();beginPhotoImport(uri);},"the shared photo")' in s, 'shares honor unsaved-changes protection'
restore=s.split('private void restoreLocal(){',1)[1].split('/** Keep editor and draft payloads',1)[0]
assert restore.count('productFlows.deliverPendingSharedImage')>=5, 'shared photo must wait for every startup-restore exit'
assert 'if(shared==null||host.startupRestoring()||!host.alive())return;' in flows
assert 'PhotoPairToken.compare(savedPhotoToken,loaded.pairToken)' in restore and 'startupSavedDraft=null;' in restore
assert '.putString(PhotoPairToken.PREF,token)' in s and 'projectPhotoStore.save(files,bitmap,full,revision,token)' in s
assert 'PhotoPairToken.write(newPhoto,pairToken)' in (src/'ProjectPhotoStore.java').read_text()
crash=flows.split('void maybeOfferCrashReport(){',1)[1].split('// ---- Library backup reminder',1)[0]
assert 'host.runDiagnostics' in crash and crash.index('host.runDiagnostics')<crash.index('VinylCrashReport.read'), 'no UI-thread crash file IO'
assert 'public void runDiagnostics(Runnable job){diagnosticsExecutor.execute(job);}' in s
lib=(src/'StudioProjectLibrary.java').read_text()
describe=lib.split('private Entry describe(String id){',1)[1].split('synchronized Entry get',1)[0]
assert 'listedBundle(id)' in describe and 'currentBundle(' not in describe, 'gallery listing must not fully inflate archives'
assert 'File bundle=currentBundle(id,true);' in lib.split('synchronized File archive(String id){',1)[1][:120], 'opening must fully verify'
print(f'PASS 553 version {name}/{code}, share filters/provider, device transfer rules and call-site wiring')
