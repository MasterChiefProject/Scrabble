package com.masterchiefproject.scrabble.game;

public class InvalidMoveException extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;

    public InvalidMoveException(String message) {
        super(message);
    }
}
