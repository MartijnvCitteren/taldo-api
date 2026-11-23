package eu.taldo.bff;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static eu.taldo.bff.BrowserLogin.ACCESS_TOKEN;
import static eu.taldo.bff.BrowserLogin.SESSION_COOKIE;
import static eu.taldo.bff.BrowserLogin.csrfToken;
import static eu.taldo.bff.BrowserLogin.finishLogin;
import static eu.taldo.bff.BrowserLogin.loggedInSession;
import static eu.taldo.bff.BrowserLogin.startLogin;
import static eu.taldo.bff.KeycloakStub.KEYCLOAK_WIREMOCK;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

import com.github.tomakehurst.wiremock.http.Fault;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/**
 * Relaying the frontend's API calls to taldo-api, only over HTTP. taldo-api and Keycloak are
 * replaced by WireMock; what taldo-api receives is the observable effect of a relayed call.
 */
class ApiRelayIntegrationTest extends BffIntegrationTest {
  private static final String PROFILE = "{\"status\": \"ACTIVE\", \"firstName\": \"Anna\"}";
  private static final String VACANCY_REQUEST = "{\"jobTitle\": \"Sales Manager\"}";

  @Test
  @DisplayName(
      "given a session, when reading through the API then taldo-api gets the same path and query"
          + " with the access token and no cookies, and its answer is passed on")
  void givenSessionWhenReadingThroughTheApiThenRelayedWithAccessTokenAndAnswerPassedOn() {
    API_WIREMOCK.stubFor(
        get(urlEqualTo("/api/v1/me?view=full"))
            .willReturn(
                aResponse().withHeader("Content-Type", "application/json").withBody(PROFILE)));

    withSession(loggedInSession())
        .when()
        .get("/api/v1/me?view=full")
        .then()
        .statusCode(HttpStatus.OK.value())
        .contentType(ContentType.JSON)
        .body("firstName", is("Anna"));

    API_WIREMOCK.verify(
        getRequestedFor(urlEqualTo("/api/v1/me?view=full"))
            .withHeader("Authorization", equalTo("Bearer " + ACCESS_TOKEN))
            .withHeader("Cookie", absent()));
  }

  @Test
  @DisplayName(
      "given a session and the CSRF token, when creating through the API then taldo-api gets the"
          + " body without the CSRF header, and its location becomes a path on the browser's origin")
  void givenSessionAndCsrfTokenWhenCreatingThroughTheApiThenRelayedWithRelativeLocation() {
    var session = loggedInSession();
    var csrfToken = csrfToken(session);
    API_WIREMOCK.stubFor(
        post(urlEqualTo("/api/v1/vacancy"))
            .willReturn(
                aResponse()
                    .withStatus(201)
                    .withHeader("Location", API_WIREMOCK.baseUrl() + "/api/v1/vacancy/42")
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"id\": \"42\"}")));

    given()
        .header("Cookie", SESSION_COOKIE + "=" + session + "; XSRF-TOKEN=" + csrfToken)
        .header("X-XSRF-TOKEN", csrfToken)
        .contentType(ContentType.JSON)
        .body(VACANCY_REQUEST)
        .when()
        .post("/api/v1/vacancy")
        .then()
        .statusCode(HttpStatus.CREATED.value())
        .header("Location", "/api/v1/vacancy/42");

