package com.masterchiefproject.scrabble.game;

import com.masterchiefproject.scrabble.dictionary.Dictionary;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;
import java.util.Set;

/**
 * Small interactive terminal client for the Java engine.
 * Coordinates are 1-based to make manual play easier.
 */
public final class ConsoleGame {
    private ConsoleGame() {
    }

    public static void main(String[] args) throws Exception {
        Dictionary dictionary = Dictionary.fromResource("/dictionary/words.txt");
        try (Scanner scanner = new Scanner(System.in)) {
            int playerCount = readPlayerCount(scanner);
            List<String> playerNames = new ArrayList<>(playerCount);
            for (int i = 1; i <= playerCount; i++) {
                String fallback = "Player " + i;
                System.out.print(fallback + " name [" + fallback + "]: ");
                playerNames.add(defaultName(scanner.nextLine(), fallback));
            }

            ScrabbleGame game = new ScrabbleGame(playerNames, dictionary);
            printHelp();

            while (!game.isGameOver()) {
                printBoard(game.board());
                printScores(game);
                Player player = game.currentPlayer();
                System.out.println("Rack: " + rackText(player));
                System.out.print(player.name() + "> ");

                if (!scanner.hasNextLine()) {
                    break;
                }
                String line = scanner.nextLine().trim();
                if (line.isEmpty()) {
                    continue;
                }

                String[] fields = line.split("\\s+");
                String command = fields[0].toLowerCase(Locale.ROOT);
                try {
                    switch (command) {
                        case "place" -> handlePlace(game, fields);
                        case "exchange" -> handleExchange(game, fields);
                        case "pass" -> game.passTurn();
                        case "help" -> printHelp();
                        case "quit", "exit" -> {
                            return;
                        }
                        default -> System.out.println("Unknown command. Type 'help'.");
                    }
                } catch (RuntimeException e) {
                    System.out.println("Move rejected: " + e.getMessage());
                }
            }

            if (game.isGameOver()) {
                printBoard(game.board());
                printScores(game);
                System.out.println("Game over.");
            }
        }
    }

    private static void handlePlace(ScrabbleGame game, String[] fields) {
        if (fields.length != 5) {
            throw new IllegalArgumentException("Usage: place <row> <col> <H|V> <WORD>");
        }
        int row = Integer.parseInt(fields[1]) - 1;
        int col = Integer.parseInt(fields[2]) - 1;
        boolean vertical = switch (fields[3].toUpperCase(Locale.ROOT)) {
            case "H" -> false;
            case "V" -> true;
            default -> throw new IllegalArgumentException("Direction must be H or V");
        };
        String word = fields[4].toUpperCase(Locale.ROOT);
        if (!word.matches("[A-Z]{2,15}")) {
            throw new IllegalArgumentException("WORD must contain 2 to 15 letters A-Z");
        }

        List<PlacementRequest> requests = requestsForWord(game, row, col, vertical, word);
        MoveResult result = game.playMove(requests);
        System.out.println("Accepted: " + String.join(", ", result.words()) + " for " + result.score() + " points"
                + (result.bingo() ? " (bingo)" : ""));
    }

    private static List<PlacementRequest> requestsForWord(
            ScrabbleGame game, int row, int col, boolean vertical, String word) {
        Player player = game.currentPlayer();
        Set<Integer> usedRack = new HashSet<>();
        List<PlacementRequest> requests = new ArrayList<>();

        for (int i = 0; i < word.length(); i++) {
            int targetRow = row + (vertical ? i : 0);
            int targetCol = col + (vertical ? 0 : i);
            if (targetRow < 0 || targetRow >= Board.SIZE || targetCol < 0 || targetCol >= Board.SIZE) {
                throw new IllegalArgumentException("Word extends beyond the board");
            }

            char wanted = word.charAt(i);
            Tile existing = game.board().tileAt(targetRow, targetCol);
            if (existing != null) {
                if (existing.letter() != wanted) {
                    throw new IllegalArgumentException("Existing tile at " + (targetRow + 1) + "," + (targetCol + 1)
                            + " is " + existing.letter() + ", not " + wanted);
                }
                continue;
            }

            int rackIndex = findRackTile(player.rack(), usedRack, wanted);
            Character blankAssignment = null;
            if (rackIndex < 0) {
                rackIndex = findRackTile(player.rack(), usedRack, '?');
                blankAssignment = wanted;
            }
            if (rackIndex < 0) {
                throw new IllegalArgumentException("Rack does not contain " + wanted + " or an available blank");
            }
            usedRack.add(rackIndex);
            requests.add(new PlacementRequest(rackIndex, targetRow, targetCol, blankAssignment));
        }

        if (requests.isEmpty()) {
            throw new IllegalArgumentException("The word must place at least one new tile");
        }
        return requests;
    }

