package io.github.usernugget.limboauth.expansion.throwable;

public class QuietIllegalStateException extends IllegalStateException {

  public QuietIllegalStateException() { }

  public QuietIllegalStateException(String message) {
    super(message);
  }

  public QuietIllegalStateException(String message, Throwable cause) {
    super(message, cause);
  }

  @Override
  public Throwable fillInStackTrace() {
    return this;
  }
}
