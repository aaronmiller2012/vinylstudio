# RC170 — Fishnet perimeter colors and reliability

**Version:** 2.55.35, versionCode 447. Based on RC169. No style ID renumbering or project ZIP format change.

## Verified changes

A. Fishnet helper parents sample image luminance and local contrast at the nearest valid downstream knot, and copy that knot's physical color layer. Excluded from equal-population quantiles, motif count and per-layer motif counters. Incoming perimeter strands remain clipped to Snip.

B. FailureText is used by StudioActivity's remaining library, artwork, import/export, assembly guide, palette, registration, and render user-facing failures. Null and provider exception errors have fallbacks. Safe long messages are truncated with an ellipsis rather than hidden entirely.

C. fromJson and parked-layer restore clamp width/margin/minimum, contour strength, detail boost, cross angle, radial focus, wave amplitude/frequency, per-layer line width/gamma/fine angle to VinylEngine.generate ranges. Invalid nonfinite numeric fields receive defaults. A pathological aspect ratio may still need a photo-dependent size adjustment after decoding.

D. StudioProjectLibrary writes SHA-256, file length and mtime to card.properties once when a revision is saved; legacy revisions cache their fingerprint on first restore. StudioProjectBackup.importAll uses archiveDigest instead of hashing every unchanged archive. A file modified without any change to its size or timestamp cannot be detected by metadata alone; project data remains app-private and backed by normal archive validation.

E. Added executable RC170IntegrityToneTest using masked color-contrast geometry, quantile counts, mock SAF mode-aware streams backed by a real filesystem file, picker state re-creation, queued UI shutdown, readable errors and import bounds. StudioProjectLibraryTest also tests SHA-256 metadata persistence and idempotent cache reads. The RC169 source-string compatibility assertion is retained only as a secondary wiring check. The mandatory gate also executes targeted RC169-RC164 engine and palette suites; the deep recursive historical suite is opt-in with FULL_TESTS=1 to avoid the long legacy chain blocking every NAS build.

F. README, Gradle hostRegression, CI, NAS build, mapping artifact and release metadata updated to RC170.

## Verification and limitations

- Host regression tests can validate the plain Java engines and integration helpers without Android SDK. Android lifecycle, SAF providers, R8, lint and Cricut physical cuts must still run on the NAS/device.
- Backup preflight does not make SAF writing atomic in all providers. A storage-provider failure mid-write can leave an incomplete target.
- Activity decomposition, undo history across process death, and main-thread JSON profiling remain separate architecture backlog items.
- No change to input photo handling, registration sheets, style ID 37 Fishnet, style ID 39 Crochet or retired IDs 36 and 38.
