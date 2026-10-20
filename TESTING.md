# Manual test plan (Paper 1.21.4)

1. Put `CustomParkourEngine.jar` in `plugins/`, start the server. First start downloads sqlite-jdbc; check the log for errors.
2. As op: `/parkour create test`, then stand on plates and run `setstart test`, `addcheckpoint test 1`, `addcheckpoint test 2`, `addcheckpoint test 3` (the last number is the finish).
3. Step on the start plate. Check: action bar shows `mm:ss.mmm`, particles show on all points.
4. Skip checkpoint 1 and step on 2: expect a warning. Step on the finish early: expect a refusal.
5. Fall into the void, water and lava: expect a teleport to the last checkpoint.
6. Try fly, elytra, ender pearl, TNT, firework, `/gamemode`: run should be blocked or cancelled.
7. Finish with a non-op account, wait >3s: `/parkour leaderboard test` shows the time. Finish again slower: no change.
8. `/stop`, restart, `/parkour leaderboard test`: the time must still be there (file: `plugins/CustomParkourEngine/parkour.db`).
9. Fall with 0 checkpoints: you return to the start block and the timer must keep running (no reset).
10. Fall after checkpoint 2: you return to checkpoint 2 and the timer keeps running.
11. Walk back onto the start plate mid-run: nothing happens.
12. Stand next to primed TNT or a wind charge: the run is cancelled when you are launched.
