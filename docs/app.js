"use strict";

import {
  BOARD_SIZE,
  CENTER,
  MAX_SCORELESS_TURNS,
  PREMIUMS,
  RACK_SIZE,
  commitMove as engineCommitMove,
  createAssignedBlank,
  createGameState,
  currentPlayer,
  exchangeTiles as engineExchangeTiles,
  normalizePlayerNames,
  passTurn as enginePassTurn,
  validateSavedState,
  winners
} from "./engine.js";
import { createGameStorage } from "./storage.js";

const THEME_KEY = "masterchief-scrabble-theme";
const gameStorage = createGameStorage(window.localStorage);

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
const newGameDialog = document.getElementById("new-game-dialog");
const newGameForm = document.getElementById("new-game-form");
const dialogCloseButton = document.getElementById("dialog-close");
const cancelNewGameButton = document.getElementById("cancel-new-game");
const playerCountElement = document.getElementById("player-count");
const playerNameFields = [1, 2, 3, 4].map(index => document.getElementById(`player-name-${index}`));
const newGameError = document.getElementById("new-game-error");

let dictionary = new Set();
let dictionaryReady = false;
let state = null;
let pending = [];
let activeRackIndex = null;
let exchangeMode = false;
let exchangeSelection = new Set();

function initializeState() {
  const restored = gameStorage.readAuto();
  if (restored) {
    state = restored;
    setStatus("Automatic save restored from this browser.", "success");
  } else {
    state = createGameState();
    gameStorage.writeAuto(state);
    setStatus("Player 1 starts. Build the first word through the center star.");
  }
  clearTransientState();
  renderAll();
}

function clearTransientState() {
  pending = [];
  activeRackIndex = null;
  exchangeMode = false;
  exchangeSelection = new Set();
}

function key(row, col) {
  return `${row},${col}`;
}

function pendingAt(row, col) {
  return pending.find(item => item.row === row && item.col === col) || null;
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
  const letter = tile.letter === "?" ? "_" : escapeHtml(tile.letter);
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

  const rackTile = currentPlayer(state).rack[activeRackIndex];
  if (!rackTile) {
    activeRackIndex = null;
    renderAll();
    return;
  }

  let placedTile = { ...rackTile };
  if (rackTile.blank) {
    const assigned = window.prompt("Assign the blank tile a letter from A to Z:", "A");
    if (assigned === null) return;
    try {
      placedTile = createAssignedBlank(assigned);
    } catch (error) {
      setStatus(error.message, "error");
      return;
    }
  }

  pending.push({ row, col, rackIndex: activeRackIndex, tile: placedTile });
  activeRackIndex = null;
  setStatus("Tile staged. Add more tiles or commit the move.");
  renderAll();
}

