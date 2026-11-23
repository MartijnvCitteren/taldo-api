package eu.taldo.bff;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static eu.taldo.bff.KeycloakStub.KEYCLOAK_WIREMOCK;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.restassured.RestAssured;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Base class for the BFF's integration tests: the application on a random port, sessions on H2, and
 * only the world outside replaced by WireMock: Keycloak ({@link KeycloakStub}) and taldo-api
 * ({@link #API_WIREMOCK}). One shared application context for every test class.
 */
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
abstract class BffIntegrationTest {
  static final WireMockServer API_WIREMOCK =
      new WireMockServer(wireMockConfig().dynamicPort().http2PlainDisabled(true));

  static {
    API_WIREMOCK.start();
  }

  @LocalServerPort protected int port;

  @DynamicPropertySource
  static void outsideWorld(DynamicPropertyRegistry registry) {
    registry.add(
        "spring.security.oauth2.client.provider.keycloak.issuer-uri", KeycloakStub::issuer);
    registry.add("taldo.api.base-url", API_WIREMOCK::baseUrl);
  }

  @BeforeEach
  void resetOutsideWorld() {
    RestAssured.port = port;
    KEYCLOAK_WIREMOCK.resetAll();
    KeycloakStub.stubRealm();
    API_WIREMOCK.resetAll();
  }

  @AfterEach
  void resetRestAssured() {
    RestAssured.reset();
  }
}
