package eu.taldo.api.application.service.identity;

import eu.taldo.api.application.port.in.identity.GetCompanyUseCase;
import eu.taldo.api.application.port.out.identity.CompanyRepository;
import eu.taldo.api.domain.identity.Company;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GetCompanyService implements GetCompanyUseCase {
  private final CompanyRepository companyRepository;

  @Override
  public Company getCompany(UUID companyId) {
    return companyRepository
        .findById(companyId)
        .orElseThrow(() -> new IllegalStateException("Company " + companyId + " does not exist"));
  }
}
