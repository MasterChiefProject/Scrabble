"use strict";

import { validateSavedState } from "./engine.js";

export const AUTO_SAVE_KEY = "masterchief-scrabble-autosave-v3";
export const MANUAL_SAVE_KEY = "masterchief-scrabble-manual-save-v3";

/** Creates a validated persistence adapter over localStorage-compatible storage. */
export function createGameStorage(storage) {
  if (!storage || typeof storage.getItem !== "function" || typeof storage.setItem !== "function") {
    throw new TypeError("A localStorage-compatible object is required");
  }

  function write(key, state) {
    if (!validateSavedState(state)) return false;
    storage.setItem(key, JSON.stringify(state));
    return true;
  }

  function read(key) {
    const raw = storage.getItem(key);
    if (!raw) return null;
    try {
      const parsed = JSON.parse(raw);
      if (!validateSavedState(parsed)) {
        storage.removeItem(key);
        return null;
      }
      return parsed;
    } catch {
      storage.removeItem(key);
      return null;
    }
  }

  return Object.freeze({
    readAuto: () => read(AUTO_SAVE_KEY),
    writeAuto: state => write(AUTO_SAVE_KEY, state),
    readManual: () => read(MANUAL_SAVE_KEY),
    writeManual: state => write(MANUAL_SAVE_KEY, state),
    hasManual: () => storage.getItem(MANUAL_SAVE_KEY) !== null
  });
}
