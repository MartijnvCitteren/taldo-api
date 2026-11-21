package eu.taldo.api.testSupport;

import static eu.taldo.api.testSupport.KeycloakStub.accessToken;
import static io.restassured.RestAssured.given;

import io.restassured.specification.RequestSpecification;

/**
 * The accounts of the seeded company {@code acme.nl} ({@code sql/identity-acme.sql}) and requests
 * sent as them, with an access token signed by the stub realm ({@link KeycloakStub}).
 */
public final class AcmeAccounts {
  public static final String SEED = "/sql/identity-acme.sql";

  /** Anna de Vries, founder and owner of Acme: an active user with access to company data. */
  public static final String OWNER_SUBJECT = "5b9c3f0e-0000-4000-8000-00000000a003";

  /** Piet Jansen, a colleague who waits for the owner's approval: no access to company data. */
  public static final String PENDING_MEMBER_SUBJECT = "5b9c3f0e-0000-4000-8000-00000000a005";

  private AcmeAccounts() {}

  /** A request sent as Acme's owner. */
  public static RequestSpecification asAcmeOwner() {
    return given().auth().oauth2(accessToken(OWNER_SUBJECT));
  }
}
