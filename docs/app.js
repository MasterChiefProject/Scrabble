"use strict";

const BOARD_SIZE = 15;
const CENTER = 7;

const TILE_DEFINITIONS = {
  A: { count: 9, score: 1 }, B: { count: 2, score: 3 }, C: { count: 2, score: 3 },
  D: { count: 4, score: 2 }, E: { count: 12, score: 1 }, F: { count: 2, score: 4 },
  G: { count: 3, score: 2 }, H: { count: 2, score: 4 }, I: { count: 9, score: 1 },
  J: { count: 1, score: 8 }, K: { count: 1, score: 5 }, L: { count: 4, score: 1 },
  M: { count: 2, score: 3 }, N: { count: 6, score: 1 }, O: { count: 8, score: 1 },
  P: { count: 2, score: 3 }, Q: { count: 1, score: 10 }, R: { count: 6, score: 1 },
  S: { count: 4, score: 1 }, T: { count: 6, score: 1 }, U: { count: 4, score: 1 },
  V: { count: 2, score: 4 }, W: { count: 2, score: 4 }, X: { count: 1, score: 8 },
  Y: { count: 2, score: 4 }, Z: { count: 1, score: 10 }
};

const boardElement = document.getElementById("board");
const rackElement = document.getElementById("rack");
const scoreElement = document.getElementById("score");
const bagCountElement = document.getElementById("bag-count");
const turnElement = document.getElementById("turn");
const wordInput = document.getElementById("word-input");
const rowInput = document.getElementById("row-input");
const colInput = document.getElementById("col-input");
const statusElement = document.getElementById("status");
const historyElement = document.getElementById("history");

let board;
let premiums;
let bag;
let rack;
let totalScore;
let turn;
let history;
let selectedStart = { row: CENTER, col: 5 };

function createPremiumBoard() {
  const grid = Array.from({ length: BOARD_SIZE }, () => Array(BOARD_SIZE).fill(""));
  const set = (row, col, type) => { grid[row][col] = type; };

  set(7, 7, "STAR");

  [[6, 6], [6, 8], [8, 6], [8, 8]].forEach(([r, c]) => set(r, c, "DLS"));
  [[5, 5], [5, 9], [9, 5], [9, 9]].forEach(([r, c]) => set(r, c, "TLS"));
  [
    [4, 4], [4, 10], [10, 4], [10, 10],
    [3, 3], [3, 11], [11, 3], [11, 11],
    [2, 2], [2, 12], [12, 2], [12, 12],
    [1, 1], [1, 13], [13, 1], [13, 13]
  ].forEach(([r, c]) => set(r, c, "DWS"));

  [[0, 0], [14, 0], [0, 14], [14, 14], [7, 0], [7, 14], [0, 7], [14, 7]]
    .forEach(([r, c]) => set(r, c, "TWS"));

  [
    [3, 0], [0, 3], [11, 0], [14, 3], [3, 14], [0, 11], [11, 14], [14, 11],
    [6, 2], [8, 2], [7, 3], [6, 12], [8, 12], [7, 11],
    [2, 6], [2, 8], [3, 7], [12, 6], [12, 8], [11, 7]
  ].forEach(([r, c]) => set(r, c, "DLS"));

  [
    [5, 1], [9, 1], [5, 13], [9, 13],
    [1, 5], [1, 9], [13, 5], [13, 9]
  ].forEach(([r, c]) => set(r, c, "TLS"));

  return grid;
}

function createBag() {
  const nextBag = {};
  Object.entries(TILE_DEFINITIONS).forEach(([letter, definition]) => {
    nextBag[letter] = definition.count;
  });
  return nextBag;
}

function bagSize() {
  return Object.values(bag).reduce((sum, count) => sum + count, 0);
}

function randomBagLetter() {
  const remaining = bagSize();
  if (remaining === 0) return null;

  let target = Math.floor(Math.random() * remaining);
  for (const [letter, count] of Object.entries(bag)) {
    if (target < count) {
      bag[letter] -= 1;
      return letter;
    }
    target -= count;
  }
  return null;
}

function refillRack() {
  while (rack.length < 7 && bagSize() > 0) {
    const letter = randomBagLetter();
    if (letter) rack.push(letter);
  }
}

