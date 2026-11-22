package eu.taldo.api.adapter.out.keycloak;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Guards what the realm puts in the access tokens of the clients users log in with: {@code
 * taldo-web}, the BFF's confidential client (ADR 0009), and {@code taldo-dev}, the public client
 * for local development (ADR 0006, revision 2026-11-21, points 2 and 3). Both use Keycloak's
 * lightweight access token, which only carries claims from mappers marked as lightweight: the
 * subject and the API's audience. Names and the e-mail address stay out. Without its own mapper the
 * lightweight token has no {@code sub} at all, and without the audience the backend rejects every
 * token.
 */
class KeycloakRealmAccessTokenTest {

  private static final Path REALM_FILE = Path.of("../docker/keycloak/taldo-realm.json");
  private static final String AUDIENCES_PROPERTY =
      "spring.security.oauth2.resourceserver.jwt.audiences";
  private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

  @ParameterizedTest
  @ValueSource(strings = {"taldo-web", "taldo-dev"})
  @DisplayName(
      "given a client users log in with, when its access token is configured then it is a"
          + " lightweight token")
  void givenLoginClientWhenItsAccessTokenIsConfiguredThenItIsALightweightToken(String clientId)
      throws IOException {
    var client = loginClient(clientId);

    assertEquals(
        "true",
        client.path("attributes").path("client.use.lightweight.access.token.enabled").asString());
  }

  @ParameterizedTest
  @ValueSource(strings = {"taldo-web", "taldo-dev"})
  @DisplayName(
      "given a client users log in with, when its access token is configured then the subject is"
          + " in the lightweight token")
  void givenLoginClientWhenItsAccessTokenIsConfiguredThenTheSubjectIsInTheToken(String clientId)
      throws IOException {
    var subjectMapper = lightweightAccessTokenMapper(loginClient(clientId), "oidc-sub-mapper");

    assertEquals("true", subjectMapper.path("config").path("access.token.claim").asString());
  }

  @ParameterizedTest
  @ValueSource(strings = {"taldo-web", "taldo-dev"})
  @DisplayName(
      "given a client users log in with, when its access token is configured then its audience is"
          + " the one the backend accepts")
  void givenLoginClientWhenItsAccessTokenIsConfiguredThenItsAudienceIsAccepted(String clientId)
      throws IOException {
    var audienceMapper =
        lightweightAccessTokenMapper(loginClient(clientId), "oidc-audience-mapper");

    assertEquals(
        acceptedAudience(),
        audienceMapper.path("config").path("included.custom.audience").asString());
  }

  private static JsonNode loginClient(String clientId) throws IOException {
    var realm = JSON_MAPPER.readTree(Files.readString(REALM_FILE));
    return StreamSupport.stream(realm.path("clients").spliterator(), false)
        .filter(client -> clientId.equals(client.path("clientId").asString()))
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("The realm has no client " + clientId));
  }

  /** The client's mapper of the given type, which must put its claim in the lightweight token. */
  private static JsonNode lightweightAccessTokenMapper(JsonNode client, String mapperType) {
    var mapper =
        StreamSupport.stream(client.path("protocolMappers").spliterator(), false)
            .filter(candidate -> mapperType.equals(candidate.path("protocolMapper").asString()))
            .findFirst()
            .orElseThrow(() ->
                new IllegalStateException(
                    client.path("clientId").asString() + " has no " + mapperType));
    assertEquals("true", mapper.path("config").path("lightweight.claim").asString());
    return mapper;
  }

  private static String acceptedAudience() {
    var yaml = new YamlPropertiesFactoryBean();
    yaml.setResources(new ClassPathResource("application.yml"));
    return Objects.requireNonNull(yaml.getObject()).getProperty(AUDIENCES_PROPERTY);
  }
}
