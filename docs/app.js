"use strict";

const BOARD_SIZE = 15;
const CENTER = 7;
const RACK_SIZE = 7;
const MAX_SCORELESS_TURNS = 6;
const SAVE_KEY = "masterchief-scrabble-save-v2";
const THEME_KEY = "masterchief-scrabble-theme";

const TILE_DEFINITIONS = {
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
};

const boardElement = document.getElementById("board");
const scoreboardElement = document.getElementById("scoreboard");
const rackElement = document.getElementById("rack");
const bagCountElement = document.getElementById("bag-count");
const scorelessElement = document.getElementById("scoreless-count");
const turnTitleElement = document.getElementById("turn-title");
const rackPlayerElement = document.getElementById("rack-player");
const modeLabelElement = document.getElementById("mode-label");
const rackHelpElement = document.getElementById("rack-help");
const statusElement = document.getElementById("status");
const historyElement = document.getElementById("history");
const dictionaryStateElement = document.getElementById("dictionary-state");
const commitButton = document.getElementById("commit-move");
const recallButton = document.getElementById("recall-tiles");
const exchangeButton = document.getElementById("exchange-tiles");
const passButton = document.getElementById("pass-turn");
const newGameButton = document.getElementById("new-game");
const saveButton = document.getElementById("save-game");
const loadButton = document.getElementById("load-game");
const themeButton = document.getElementById("theme-toggle");

let dictionary = new Set();
let dictionaryReady = false;
let state = null;
let pending = [];
let activeRackIndex = null;
let exchangeMode = false;
let exchangeSelection = new Set();

function createPremiumBoard() {
  const premiums = Array.from({ length: BOARD_SIZE }, () => Array(BOARD_SIZE).fill(""));
  const mark = (type, coordinates) => coordinates.forEach(([row, col]) => { premiums[row][col] = type; });

  mark("TW", [[0,0],[0,7],[0,14],[7,0],[7,14],[14,0],[14,7],[14,14]]);
  mark("DW", [[1,1],[1,13],[2,2],[2,12],[3,3],[3,11],[4,4],[4,10],[10,4],[10,10],[11,3],[11,11],[12,2],[12,12],[13,1],[13,13]]);
  premiums[7][7] = "CENTER";
  mark("TL", [[1,5],[1,9],[5,1],[5,5],[5,9],[5,13],[9,1],[9,5],[9,9],[9,13],[13,5],[13,9]]);
  mark("DL", [[0,3],[0,11],[2,6],[2,8],[3,0],[3,7],[3,14],[6,2],[6,6],[6,8],[6,12],[7,3],[7,11],[8,2],[8,6],[8,8],[8,12],[11,0],[11,7],[11,14],[12,6],[12,8],[14,3],[14,11]]);
  return premiums;
}

const PREMIUMS = createPremiumBoard();

function createTile(letter) {
  const definition = TILE_DEFINITIONS[letter];
  return { letter, points: definition.points, blank: letter === "?" };
}

function createBag() {
  const bag = [];
  Object.entries(TILE_DEFINITIONS).forEach(([letter, definition]) => {
    for (let i = 0; i < definition.count; i += 1) bag.push(createTile(letter));
  });
  shuffle(bag);
  return bag;
}

function shuffle(values) {
  for (let i = values.length - 1; i > 0; i -= 1) {
    const j = Math.floor(Math.random() * (i + 1));
    [values[i], values[j]] = [values[j], values[i]];
  }
}

function drawTile() {
  return state.bag.length ? state.bag.pop() : null;
}

function refillRack(player) {
  while (player.rack.length < RACK_SIZE && state.bag.length) {
    player.rack.push(drawTile());
  }
}

function newGame(confirmExisting = false) {
  if (confirmExisting && state && !window.confirm("Start a new game and replace the current board?")) return;

  state = {
    version: 2,
    board: Array.from({ length: BOARD_SIZE }, () => Array(BOARD_SIZE).fill(null)),
    bag: createBag(),
    players: [
      { name: "Player 1", score: 0, rack: [] },
      { name: "Player 2", score: 0, rack: [] }
    ],
    currentPlayer: 0,
    scorelessTurns: 0,
    gameOver: false,
    history: []
  };
  state.players.forEach(refillRack);
  clearTransientState();
  setStatus("Player 1 starts. Build the first word through the center star.");
  autoSave();
  renderAll();
}

