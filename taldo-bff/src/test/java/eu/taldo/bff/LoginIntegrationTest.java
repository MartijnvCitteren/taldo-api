package eu.taldo.bff;

import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static eu.taldo.bff.KeycloakStub.KEYCLOAK_WIREMOCK;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.restassured.RestAssured;
import io.restassured.response.Response;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Logging in and out through the BFF, only over HTTP. Keycloak is the only thing replaced
 * (WireMock): what it receives on its token endpoint is the observable effect of a login.
 *
 * <p>The session cookie is {@code Secure}, which an HTTP client does not send back over plain HTTP
 * on its own, so the tests send it explicitly.
 */
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LoginIntegrationTest {
  private static final String SESSION_COOKIE = "__Host-session";
  private static final String ACCESS_TOKEN = "access-token-of-the-user";

  @LocalServerPort private int port;

  @DynamicPropertySource
  static void keycloakProperties(DynamicPropertyRegistry registry) {
    registry.add(
        "spring.security.oauth2.client.provider.keycloak.issuer-uri", KeycloakStub::issuer);
  }

  @BeforeEach
  void setUp() {
    RestAssured.port = port;
    KEYCLOAK_WIREMOCK.resetAll();
    KeycloakStub.stubRealm();
  }

  @AfterEach
  void resetRestAssured() {
    RestAssured.reset();
  }

  @Test
  @DisplayName(
      "given no session, when starting the login then a redirect to Keycloak with the client, PKCE"
          + " and the callback, and a strict session cookie")
  void givenNoSessionWhenStartingTheLoginThenRedirectToKeycloakWithPkceAndStrictCookie() {
    var response = startLogin();

    var location = response.header("Location");
    assertTrue(location.startsWith(KeycloakStub.issuer() + "/protocol/openid-connect/auth?"));
    assertEquals(KeycloakStub.CLIENT_ID, queryParameter(location, "client_id"));
    assertEquals("code", queryParameter(location, "response_type"));
    assertEquals("S256", queryParameter(location, "code_challenge_method"));
    assertEquals(callbackUrl(), queryParameter(location, "redirect_uri"));
    var sessionCookie = response.header("Set-Cookie");
    assertTrue(sessionCookie.startsWith(SESSION_COOKIE + "="));
    assertTrue(sessionCookie.contains("Secure"));
    assertTrue(sessionCookie.contains("HttpOnly"));
    assertTrue(sessionCookie.contains("SameSite=Strict"));
    assertTrue(sessionCookie.contains("Path=/"));
  }

  @Test
  @DisplayName(
      "given a started login, when Keycloak returns to the callback then the code is exchanged with"
          + " the client secret and the PKCE verifier, the browser lands on / with a new session")
  void givenStartedLoginWhenKeycloakReturnsToTheCallbackThenLoggedInWithNewSession() {
    var login = startLogin();
    var loginSession = login.getCookie(SESSION_COOKIE);

    var callback = finishLogin(login);

    callback
        .then()
        .statusCode(HttpStatus.FOUND.value())
        .header("Location", endsWith(":" + port + "/"));
    assertNotEquals(loginSession, callback.getCookie(SESSION_COOKIE));
    KEYCLOAK_WIREMOCK.verify(
        postRequestedFor(urlEqualTo(KeycloakStub.TOKEN_PATH))
            .withHeader("Authorization", matching("Basic .+"))
            .withRequestBody(containing("code_verifier=")));
  }

  @Test
  @DisplayName(
      "given a callback that does not belong to a started login, when returned then login failed")
  void givenCallbackThatDoesNotBelongToStartedLoginWhenReturnedThenLoginFailed() {
    var login = startLogin();

    given()
        .redirects()
        .follow(false)
        .header("Cookie", SESSION_COOKIE + "=" + login.getCookie(SESSION_COOKIE))
        .queryParam("code", "a-code")
        .queryParam("state", "not-the-state-of-this-login")
        .when()
        .get("/login/callback")
        .then()
        .statusCode(HttpStatus.FOUND.value())
        .header("Location", endsWith("/?login=failed"));
  }

  @Test
  @DisplayName("given no session, when calling the API then 401 and no redirect")
  void givenNoSessionWhenCallingTheApiThen401() {
    given()
        .redirects()
        .follow(false)
        .when()
        .get("/api/v1/me")
        .then()
        .statusCode(HttpStatus.UNAUTHORIZED.value());
  }

  @Test
  @DisplayName(
      "given a session, when logging out with the CSRF token then the Keycloak logout URL, and the"
          + " session is gone")
  void givenSessionWhenLoggingOutWithCsrfTokenThenKeycloakLogoutUrlAndSessionGone() {
    var session = finishLogin(startLogin()).getCookie(SESSION_COOKIE);
    var csrfToken = csrfToken(session);

    given()
        .header("Cookie", SESSION_COOKIE + "=" + session + "; XSRF-TOKEN=" + csrfToken)
        .header("X-XSRF-TOKEN", csrfToken)
        .when()
        .post("/logout")
        .then()
        .statusCode(HttpStatus.OK.value())
        .body("logoutUrl", startsWith(KEYCLOAK_WIREMOCK.baseUrl() + KeycloakStub.END_SESSION_PATH))
        .body("logoutUrl", containsString("id_token_hint="));

    given()
        .header("Cookie", SESSION_COOKIE + "=" + session)
        .when()
        .get("/api/v1/me")
        .then()
        .statusCode(HttpStatus.UNAUTHORIZED.value());
  }

  @Test
  @DisplayName("given a session, when logging out without the CSRF token then 403")
  void givenSessionWhenLoggingOutWithoutCsrfTokenThen403() {
    var session = finishLogin(startLogin()).getCookie(SESSION_COOKIE);

    given()
        .header("Cookie", SESSION_COOKIE + "=" + session)
        .when()
        .post("/logout")
        .then()
        .statusCode(HttpStatus.FORBIDDEN.value());
  }

  private static Response startLogin() {
    var response = given().redirects().follow(false).when().get("/login");
    response.then().statusCode(HttpStatus.FOUND.value());
    return response;
  }

  /** Plays Keycloak's part: the user logged in, and Keycloak sends the browser back. */
  private Response finishLogin(Response login) {
    var location = login.header("Location");
    KeycloakStub.stubTokenEndpoint(queryParameter(location, "nonce"), ACCESS_TOKEN);
    return given()
        .redirects()
        .follow(false)
        .header("Cookie", SESSION_COOKIE + "=" + login.getCookie(SESSION_COOKIE))
        .queryParam("code", "a-code")
        .queryParam("state", queryParameter(location, "state"))
        .when()
        .get("/login/callback");
  }

  /** The CSRF token the frontend reads from the {@code XSRF-TOKEN} cookie. */
  private static String csrfToken(String session) {
    return given()
        .header("Cookie", SESSION_COOKIE + "=" + session)
        .when()
        .get("/api/v1/me")
        .getCookie("XSRF-TOKEN");
  }

  private String callbackUrl() {
    return "http://localhost:" + port + "/login/callback";
  }

  private static String queryParameter(String url, String name) {
    var value = UriComponentsBuilder.fromUriString(url).build().getQueryParams().getFirst(name);
    return URLDecoder.decode(value, StandardCharsets.UTF_8);
  }
}
