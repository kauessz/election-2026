package br.com.election.tse.exception;
public class TseParsingException extends RuntimeException {
 public TseParsingException(String message) { super(message); }
 public TseParsingException(String message, Throwable cause) { super(message,cause); }
}