function clearTransientState() {
  pending = [];
  activeRackIndex = null;
  exchangeMode = false;
  exchangeSelection = new Set();
}

function currentPlayer() {
  return state.players[state.currentPlayer];
}

function key(row, col) {
  return `${row},${col}`;
}

function inside(row, col) {
  return row >= 0 && row < BOARD_SIZE && col >= 0 && col < BOARD_SIZE;
}

function boardIsEmpty() {
  return state.board.every(row => row.every(tile => tile === null));
}

function pendingAt(row, col) {
  return pending.find(item => item.row === row && item.col === col) || null;
}

function tileConsideringPending(row, col, pendingMap) {
  const next = pendingMap.get(key(row, col));
  return next ? next.tile : state.board[row][col];
}

function premiumClass(type) {
  return {
    DL: "premium-dl",
    TL: "premium-tl",
    DW: "premium-dw",
    TW: "premium-tw",
    CENTER: "premium-center"
  }[type] || "";
}

function premiumLabel(type) {
  return { DL: "DL", TL: "TL", DW: "DW", TW: "TW", CENTER: "★" }[type] || "";
}

function tileHtml(tile, pendingTile = false) {
  const letter = tile.letter === "?" ? "_" : tile.letter;
  const blankMark = tile.blank ? '<span class="blank-mark">BLANK</span>' : "";
  return `<span class="board-tile${pendingTile ? " pending" : ""}"><span>${letter}</span><span class="tile-points">${tile.points}</span>${blankMark}</span>`;
}

function renderBoard() {
  boardElement.innerHTML = "";
  for (let row = 0; row < BOARD_SIZE; row += 1) {
    for (let col = 0; col < BOARD_SIZE; col += 1) {
      const button = document.createElement("button");
      button.type = "button";
      const premium = PREMIUMS[row][col];
      const fixedTile = state.board[row][col];
      const temp = pendingAt(row, col);
      button.className = `cell ${premiumClass(premium)}`.trim();
      button.dataset.row = row;
      button.dataset.col = col;
      button.setAttribute("role", "gridcell");

      if (temp) {
        button.innerHTML = tileHtml(temp.tile, true);
        button.setAttribute("aria-label", `Pending ${temp.tile.letter} at row ${row + 1}, column ${col + 1}`);
      } else if (fixedTile) {
        button.innerHTML = tileHtml(fixedTile, false);
        button.setAttribute("aria-label", `${fixedTile.letter} at row ${row + 1}, column ${col + 1}`);
      } else {
        button.innerHTML = premium ? `<span class="premium-label">${premiumLabel(premium)}</span>` : "";
        button.setAttribute("aria-label", `Empty row ${row + 1}, column ${col + 1}`);
      }

      button.addEventListener("click", () => handleBoardClick(row, col));
      boardElement.appendChild(button);
    }
  }
}

function handleBoardClick(row, col) {
  if (state.gameOver) return;

  const tempIndex = pending.findIndex(item => item.row === row && item.col === col);
  if (tempIndex >= 0) {
    const [removed] = pending.splice(tempIndex, 1);
    activeRackIndex = removed.rackIndex;
    setStatus(`${removed.tile.letter} returned to the rack.`);
    renderAll();
    return;
  }

  if (state.board[row][col]) {
    setStatus("That square is already occupied.", "warning");
    return;
  }
  if (exchangeMode) {
    setStatus("Finish or cancel exchange mode before placing tiles.", "warning");
    return;
  }
  if (activeRackIndex === null) {
    setStatus("Select a rack tile first.", "warning");
    return;
  }
  if (pending.some(item => item.rackIndex === activeRackIndex)) {
    setStatus("That rack tile is already pending on the board.", "warning");
    return;
  }

  const rackTile = currentPlayer().rack[activeRackIndex];
  if (!rackTile) {
    activeRackIndex = null;
    renderAll();
    return;
  }

  let placedTile = { ...rackTile };
  if (rackTile.blank) {
    const assigned = window.prompt("Assign the blank tile a letter from A to Z:", "A");
    if (assigned === null) return;
    const normalized = assigned.trim().toUpperCase();
    if (!/^[A-Z]$/.test(normalized)) {
      setStatus("A blank tile must be assigned exactly one letter from A to Z.", "error");
      return;
    }
    placedTile = { letter: normalized, points: 0, blank: true };
  }

  pending.push({ row, col, rackIndex: activeRackIndex, tile: placedTile });
  activeRackIndex = null;
  setStatus("Tile staged. Add more tiles or commit the move.");
  renderAll();
}

