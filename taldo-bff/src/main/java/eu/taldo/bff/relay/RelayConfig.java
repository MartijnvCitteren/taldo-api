package eu.taldo.bff.relay;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(ApiProperties.class)
class RelayConfig {

  /**
   * Hands out the user's tokens from their session and renews the access token with the refresh
   * token once it has (almost) expired. A refresh token Keycloak refuses is removed from the
   * session, so the next request does not try it again.
   */
  @Bean
  OAuth2AuthorizedClientManager authorizedClientManager(
      ClientRegistrationRepository clientRegistrations,
      OAuth2AuthorizedClientRepository authorizedClientRepository) {
    var manager =
        new DefaultOAuth2AuthorizedClientManager(clientRegistrations, authorizedClientRepository);
    manager.setAuthorizedClientProvider(
        OAuth2AuthorizedClientProviderBuilder.builder().authorizationCode().refreshToken().build());
    return manager;
  }

  /**
   * The client for taldo-api, from Spring Boot's builder so {@code spring.http.clients.*} timeouts
   * apply. Statuses are never turned into exceptions: the relay passes every answer on as it is.
   */
  @Bean
  RestClient apiClient(RestClient.Builder builder, ApiProperties apiProperties) {
    return builder
        .baseUrl(apiProperties.baseUrl())
        .defaultStatusHandler(status -> true, (request, response) -> {})
        .build();
  }
}
