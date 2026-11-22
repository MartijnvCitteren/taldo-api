package eu.taldo.api.integration.security;

import static eu.taldo.api.testSupport.AcmeAccounts.asAcmeOwner;
import static eu.taldo.api.testSupport.KeycloakStub.accessToken;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import eu.taldo.api.integration.BaseIntegrationTest;
import eu.taldo.api.shared.Constants;
import eu.taldo.api.testSupport.AcmeAccounts;
import io.restassured.http.ContentType;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.jdbc.Sql;

/**
 * Company endpoints are only for accounts whose Taldo user may access company data (ADR 0006,
 * revision 2026-11-21, point 5). Taldo decides that from its own user, not from the token: a valid
 * token of an account that has not onboarded, or whose user waits for approval, gets {@code 403}.
 */
@Sql(AcmeAccounts.SEED)
class CompanyAccessIntegrationTest extends BaseIntegrationTest {
  private static final String NOT_ONBOARDED_SUBJECT = "6f1d2c3b-4a5e-4f60-8a7b-9c0d1e2f3a4b";
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
  @MethodSource("accountsWithoutCompanyAccess")
  @DisplayName(
      "given an account without company access, when calling a company endpoint then 403 with a"
          + " problem body")
  void givenAccountWithoutCompanyAccessWhenCallingCompanyEndpointThen403WithProblem(
      String account, String subject) {
    given()
        .auth()
        .oauth2(accessToken(subject))
        .contentType(ContentType.JSON)
        .body(VALID_VACANCY_REQUEST)
        .when()
        .post("/vacancy")
        .then()
        .statusCode(HttpStatus.FORBIDDEN.value())
        .contentType("application/problem+json")
        .body("title", equalTo(Constants.COMPANY_ACCESS_DENIED_TITLE))
        .body("detail", equalTo(Constants.COMPANY_ACCESS_DENIED_DETAIL));
  }

  private static Stream<Arguments> accountsWithoutCompanyAccess() {
    return Stream.of(
        arguments("not onboarded", NOT_ONBOARDED_SUBJECT),
        arguments("waiting for approval", AcmeAccounts.PENDING_MEMBER_SUBJECT));
  }

  @Test
  @DisplayName("given an active user, when calling a company endpoint then the request is served")
  void givenActiveUserWhenCallingCompanyEndpointThenTheRequestIsServed() {
    asAcmeOwner()
        .contentType(ContentType.JSON)
        .body(VALID_VACANCY_REQUEST)
        .when()
        .post("/vacancy")
        .then()
        .statusCode(HttpStatus.CREATED.value());
  }
}
