package com.masterchiefproject.scrabble.game;

import com.masterchiefproject.scrabble.dictionary.WordValidator;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** Local pass-and-play game coordinator for two to four players. */
public final class ScrabbleGame {
    public static final int MAX_SCORELESS_TURNS = 6;

    private final Board board = new Board();
    private final TileBag bag;
    private final WordValidator validator;
    private final List<Player> players;

    private int currentPlayerIndex;
    private int scorelessTurns;
    private boolean gameOver;

    public ScrabbleGame(List<String> playerNames, WordValidator validator) {
        this(playerNames, validator, new TileBag());
    }

    public ScrabbleGame(List<String> playerNames, WordValidator validator, long seed) {
        this(playerNames, validator, new TileBag(seed));
    }

    ScrabbleGame(List<String> playerNames, WordValidator validator, TileBag bag) {
        Objects.requireNonNull(playerNames, "playerNames");
        if (playerNames.size() < 2 || playerNames.size() > 4) {
            throw new IllegalArgumentException("A game requires two to four players");
        }
        this.validator = Objects.requireNonNull(validator, "validator");
        this.bag = Objects.requireNonNull(bag, "bag");
        this.players = playerNames.stream().map(Player::new).toList();

        Set<String> uniqueNames = new HashSet<>();
        for (Player player : players) {
            if (!uniqueNames.add(player.name().toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("Player names must be unique");
            }
        }
        refillAllRacks();
    }

    public Board board() {
        return board;
    }

    public List<Player> players() {
        return players;
    }

    public Player currentPlayer() {
        return players.get(currentPlayerIndex);
    }

    public int currentPlayerIndex() {
        return currentPlayerIndex;
    }

    public int bagRemaining() {
        return bag.remaining();
    }

    public boolean isGameOver() {
        return gameOver;
    }

    public int scorelessTurns() {
        return scorelessTurns;
    }

    public MoveResult playMove(List<PlacementRequest> requests) {
        ensureRunning();
        if (requests == null || requests.isEmpty()) {
            throw new InvalidMoveException("A tile move cannot be empty. Use passTurn() to pass");
        }

        Player player = currentPlayer();
        Set<Integer> rackIndexes = new HashSet<>();
        List<Placement> placements = new ArrayList<>(requests.size());

        for (PlacementRequest request : requests) {
            if (request == null) {
                throw new InvalidMoveException("Placement requests cannot contain null");
            }
            int rackIndex = request.rackIndex();
            if (rackIndex < 0 || rackIndex >= player.rack().size()) {
                throw new InvalidMoveException("Invalid rack index: " + rackIndex);
            }
            if (!rackIndexes.add(rackIndex)) {
                throw new InvalidMoveException("A rack tile can only be used once per move");
            }

            Tile rackTile = player.tileAt(rackIndex);
            Tile placedTile = rackTile;
            if (rackTile.blank()) {
                if (request.assignedLetter() == null) {
                    throw new InvalidMoveException("Assign A-Z when placing a blank tile");
                }
                placedTile = rackTile.assignBlank(request.assignedLetter());
            }
            placements.add(new Placement(new Position(request.row(), request.col()), placedTile));
        }

        MoveResult result = board.place(placements, validator);

        rackIndexes.stream()
                .sorted(Comparator.reverseOrder())
                .forEach(index -> player.removeTileAt(index));
        player.addScore(result.score());
        refillRack(player);

        scorelessTurns = result.score() == 0 ? scorelessTurns + 1 : 0;
        completeTurnIfNeeded(player);
        return result;
    }

    public void exchangeTiles(List<Integer> rackIndexes) {
        ensureRunning();
        if (rackIndexes == null || rackIndexes.isEmpty()) {
            throw new InvalidMoveException("Select at least one rack tile to exchange");
        }
        if (bag.remaining() < Player.RACK_SIZE) {
            throw new InvalidMoveException("Tiles may only be exchanged while at least seven tiles remain in the bag");
        }

        Player player = currentPlayer();
        Set<Integer> unique = new HashSet<>(rackIndexes);
        if (unique.size() != rackIndexes.size()) {
            throw new InvalidMoveException("Each rack index may be exchanged only once");
        }
        for (int index : unique) {
            if (index < 0 || index >= player.rack().size()) {
                throw new InvalidMoveException("Invalid rack index: " + index);
            }
        }

        List<Integer> ascending = unique.stream().sorted().toList();
        List<Tile> returned = ascending.stream().map(player::tileAt).toList();
        List<Tile> replacements = bag.exchange(returned);
        for (int i = 0; i < ascending.size(); i++) {
            player.replaceTileAt(ascending.get(i), replacements.get(i));
        }

        scorelessTurns++;
        completeTurnIfNeeded(null);
    }

    public void passTurn() {
        ensureRunning();
        scorelessTurns++;
        completeTurnIfNeeded(null);
    }

    private void refillAllRacks() {
        for (Player player : players) {
            refillRack(player);
        }
    }

    private void refillRack(Player player) {
        int needed = Player.RACK_SIZE - player.rack().size();
        if (needed > 0) {
            player.addTiles(bag.draw(needed));
        }
    }

    private void completeTurnIfNeeded(Player playerWhoMoved) {
        if (bag.isEmpty() && playerWhoMoved != null && playerWhoMoved.rackEmpty()) {
            finishGame(playerWhoMoved);
            return;
        }
        if (scorelessTurns >= MAX_SCORELESS_TURNS) {
            finishGame(null);
            return;
        }
        currentPlayerIndex = (currentPlayerIndex + 1) % players.size();
    }

    private void finishGame(Player finisher) {
        int opponentRackPoints = 0;
        for (Player player : players) {
            int deduction = player.rackPoints();
            player.addScore(-deduction);
            if (finisher != null && player != finisher) {
                opponentRackPoints += deduction;
            }
        }
        if (finisher != null) {
            finisher.addScore(opponentRackPoints);
        }
        gameOver = true;
    }

    private void ensureRunning() {
        if (gameOver) {
            throw new IllegalStateException("The game is already over");
        }
    }
}
