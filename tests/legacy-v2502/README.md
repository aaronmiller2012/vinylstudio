These files are historical v2.50.0/v2.50.2 proof-of-regression checks, NOT v2.50.3 release gates.
They intentionally require the rejected minimum-width carrier floor and/or the v2.50.0 renderer.
Do not execute them as new-release tests. The active 2.50.3 verification gate is
`tests/run-v2503-gate.sh` plus `tests/run-release-smoke.sh` for the historical suite.
