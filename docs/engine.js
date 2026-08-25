"use strict";

export const BOARD_SIZE = 15;
export const CENTER = 7;
export const RACK_SIZE = 7;
export const MAX_SCORELESS_TURNS = 6;
export const SAVE_VERSION = 3;
export const MAX_PLAYER_NAME_LENGTH = 40;

export const TILE_DEFINITIONS = Object.freeze({
  A: { count: 9, points: 1 }, B: { count: 2, points: 3 }, C: { count: 2, points: 3 },
  D: { count: 4, points: 2 }, E: { count: 12, points: 1 }, F: { count: 2, points: 4 },
  G: { count: 3, points: 2 }, H: { count: 2, points: 4 }, I: { count: 9, points: 1 },
  J: { count: 1, points: 8 }, K: { count: 1, points: 5 }, L: { count: 4, points: 1 },
  M: { count: 2, points: 3 }, N: { count: 6, points: 1 }, O: { count: 8, points: 1 },
  P: { count: 2, points: 3 }, Q: { count: 1, points: 10 }, R: { count: 6, points: 1 },
  S: { count: 4, points: 1 }, T: { count: 6, points: 1 }, U: { count: 4, points: 1 },
  V: { count: 2, points: 4 }, W: { count: 2, points: 4 }, X: { count: 1, points: 8 },
  Y: { count: 2, points: 4 }, Z: { count: 1, points: 10 },
  "?": { count: 2, points: 0 }
});

export function createPremiumBoard() {
  const premiums = Array.from({ length: BOARD_SIZE }, () => Array(BOARD_SIZE).fill(""));
  const mark = (type, coordinates) => coordinates.forEach(([row, col]) => { premiums[row][col] = type; });

  mark("TW", [[0,0],[0,7],[0,14],[7,0],[7,14],[14,0],[14,7],[14,14]]);
  mark("DW", [[1,1],[1,13],[2,2],[2,12],[3,3],[3,11],[4,4],[4,10],[10,4],[10,10],[11,3],[11,11],[12,2],[12,12],[13,1],[13,13]]);
  premiums[CENTER][CENTER] = "CENTER";
  mark("TL", [[1,5],[1,9],[5,1],[5,5],[5,9],[5,13],[9,1],[9,5],[9,9],[9,13],[13,5],[13,9]]);
  mark("DL", [[0,3],[0,11],[2,6],[2,8],[3,0],[3,7],[3,14],[6,2],[6,6],[6,8],[6,12],[7,3],[7,11],[8,2],[8,6],[8,8],[8,12],[11,0],[11,7],[11,14],[12,6],[12,8],[14,3],[14,11]]);
  return premiums;
}

export const PREMIUMS = createPremiumBoard();

export function normalizePlayerNames(names) {
  if (!Array.isArray(names) || names.length < 2 || names.length > 4) {
    throw new Error("A game requires two to four players.");
  }
  const normalized = names.map((name, index) => {
    const value = String(name ?? "").trim();
    if (!value) throw new Error(`Player ${index + 1} needs a name.`);
    if (value.length > MAX_PLAYER_NAME_LENGTH) {
      throw new Error(`Player names cannot exceed ${MAX_PLAYER_NAME_LENGTH} characters.`);
    }
    if (/\p{Cc}/u.test(value)) throw new Error("Player names cannot contain control characters.");
    return value;
  });
  const unique = new Set(normalized.map(name => name.toLocaleLowerCase("en-US")));
  if (unique.size !== normalized.length) throw new Error("Player names must be unique.");
  return normalized;
}

export function createTile(letter) {
  const normalized = String(letter).toUpperCase();
  const definition = TILE_DEFINITIONS[normalized];
  if (!definition) throw new Error(`Unknown tile: ${letter}`);
  return { letter: normalized, points: definition.points, blank: normalized === "?" };
}

export function createAssignedBlank(letter) {
  const normalized = String(letter).trim().toUpperCase();
  if (!/^[A-Z]$/.test(normalized)) throw new Error("A blank tile must be assigned A-Z.");
  return { letter: normalized, points: 0, blank: true };
}

