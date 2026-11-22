package eu.taldo.api.application.service.identity;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import eu.taldo.api.application.port.out.identity.CompanyRepository;
import eu.taldo.api.testSupport.IdentityFactory;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetCompanyServiceTest {

  @Mock private CompanyRepository companyRepository;

  @InjectMocks private GetCompanyService service;

  @Test
  @DisplayName("given a company that does not exist, when getting it then it fails")
  void givenCompanyThatDoesNotExistWhenGettingItThenItFails() {
    var companyId = IdentityFactory.company().getId();
    when(companyRepository.findById(companyId)).thenReturn(Optional.empty());

    assertThrows(IllegalStateException.class, () -> service.getCompany(companyId));
  }
}
