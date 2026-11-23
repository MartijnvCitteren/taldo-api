package eu.taldo.bff.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import tools.jackson.databind.json.JsonMapper;

/**
 * Answers a logout with the URL that ends the Keycloak session too (OIDC RP-initiated logout),
 * instead of redirecting to it. The frontend calls {@code POST /logout} with {@code fetch} and the
 * CSRF header, so a redirect would be followed inside {@code fetch} and never reach the browser's
 * address bar; the frontend navigates to {@code logoutUrl} itself. By then this session is already
 * gone, so the tokens are useless here even if the navigation never happens.
 */
class LogoutUrlResponder extends OidcClientInitiatedLogoutSuccessHandler {
  private final JsonMapper jsonMapper;

  LogoutUrlResponder(ClientRegistrationRepository clientRegistrations, JsonMapper jsonMapper) {
    super(clientRegistrations);
    this.jsonMapper = jsonMapper;
    setPostLogoutRedirectUri("{baseUrl}/");
  }

  @Override
  public void onLogoutSuccess(
      HttpServletRequest request,
      HttpServletResponse response,
      @Nullable Authentication authentication)
      throws IOException {
    var logoutUrl = determineTargetUrl(request, response, authentication);
    response.setStatus(HttpStatus.OK.value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    jsonMapper.writeValue(response.getOutputStream(), Map.of("logoutUrl", logoutUrl));
  }
}