function resetGame() {
  board = Array.from({ length: BOARD_SIZE }, () => Array(BOARD_SIZE).fill(null));
  premiums = createPremiumBoard();
  bag = createBag();
  rack = [];
  totalScore = 0;
  turn = 1;
  history = [];
  selectedStart = { row: CENTER, col: 5 };
  rowInput.value = selectedStart.row + 1;
  colInput.value = selectedStart.col + 1;
  wordInput.value = "";
  refillRack();
  setStatus("Place the first word through the center star.", "info");
  renderAll();
}

function premiumLabel(type) {
  return {
    TWS: "TW",
    DWS: "DW",
    TLS: "TL",
    DLS: "DL",
    STAR: "★"
  }[type] || "";
}

function premiumClass(type) {
  return {
    TWS: "premium-tws",
    DWS: "premium-dws",
    TLS: "premium-tls",
    DLS: "premium-dls",
    STAR: "premium-star"
  }[type] || "";
}

function tileMarkup(letter, className = "board-tile") {
  const score = TILE_DEFINITIONS[letter].score;
  return `<span class="${className}"><span>${letter}</span><span class="tile-score">${score}</span></span>`;
}

function renderBoard() {
  boardElement.innerHTML = "";

  for (let row = 0; row < BOARD_SIZE; row += 1) {
    for (let col = 0; col < BOARD_SIZE; col += 1) {
      const cell = document.createElement("button");
      const premium = premiums[row][col];
      const tile = board[row][col];
      cell.type = "button";
      cell.className = `cell ${premiumClass(premium)}`.trim();
      cell.dataset.row = String(row);
      cell.dataset.col = String(col);
      cell.setAttribute("role", "gridcell");
      cell.setAttribute("aria-label", `Row ${row + 1}, column ${col + 1}${tile ? `, ${tile.letter}` : premium ? `, ${premiumLabel(premium)}` : ""}`);

      if (selectedStart.row === row && selectedStart.col === col) {
        cell.classList.add("selected-start");
      }

      if (tile) {
        cell.innerHTML = tileMarkup(tile.letter);
      } else if (premium) {
        cell.innerHTML = `<span class="premium-label">${premiumLabel(premium)}</span>`;
      }

      cell.addEventListener("click", () => {
        selectedStart = { row, col };
        rowInput.value = row + 1;
        colInput.value = col + 1;
        renderBoard();
        wordInput.focus();
      });

      boardElement.appendChild(cell);
    }
  }
}

function renderRack() {
  rackElement.innerHTML = "";
  rack.forEach((letter) => {
    const tile = document.createElement("div");
    tile.className = "rack-tile";
    tile.innerHTML = `<span>${letter}</span><span class="tile-score">${TILE_DEFINITIONS[letter].score}</span>`;
    rackElement.appendChild(tile);
  });
}

function renderHistory() {
  historyElement.innerHTML = "";
  if (history.length === 0) {
    const item = document.createElement("li");
    item.textContent = "No words played yet.";
    historyElement.appendChild(item);
    return;
  }

  history.slice().reverse().slice(0, 8).forEach((entry) => {
    const item = document.createElement("li");
    item.innerHTML = `<strong>${entry.word}</strong> · +${entry.score} points · ${entry.row},${entry.col} ${entry.orientation}`;
    historyElement.appendChild(item);
  });
}

function renderStats() {
  scoreElement.textContent = String(totalScore);
  bagCountElement.textContent = String(bagSize());
  turnElement.textContent = String(turn);
}

function renderAll() {
  renderBoard();
  renderRack();
  renderStats();
  renderHistory();
}

function setStatus(message, type = "info") {
  statusElement.textContent = message;
  statusElement.className = `status status-${type}`;
}

function normalizeWord(value) {
  return value.toUpperCase().replace(/[^A-Z]/g, "");
}

function isBoardEmpty() {
  return board.every((row) => row.every((cell) => cell === null));
}

function isInside(row, col) {
  return row >= 0 && row < BOARD_SIZE && col >= 0 && col < BOARD_SIZE;
}

