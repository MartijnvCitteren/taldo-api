package eu.taldo.api.integration.security;

import static eu.taldo.api.testSupport.KeycloakStub.expiredAccessToken;
import static eu.taldo.api.testSupport.KeycloakStub.foreignAccessToken;
import static eu.taldo.api.testSupport.KeycloakStub.otherAudienceAccessToken;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import eu.taldo.api.integration.BaseIntegrationTest;
import eu.taldo.api.shared.Constants;
import io.restassured.http.ContentType;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;

/**
 * Every endpoint requires a valid access token: without one, or with an expired, forged or
 * malformed one, a company endpoint answers {@code 401} with the RFC 6750 challenge and a problem
 * body that does not say why the token was rejected.
 */
class InvalidAccessTokenIntegrationTest extends BaseIntegrationTest {
  private static final String SUBJECT = "6f1d2c3b-4a5e-4f60-8a7b-9c0d1e2f3a4b";
  private static final String NO_TOKEN_CHALLENGE = "Bearer";
  private static final String REJECTED_TOKEN_CHALLENGE = "Bearer error=\"invalid_token\"";
  private static final String VALID_VACANCY_REQUEST =
      """
      {
        "jobTitle": "Sales Manager",
        "language": "NL",
        "category": "SALES",
        "location": {"country": "NL", "city": "Amsterdam"},
        "workplaceType": "HYBRID",
        "minHoursPerWeek": 32,
        "maxHoursPerWeek": 40
      }
      """;

  @ParameterizedTest(name = "{0}")
  @MethodSource("unauthenticatedRequests")
  @DisplayName(
      "given no valid access token, when calling a company endpoint then 401 with the challenge"
          + " and a problem body")
  void givenNoValidAccessTokenWhenCallingCompanyEndpointThen401WithChallengeAndProblem(
      String problem, @Nullable String accessToken, String challenge) {
    var request = accessToken == null ? given() : given().auth().oauth2(accessToken);

    request
        .contentType(ContentType.JSON)
        .body(VALID_VACANCY_REQUEST)
        .when()
        .post("/vacancy")
        .then()
        .statusCode(HttpStatus.UNAUTHORIZED.value())
        .header("WWW-Authenticate", challenge)
        .contentType("application/problem+json")
        .body("title", equalTo(Constants.UNAUTHORIZED_TITLE))
        .body("detail", equalTo(Constants.UNAUTHORIZED_DETAIL));
  }

  private static Stream<Arguments> unauthenticatedRequests() {
    return Stream.of(
        arguments("no token", null, NO_TOKEN_CHALLENGE),
        arguments("expired token", expiredAccessToken(SUBJECT), REJECTED_TOKEN_CHALLENGE),
        arguments(
            "token signed with another key", foreignAccessToken(SUBJECT), REJECTED_TOKEN_CHALLENGE),
        arguments(
            "token for another audience",
            otherAudienceAccessToken(SUBJECT),
            REJECTED_TOKEN_CHALLENGE),
        arguments("not a token", "not-a-jwt", REJECTED_TOKEN_CHALLENGE));
  }
}
