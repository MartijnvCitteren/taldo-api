package eu.taldo.bff.relay;

/** Keycloak could not renew the access token right now; a retry may succeed. */
class IdentityProviderUnavailableException extends RuntimeException {
  IdentityProviderUnavailableException(Throwable cause) {
    super("Keycloak could not renew the access token", cause);
  }
}
