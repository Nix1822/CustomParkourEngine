Rah jrbto fservr dyali 4dam 100/100
# CustomParkourEngine

Parkour timer plugin for Paper 1.21.4 (Java 21). Courses are saved in `config.yml`, personal bests in SQLite.

## Build
```bash
mvn clean package
```
Output: `target/CustomParkourEngine.jar`

## Setup (admins, need `parkour.admin`)
Stand on the block you want as the pad, then:
```text
/parkour create <n>
/parkour setstart <n>
/parkour addcheckpoint <n> <number>
/parkour setfinish <n>
/parkour setfail <n> <y-level>
/parkour delete <n>
```
Checkpoints are numbered from 1, in order.

## Player commands
- `/parkour play <n>` start a run
- `/parkour checkpoint` go back to the last checkpoint (timer keeps going)
- `/parkour restart`, `/parkour leave`
- `/parkour leaderboard <n>` top 3 times
- `/parkour list`

## Notes
- Falling below the fail Y level or touching water/lava sends you back to your last checkpoint.
- Dying, quitting or getting kicked ends the run.
- Teleports, flight, elytra, pearls and game mode changes cancel the run.
- Set `require-all-checkpoints: true` in `config.yml` if the last checkpoint must be reached before finishing.

## Try it on a local server
1. Build the jar with `mvn clean package`.
2. Download Paper 1.21.4 from https://papermc.io/downloads/paper and put `paper-1.21.4-*.jar` in an empty folder.
3. Create `eula.txt` with `eula=true`, then start it with `java -Xmx2G -jar paper-1.21.4-*.jar --nogui` (Java 21).
4. Stop the server, copy `target/CustomParkourEngine.jar` into `plugins/`, and start it again.
5. Join `localhost`, op yourself (`op <name>` in the console) and follow the setup commands above.
6. Edit `plugins/CustomParkourEngine/config.yml` and restart (or reload the plugin) to test config changes
7. Ra jrbto fservr dyali w4dam 100/100
