package eu.taldo.bff;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.json.JsonMapper;

/**
 * A WireMock stand-in for Keycloak's realm: the discovery document, the signing key and the token
 * endpoint. One server for the whole test run, so cached Spring contexts keep pointing at its port.
 * It serves the realm from the start, because the application reads the discovery document when its
 * context starts. Tests reset it and serve the realm again before use.
 */
final class KeycloakStub {
  static final String SUBJECT = "6f1d2c3b-4a5e-4f60-8a7b-9c0d1e2f3a4b";
  static final String CLIENT_ID = "taldo-web";
  static final String REALM_PATH = "/realms/taldo";
  static final String AUTHORIZATION_PATH = REALM_PATH + "/protocol/openid-connect/auth";
  static final String TOKEN_PATH = REALM_PATH + "/protocol/openid-connect/token";
  static final String END_SESSION_PATH = REALM_PATH + "/protocol/openid-connect/logout";
  private static final String CERTS_PATH = REALM_PATH + "/protocol/openid-connect/certs";
  private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();
  private static final RSAKey REALM_KEY = generateKey();

  static final WireMockServer KEYCLOAK_WIREMOCK =
      new WireMockServer(wireMockConfig().dynamicPort().http2PlainDisabled(true));

  static {
    KEYCLOAK_WIREMOCK.start();
    stubRealm();
  }

  private KeycloakStub() {}

  static String issuer() {
    return KEYCLOAK_WIREMOCK.baseUrl() + REALM_PATH;
  }

  /** Serves the discovery document and the realm's public key. */
  static void stubRealm() {
    KEYCLOAK_WIREMOCK.stubFor(
        get(REALM_PATH + "/.well-known/openid-configuration").willReturn(json(discovery())));
    KEYCLOAK_WIREMOCK.stubFor(
        get(CERTS_PATH)
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(new JWKSet(REALM_KEY.toPublicJWK()).toString())));
  }

  /**
   * Lets the token endpoint exchange any code for an access token and an ID token carrying the
   * nonce of the login it belongs to.
   */
  static void stubTokenEndpoint(String nonce, String accessToken) {
    KEYCLOAK_WIREMOCK.stubFor(
        post(TOKEN_PATH)
            .willReturn(
                json(
                    Map.of(
                        "access_token",
                        accessToken,
                        "id_token",
                        idToken(nonce),
                        "refresh_token",
                        "refresh-token",
                        "token_type",
                        "Bearer",
                        "expires_in",
                        600,
                        "scope",
                        "openid"))));
  }

  private static String idToken(String nonce) {
    var now = Instant.now();
    var claims =
        new JWTClaimsSet.Builder()
            .issuer(issuer())
            .subject(SUBJECT)
            .audience(CLIENT_ID)
            .issueTime(Date.from(now))
            .expirationTime(Date.from(now.plusSeconds(600)))
            .claim("nonce", nonce)
            .claim("azp", CLIENT_ID)
            .build();
    var jwt =
        new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(REALM_KEY.getKeyID()).build(), claims);
    try {
      jwt.sign(new RSASSASigner(REALM_KEY));
    } catch (JOSEException e) {
      throw new IllegalStateException("Cannot sign a test ID token", e);
    }
    return jwt.serialize();
  }

  private static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder json(
      Object body) {
    return aResponse()
        .withHeader("Content-Type", "application/json")
        .withBody(JSON_MAPPER.writeValueAsString(body));
  }

  private static Map<String, Object> discovery() {
    return Map.of(
        "issuer",
        issuer(),
        "authorization_endpoint",
        issuer() + "/protocol/openid-connect/auth",
        "token_endpoint",
        KEYCLOAK_WIREMOCK.baseUrl() + TOKEN_PATH,
        "jwks_uri",
        KEYCLOAK_WIREMOCK.baseUrl() + CERTS_PATH,
        "end_session_endpoint",
        KEYCLOAK_WIREMOCK.baseUrl() + END_SESSION_PATH,
        "response_types_supported",
        List.of("code"),
        "subject_types_supported",
        List.of("public"),
        "id_token_signing_alg_values_supported",
        List.of("RS256"));
  }

  private static RSAKey generateKey() {
    try {
      return new RSAKeyGenerator(2048).keyID("realm-key").generate();
    } catch (JOSEException e) {
      throw new IllegalStateException("Cannot generate a test signing key", e);
    }
  }
}
