package eu.taldo.bff.security;

import jakarta.servlet.DispatcherType;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import tools.jackson.databind.json.JsonMapper;

/**
 * The browser's side of login (ADR 0009). The browser only ever gets an opaque session cookie; the
 * tokens stay in the session on this server.
 *
 * <ul>
 *   <li>{@code GET /login} starts authorization code + PKCE with Keycloak as a confidential client;
 *       Keycloak returns to {@code /login/callback}, after which the browser lands on {@code /}. A
 *       failed login lands on {@code /?login=failed}, so the frontend can say so instead of
 *       starting a new login right away.
 *   <li>{@code POST /logout} ends the session and answers the URL that ends Keycloak's session
 *       ({@link LogoutUrlResponder}).
 *   <li>Every other request needs a session; without one it is {@code 401}, never a redirect, so
 *       the frontend decides when to send the user to {@code /login}.
 *   <li>CSRF: the session is a cookie, so every state-changing request needs the token from the
 *       {@code XSRF-TOKEN} cookie in the {@code X-XSRF-TOKEN} header (Spring Security's
 *       single-page-app setup). {@code SameSite=Strict} on the session cookie is a second layer,
 *       not a replacement: a marketing site on the same site could still send requests.
 * </ul>
 *
 * <p>The paths name neither the protocol nor the identity provider (ADR 0009, section 2).
 */
@Log4j2
@Configuration
public class SecurityConfig {
  static final String LOGIN_PATH = "/login";
  static final String CALLBACK_PATH = "/login/callback";
  static final String LOGOUT_PATH = "/logout";
  static final String LOGIN_FAILED_URL = "/?login=failed";

  /** The client registration in {@code application.yml}: the only identity provider. */
  public static final String REGISTRATION_ID = "keycloak";

  /**
   * Keeps the user's tokens in their session, so in PostgreSQL. Spring Boot's default keeps them in
   * memory, which a restart loses and another instance cannot see.
   */
  @Bean
  OAuth2AuthorizedClientRepository authorizedClientRepository() {
    return new HttpSessionOAuth2AuthorizedClientRepository();
  }

  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http, ClientRegistrationRepository clientRegistrations, JsonMapper jsonMapper)
      throws Exception {
    return http.authorizeHttpRequests(requests ->
            requests
                .dispatcherTypeMatchers(DispatcherType.ERROR)
                .permitAll()
                .requestMatchers(LOGIN_PATH, CALLBACK_PATH)
                .permitAll()
                .anyRequest()
                .authenticated())
        .oauth2Login(login ->
            login
                .loginPage(LOGIN_PATH)
                .authorizationEndpoint(endpoint ->
                    endpoint.authorizationRequestResolver(
                        new LoginRequestResolver(clientRegistrations, REGISTRATION_ID)))
                .redirectionEndpoint(endpoint -> endpoint.baseUri(CALLBACK_PATH))
                .defaultSuccessUrl("/", true)
                .failureHandler((request, response, exception) -> {
                  log.warn("Login failed: {}", exception.getMessage());
                  new SimpleUrlAuthenticationFailureHandler(LOGIN_FAILED_URL)
                      .onAuthenticationFailure(request, response, exception);
                }))
        .logout(logout ->
            logout
                .logoutUrl(LOGOUT_PATH)
                .logoutSuccessHandler(new LogoutUrlResponder(clientRegistrations, jsonMapper)))
        .csrf(csrf -> csrf.spa())
        .exceptionHandling(exceptions ->
            exceptions.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
        .build();
  }
}