function renderRack() {
  rackElement.innerHTML = "";
  const player = currentPlayer();
  player.rack.forEach((tile, index) => {
    const button = document.createElement("button");
    button.type = "button";
    button.className = "rack-tile";
    const used = pending.some(item => item.rackIndex === index);
    if (used) button.classList.add("used");
    if (!exchangeMode && activeRackIndex === index) button.classList.add("active");
    if (exchangeMode && exchangeSelection.has(index)) button.classList.add("exchange");
    button.disabled = used;
    const letter = tile.letter === "?" ? "_" : tile.letter;
    button.innerHTML = `<span>${letter}</span><span class="tile-points">${tile.points}</span>${tile.blank ? '<span class="blank-mark">BLANK</span>' : ""}`;
    button.setAttribute("aria-label", tile.blank ? "Blank tile" : `${tile.letter}, ${tile.points} points`);

    button.addEventListener("click", () => {
      if (exchangeMode) {
        if (exchangeSelection.has(index)) exchangeSelection.delete(index);
        else exchangeSelection.add(index);
        renderRack();
        updateActionState();
      } else {
        activeRackIndex = activeRackIndex === index ? null : index;
        renderRack();
      }
    });
    rackElement.appendChild(button);
  });
}

function renderScoreboard() {
  scoreboardElement.innerHTML = "";
  state.players.forEach((player, index) => {
    const row = document.createElement("div");
    row.className = `player-score${index === state.currentPlayer && !state.gameOver ? " current" : ""}`;
    row.innerHTML = `<span>${escapeHtml(player.name)}</span><strong>${player.score}</strong>`;
    scoreboardElement.appendChild(row);
  });
}

function renderHistory() {
  historyElement.innerHTML = "";
  if (!state.history.length) {
    const li = document.createElement("li");
    li.textContent = "No completed turns yet.";
    historyElement.appendChild(li);
    return;
  }

  state.history.slice().reverse().slice(0, 12).forEach(entry => {
    const li = document.createElement("li");
    if (entry.type === "move") {
      li.innerHTML = `<strong>${escapeHtml(entry.player)}</strong> ${entry.words.map(escapeHtml).join(", ")} <span class="score">+${entry.score}</span>`;
    } else if (entry.type === "exchange") {
      li.innerHTML = `<strong>${escapeHtml(entry.player)}</strong> exchanged ${entry.count} tile${entry.count === 1 ? "" : "s"}.`;
    } else {
      li.innerHTML = `<strong>${escapeHtml(entry.player)}</strong> passed.`;
    }
    historyElement.appendChild(li);
  });
}

function renderStats() {
  const player = currentPlayer();
  bagCountElement.textContent = state.bag.length;
  scorelessElement.textContent = `${state.scorelessTurns} / ${MAX_SCORELESS_TURNS}`;
  turnTitleElement.textContent = state.gameOver ? "Game over" : `${player.name}'s turn`;
  rackPlayerElement.textContent = player.name;

  if (exchangeMode) {
    modeLabelElement.textContent = "Exchange mode";
    rackHelpElement.textContent = "Select one or more rack tiles, then click Confirm exchange. Exchanges require at least seven tiles in the bag.";
  } else {
    modeLabelElement.textContent = "Placement mode";
    rackHelpElement.textContent = "Select a rack tile, then click an empty board square. Click a pending board tile to return it.";
  }
}

