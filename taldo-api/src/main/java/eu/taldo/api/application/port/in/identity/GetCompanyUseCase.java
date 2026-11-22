package eu.taldo.api.application.port.in.identity;

import eu.taldo.api.domain.identity.Company;
import java.util.UUID;

public interface GetCompanyUseCase {

  /**
   * Gets a company that must exist, such as a user's company.
   *
   * @throws IllegalStateException when it does not exist: a caller's company id comes from our own
   *     data, so a missing company is our bug
   */
  Company getCompany(UUID companyId);
}
