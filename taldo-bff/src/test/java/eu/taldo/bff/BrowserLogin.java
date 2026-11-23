package eu.taldo.bff;

import static io.restassured.RestAssured.given;

import io.restassured.response.Response;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * What a browser does to log in through the BFF, with Keycloak's part played by {@link
 * KeycloakStub}. The session cookie is {@code Secure}, which an HTTP client does not send back over
 * plain HTTP on its own, so it is sent explicitly.
 */
final class BrowserLogin {
  static final String SESSION_COOKIE = "__Host-session";
  static final String ACCESS_TOKEN = "access-token-of-the-user";

  private BrowserLogin() {}

  static Response startLogin() {
    var response = given().redirects().follow(false).when().get("/login");
    response.then().statusCode(HttpStatus.FOUND.value());
    return response;
  }

  /** The user logged in at Keycloak, and Keycloak sends the browser back to the callback. */
  static Response finishLogin(Response login, int accessTokenSeconds) {
    var location = login.header("Location");
    KeycloakStub.stubTokenEndpoint(
        queryParameter(location, "nonce"), ACCESS_TOKEN, accessTokenSeconds);
    return given()
        .redirects()
        .follow(false)
        .header("Cookie", SESSION_COOKIE + "=" + login.getCookie(SESSION_COOKIE))
        .queryParam("code", "a-code")
        .queryParam("state", queryParameter(location, "state"))
        .when()
        .get("/login/callback");
  }

  /** Logs in with an access token valid for ten minutes and returns the session id. */
  static String loggedInSession() {
    return finishLogin(startLogin(), 600).getCookie(SESSION_COOKIE);
  }

  /**
   * The CSRF token the frontend reads from the {@code XSRF-TOKEN} cookie, which every response to a
   * request with a session sets. Read from a path that is neither relayed nor part of the login.
   */
  static String csrfToken(String session) {
    return given()
        .header("Cookie", SESSION_COOKIE + "=" + session)
        .when()
        .get("/")
        .getCookie("XSRF-TOKEN");
  }

  static String queryParameter(String url, String name) {
    var value = UriComponentsBuilder.fromUriString(url).build().getQueryParams().getFirst(name);
    return URLDecoder.decode(value, StandardCharsets.UTF_8);
  }
}
