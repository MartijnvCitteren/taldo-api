package eu.taldo.bff.relay;

import eu.taldo.bff.security.SecurityConfig;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.stereotype.Component;

/** The logged-in user's current access token, renewed when it has (almost) expired. */
@Component
@RequiredArgsConstructor
class AccessTokens {
  private final OAuth2AuthorizedClientManager authorizedClientManager;

  /**
   * @throws SessionEndedException when the session holds no tokens, or Keycloak refuses to renew
   *     them because its own session ended (idle or maximum lifetime): the user must log in again
   * @throws IdentityProviderUnavailableException when Keycloak could not be asked; a retry may
   *     succeed, so the session stays
   */
  String currentAccessToken(
      Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
    var authorizeRequest =
        OAuth2AuthorizeRequest.withClientRegistrationId(SecurityConfig.REGISTRATION_ID)
            .principal(authentication)
            .attribute(HttpServletRequest.class.getName(), request)
            .attribute(HttpServletResponse.class.getName(), response)
            .build();
    try {
      var authorizedClient = authorizedClientManager.authorize(authorizeRequest);
      if (authorizedClient == null) {
        throw new SessionEndedException("The session holds no tokens");
      }
      return authorizedClient.getAccessToken().getTokenValue();
    } catch (OAuth2AuthorizationException failure) {
      if (OAuth2ErrorCodes.INVALID_GRANT.equals(failure.getError().getErrorCode())) {
        throw new SessionEndedException("Keycloak refused to renew the tokens");
      }
      throw new IdentityProviderUnavailableException(failure);
    }
  }
}