function adjacentExisting(row, col) {
  const neighbors = [[-1, 0], [1, 0], [0, -1], [0, 1]];
  return neighbors.some(([dr, dc]) => {
    const nr = row + dr;
    const nc = col + dc;
    return isInside(nr, nc) && board[nr][nc] !== null;
  });
}

function rackAssignment(word, row, col, vertical) {
  const available = rack.map((letter, index) => ({ letter, index, used: false }));
  const cells = [];
  const usedRackIndices = [];

  for (let i = 0; i < word.length; i += 1) {
    const targetRow = row + (vertical ? i : 0);
    const targetCol = col + (vertical ? 0 : i);
    if (!isInside(targetRow, targetCol)) {
      return { error: "The word extends beyond the 15×15 board." };
    }

    const letter = word[i];
    const existing = board[targetRow][targetCol];
    if (existing) {
      if (existing.letter !== letter) {
        return { error: `Row ${targetRow + 1}, column ${targetCol + 1} already contains ${existing.letter}.` };
      }
      cells.push({ row: targetRow, col: targetCol, letter, isNew: false });
      continue;
    }

    const rackEntry = available.find((entry) => !entry.used && entry.letter === letter);
    if (!rackEntry) {
      return { error: `Your rack does not contain enough ${letter} tiles for ${word}.` };
    }
    rackEntry.used = true;
    usedRackIndices.push(rackEntry.index);
    cells.push({ row: targetRow, col: targetCol, letter, isNew: true });
  }

  return { cells, usedRackIndices };
}

function ensureWordBoundsAreClosed(cells, vertical) {
  const first = cells[0];
  const last = cells[cells.length - 1];
  const before = { row: first.row - (vertical ? 1 : 0), col: first.col - (vertical ? 0 : 1) };
  const after = { row: last.row + (vertical ? 1 : 0), col: last.col + (vertical ? 0 : 1) };

  if (isInside(before.row, before.col) && board[before.row][before.col]) {
    return "There is already a letter immediately before this word. Include it in the word you type.";
  }
  if (isInside(after.row, after.col) && board[after.row][after.col]) {
    return "There is already a letter immediately after this word. Include it in the word you type.";
  }
  return null;
}

function findCrossWord(cell, vertical) {
  const dr = vertical ? 0 : 1;
  const dc = vertical ? 1 : 0;
  let row = cell.row;
  let col = cell.col;

  while (isInside(row - dr, col - dc) && board[row - dr][col - dc]) {
    row -= dr;
    col -= dc;
  }

  const cells = [];
  while (isInside(row, col)) {
    if (row === cell.row && col === cell.col) {
      cells.push(cell);
    } else if (board[row][col]) {
      cells.push({ row, col, letter: board[row][col].letter, isNew: false });
    } else {
      break;
    }
    row += dr;
    col += dc;
  }

  return cells.length > 1 ? cells : null;
}

function scoreWord(cells) {
  let sum = 0;
  let wordMultiplier = 1;

  cells.forEach((cell) => {
    const baseScore = TILE_DEFINITIONS[cell.letter].score;
    let letterScore = baseScore;

    if (cell.isNew) {
      switch (premiums[cell.row][cell.col]) {
        case "DLS": letterScore *= 2; break;
        case "TLS": letterScore *= 3; break;
        case "STAR":
        case "DWS": wordMultiplier *= 2; break;
        case "TWS": wordMultiplier *= 3; break;
        default: break;
      }
    }

    sum += letterScore;
  });

  return sum * wordMultiplier;
}