    private static int findRackTile(List<Tile> rack, Set<Integer> used, char letter) {
        for (int i = 0; i < rack.size(); i++) {
            if (!used.contains(i) && rack.get(i).letter() == letter) {
                return i;
            }
        }
        return -1;
    }

    private static void handleExchange(ScrabbleGame game, String[] fields) {
        if (fields.length != 2 || !fields[1].toUpperCase(Locale.ROOT).matches("[A-Z?]{1,7}")) {
            throw new IllegalArgumentException("Usage: exchange <LETTERS>, use ? for a blank");
        }
        String letters = fields[1].toUpperCase(Locale.ROOT);
        List<Tile> rack = game.currentPlayer().rack();
        Set<Integer> used = new HashSet<>();
        List<Integer> indexes = new ArrayList<>();
        for (char letter : letters.toCharArray()) {
            int index = findRackTile(rack, used, letter);
            if (index < 0) {
                throw new IllegalArgumentException("Rack does not contain enough " + letter + " tiles");
            }
            used.add(index);
            indexes.add(index);
        }
        game.exchangeTiles(indexes);
        System.out.println("Tiles exchanged.");
    }

    private static void printBoard(Board board) {
        System.out.print("    ");
        for (int col = 1; col <= Board.SIZE; col++) {
            System.out.printf("%3d", col);
        }
        System.out.println();

        for (int row = 0; row < Board.SIZE; row++) {
            System.out.printf("%3d ", row + 1);
            for (int col = 0; col < Board.SIZE; col++) {
                Tile tile = board.tileAt(row, col);
                if (tile != null) {
                    System.out.printf(" %c ", tile.letter());
                } else {
                    String label = board.premiumAt(row, col).label();
                    if (label.isEmpty()) {
                        System.out.print(" . ");
                    } else if (label.equals("★")) {
                        System.out.print(" * ");
                    } else {
                        System.out.printf("%-3s", label);
                    }
                }
            }
            System.out.println();
        }
    }

    private static void printScores(ScrabbleGame game) {
        StringBuilder out = new StringBuilder("Scores: ");
        for (int i = 0; i < game.players().size(); i++) {
            if (i > 0) out.append(" | ");
            Player player = game.players().get(i);
            out.append(player.name()).append(' ').append(player.score());
        }
        out.append(" | Bag ").append(game.bagRemaining());
        System.out.println(out);
    }

    private static String rackText(Player player) {
        return player.rack().stream().map(tile -> tile.letter() == '?' ? "_" : tile.display()).toList().toString();
    }

    private static int readPlayerCount(Scanner scanner) {
        while (true) {
            System.out.print("Number of players [2-4, default 2]: ");
            String value = scanner.nextLine().trim();
            if (value.isEmpty()) {
                return 2;
            }
            try {
                int count = Integer.parseInt(value);
                if (count >= 2 && count <= 4) {
                    return count;
                }
            } catch (NumberFormatException ignored) {
                // Ask again below.
            }
            System.out.println("Enter 2, 3, or 4.");
        }
    }

    private static String defaultName(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static void printHelp() {
        System.out.println("Commands:");
        System.out.println("  place <row> <col> <H|V> <WORD>   Place a word using rack and existing board letters");
        System.out.println("  exchange <LETTERS>                Exchange rack letters, use ? for a blank");
        System.out.println("  pass                              Pass the turn");
        System.out.println("  help                              Show commands");
        System.out.println("  quit                              Exit");
    }
}
