package eu.taldo.bff.relay;

/** The user's session can no longer reach the API: they must log in again. */
class SessionEndedException extends RuntimeException {
  SessionEndedException(String message) {
    super(message);
  }
}
