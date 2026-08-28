# Scrabble

[![CI](https://github.com/MasterChiefProject/Scrabble/actions/workflows/ci.yml/badge.svg)](https://github.com/MasterChiefProject/Scrabble/actions/workflows/ci.yml)

**Scrabble** is a Java 17 word-board game engine with full board validation and scoring, exact dictionary lookup, a bounded TCP dictionary service, automated tests, and a browser-playable multiplayer adaptation.

**Playable demo:** https://masterchiefproject.github.io/Scrabble/

The Java implementation is the primary rules engine. A JavaScript browser adaptation mirrors the main placement, scoring, exchange, and game-ending rules so the project can be evaluated without a local Java installation.

## Highlights

- Standard 15 x 15 board
- Standard 100-tile English distribution
- Two to four players
- Blank-tile letter assignment
- First-move center validation
- Horizontal and vertical placement validation
- Existing-tile reuse and gap validation
- Main-word and cross-word generation
- Premium-square scoring
- 50-point seven-tile bingo bonus
- Correct one-time premium-square consumption
- Tile exchange semantics
- Six-scoreless-turn game ending
- Final rack deductions and going-out bonus
- Exact offline dictionary validation
- Bloom-filter negative pre-check
- LRU and LFU caches
- Bounded loopback TCP dictionary service
- JUnit and Node.js test suites
- GitHub Actions CI on Java 17 and Java 21

## Architecture

```text
Scrabble/
├── .github/
│   └── workflows/
│       └── ci.yml
├── docs/                         # GitHub Pages browser application
│   ├── app.js                    # UI and browser persistence
│   ├── engine.js                 # Browser rules engine
│   ├── storage.js                # Validated browser saves
│   ├── index.html
│   ├── style.css
│   └── words.txt
├── src/
│   ├── main/
│   │   ├── java/com/masterchiefproject/scrabble/
│   │   │   ├── game/             # Board, scoring, bag, players, console client
│   │   │   ├── dictionary/       # Exact dictionary, Bloom filter, caches
│   │   │   └── server/           # TCP dictionary service
│   │   └── resources/dictionary/
│   └── test/java/com/masterchiefproject/scrabble/
├── tests/
│   └── web/                      # Browser-engine tests
├── package.json
├── pom.xml
└── README.md
```

## Java game engine

### Move validation

`Board` validates and scores a move before mutating board state. Invalid moves are atomic and leave the board unchanged.

A legal move satisfies the applicable Scrabble rules:

1. One to seven new rack tiles are placed.
2. Every placement stays within the board.
3. Existing tiles are never overwritten.
4. New tiles occupy a single row or column.
5. No empty gap exists between the move endpoints unless an existing tile fills it.
6. The first move covers the center square.
7. Later moves connect to the existing board.
8. At least one word of two or more letters is formed.
9. Every main word and cross-word passes the configured `WordValidator`.

`Board.preview(...)` executes the same validation and scoring path without modifying the board.

### Scoring

The engine supports:

- standard English letter values
- double-letter and triple-letter squares
- double-word and triple-word squares
- center-square double-word scoring
- multiple word multipliers in one word
- perpendicular cross-word scoring
- zero-point blank tiles
- 50-point bingo bonus for using all seven rack tiles

Premium squares apply only to newly placed tiles.

### Game coordination

`ScrabbleGame` manages player order, racks, bag state, score changes, passes, exchanges, scoreless-turn tracking, end-game conditions, and final scoring.

A game ends when either:

- the bag is empty and a player uses the final tile in their rack, or
- six consecutive scoreless turns occur

Final scoring deducts remaining rack values and awards the going-out bonus when applicable.

## Dictionary subsystem

Dictionary lookup combines exact validation with lightweight caching and a Bloom-filter pre-check:

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

The Bloom filter is only used to reject definite negatives. Every possible match is still confirmed against the exact word set, so Bloom-filter false positives cannot legalize invalid words.

`DictionaryManager` also constrains file-backed dictionary access to configured root directories.

## TCP dictionary service

The networking layer preserves the original dictionary-server concept while adding bounded resource use and safer local defaults.

`DictionaryServer` includes:

- loopback-only binding by default
- bounded worker pool
- bounded pending-client queue
- socket read timeouts
- request-size limits
- UTF-8 request validation
- dictionary path-root enforcement
- deterministic shutdown

Protocol:

```text
Q,file1.txt,file2.txt,WORD
C,file1.txt,file2.txt,WORD
```

Responses:

```text
true
false
```

Malformed requests receive an `ERROR,...` response.

After packaging, the local server entry point is:

```bash
java -cp target/scrabble-game-2.1.0.jar \
  com.masterchiefproject.scrabble.server.DictionaryServerMain \
  6000 src/main/resources/dictionary
```

The default bind address is `127.0.0.1`.

## Browser demo

The browser implementation in `docs/` provides:

- two to four local players
- the standard board and tile distribution
- blank-tile assignment
- dictionary validation
- cross-word scoring
- exchange and pass actions
- final scoring and tie handling
- turn history
- autosave and manual save/load
- deep save validation and tile-conservation checks
- persistent dark/light theme
- responsive layout

Automatic and manual saves are stored independently. Valid autosave data can restore a session after reload, while a manual save remains available across new games.

## Build and run

Requirements:

- Java 17 or newer
- Maven 3.9 or newer

Full Java verification:

```bash
mvn verify
```

Executable JAR:

```bash
mvn package
java -jar target/scrabble-game-2.1.0.jar
```

The console client supports:

```text
place <row> <col> <H|V> <WORD>
exchange <LETTERS>
pass
help
quit
```

Console coordinates are 1-based.

## Browser verification

The browser engine has no npm runtime dependencies. Node.js 20 or newer is sufficient for its verification suite.

```bash
npm run check:web
npm run test:web
```

The browser tests cover tile conservation, center-square rules, scoring, atomic invalid moves, blank tiles, exchanges, game-ending conditions, save validation, and ties.

## Continuous integration

GitHub Actions validates:

- Maven build and JUnit tests on Java 17
- Maven build and JUnit tests on Java 21
- Java compiler warnings via `-Xlint:all -Werror`
- browser JavaScript syntax
- Node.js browser-engine tests

## Dictionary data

The bundled offline word list is derived from CMU Pronouncing Dictionary data and is intended for the programming demonstration rather than official tournament play.

The CMU dictionary license is included with both Java and browser runtime data:

```text
src/main/resources/dictionary/CMUDICT-LICENSE.txt
docs/CMUDICT-LICENSE.txt
```

The dictionary implementation is replaceable through the `WordValidator` abstraction.

## Deployment

The browser adaptation is deployed from `docs/` through GitHub Pages.

**Live demo:** https://masterchiefproject.github.io/Scrabble/

## Project evolution

The repository began as a university Book Scrabble assignment. The current version uses a standard Maven structure, separates production code from tests and browser assets, strengthens board and dictionary behavior, preserves the networking component, and adds a polished browser demonstration with automated verification.