function validatePlacement(word, row, col, vertical) {
  if (!word) return { error: "Enter a word using A to Z." };
  if (word.length > BOARD_SIZE) return { error: "A word cannot exceed 15 letters." };
  if (!Number.isInteger(row) || !Number.isInteger(col) || !isInside(row, col)) {
    return { error: "Choose a starting row and column between 1 and 15." };
  }

  const assignment = rackAssignment(word, row, col, vertical);
  if (assignment.error) return assignment;

  const { cells, usedRackIndices } = assignment;
  const newCells = cells.filter((cell) => cell.isNew);
  if (newCells.length === 0) return { error: "The move must place at least one new tile." };

  const closedError = ensureWordBoundsAreClosed(cells, vertical);
  if (closedError) return { error: closedError };

  if (isBoardEmpty()) {
    const crossesCenter = cells.some((cell) => cell.row === CENTER && cell.col === CENTER);
    if (!crossesCenter) return { error: "The first word must pass through the center star." };
  } else {
    const overlapsExisting = cells.some((cell) => !cell.isNew);
    const touchesExisting = newCells.some((cell) => adjacentExisting(cell.row, cell.col));
    if (!overlapsExisting && !touchesExisting) {
      return { error: "The word must connect to at least one tile already on the board." };
    }
  }

  const crossWords = newCells
    .map((cell) => findCrossWord(cell, vertical))
    .filter(Boolean);

  let score = scoreWord(cells);
  crossWords.forEach((crossWord) => { score += scoreWord(crossWord); });
  if (newCells.length === 7) score += 50;

  return { cells, newCells, usedRackIndices, crossWords, score };
}

function commitPlacement(word, row, col, vertical, result) {
  result.newCells.forEach((cell) => {
    board[cell.row][cell.col] = { letter: cell.letter };
  });

  const sortedIndices = [...result.usedRackIndices].sort((a, b) => b - a);
  sortedIndices.forEach((index) => rack.splice(index, 1));
  refillRack();

  totalScore += result.score;
  history.push({
    word,
    score: result.score,
    row: row + 1,
    col: col + 1,
    orientation: vertical ? "vertical" : "horizontal"
  });
  turn += 1;

  wordInput.value = "";
  setStatus(`${word} placed for ${result.score} points.${result.crossWords.length ? ` ${result.crossWords.length} cross-word${result.crossWords.length === 1 ? "" : "s"} scored too.` : ""}`, "success");
  renderAll();
  wordInput.focus();
}

function playWord() {
  const word = normalizeWord(wordInput.value);
  wordInput.value = word;
  const row = Number.parseInt(rowInput.value, 10) - 1;
  const col = Number.parseInt(colInput.value, 10) - 1;
  const orientation = document.querySelector('input[name="orientation"]:checked').value;
  const vertical = orientation === "vertical";

  selectedStart = { row, col };
  const result = validatePlacement(word, row, col, vertical);
  if (result.error) {
    setStatus(result.error, "error");
    renderBoard();
    return;
  }

  commitPlacement(word, row, col, vertical, result);
}

function shuffleRack() {
  for (let i = rack.length - 1; i > 0; i -= 1) {
    const j = Math.floor(Math.random() * (i + 1));
    [rack[i], rack[j]] = [rack[j], rack[i]];
  }
  renderRack();
}

function exchangeRack() {
  if (bagSize() < rack.length) {
    setStatus("Not enough tiles remain in the bag to exchange the entire rack.", "warning");
    return;
  }

  rack.forEach((letter) => { bag[letter] += 1; });
  rack = [];
  refillRack();
  turn += 1;
  history.push({ word: "Rack exchange", score: 0, row: "-", col: "-", orientation: "" });
  setStatus("Rack exchanged. No points were awarded.", "info");
  renderAll();
}

wordInput.addEventListener("input", () => {
  const normalized = normalizeWord(wordInput.value);
  if (wordInput.value !== normalized) wordInput.value = normalized;
});

wordInput.addEventListener("keydown", (event) => {
  if (event.key === "Enter") playWord();
});

rowInput.addEventListener("change", () => {
  selectedStart.row = Math.max(0, Math.min(14, Number.parseInt(rowInput.value, 10) - 1 || 0));
  rowInput.value = selectedStart.row + 1;
  renderBoard();
});

colInput.addEventListener("change", () => {
  selectedStart.col = Math.max(0, Math.min(14, Number.parseInt(colInput.value, 10) - 1 || 0));
  colInput.value = selectedStart.col + 1;
  renderBoard();
});

document.getElementById("place-word").addEventListener("click", playWord);
document.getElementById("shuffle-rack").addEventListener("click", shuffleRack);
document.getElementById("exchange-rack").addEventListener("click", exchangeRack);
document.getElementById("new-game").addEventListener("click", () => {
  if (window.confirm("Start a new game and clear the current board?")) resetGame();
});

resetGame();