function updateActionState() {
  commitButton.disabled = state.gameOver || !pending.length || !dictionaryReady || exchangeMode;
  recallButton.disabled = state.gameOver || (!pending.length && !exchangeMode);
  passButton.disabled = state.gameOver || pending.length > 0 || exchangeMode;
  exchangeButton.disabled = state.gameOver || pending.length > 0 || state.bag.length < RACK_SIZE;
  exchangeButton.textContent = exchangeMode ? "Confirm exchange" : "Exchange tiles";
}

function renderAll() {
  renderBoard();
  renderRack();
  renderScoreboard();
  renderHistory();
  renderStats();
  updateActionState();
}

function analyzeMove(placements) {
  if (!placements.length) throw new Error("Place at least one tile.");
  if (placements.length > RACK_SIZE) throw new Error("A move cannot place more than seven rack tiles.");

  const newTiles = new Map();
  placements.forEach(item => {
    if (!inside(item.row, item.col)) throw new Error("A tile is outside the board.");
    if (state.board[item.row][item.col]) throw new Error("A new tile cannot overwrite an existing tile.");
    const positionKey = key(item.row, item.col);
    if (newTiles.has(positionKey)) throw new Error("Two tiles cannot occupy the same square.");
    newTiles.set(positionKey, item);
  });

  const sameRow = new Set(placements.map(item => item.row)).size === 1;
  const sameCol = new Set(placements.map(item => item.col)).size === 1;
  if (placements.length > 1 && !sameRow && !sameCol) {
    throw new Error("All newly placed tiles must be in one row or one column.");
  }

  if (boardIsEmpty()) {
    if (!newTiles.has(key(CENTER, CENTER))) throw new Error("The first move must cover the center star.");
  } else if (!touchesExisting(placements)) {
    throw new Error("The move must connect to at least one existing tile.");
  }

  if (placements.length > 1) ensureContiguous(placements, newTiles, sameRow);

  const words = collectFormedWords(placements, newTiles, sameRow, sameCol);
  if (!words.length) throw new Error("The move must form at least one word of two or more letters.");

  let score = 0;
  const wordTexts = [];
  for (const formed of words) {
    const text = wordText(formed, newTiles);
    if (!dictionary.has(text)) throw new Error(`"${text}" is not in the included dictionary.`);
    wordTexts.push(text);
    score += scoreWord(formed, newTiles);
  }

  const bingo = placements.length === RACK_SIZE;
  if (bingo) score += 50;
  return { score, words: wordTexts, bingo };
}

function ensureContiguous(placements, newTiles, horizontal) {
  if (horizontal) {
    const row = placements[0].row;
    const cols = placements.map(item => item.col);
    for (let col = Math.min(...cols); col <= Math.max(...cols); col += 1) {
      if (!tileConsideringPending(row, col, newTiles)) throw new Error("Placed tiles cannot leave an empty gap.");
    }
  } else {
    const col = placements[0].col;
    const rows = placements.map(item => item.row);
    for (let row = Math.min(...rows); row <= Math.max(...rows); row += 1) {
      if (!tileConsideringPending(row, col, newTiles)) throw new Error("Placed tiles cannot leave an empty gap.");
    }
  }
}

function touchesExisting(placements) {
  const directions = [[-1,0],[1,0],[0,-1],[0,1]];
  return placements.some(item => directions.some(([dr, dc]) => {
    const row = item.row + dr;
    const col = item.col + dc;
    return inside(row, col) && state.board[row][col] !== null;
  }));
}

function collectFormedWords(placements, newTiles, sameRow, sameCol) {
  const unique = new Map();
  const add = positions => {
    if (positions.length < 2) return;
    const signature = positions.map(p => key(p.row, p.col)).join("|");
    if (!unique.has(signature)) unique.set(signature, positions);
  };

  const origin = placements[0];
  if (placements.length === 1) {
    add(buildWord(origin.row, origin.col, 0, 1, newTiles));
    add(buildWord(origin.row, origin.col, 1, 0, newTiles));
  } else if (sameRow) {
    add(buildWord(origin.row, origin.col, 0, 1, newTiles));
    placements.forEach(item => add(buildWord(item.row, item.col, 1, 0, newTiles)));
  } else if (sameCol) {
    add(buildWord(origin.row, origin.col, 1, 0, newTiles));
    placements.forEach(item => add(buildWord(item.row, item.col, 0, 1, newTiles)));
  }
  return [...unique.values()];
}

