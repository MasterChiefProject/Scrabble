import test from "node:test";
import assert from "node:assert/strict";

import {
  CENTER,
  MAX_SCORELESS_TURNS,
  analyzeMove,
  commitMove,
  createAssignedBlank,
  createGameState,
  createTile,
  exchangeTiles,
  passTurn,
  validateSavedState,
  winners
} from "../../docs/engine.js";

function tile(letter) {
  return createTile(letter);
}

function placement(rackIndex, row, col, placedTile) {
  return { rackIndex, row, col, tile: placedTile };
}

test("new game contains a standard conserved 100-tile set", () => {
  const state = createGameState(["One", "Two"], () => 0.5);
  assert.equal(state.players[0].rack.length, 7);
  assert.equal(state.players[1].rack.length, 7);
  assert.equal(state.bag.length, 86);
  assert.equal(validateSavedState(state), true);
});

test("first move must cross center and scores the center double word", () => {
  const state = createGameState(["One", "Two"], () => 0.5);
  state.players[0].rack = [tile("H"), tile("O"), tile("R"), tile("N"), tile("A"), tile("E"), tile("I")];
  const placements = [
    placement(0, CENTER, 5, tile("H")),
    placement(1, CENTER, 6, tile("O")),
    placement(2, CENTER, 7, tile("R")),
    placement(3, CENTER, 8, tile("N"))
  ];
  const dictionary = new Set(["HORN"]);
  const result = analyzeMove(state, placements, dictionary);
  assert.equal(result.score, 14);
  assert.deepEqual(result.words, ["HORN"]);

  const committed = commitMove(state, placements, dictionary);
  assert.equal(committed.score, 14);
  assert.equal(state.board[CENTER][5].letter, "H");
  assert.equal(state.currentPlayer, 1);
});

test("invalid or disconnected moves do not mutate the board", () => {
  const state = createGameState(["One", "Two"], () => 0.5);
  state.players[0].rack = [tile("C"), tile("A"), tile("T"), tile("E"), tile("I"), tile("O"), tile("N")];
  const before = JSON.stringify(state.board);
  assert.throws(() => analyzeMove(state, [
    placement(0, 0, 0, tile("C")),
    placement(1, 0, 1, tile("A")),
    placement(2, 0, 2, tile("T"))
  ], new Set(["CAT"])), /center/);
  assert.equal(JSON.stringify(state.board), before);
});

test("blank tile assignment stays zero points", () => {
  const state = createGameState(["One", "Two"], () => 0.5);
  state.players[0].rack = [tile("?"), tile("A"), tile("T"), tile("E"), tile("I"), tile("O"), tile("N")];
  const placements = [
    placement(0, CENTER, 6, createAssignedBlank("C")),
    placement(1, CENTER, 7, tile("A")),
    placement(2, CENTER, 8, tile("T"))
  ];
  const result = analyzeMove(state, placements, new Set(["CAT"]));
  assert.equal(result.score, 4);
});

test("exchange draws replacements before returning exchanged tiles", () => {
  const state = createGameState(["One", "Two"], () => 0.5);
  state.players[0].rack = [tile("A"), tile("B"), tile("C"), tile("D"), tile("E"), tile("F"), tile("G")];
  state.bag = [tile("H"), tile("I"), tile("J"), tile("K"), tile("L"), tile("M"), tile("N")];

  exchangeTiles(state, [0], () => 0.5);
  assert.equal(state.players[0].rack[0].letter, "N");
  assert.equal(state.bag.some(entry => entry.letter === "A"), true);
});

test("six consecutive scoreless turns end the game", () => {
  const state = createGameState(["One", "Two"], () => 0.5);
  for (let i = 0; i < MAX_SCORELESS_TURNS; i += 1) passTurn(state);
  assert.equal(state.gameOver, true);
});

test("saved-state validation rejects corrupt tile conservation", () => {
  const state = createGameState(["One", "Two"], () => 0.5);
  assert.equal(validateSavedState(state), true);
  state.bag.push(tile("A"));
  assert.equal(validateSavedState(state), false);
});

test("winner calculation handles ties", () => {
  const state = createGameState(["One", "Two"], () => 0.5);
  state.players[0].score = 42;
  state.players[1].score = 42;
  assert.deepEqual(winners(state).map(player => player.name), ["One", "Two"]);
});

import { createGameStorage } from "../../docs/storage.js";

class MemoryStorage {
  constructor() { this.values = new Map(); }
  getItem(key) { return this.values.has(key) ? this.values.get(key) : null; }
  setItem(key, value) { this.values.set(key, String(value)); }
  removeItem(key) { this.values.delete(key); }
}

test("autosave restoration does not overwrite the separate manual save", () => {
  const memory = new MemoryStorage();
  const storage = createGameStorage(memory);
  const manual = createGameState(["Manual One", "Manual Two"], () => 0.25);
  const automatic = createGameState(["Auto One", "Auto Two"], () => 0.75);

  assert.equal(storage.writeManual(manual), true);
  assert.equal(storage.writeAuto(automatic), true);
  assert.equal(storage.readAuto().players[0].name, "Auto One");
  assert.equal(storage.readManual().players[0].name, "Manual One");
});

test("persistence rejects and removes corrupt saved state", () => {
  const memory = new MemoryStorage();
  const storage = createGameStorage(memory);
  memory.setItem("masterchief-scrabble-autosave-v3", "{not-json");
  assert.equal(storage.readAuto(), null);
  assert.equal(memory.getItem("masterchief-scrabble-autosave-v3"), null);
});