export function shuffle(values, random = Math.random) {
  for (let i = values.length - 1; i > 0; i -= 1) {
    const j = Math.floor(random() * (i + 1));
    [values[i], values[j]] = [values[j], values[i]];
  }
  return values;
}

export function createBag(random = Math.random) {
  const bag = [];
  Object.entries(TILE_DEFINITIONS).forEach(([letter, definition]) => {
    for (let i = 0; i < definition.count; i += 1) bag.push(createTile(letter));
  });
  return shuffle(bag, random);
}

export function createGameState(playerNames = ["Player 1", "Player 2"], random = Math.random) {
  const names = normalizePlayerNames(playerNames);
  const state = {
    version: SAVE_VERSION,
    board: Array.from({ length: BOARD_SIZE }, () => Array(BOARD_SIZE).fill(null)),
    bag: createBag(random),
    players: names.map(name => ({ name, score: 0, rack: [] })),
    currentPlayer: 0,
    scorelessTurns: 0,
    gameOver: false,
    history: []
  };
  state.players.forEach(player => refillRack(state, player));
  return state;
}

export function currentPlayer(state) {
  return state.players[state.currentPlayer];
}

export function refillRack(state, player) {
  while (player.rack.length < RACK_SIZE && state.bag.length) {
    player.rack.push(state.bag.pop());
  }
}

function key(row, col) {
  return `${row},${col}`;
}

function inside(row, col) {
  return Number.isInteger(row) && Number.isInteger(col)
    && row >= 0 && row < BOARD_SIZE && col >= 0 && col < BOARD_SIZE;
}

function boardIsEmpty(state) {
  return state.board.every(row => row.every(tile => tile === null));
}

function tileConsideringNew(state, row, col, newTiles) {
  const next = newTiles.get(key(row, col));
  return next ? next.tile : state.board[row][col];
}

export function analyzeMove(state, placements, dictionary) {
  assertRunningState(state);
  if (!(dictionary instanceof Set)) throw new Error("Dictionary is not ready.");
  if (!Array.isArray(placements) || !placements.length) throw new Error("Place at least one tile.");
  if (placements.length > RACK_SIZE) throw new Error("A move cannot place more than seven rack tiles.");

  const player = currentPlayer(state);
  const newTiles = new Map();
  const rackIndexes = new Set();

  placements.forEach(item => {
    if (!item || !inside(item.row, item.col)) throw new Error("A tile is outside the board.");
    if (!Number.isInteger(item.rackIndex) || item.rackIndex < 0 || item.rackIndex >= player.rack.length) {
      throw new Error("A placement references an invalid rack tile.");
    }
    if (!rackIndexes.add(item.rackIndex)) throw new Error("A rack tile can only be used once per move.");
    if (state.board[item.row][item.col]) throw new Error("A new tile cannot overwrite an existing tile.");

    validatePlacedTileAgainstRack(player.rack[item.rackIndex], item.tile);
    const positionKey = key(item.row, item.col);
    if (newTiles.has(positionKey)) throw new Error("Two tiles cannot occupy the same square.");
    newTiles.set(positionKey, item);
  });

  const sameRow = new Set(placements.map(item => item.row)).size === 1;
  const sameCol = new Set(placements.map(item => item.col)).size === 1;
  if (placements.length > 1 && !sameRow && !sameCol) {
    throw new Error("All newly placed tiles must be in one row or one column.");
  }

  if (boardIsEmpty(state)) {
    if (!newTiles.has(key(CENTER, CENTER))) throw new Error("The first move must cover the center star.");
  } else if (!touchesExisting(state, placements)) {
    throw new Error("The move must connect to at least one existing tile.");
  }

  if (placements.length > 1) ensureContiguous(state, placements, newTiles, sameRow);

  const words = collectFormedWords(state, placements, newTiles, sameRow, sameCol);
  if (!words.length) throw new Error("The move must form at least one word of two or more letters.");

  let score = 0;
  const wordTexts = [];
  for (const formed of words) {
    const text = wordText(state, formed, newTiles);
    if (!dictionary.has(text)) throw new Error(`"${text}" is not in the included dictionary.`);
    wordTexts.push(text);
    score += scoreWord(state, formed, newTiles);
  }

  const bingo = placements.length === RACK_SIZE;
  if (bingo) score += 50;
  return { score, words: wordTexts, bingo };
}

