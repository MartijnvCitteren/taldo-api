package eu.taldo.bff;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
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
 * A WireMock stand-in for Keycloak's realm: the discovery document, the signing key, the token
 * endpoint and the UserInfo endpoint. One server for the whole test run, so cached Spring contexts
 * keep pointing at its port. It serves the realm from the start, because the application reads the
 * discovery document when its context starts. Tests reset it and serve the realm again before use.
 */
final class KeycloakStub {
  static final String SUBJECT = "6f1d2c3b-4a5e-4f60-8a7b-9c0d1e2f3a4b";
  static final String CLIENT_ID = "taldo-web";
  static final String REALM_PATH = "/realms/taldo";
  static final String AUTHORIZATION_PATH = REALM_PATH + "/protocol/openid-connect/auth";
  static final String TOKEN_PATH = REALM_PATH + "/protocol/openid-connect/token";
  static final String END_SESSION_PATH = REALM_PATH + "/protocol/openid-connect/logout";
  private static final String USERINFO_PATH = REALM_PATH + "/protocol/openid-connect/userinfo";
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

  /**
   * Serves the discovery document, the realm's public key and a UserInfo endpoint that refuses
   * {@code taldo-web}'s lightweight access token, as Keycloak does.
   */
  static void stubRealm() {
    KEYCLOAK_WIREMOCK.stubFor(
        get(REALM_PATH + "/.well-known/openid-configuration").willReturn(json(discovery())));
    KEYCLOAK_WIREMOCK.stubFor(
        get(CERTS_PATH)
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(new JWKSet(REALM_KEY.toPublicJWK()).toString())));
    KEYCLOAK_WIREMOCK.stubFor(
        get(USERINFO_PATH)
            .willReturn(
                aResponse()
                    .withStatus(401)
                    .withHeader(
                        "WWW-Authenticate",
                        "Bearer realm=\"taldo\", error=\"invalid_token\","
                            + " error_description=\"Lightweight access token not allowed for"
                            + " userinfo endpoint\"")));
  }

  /**
   * Lets the token endpoint exchange any code for an access token, valid for the given number of
   * seconds, and an ID token carrying the nonce of the login it belongs to. The scope is what
   * Keycloak grants with the realm's default client scopes.
   */
  static void stubTokenEndpoint(String nonce, String accessToken, int expiresInSeconds) {
    KEYCLOAK_WIREMOCK.stubFor(
        post(TOKEN_PATH)
            .withRequestBody(containing("grant_type=authorization_code"))
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
                        expiresInSeconds,
                        "scope",
                        "openid profile email"))));
  }

  /** Lets the token endpoint renew the tokens with the refresh token. */
  static void stubRefresh(String newAccessToken) {
    KEYCLOAK_WIREMOCK.stubFor(
        post(TOKEN_PATH)
            .withRequestBody(containing("grant_type=refresh_token"))
            .willReturn(
                json(
                    Map.of(
                        "access_token",
                        newAccessToken,
                        "refresh_token",
                        "renewed-refresh-token",
                        "token_type",
                        "Bearer",
                        "expires_in",
                        600))));
  }

  /** Lets the token endpoint answer a refresh as Keycloak does once its session has ended. */
  static void stubRefreshRefused() {
    KEYCLOAK_WIREMOCK.stubFor(
        post(TOKEN_PATH)
            .withRequestBody(containing("grant_type=refresh_token"))
            .willReturn(
                aResponse()
                    .withStatus(400)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"error\":\"invalid_grant\",\"error_description\":\"Session not active\"}")));
  }

  /** Lets the token endpoint fail on a refresh, as when Keycloak is down. */
  static void stubRefreshUnavailable() {
    KEYCLOAK_WIREMOCK.stubFor(
        post(TOKEN_PATH)
            .withRequestBody(containing("grant_type=refresh_token"))
            .willReturn(aResponse().withStatus(503)));
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
        "userinfo_endpoint",
        KEYCLOAK_WIREMOCK.baseUrl() + USERINFO_PATH,
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