function renderRack() {
  rackElement.innerHTML = "";
  const player = currentPlayer(state);
  player.rack.forEach((tile, index) => {
    const button = document.createElement("button");
    button.type = "button";
    button.className = "rack-tile";
    const used = pending.some(item => item.rackIndex === index);
    if (used) button.classList.add("used");
    if (!exchangeMode && activeRackIndex === index) button.classList.add("active");
    if (exchangeMode && exchangeSelection.has(index)) button.classList.add("exchange");
    button.disabled = used;
    const letter = tile.letter === "?" ? "_" : escapeHtml(tile.letter);
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
    const name = document.createElement("span");
    name.textContent = player.name;
    const score = document.createElement("strong");
    score.textContent = player.score;
    row.append(name, score);
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

  state.history.slice().reverse().slice(0, 20).forEach(entry => {
    const li = document.createElement("li");
    const strong = document.createElement("strong");
    strong.textContent = entry.player;
    li.appendChild(strong);

    if (entry.type === "move") {
      li.appendChild(document.createTextNode(` ${entry.words.join(", ")} `));
      const score = document.createElement("span");
      score.className = "score";
      score.textContent = `+${entry.score}`;
      li.appendChild(score);
    } else if (entry.type === "exchange") {
      li.appendChild(document.createTextNode(` exchanged ${entry.count} tile${entry.count === 1 ? "" : "s"}.`));
    } else if (entry.type === "pass") {
      li.appendChild(document.createTextNode(" passed."));
    }
    historyElement.appendChild(li);
  });
}

function renderStats() {
  const player = currentPlayer(state);
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
  loadButton.disabled = !gameStorage.hasManual();
}

function renderAll() {
  renderBoard();
  renderRack();
  renderScoreboard();
  renderHistory();
  renderStats();
  updateActionState();
}

function commitMove() {
  if (!dictionaryReady) {
    setStatus("Dictionary is still loading.", "warning");
    return;
  }

  let result;
  try {
    result = engineCommitMove(state, pending, dictionary);
  } catch (error) {
    setStatus(error.message, "error");
    return;
  }

  const summary = `${result.words.join(", ")} scored ${result.score} point${result.score === 1 ? "" : "s"}${result.bingo ? " including a 50-point bingo bonus" : ""}.`;
  clearTransientState();
  if (state.gameOver) announceGameOver();
  else setStatus(summary, "success");
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

  const playerName = currentPlayer(state).name;
  const count = exchangeSelection.size;
  try {
    engineExchangeTiles(state, [...exchangeSelection]);
  } catch (error) {
    setStatus(error.message, "error");
    return;
  }

  clearTransientState();
  if (state.gameOver) announceGameOver();
  else setStatus(`${playerName} exchanged ${count} tile${count === 1 ? "" : "s"}.`);
  autoSave();
  renderAll();
}

function passTurn() {
  if (pending.length || exchangeMode) {
    setStatus("Recall pending tiles or leave exchange mode before passing.", "warning");
    return;
  }
  const playerName = currentPlayer(state).name;
  try {
    enginePassTurn(state);
  } catch (error) {
    setStatus(error.message, "error");
    return;
  }
  if (state.gameOver) announceGameOver();
  else setStatus(`${playerName} passed.`);
  autoSave();
  renderAll();
}

function announceGameOver() {
  const top = winners(state);
  if (top.length === 1) {
    setStatus(`Game over. ${top[0].name} wins with ${top[0].score} points.`, "success");
  } else {
    setStatus(`Game over. Tie between ${top.map(player => player.name).join(", ")} at ${top[0].score} points.`, "success");
  }
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
  if (!validateSavedState(state)) {
    setStatus("The current game state could not be saved safely.", "error");
    return;
  }
  gameStorage.writeManual(state);
  setStatus("Manual save created in this browser.", "success");
  updateActionState();
}

function autoSave() {
  if (validateSavedState(state)) gameStorage.writeAuto(state);
}

function loadManualSave() {
  const loaded = gameStorage.readManual();
  if (!loaded) {
    setStatus("No valid manual save was found in this browser.", "warning");
    updateActionState();
    return;
  }
  state = loaded;
  clearTransientState();
  autoSave();
  setStatus("Manual save loaded.", "success");
  renderAll();
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
  dictionaryStateElement.textContent = "Loading dictionary…";
  dictionaryStateElement.className = "dictionary-state";
  try {
    const response = await fetch("words.txt", { cache: "force-cache" });
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    const text = await response.text();
    dictionary = new Set(text.split(/\s+/).map(word => word.trim().toUpperCase()).filter(Boolean));
    dictionaryReady = true;
    dictionaryStateElement.textContent = `${dictionary.size.toLocaleString()} offline words loaded`;
    dictionaryStateElement.classList.add("ready");
    if (!statusElement.textContent || statusElement.textContent === "Loading…") {
      setStatus("Dictionary ready. Select a tile and start playing.");
    }
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
  themeButton.setAttribute("aria-label", `Switch to ${normalized === "dark" ? "light" : "dark"} mode`);
}

function initializeTheme() {
  applyTheme(localStorage.getItem(THEME_KEY) || "dark");
}

function toggleTheme() {
  applyTheme(document.documentElement.dataset.theme === "dark" ? "light" : "dark");
}

function openNewGameDialog() {
  const names = state?.players?.map(player => player.name) ?? ["Player 1", "Player 2"];
  playerCountElement.value = String(Math.min(Math.max(names.length, 2), 4));
  playerNameFields.forEach((input, index) => { input.value = names[index] || `Player ${index + 1}`; });
  newGameError.textContent = "";
  updatePlayerNameFields();
  if (typeof newGameDialog.showModal === "function") newGameDialog.showModal();
  else newGameDialog.setAttribute("open", "");
}

function closeNewGameDialog() {
  if (typeof newGameDialog.close === "function") newGameDialog.close();
  else newGameDialog.removeAttribute("open");
}

function updatePlayerNameFields() {
  const count = Number(playerCountElement.value);
  playerNameFields.forEach((input, index) => {
    const field = input.closest("label");
    const visible = index < count;
    field.hidden = !visible;
    input.disabled = !visible;
    input.required = visible;
  });
}

function startConfiguredGame(event) {
  event.preventDefault();
  const count = Number(playerCountElement.value);
  const names = playerNameFields.slice(0, count).map(input => input.value);
  try {
    const normalized = normalizePlayerNames(names);
    state = createGameState(normalized);
  } catch (error) {
    newGameError.textContent = error.message;
    return;
  }

  clearTransientState();
  gameStorage.writeAuto(state);
  closeNewGameDialog();
  setStatus(`${state.players[0].name} starts. Build the first word through the center star.`, "success");
  renderAll();
}

commitButton.addEventListener("click", commitMove);
recallButton.addEventListener("click", recallTiles);
exchangeButton.addEventListener("click", startOrConfirmExchange);
passButton.addEventListener("click", passTurn);
newGameButton.addEventListener("click", openNewGameDialog);
saveButton.addEventListener("click", saveGame);
loadButton.addEventListener("click", loadManualSave);
themeButton.addEventListener("click", toggleTheme);
playerCountElement.addEventListener("change", updatePlayerNameFields);
newGameForm.addEventListener("submit", startConfiguredGame);
dialogCloseButton.addEventListener("click", closeNewGameDialog);
cancelNewGameButton.addEventListener("click", closeNewGameDialog);
newGameDialog.addEventListener("cancel", event => {
  event.preventDefault();
  closeNewGameDialog();
});

initializeTheme();
initializeState();
loadDictionary();
