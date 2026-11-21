package eu.taldo.api.shared.config.security;

import eu.taldo.api.shared.config.WebConfig;
import jakarta.servlet.DispatcherType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.SecurityFilterChain;
import tools.jackson.databind.json.JsonMapper;

/**
 * The security filter chain. The backend is an OAuth2 resource server for the access tokens
 * Keycloak issues (ADR 0006, section 6): it checks their signature with Keycloak's public keys, the
 * issuer, the expiry and the audience, without calling Keycloak per request.
 *
 * <p>Every endpoint requires a valid token (ADR 0006, revision 2026-11-21). The identity endpoints
 * that act on the logged-in account ({@code /onboarding}, {@code /me}) need nothing more: they work
 * before onboarding and while a user waits for approval. Every other endpoint reaches company data
 * and needs the {@link #COMPANY_ACCESS} authority, which the token converter grants from Taldo's
 * own user, not from the token. So a new endpoint is closed to accounts without company access
 * unless it is added here on purpose.
 *
 * <p>No token, or an invalid one, is {@code 401} with the bearer challenge of RFC 6750 ({@link
 * BearerChallengeEntryPoint}); a valid token without company access is {@code 403} ({@link
 * CompanyAccessDeniedHandler}). Both answer with a problem body of fixed texts and log the reason.
 *
 * <p>Error dispatches are permitted: when a request fails before it reaches a controller, the
 * container forwards it to the error page without the security context, and that forward must not
 * turn a {@code 500} into a {@code 401}.
 *
 * <p>CSRF protection, form login, HTTP basic and logout are disabled because the API is stateless
 * and authenticates with bearer tokens, not cookies or sessions. CORS defers to the Spring MVC
 * configuration in {@link WebConfig}.
 *
 * <p>Only in a servlet web application: a filter chain has nothing to filter without one, and a
 * test that starts the context without a web environment has no {@code HttpSecurity} to build it.
 */
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SecurityConfig {

  /** The authority of an account that acts as a user with access to its company's data. */
  public static final String COMPANY_ACCESS = "COMPANY_ACCESS";

  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      Converter<Jwt, AbstractAuthenticationToken> accountAuthenticationConverter,
      JsonMapper jsonMapper)
      throws Exception {
    var problemResponseWriter = new ProblemResponseWriter(jsonMapper);
    return http.csrf(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable)
        .logout(AbstractHttpConfigurer::disable)
        .cors(Customizer.withDefaults())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(requests ->
            requests
                .dispatcherTypeMatchers(DispatcherType.ERROR)
                .permitAll()
                .requestMatchers("/onboarding", "/me")
                .authenticated()
                .anyRequest()
                .hasAuthority(COMPANY_ACCESS))
        .oauth2ResourceServer(resourceServer ->
            resourceServer
                .jwt(jwt -> jwt.jwtAuthenticationConverter(accountAuthenticationConverter))
                .authenticationEntryPoint(new BearerChallengeEntryPoint(problemResponseWriter))
                .accessDeniedHandler(new CompanyAccessDeniedHandler(problemResponseWriter)))
        .build();
  }
}