function validatePlacedTileAgainstRack(rackTile, placedTile) {
  if (!validRackTile(rackTile) || !validBoardTile(placedTile)) throw new Error("Invalid tile data.");
  if (rackTile.blank) {
    if (!placedTile.blank || placedTile.points !== 0 || !/^[A-Z]$/.test(placedTile.letter)) {
      throw new Error("A blank tile must be assigned exactly one letter from A to Z.");
    }
  } else if (placedTile.blank || placedTile.letter !== rackTile.letter || placedTile.points !== rackTile.points) {
    throw new Error("The placed tile does not match the selected rack tile.");
  }
}

function ensureContiguous(state, placements, newTiles, horizontal) {
  if (horizontal) {
    const row = placements[0].row;
    const cols = placements.map(item => item.col);
    for (let col = Math.min(...cols); col <= Math.max(...cols); col += 1) {
      if (!tileConsideringNew(state, row, col, newTiles)) throw new Error("Placed tiles cannot leave an empty gap.");
    }
  } else {
    const col = placements[0].col;
    const rows = placements.map(item => item.row);
    for (let row = Math.min(...rows); row <= Math.max(...rows); row += 1) {
      if (!tileConsideringNew(state, row, col, newTiles)) throw new Error("Placed tiles cannot leave an empty gap.");
    }
  }
}

function touchesExisting(state, placements) {
  const directions = [[-1,0],[1,0],[0,-1],[0,1]];
  return placements.some(item => directions.some(([dr, dc]) => {
    const row = item.row + dr;
    const col = item.col + dc;
    return inside(row, col) && state.board[row][col] !== null;
  }));
}

function collectFormedWords(state, placements, newTiles, sameRow, sameCol) {
  const unique = new Map();
  const add = positions => {
    if (positions.length < 2) return;
    const signature = positions.map(p => key(p.row, p.col)).join("|");
    if (!unique.has(signature)) unique.set(signature, positions);
  };

  const origin = placements[0];
  if (placements.length === 1) {
    add(buildWord(state, origin.row, origin.col, 0, 1, newTiles));
    add(buildWord(state, origin.row, origin.col, 1, 0, newTiles));
  } else if (sameRow) {
    add(buildWord(state, origin.row, origin.col, 0, 1, newTiles));
    placements.forEach(item => add(buildWord(state, item.row, item.col, 1, 0, newTiles)));
  } else if (sameCol) {
    add(buildWord(state, origin.row, origin.col, 1, 0, newTiles));
    placements.forEach(item => add(buildWord(state, item.row, item.col, 0, 1, newTiles)));
  }
  return [...unique.values()];
}

function buildWord(state, originRow, originCol, dr, dc, newTiles) {
  let row = originRow;
  let col = originCol;
  while (inside(row - dr, col - dc) && tileConsideringNew(state, row - dr, col - dc, newTiles)) {
    row -= dr;
    col -= dc;
  }

  const positions = [];
  while (inside(row, col) && tileConsideringNew(state, row, col, newTiles)) {
    positions.push({ row, col });
    row += dr;
    col += dc;
  }
  return positions;
}

function wordText(state, positions, newTiles) {
  return positions.map(position => tileConsideringNew(state, position.row, position.col, newTiles).letter).join("");
}

