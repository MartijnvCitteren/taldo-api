package eu.taldo.api.integration.identity;

import static eu.taldo.api.testSupport.AcmeAccounts.asAcmeOwner;
import static eu.taldo.api.testSupport.KeycloakStub.KEYCLOAK_WIREMOCK;
import static eu.taldo.api.testSupport.KeycloakStub.accessToken;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import com.github.tomakehurst.wiremock.client.WireMock;
import eu.taldo.api.integration.BaseIntegrationTest;
import eu.taldo.api.testSupport.AcmeAccounts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.jdbc.Sql;

/**
 * The logged-in user's profile ({@code GET /me}), only through the API. It answers for every
 * authenticated account, also before onboarding and while waiting for approval, from Taldo's copy:
 * Keycloak is never asked. The expected values are those of the seed ({@code identity-acme.sql}).
 */
@Sql(AcmeAccounts.SEED)
class GetProfileIntegrationTest extends BaseIntegrationTest {
  private static final String NOT_ONBOARDED_SUBJECT = "6f1d2c3b-4a5e-4f60-8a7b-9c0d1e2f3a4b";

  @Test
  @DisplayName(
      "given an active owner, when getting the profile then 200 with their profile and company, and"
          + " Keycloak is not asked")
  void givenActiveOwnerWhenGettingTheProfileThen200WithProfileAndCompany() {
    asAcmeOwner()
        .when()
        .get("/me")
        .then()
        .statusCode(HttpStatus.OK.value())
        .body("status", equalTo("ACTIVE"))
        .body("firstName", equalTo("Anna"))
        .body("lastName", equalTo("de Vries"))
        .body("email", equalTo("anna@acme.nl"))
        .body("jobRole", equalTo("FOUNDER_OR_DIRECTOR"))
        .body("membership", equalTo("OWNER"))
        .body("companyName", equalTo("Acme"));

    KEYCLOAK_WIREMOCK.verify(0, WireMock.anyRequestedFor(WireMock.urlMatching("/admin/.*")));
  }

  @Test
  @DisplayName(
      "given a member pending approval, when getting the profile then 200 with status pending"
          + " approval")
  void givenMemberPendingApprovalWhenGettingTheProfileThen200PendingApproval() {
    given()
        .auth()
        .oauth2(accessToken(AcmeAccounts.PENDING_MEMBER_SUBJECT))
        .when()
        .get("/me")
        .then()
        .statusCode(HttpStatus.OK.value())
        .body("status", equalTo("PENDING_APPROVAL"))
        .body("membership", equalTo("MEMBER"))
        .body("companyName", equalTo("Acme"));
  }

  @Test
  @DisplayName(
      "given an account that has not onboarded, when getting the profile then 200 with status"
          + " onboarding required and no profile")
  void givenAccountThatHasNotOnboardedWhenGettingTheProfileThen200OnboardingRequired() {
    given()
        .auth()
        .oauth2(accessToken(NOT_ONBOARDED_SUBJECT))
        .when()
        .get("/me")
        .then()
        .statusCode(HttpStatus.OK.value())
        .body("status", equalTo("ONBOARDING_REQUIRED"))
        .body("email", nullValue())
        .body("companyName", nullValue());
  }

  @Test
  @DisplayName("given no access token, when getting the profile then 401")
  void givenNoAccessTokenWhenGettingTheProfileThen401() {
    given().when().get("/me").then().statusCode(HttpStatus.UNAUTHORIZED.value());
  }
}
