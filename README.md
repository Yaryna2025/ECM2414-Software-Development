# Multi-threaded Card Game (ECM2414)

## Versions used

- JDK: Eclipse Temurin 25.0.4.1 (code is compiled with `--release 17`, so it runs on JDK 17 or newer)
- JUnit: **JUnit 5 (Jupiter) 5.10.2**, supplied by `lib/junit-platform-console-standalone-1.10.2.jar`
- No other dependencies. The jar in `lib/` is the only external file and is included.

## Contents of this zip

- `src/` - production source (`Card`, `CardDeck`, `Player`, `GameState`, `CardGame`)
- `test/` - JUnit 5 tests
- `lib/` - JUnit console launcher jar
- `classes/main`, `classes/test` - a prebuilt copy of the compiled classes

All classes are in the default package, so no package directory structure is needed.

## Compile and run the tests (macOS / Linux)

Run these from the folder containing this README:

```
mkdir -p out/main out/test
javac --release 17 -d out/main src/*.java
javac --release 17 -cp out/main:lib/junit-platform-console-standalone-1.10.2.jar -d out/test test/*.java
java -jar lib/junit-platform-console-standalone-1.10.2.jar execute --class-path out/main:out/test --scan-class-path out/test
```

On Windows use `;` instead of `:` in the class paths and `mkdir out\main out\test`.

Expected result: 74 tests found, 74 successful, 0 failed. Lines saying
`player 1 wins` appear during the run because the game tests print the winner; this is normal.

To run the tests against the prebuilt classes instead, use
`classes/main` and `classes/test` in place of `out/main` and `out/test` in the last command.

## Run the game

`cards.jar` is submitted alongside this zip:

```
java -jar cards.jar
```

Enter the number of players, then the path of a pack file. A valid pack is a plain text file with
exactly 8n lines, each a single non-negative integer. Output files `playerN_output.txt` and
`deckN_output.txt` are written to the current directory.

Example pack for 4 players (macOS / Linux):

```
for v in 1 2 3 4; do for k in 1 2 3 4 5 6 7 8; do echo $v; done; done | sort -R > four.txt
```