function scoreWord(state, positions, newTiles) {
  let letterScore = 0;
  let wordMultiplier = 1;

  positions.forEach(position => {
    const item = newTiles.get(key(position.row, position.col));
    const tile = item ? item.tile : state.board[position.row][position.col];
    if (!item) {
      letterScore += tile.points;
      return;
    }

    const premium = PREMIUMS[position.row][position.col];
    let letterMultiplier = 1;
    if (premium === "DL") letterMultiplier = 2;
    if (premium === "TL") letterMultiplier = 3;
    if (premium === "DW" || premium === "CENTER") wordMultiplier *= 2;
    if (premium === "TW") wordMultiplier *= 3;
    letterScore += tile.points * letterMultiplier;
  });
  return letterScore * wordMultiplier;
}

export function commitMove(state, placements, dictionary) {
  const result = analyzeMove(state, placements, dictionary);
  const player = currentPlayer(state);
  placements.forEach(item => { state.board[item.row][item.col] = { ...item.tile }; });

  const usedRackIndexes = [...new Set(placements.map(item => item.rackIndex))].sort((a, b) => b - a);
  usedRackIndexes.forEach(index => player.rack.splice(index, 1));
  player.score += result.score;
  refillRack(state, player);
  state.history.push({ type: "move", player: player.name, words: result.words, score: result.score, bingo: result.bingo });
  state.scorelessTurns = result.score === 0 ? state.scorelessTurns + 1 : 0;
  completeTurn(state, player);
  return result;
}

export function exchangeTiles(state, rackIndexes, random = Math.random) {
  assertRunningState(state);
  if (!Array.isArray(rackIndexes) || !rackIndexes.length) throw new Error("Select at least one rack tile to exchange.");
  if (state.bag.length < RACK_SIZE) throw new Error("Exchanges require at least seven tiles remaining in the bag.");

  const player = currentPlayer(state);
  const unique = new Set(rackIndexes);
  if (unique.size !== rackIndexes.length) throw new Error("Each rack tile can only be exchanged once.");
  const indexes = [...unique].sort((a, b) => a - b);
  indexes.forEach(index => {
    if (!Number.isInteger(index) || index < 0 || index >= player.rack.length) throw new Error("Invalid rack tile selection.");
  });

  // Draw replacements before returning the old tiles, so the same exchange cannot draw them back.
  const replacements = [];
  for (let i = 0; i < indexes.length; i += 1) replacements.push(state.bag.pop());
  const returned = indexes.map(index => player.rack[index].blank ? createTile("?") : { ...player.rack[index] });
  indexes.forEach((index, i) => { player.rack[index] = replacements[i]; });
  state.bag.push(...returned);
  shuffle(state.bag, random);

  state.history.push({ type: "exchange", player: player.name, count: indexes.length });
  state.scorelessTurns += 1;
  completeTurn(state, null);
}

export function passTurn(state) {
  assertRunningState(state);
  const player = currentPlayer(state);
  state.history.push({ type: "pass", player: player.name });
  state.scorelessTurns += 1;
  completeTurn(state, null);
}

function completeTurn(state, playerWhoMoved) {
  if (state.bag.length === 0 && playerWhoMoved && playerWhoMoved.rack.length === 0) {
    finishGame(state, state.currentPlayer);
    return;
  }
  if (state.scorelessTurns >= MAX_SCORELESS_TURNS) {
    finishGame(state, null);
    return;
  }
  state.currentPlayer = (state.currentPlayer + 1) % state.players.length;
}

export function finishGame(state, finisherIndex) {
  let opponentRackPoints = 0;
  state.players.forEach((player, index) => {
    const deduction = player.rack.reduce((sum, tile) => sum + tile.points, 0);
    player.score -= deduction;
    if (finisherIndex !== null && index !== finisherIndex) opponentRackPoints += deduction;
  });
  if (finisherIndex !== null) state.players[finisherIndex].score += opponentRackPoints;
  state.gameOver = true;
}

export function winners(state) {
  const highest = Math.max(...state.players.map(player => player.score));
  return state.players.filter(player => player.score === highest);
}