function buildWord(originRow, originCol, dr, dc, newTiles) {
  let row = originRow;
  let col = originCol;
  while (inside(row - dr, col - dc) && tileConsideringPending(row - dr, col - dc, newTiles)) {
    row -= dr;
    col -= dc;
  }

  const positions = [];
  while (inside(row, col) && tileConsideringPending(row, col, newTiles)) {
    positions.push({ row, col });
    row += dr;
    col += dc;
  }
  return positions;
}

function wordText(positions, newTiles) {
  return positions.map(position => tileConsideringPending(position.row, position.col, newTiles).letter).join("");
}

function scoreWord(positions, newTiles) {
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

function commitMove() {
  if (!dictionaryReady) {
    setStatus("Dictionary is still loading.", "warning");
    return;
  }

  let result;
  try {
    result = analyzeMove(pending);
  } catch (error) {
    setStatus(error.message, "error");
    return;
  }

  const player = currentPlayer();
  pending.forEach(item => { state.board[item.row][item.col] = { ...item.tile }; });
  const usedRackIndexes = [...new Set(pending.map(item => item.rackIndex))].sort((a, b) => b - a);
  usedRackIndexes.forEach(index => player.rack.splice(index, 1));
  player.score += result.score;
  refillRack(player);

  state.history.push({ type: "move", player: player.name, words: result.words, score: result.score, bingo: result.bingo });
  state.scorelessTurns = result.score === 0 ? state.scorelessTurns + 1 : 0;

  const summary = `${result.words.join(", ")} scored ${result.score} point${result.score === 1 ? "" : "s"}${result.bingo ? " including a 50-point bingo bonus" : ""}.`;
  clearTransientState();
  completeTurn(player);
  if (!state.gameOver) setStatus(summary, "success");
  autoSave();
  renderAll();
}

function startOrConfirmExchange() {
  if (!exchangeMode) {
    exchangeMode = true;
    exchangeSelection.clear();
    activeRackIndex = null;
    setStatus("Exchange mode active. Select rack tiles, then click Confirm exchange.");
    renderAll();
    return;
  }

  if (!exchangeSelection.size) {
    setStatus("Select at least one rack tile to exchange.", "warning");
    return;
  }
  if (state.bag.length < RACK_SIZE) {
    setStatus("Exchanges are disabled when fewer than seven tiles remain in the bag.", "error");
    return;
  }

  const player = currentPlayer();
  const indexes = [...exchangeSelection].sort((a, b) => a - b);
  const returned = indexes.map(index => {
    const tile = player.rack[index];
    return tile.blank ? createTile("?") : { ...tile };
  });
  state.bag.push(...returned);
  shuffle(state.bag);
  indexes.forEach(index => { player.rack[index] = drawTile(); });

  state.history.push({ type: "exchange", player: player.name, count: indexes.length });
  state.scorelessTurns += 1;
  clearTransientState();
  completeTurn(null);
  if (!state.gameOver) setStatus(`${player.name} exchanged ${indexes.length} tile${indexes.length === 1 ? "" : "s"}.`);
  autoSave();
  renderAll();
}

function passTurn() {
  if (pending.length || exchangeMode) {
    setStatus("Recall pending tiles or leave exchange mode before passing.", "warning");
    return;
  }
  const player = currentPlayer();
  state.history.push({ type: "pass", player: player.name });
  state.scorelessTurns += 1;
  completeTurn(null);
  if (!state.gameOver) setStatus(`${player.name} passed.`);
  autoSave();
  renderAll();
}

function completeTurn(playerWhoMoved) {
  if (state.bag.length === 0 && playerWhoMoved && playerWhoMoved.rack.length === 0) {
    finishGame(state.currentPlayer);
    return;
  }
  if (state.scorelessTurns >= MAX_SCORELESS_TURNS) {
    finishGame(null);
    return;
  }
  state.currentPlayer = (state.currentPlayer + 1) % state.players.length;
}

function finishGame(finisherIndex) {
  let opponentRackPoints = 0;
  state.players.forEach((player, index) => {
    const deduction = player.rack.reduce((sum, tile) => sum + tile.points, 0);
    player.score -= deduction;
    if (finisherIndex !== null && index !== finisherIndex) opponentRackPoints += deduction;
  });
  if (finisherIndex !== null) state.players[finisherIndex].score += opponentRackPoints;
  state.gameOver = true;
  const sorted = [...state.players].sort((a, b) => b.score - a.score);
  setStatus(`Game over. ${sorted[0].name} wins with ${sorted[0].score} points.`, "success");
}

function recallTiles() {
  if (exchangeMode) {
    exchangeMode = false;
    exchangeSelection.clear();
    setStatus("Exchange mode cancelled.");
  } else if (pending.length) {
    pending = [];
    activeRackIndex = null;
    setStatus("Pending tiles returned to the rack.");
  }
  renderAll();
}

function saveGame() {
  localStorage.setItem(SAVE_KEY, JSON.stringify(state));
  setStatus("Game saved in this browser.", "success");
}

function autoSave() {
  localStorage.setItem(SAVE_KEY, JSON.stringify(state));
}

function loadGame() {
  const raw = localStorage.getItem(SAVE_KEY);
  if (!raw) {
    setStatus("No saved game was found in this browser.", "warning");
    return;
  }

  try {
    const parsed = JSON.parse(raw);
    if (!validSavedState(parsed)) throw new Error("Save data has an unsupported format.");
    state = parsed;
    clearTransientState();
    setStatus("Saved game loaded.", "success");
    renderAll();
  } catch (error) {
    setStatus(`Could not load the saved game: ${error.message}`, "error");
  }
}

function validSavedState(value) {
  return value && value.version === 2
    && Array.isArray(value.board) && value.board.length === BOARD_SIZE
    && Array.isArray(value.players) && value.players.length >= 2
    && Array.isArray(value.bag) && Array.isArray(value.history)
    && Number.isInteger(value.currentPlayer);
}

function setStatus(message, type = "info") {
  statusElement.textContent = message;
  statusElement.className = `status${type === "info" ? "" : ` ${type}`}`;
}

function escapeHtml(value) {
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}

async function loadDictionary() {
  dictionaryStateElement.textContent = "Loading dictionary...";
  dictionaryStateElement.className = "dictionary-state";
  try {
    const response = await fetch("words.txt", { cache: "force-cache" });
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    const text = await response.text();
    dictionary = new Set(text.split(/\r?\n/).map(word => word.trim().toUpperCase()).filter(Boolean));
    dictionaryReady = true;
    dictionaryStateElement.textContent = `${dictionary.size.toLocaleString()} offline words loaded`;
    dictionaryStateElement.classList.add("ready");
    setStatus("Dictionary ready. Select a tile and start playing.");
  } catch (error) {
    dictionaryReady = false;
    dictionaryStateElement.textContent = "Dictionary failed to load";
    dictionaryStateElement.classList.add("error");
    setStatus(`Dictionary could not be loaded: ${error.message}. Serve the site through GitHub Pages or another HTTP server.`, "error");
  }
  updateActionState();
}

function applyTheme(theme) {
  const normalized = theme === "light" ? "light" : "dark";
  document.documentElement.dataset.theme = normalized;
  localStorage.setItem(THEME_KEY, normalized);
  themeButton.textContent = normalized === "dark" ? "Light mode" : "Dark mode";
}

function initializeTheme() {
  applyTheme(localStorage.getItem(THEME_KEY) || "dark");
}

function toggleTheme() {
  applyTheme(document.documentElement.dataset.theme === "dark" ? "light" : "dark");
}

commitButton.addEventListener("click", commitMove);
recallButton.addEventListener("click", recallTiles);
exchangeButton.addEventListener("click", startOrConfirmExchange);
passButton.addEventListener("click", passTurn);
newGameButton.addEventListener("click", () => newGame(true));
saveButton.addEventListener("click", saveGame);
loadButton.addEventListener("click", loadGame);
themeButton.addEventListener("click", toggleTheme);

initializeTheme();
newGame(false);
loadDictionary();
