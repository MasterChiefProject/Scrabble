# Scrabble

A complete Java word-board game engine with a playable browser adaptation.

The project started as a university Book Scrabble assignment and has been refactored into a production-style structure. The Java implementation now contains the board rules, scoring engine, tile bag, local multiplayer coordinator, dictionary subsystem, cache policies, and TCP dictionary service. The browser version mirrors the core placement and scoring behavior and can be hosted directly with GitHub Pages.

## Play in the browser

https://masterchiefproject.github.io/ScrabbleGame/

The browser version is local two-player pass-and-play. It includes:

- 15 x 15 standard premium-square board
- 100-tile English distribution, including two blank tiles
- Seven-tile racks
- First move center-square validation
- Horizontal and vertical placement validation
- Contiguity and board-connectivity validation
- Cross-word generation and scoring
- Double-letter, triple-letter, double-word, and triple-word scoring
- 50-point seven-tile bingo bonus
- Offline dictionary validation
- Tile exchange and pass actions
- Six-scoreless-turn game ending
- Final rack-value score adjustment
- Save and load using browser local storage
- Dark mode by default
- Light and dark theme switch with the selected background saved automatically
- Responsive layout for desktop and smaller screens

## Project structure

```text
ScrabbleGame/
├── pom.xml
├── README.md
├── coursework/
│   └── README.md
├── docs/
│   ├── index.html
│   ├── style.css
│   ├── app.js
│   ├── words.txt
│   ├── CMUDICT-LICENSE.txt
│   └── .nojekyll
└── src/
    ├── main/
    │   ├── java/com/masterchiefproject/scrabble/
    │   │   ├── game/
    │   │   ├── dictionary/
    │   │   └── server/
    │   └── resources/dictionary/
    │       ├── words.txt
    │       └── CMUDICT-LICENSE.txt
    └── test/
        └── java/com/masterchiefproject/scrabble/
```

The original project placed production classes directly under `src/test`. That structure has been replaced with the standard Maven layout:

- `src/main/java` contains production Java code.
- `src/main/resources` contains runtime data.
- `src/test/java` contains automated tests only.
- `docs` contains the GitHub Pages application.
- `coursework` is reserved for the original assignment documents.

## What was fixed in the original project

The refactor addresses several correctness and structure problems in the original coursework snapshot:

- Production classes no longer live directly under `src/test`.
- The three legacy `MainTrain1.java`, `MainTrain2.java`, and `MainTrain3.java` files each declared a public class named `MainTrain`, which prevents a normal Java build. They are replaced by proper JUnit tests.
- `Board.dictionaryLegal()` previously returned `true` unconditionally. Board moves now validate every formed word through an injected exact `WordValidator`.
- The original bag contained 98 letter tiles and no blanks. The game now uses the standard 100-tile distribution with two zero-point blanks.
- Premium-square scoring is now applied only to newly placed tiles. Existing tiles cannot reuse letter or word multipliers on later turns.
- Main words and perpendicular cross-words are constructed and scored independently.
- Move validation is atomic. A rejected move cannot partially modify the board.
- Alignment, gaps, center-square rules, board connectivity, overwrites, blank assignment, and seven-tile limits are validated explicitly.
- Bloom-filter matches are verified against the exact dictionary, so false positives cannot legalize invalid words.
- LRU reads now refresh recency, LFU eviction is deterministic, and cache size cannot drift from stored contents.
- Dictionary files again support multiple whitespace-separated words per line.
- The TCP server now handles clients concurrently, reports malformed requests, closes resources deterministically, and can be tested on an ephemeral port.
- The browser adaptation now follows the corrected board/scoring rules and includes two-player turns, blanks, exchange, pass, dictionary validation, saves, and persistent dark/light themes.

## Java engine

### Board and move validation

`Board` owns the 15 x 15 tile grid and premium-square layout.

A move is accepted only when all applicable rules pass:

1. One to seven new rack tiles are placed.
2. Every new tile is inside the board.
3. A new tile does not overwrite an occupied square.
4. All new tiles are aligned in a single row or column.
5. Empty gaps are allowed only when an existing board tile fills the gap.
6. The first move covers the center square.
7. Later moves connect to the existing board.
8. The move forms at least one word with two or more letters.
9. Every formed word passes the configured `WordValidator`.

Validation and scoring happen before the board is mutated, so a rejected move cannot partially alter game state.

### Word construction

For every accepted move the engine constructs:

- the main horizontal or vertical word
- every perpendicular cross-word created by a newly placed tile

The engine deduplicates formed words and validates each one independently.

### Scoring

Premium squares affect only tiles placed during the current turn.

The engine supports:

- normal letter values
- double-letter squares
- triple-letter squares
- double-word squares
- triple-word squares
- the center square as a double-word square
- multiple word multipliers in one word
- cross-word scoring
- zero-point blank tiles
- 50-point bingo bonus when all seven rack tiles are played

Existing tiles never receive a premium-square bonus again.

### Tile bag

`TileBag` uses the standard 100-tile English distribution:

- 98 letter tiles
- 2 blank tiles

The bag supports random drawing and tile exchange. Exchanges are restricted by `ScrabbleGame` to turns where at least seven tiles remain in the bag.

### Local multiplayer

`ScrabbleGame` coordinates two to four players.

It manages:

- current player
- racks
- scores
- tile refills
- move execution
- exchanges
- passes
- scoreless-turn tracking
- end-of-game detection
- final rack deductions
- finisher bonus from opponents' remaining rack values

The game ends when either:

