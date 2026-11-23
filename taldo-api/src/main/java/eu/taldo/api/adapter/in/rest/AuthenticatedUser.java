package eu.taldo.api.adapter.in.rest;

import eu.taldo.api.application.port.in.identity.ActingUser;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** The account behind the current request, as the resource server authenticated it. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AuthenticatedUser {

  /**
   * The {@code sub} claim of the request's access token: the account's id at the identity provider.
   * Only for endpoints that require authentication; {@code SecurityConfig} guarantees a validated
   * token there.
   *
   * @throws IllegalStateException when the request is not authenticated: the endpoint is missing
   *     from {@code SecurityConfig}, which is our bug
   */
  public static String subject() {
    return authentication().getName();
  }

  /**
   * The user the request acts as, and the company whose data it may reach. Only for company
   * endpoints; {@code SecurityConfig} guarantees there that the account has company access.
   *
   * @throws IllegalStateException when the request has no company access: the endpoint is not
   *     protected as a company endpoint in {@code SecurityConfig}, which is our bug
   */
  public static ActingUser actingUser() {
    if (authentication() instanceof AccountAuthentication account) {
      return account
          .getActingUser()
          .orElseThrow(
              () -> new IllegalStateException("No company access; the endpoint must require it"));
    }
    throw new IllegalStateException("No account authentication; the endpoint must require a token");
  }

  private static Authentication authentication() {
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null) {
      throw new IllegalStateException("No authenticated user; the endpoint must require a token");
    }
    return authentication;
  }
}