    API_WIREMOCK.verify(
        postRequestedFor(urlEqualTo("/api/v1/vacancy"))
            .withHeader("Authorization", equalTo("Bearer " + ACCESS_TOKEN))
            .withHeader("Content-Type", equalTo("application/json"))
            .withHeader("X-XSRF-TOKEN", absent())
            .withRequestBody(equalToJson(VACANCY_REQUEST)));
  }

  @Test
  @DisplayName(
      "given a session, when creating without the CSRF token then 403 and taldo-api is not called")
  void givenSessionWhenCreatingWithoutCsrfTokenThen403AndApiNotCalled() {
    withSession(loggedInSession())
        .contentType(ContentType.JSON)
        .body(VACANCY_REQUEST)
        .when()
        .post("/api/v1/vacancy")
        .then()
        .statusCode(HttpStatus.FORBIDDEN.value());

    API_WIREMOCK.verify(0, anyRequestedFor(anyUrl()));
  }

  @Test
  @DisplayName(
      "given taldo-api answers a problem, when relayed then the problem is passed on as it is")
  void givenApiAnswersProblemWhenRelayedThenProblemPassedOn() {
    API_WIREMOCK.stubFor(
        get(urlEqualTo("/api/v1/vacancy/unknown"))
            .willReturn(
                aResponse()
                    .withStatus(404)
                    .withHeader("Content-Type", "application/problem+json")
                    .withBody("{\"title\": \"Not found\", \"status\": 404}")));

    withSession(loggedInSession())
        .when()
        .get("/api/v1/vacancy/unknown")
        .then()
        .statusCode(HttpStatus.NOT_FOUND.value())
        .contentType("application/problem+json")
        .body("title", is("Not found"));
  }

  @Test
  @DisplayName(
      "given an access token that has expired, when relayed then it is renewed first and taldo-api"
          + " gets the new one")
  void givenExpiredAccessTokenWhenRelayedThenRenewedFirst() {
    var session = finishLogin(startLogin(), 30).getCookie(SESSION_COOKIE);
    KeycloakStub.stubRefresh("renewed-access-token");
    API_WIREMOCK.stubFor(any(anyUrl()).willReturn(aResponse().withStatus(200)));

    withSession(session).when().get("/api/v1/me").then().statusCode(HttpStatus.OK.value());

    API_WIREMOCK.verify(
        getRequestedFor(urlEqualTo("/api/v1/me"))
            .withHeader("Authorization", equalTo("Bearer renewed-access-token")));
  }

  @Test
  @DisplayName(
      "given Keycloak refuses to renew the tokens, when relayed then 401 and the session is gone")
  void givenKeycloakRefusesToRenewWhenRelayedThen401AndSessionGone() {
    var session = finishLogin(startLogin(), 30).getCookie(SESSION_COOKIE);
    KeycloakStub.stubRefreshRefused();

    withSession(session)
        .when()
        .get("/api/v1/me")
        .then()
        .statusCode(HttpStatus.UNAUTHORIZED.value());
    KEYCLOAK_WIREMOCK.resetRequests();
    withSession(session)
        .when()
        .get("/api/v1/me")
        .then()
        .statusCode(HttpStatus.UNAUTHORIZED.value());

    KEYCLOAK_WIREMOCK.verify(0, postRequestedFor(urlEqualTo(KeycloakStub.TOKEN_PATH)));
    API_WIREMOCK.verify(0, anyRequestedFor(anyUrl()));
  }

  @Test
  @DisplayName(
      "given Keycloak is unavailable for a renewal, when relayed then 503 and the session stays")
  void givenKeycloakUnavailableForRenewalWhenRelayedThen503AndSessionStays() {
    var session = finishLogin(startLogin(), 30).getCookie(SESSION_COOKIE);
    KeycloakStub.stubRefreshUnavailable();
    withSession(session)
        .when()
        .get("/api/v1/me")
        .then()
        .statusCode(HttpStatus.SERVICE_UNAVAILABLE.value());

    KeycloakStub.stubRefresh("renewed-access-token");
    API_WIREMOCK.stubFor(any(anyUrl()).willReturn(aResponse().withStatus(200)));

    withSession(session).when().get("/api/v1/me").then().statusCode(HttpStatus.OK.value());
  }

  @Test
  @DisplayName("given taldo-api cannot be reached, when relayed then 502")
  void givenApiCannotBeReachedWhenRelayedThen502() {
    API_WIREMOCK.stubFor(
        any(anyUrl()).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

    withSession(loggedInSession())
        .when()
        .get("/api/v1/me")
        .then()
        .statusCode(HttpStatus.BAD_GATEWAY.value());
  }

  private static RequestSpecification withSession(String session) {
    return given().header("Cookie", SESSION_COOKIE + "=" + session);
  }
}
