
## Benchmark Run A (manual dispatch, AUTO_ASSIGN=0 MANUAL_DISPATCH=1)
- overall utilisation: 0.093 (idle 0.907)
- windows: ~889-893 s (about 15 min since `up`, not 10)
- workers 0.0 (marked busy, never dispatched); RebarBot-1 0.183, DrywallBot-1 0.376, RebarBot-2 0.0
- SPAWN_TASKS=1, SPAWN_EVERY=6, MANUAL_DELAY=20
- Run A task counts at measurement: open 109, assigned 2, done 45

## Benchmark Run B (auto-assign, workers held busy)
- overall utilisation: 0.252 (idle 0.748), windows ~928-932 s
- RebarBot-1 0.47, RebarBot-2 0.44, DrywallBot-1 0.606, workers 0.0
- task counts: open 38, assigned 1, in_progress 1, done 114
- Result: idle reduced 17.5% overall ((0.907-0.748)/0.907), 39% for robots only
