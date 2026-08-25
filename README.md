# ScrabbleGame

[![CI](https://github.com/MasterChiefProject/ScrabbleGame/actions/workflows/ci.yml/badge.svg)](https://github.com/MasterChiefProject/ScrabbleGame/actions/workflows/ci.yml)

A Java 17 word-board game engine with standard tile distribution and scoring, exact dictionary validation, a bounded TCP dictionary service, automated tests, and a browser-playable local multiplayer adaptation.

**Live demo:** https://masterchiefproject.github.io/ScrabbleGame/

## Highlights

- Standard 15 x 15 board and 100-tile English distribution
- Two blank tiles with zero-point letter assignment
- Two to four local players
- First-move center validation
- Horizontal and vertical placement validation
- Existing-tile reuse and gap validation
- Main-word and cross-word generation
- Double-letter, triple-letter, double-word, and triple-word scoring
- 50-point seven-tile bingo bonus
- Premium squares apply only when a tile is first placed
- Tile exchange without immediately redrawing returned tiles
- Six-scoreless-turn game ending
- Final rack deductions and going-out bonus
- Exact offline dictionary validation
- Bloom-filter negative pre-check with LRU and LFU caches
- Bounded, loopback-only TCP dictionary server by default
- Browser demo with dark mode by default, persistent theme, autosave, manual save/load, and two to four players
- Maven/JUnit Java test suite and Node.js browser-engine tests
- GitHub Actions CI on Java 17 and Java 21

## Architecture

```text
ScrabbleGame/
├── .github/workflows/ci.yml
├── docs/                         # GitHub Pages browser application
│   ├── index.html
│   ├── style.css
│   ├── app.js                    # UI and browser persistence
│   ├── engine.js                 # Testable browser rules engine
│   ├── storage.js                # Validated autosave/manual-save adapter
│   ├── words.txt
│   ├── CMUDICT-LICENSE.txt
│   └── .nojekyll
├── src/
│   ├── main/
│   │   ├── java/com/masterchiefproject/scrabble/
│   │   │   ├── game/             # Board, scoring, players, bag, console client
│   │   │   ├── dictionary/       # Dictionary, Bloom filter, caches
│   │   │   └── server/           # Bounded TCP dictionary service
│   │   └── resources/dictionary/
│   │       ├── words.txt
│   │       └── CMUDICT-LICENSE.txt
│   └── test/java/com/masterchiefproject/scrabble/
│       ├── game/
│       ├── dictionary/
│       └── server/
├── tests/web/                    # Node.js tests for browser rules engine
├── package.json
├── pom.xml
└── README.md
```

The Java engine is the primary implementation. The browser application is a JavaScript adaptation of the same placement, scoring, exchange, and game-ending rules so the project can be evaluated without installing Java.

## Java game engine

### Board validation

`Board` validates and scores a move before changing any board state. A rejected move is atomic and leaves the board unchanged.

A legal move must satisfy the applicable rules:

1. Place one to seven new rack tiles.
2. Keep every placement inside the 15 x 15 board.
3. Never overwrite an occupied square.
4. Place new tiles in one row or one column.
5. Leave no empty gap between the move's endpoints unless an existing tile fills it.
6. Cover the center square on the first move.
7. Connect to the existing board on later moves.
8. Form at least one word containing two or more letters.
9. Validate every main word and cross-word against the configured `WordValidator`.

`Board.preview(...)` performs the same validation and scoring without mutating the board.

### Scoring

The scoring engine supports:

- standard English letter values
- double-letter and triple-letter squares
- double-word and triple-word squares
- center square as a double-word square
- multiple word multipliers in one word
- perpendicular cross-word scoring
- blank tiles worth zero points
- 50-point bingo bonus for using all seven rack tiles

Premium squares affect only newly placed tiles. Existing tiles never reuse a premium on later turns.

### Tile bag and exchange

`TileBag` contains the standard 100-tile English distribution: 98 letter tiles and two blanks.

During an exchange, replacement tiles are drawn before the returned tiles go back into the bag. This prevents the player from immediately drawing back a tile from the same exchange.

### Game coordination

`ScrabbleGame` manages:

- two to four uniquely named players
- current turn
- seven-tile racks
- rack refills
- scores
- passes
- exchanges
- scoreless-turn tracking
- end-of-game detection
- final rack deductions
- going-out bonus

The game ends when either:

- the bag is empty and a player uses the last tile in their rack, or
- six consecutive scoreless turns occur

## Dictionary subsystem

`Dictionary` performs exact case-insensitive validation using an in-memory word set.

Lookup flow:

```text
word
  |
  v
positive LRU cache
  |
  v
negative LFU cache
  |
  v
Bloom filter pre-check
  |
  v
exact word set
```

The Bloom filter is only a negative pre-check. Every possible Bloom-filter match is confirmed against the exact word set, so a Bloom false positive cannot legalize an invalid word.

`DictionaryManager` lazily loads file-based dictionaries for the TCP protocol and restricts access to configured dictionary root directories.

## TCP dictionary service

The `server` package preserves the networking component of the original project while hardening it for local use.

`DictionaryServer` provides:

- loopback-only binding by default
- bounded worker pool and bounded pending-client queue
- per-client socket read timeout
- deterministic shutdown
- request-size limits
- limited dictionaries per request
- UTF-8 request validation
- dictionary path-root enforcement

Protocol:

```text
Q,file1.txt,file2.txt,WORD
C,file1.txt,file2.txt,WORD
```

Response:

```text
true
false
```

Invalid requests return an `ERROR,...` response.

Start the local server after building:

```bash
java -cp target/scrabble-game-2.1.0.jar \
  com.masterchiefproject.scrabble.server.DictionaryServerMain 6000 src/main/resources/dictionary
```

The default bind address is `127.0.0.1`. An explicit API constructor is available when another bind address is intentionally required.

## Browser demo

The GitHub Pages application lives in `docs/`.

It includes:

- two to four configurable local players
- the same standard board and tile distribution
- blank-tile assignment
- dictionary validation
- cross-word scoring
- exchange and pass actions
- final scoring and tie handling
- turn history
- dark mode by default
- light/dark theme persistence
- automatic recovery after page reload
- separate manual Save and Load controls
- deep save-state validation and tile-conservation checks
- responsive layout

### Save behavior

The browser uses two independent local-storage records:

- an automatic save updated after completed turns and new games
- a manual save created only when **Save game** is selected

On page load, the automatic save is restored when valid. Starting a new game does not destroy the separate manual save.

## Build and run

Requirements:

- Java 17 or newer
- Maven 3.9 or newer

Run the full Java build and tests:

```bash
mvn verify
```

Build the executable JAR:

```bash
mvn package
```

Run the console game:

```bash
java -jar target/scrabble-game-2.1.0.jar
```

The terminal client supports two to four players and the commands:

```text
place <row> <col> <H|V> <WORD>
exchange <LETTERS>
pass
help
quit
```

Coordinates in the console UI are 1-based.

## Browser tests

Node.js 20 or newer is sufficient. There are no npm runtime dependencies.

Check browser JavaScript syntax:

```bash
npm run check:web
```

Run browser-engine tests:

```bash
npm run test:web
```

The browser tests cover standard tile conservation, center-square behavior, scoring, invalid move atomicity, blanks, exchange semantics, game ending, save-state validation, and ties.

## CI

GitHub Actions runs on every pull request and every push to `main`.

The pipeline verifies:

- Maven build and JUnit tests on Java 17
- Maven build and JUnit tests on Java 21
- browser JavaScript syntax
- Node.js browser-engine tests

The Maven compiler treats all Java compiler warnings as build failures with `-Xlint:all -Werror`.

## GitHub Pages

Publish the browser demo from:

```text
Branch: main
Folder: /docs
```

The live site is:

https://masterchiefproject.github.io/ScrabbleGame/

## Dictionary data

The bundled offline word list is derived from CMU Pronouncing Dictionary data and filtered for this programming demo. It is not an official tournament Scrabble lexicon.

The CMU dictionary license is included in both runtime locations:

```text
src/main/resources/dictionary/CMUDICT-LICENSE.txt
docs/CMUDICT-LICENSE.txt
```

The dictionary is replaceable through the `WordValidator` abstraction.

## Project history

This repository began as a university Book Scrabble assignment. The current version replaces the original coursework layout with a standard Maven project, separates production code from tests and web assets, corrects the original board and dictionary logic, and adds a polished playable demonstration and CI pipeline.
