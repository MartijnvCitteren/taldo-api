package eu.taldo.api.application.port.in.identity;

import java.util.Optional;

public interface GetProfileUseCase {

  /**
   * Finds the profile of the user an identity-provider account belongs to.
   *
   * @param subject the account's id at the identity provider
   * @return empty when the account has not been onboarded yet
   */
  Optional<UserProfile> getProfile(String subject);
}