export function validateSavedState(value) {
  try {
    if (!value || value.version !== SAVE_VERSION || typeof value !== "object") return false;
    if (!Array.isArray(value.board) || value.board.length !== BOARD_SIZE) return false;
    if (!value.board.every(row => Array.isArray(row) && row.length === BOARD_SIZE && row.every(tile => tile === null || validBoardTile(tile)))) return false;
    if (!Array.isArray(value.players) || value.players.length < 2 || value.players.length > 4) return false;
    normalizePlayerNames(value.players.map(player => player?.name));
    if (!value.players.every(player => player && Number.isInteger(player.score) && Array.isArray(player.rack)
      && player.rack.length <= RACK_SIZE && player.rack.every(validRackTile))) return false;
    if (!Array.isArray(value.bag) || !value.bag.every(validRackTile)) return false;
    if (!Number.isInteger(value.currentPlayer) || value.currentPlayer < 0 || value.currentPlayer >= value.players.length) return false;
    if (!Number.isInteger(value.scorelessTurns) || value.scorelessTurns < 0 || value.scorelessTurns > MAX_SCORELESS_TURNS) return false;
    if (typeof value.gameOver !== "boolean" || !Array.isArray(value.history) || value.history.length > 10_000) return false;
    if (!historyIsValid(value)) return false;
    if (!tileConservationIsValid(value)) return false;
    return true;
  } catch {
    return false;
  }
}

function validRackTile(tile) {
  if (!tile || typeof tile !== "object" || typeof tile.letter !== "string" || tile.letter.length !== 1) return false;
  if (tile.blank) return tile.letter === "?" && tile.points === 0;
  const definition = TILE_DEFINITIONS[tile.letter];
  return Boolean(definition) && tile.points === definition.points && tile.blank === false;
}

function validBoardTile(tile) {
  if (!tile || typeof tile !== "object" || typeof tile.letter !== "string" || tile.letter.length !== 1) return false;
  if (tile.blank) return /^[A-Z]$/.test(tile.letter) && tile.points === 0;
  const definition = TILE_DEFINITIONS[tile.letter];
  return Boolean(definition) && tile.points === definition.points && tile.blank === false;
}


function historyIsValid(state) {
  const names = new Set(state.players.map(player => player.name));
  return state.history.every(entry => {
    if (!entry || typeof entry !== "object" || !names.has(entry.player)) return false;
    if (entry.type === "pass") return true;
    if (entry.type === "exchange") {
      return Number.isInteger(entry.count) && entry.count >= 1 && entry.count <= RACK_SIZE;
    }
    if (entry.type === "move") {
      return Number.isInteger(entry.score) && entry.score >= 0
        && typeof entry.bingo === "boolean"
        && Array.isArray(entry.words) && entry.words.length >= 1
        && entry.words.every(word => typeof word === "string" && /^[A-Z]{2,15}$/.test(word));
    }
    return false;
  });
}

function tileConservationIsValid(state) {
  const counts = Object.fromEntries(Object.keys(TILE_DEFINITIONS).map(letter => [letter, 0]));
  const add = tile => {
    const keyLetter = tile.blank ? "?" : tile.letter;
    if (!(keyLetter in counts)) return false;
    counts[keyLetter] += 1;
    return counts[keyLetter] <= TILE_DEFINITIONS[keyLetter].count;
  };

  for (const tile of state.bag) if (!add(tile)) return false;
  for (const player of state.players) for (const tile of player.rack) if (!add(tile)) return false;
  for (const row of state.board) for (const tile of row) if (tile && !add(tile)) return false;
  return Object.entries(TILE_DEFINITIONS).every(([letter, definition]) => counts[letter] === definition.count);
}

function assertRunningState(state) {
  if (!validateRuntimeState(state)) throw new Error("Game state is invalid.");
  if (state.gameOver) throw new Error("The game is already over.");
}

function validateRuntimeState(state) {
  return state && Array.isArray(state.players) && state.players.length >= 2
    && Number.isInteger(state.currentPlayer) && state.currentPlayer >= 0 && state.currentPlayer < state.players.length
    && Array.isArray(state.board) && state.board.length === BOARD_SIZE
    && Array.isArray(state.bag) && Array.isArray(state.history);
}