- the bag is empty and a player uses the last tile in their rack, or
- six consecutive scoreless turns occur

## Dictionary subsystem

The original coursework included a Bloom filter, LRU and LFU cache policies, dictionary management, and a small TCP service. Those components are retained and corrected.

### Exact validation

`Dictionary` implements `WordValidator`.

The lookup flow is:

```text
word
  |
  v
positive cache
  |
  v
negative cache
  |
  v
Bloom filter pre-check
  |
  v
exact word set
```

The Bloom filter is used only as a fast negative pre-check. A possible Bloom-filter match is always confirmed against the exact word set, so Bloom false positives cannot make an invalid word legal.

### LRU cache

`LRU` now refreshes recency on successful reads as well as writes.

### LFU cache

`LFU` tracks access frequency and uses deterministic age ordering to resolve equal-frequency eviction candidates.

### CacheManager

`CacheManager` no longer keeps an independent size counter that can become inconsistent with the actual set. Capacity is derived from the cache contents and replacement policy updates are synchronized.

### DictionaryManager

`DictionaryManager` lazily loads dictionaries by normalized file path and reuses them through a thread-safe map.

It retains the original assignment-style APIs:

```java
DictionaryManager.get().query("book1.txt", "book2.txt", "HELLO");
DictionaryManager.get().challenge("book1.txt", "book2.txt", "HELLO");
```

Dictionary files may contain one word per line or multiple whitespace-separated words per line.

## TCP dictionary server

`MyServer` and `BookScrabbleHandler` retain the original line protocol while fixing lifecycle and error handling.

Supported requests:

```text
Q,file1.txt,file2.txt,WORD
C,file1.txt,file2.txt,WORD
```

Responses:

```text
true
false
```

Malformed requests return an `ERROR,...` response.

The server now provides:

- deterministic startup and shutdown
- concurrent client handling
- socket cleanup with try-with-resources
- idempotent start behavior
- clean executor shutdown
- support for port `0` in automated tests

## Browser adaptation

The browser implementation lives in `docs` because GitHub Pages can publish that folder directly.

```text
docs/
├── index.html
├── style.css
├── app.js
├── words.txt
└── .nojekyll
```

The web version does not run the JVM in the browser. It is a JavaScript adaptation of the same game rules so the repository provides both:

- the original Java engineering implementation
- a zero-install playable demonstration

### Theme persistence

Dark mode is the default.

The light/dark selection is stored under browser local storage, so the selected background is restored on the next visit.

### Game saves

The web demo also stores an explicit game save in local storage. The Save game and Load game buttons preserve:

- board tiles
- racks
- scores
- bag contents
- current player
- turn history
- scoreless-turn count

Pending, uncommitted tiles are intentionally not persisted.

## Dictionary data

The included offline word list is derived from the CMU Pronouncing Dictionary data and filtered to alphabetic words between 2 and 15 letters.

It is useful for a self-contained programming demo, but it is not an official tournament Scrabble lexicon. The dictionary layer is intentionally replaceable. To use another lexicon, replace the newline/whitespace-separated word list while keeping the same `WordValidator` interface.

The CMU dictionary data license is included in:

```text
src/main/resources/dictionary/CMUDICT-LICENSE.txt
docs/CMUDICT-LICENSE.txt
```

## Build and test

Requirements:

- Java 17 or newer
- Maven 3.9 or newer

Run the automated tests:

```bash
mvn test
```

Compile the project:

```bash
mvn package
```

Run the interactive Java console game after compilation:

```bash
java -cp target/classes com.masterchiefproject.scrabble.game.ConsoleGame
```

Or run the small non-interactive verification entry point:

```bash
java -cp target/classes com.masterchiefproject.scrabble.game.GameDemo
```

## GitHub Pages

To publish the browser version:

1. Open repository Settings.
2. Open Pages.
3. Choose `Deploy from a branch`.
4. Select branch `main`.
5. Select folder `/docs`.
6. Save.

The live URL is then:

https://masterchiefproject.github.io/ScrabbleGame/

## Automated tests

The test suite covers the major engine behaviors:

- standard 100-tile distribution
- blank tile count
- center-square first move
- premium-square scoring
- reuse of existing letters
- disconnected move rejection
- overwrite rejection
- atomic rejection of invalid dictionary words
- bingo bonus
- exact dictionary validation
- cache behavior
- initial multiplayer rack state
- six-scoreless-turn game ending

## Main classes

| Class | Responsibility |
| --- | --- |
| `Board` | Placement validation, word construction, scoring, board mutation |
| `Tile` | Immutable letter or assigned blank tile |
| `TileBag` | Standard distribution, random draw, exchange |
| `ScrabbleGame` | Players, turns, racks, scores, game ending |
| `Dictionary` | Exact word validation with caches and Bloom pre-check |
| `BloomFilter` | Probabilistic negative pre-check |
| `LRU` | Least-recently-used replacement policy |
| `LFU` | Least-frequently-used replacement policy |
| `CacheManager` | Fixed-capacity word cache |
| `DictionaryManager` | Lazy multi-file dictionary registry |
| `MyServer` | Concurrent TCP server lifecycle |
| `BookScrabbleHandler` | Q/C dictionary request protocol |
| `ConsoleGame` | Interactive terminal client for the Java engine |
| `GameDemo` | Minimal console verification entry point |

## Design goals

This refactor focuses on four things:

1. Correct game rules and scoring.
2. Clear separation between production code, tests, resources, and web assets.
3. Preservation of the technically interesting parts of the original university project.
4. A playable demo that a recruiter or engineer can open immediately without installing Java.
