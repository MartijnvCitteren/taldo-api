package eu.taldo.bff.security;

import jakarta.servlet.http.HttpServletRequest;
import org.jspecify.annotations.Nullable;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

/**
 * Starts the login at {@code /login}, instead of Spring's {@code
 * /oauth2/authorization/{registration}}, so the URL names neither the protocol nor the identity
 * provider (ADR 0009, section 2). There is one identity provider, so the path needs no registration
 * id.
 *
 * <p>Adds PKCE although the client is confidential: a stolen authorization code is then useless
 * without both the client secret and the code verifier, which never leaves this server. Keycloak
 * requires it for the client too.
 */
class LoginRequestResolver implements OAuth2AuthorizationRequestResolver {
  private final DefaultOAuth2AuthorizationRequestResolver resolver;
  private final String registrationId;

  LoginRequestResolver(ClientRegistrationRepository clientRegistrations, String registrationId) {
    this.resolver =
        new DefaultOAuth2AuthorizationRequestResolver(
            clientRegistrations, SecurityConfig.LOGIN_PATH);
    this.resolver.setAuthorizationRequestCustomizer(
        OAuth2AuthorizationRequestCustomizers.withPkce());
    this.registrationId = registrationId;
  }

  @Override
  public @Nullable OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
    if (!SecurityConfig.LOGIN_PATH.equals(request.getServletPath())) {
      return null;
    }
    return resolver.resolve(request, registrationId);
  }

  @Override
  public @Nullable OAuth2AuthorizationRequest resolve(
      HttpServletRequest request, String clientRegistrationId) {
    return resolver.resolve(request, clientRegistrationId);
  }
}
