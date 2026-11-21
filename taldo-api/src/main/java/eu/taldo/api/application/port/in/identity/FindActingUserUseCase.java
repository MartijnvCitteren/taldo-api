package eu.taldo.api.application.port.in.identity;

import java.util.Optional;

public interface FindActingUserUseCase {

  /**
   * Finds the user an identity-provider account acts as, when that user may access company data.
   * Taldo decides this, not the token (ADR 0006, revision 2026-11-21, point 1), so approving or
   * blocking a user takes effect on their next request.
   *
   * @param subject the account's id at the identity provider
   * @return empty when the account has no Taldo user yet, or its user waits for approval
   */
  Optional<ActingUser> findActingUser(String subject);
}
