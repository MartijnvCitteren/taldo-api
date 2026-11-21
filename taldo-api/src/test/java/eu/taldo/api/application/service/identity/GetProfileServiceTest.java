package eu.taldo.api.application.service.identity;

import static eu.taldo.api.testSupport.IdentityFactory.SUBJECT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import eu.taldo.api.application.port.in.identity.UserProfile;
import eu.taldo.api.application.port.out.identity.CompanyRepository;
import eu.taldo.api.application.port.out.identity.UserRepository;
import eu.taldo.api.testSupport.IdentityFactory;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetProfileServiceTest {

  @Mock private UserRepository userRepository;
  @Mock private CompanyRepository companyRepository;

  @InjectMocks private GetProfileService service;

  @Test
  @DisplayName("given an onboarded user, when getting the profile then the user with their company")
  void givenOnboardedUserWhenGettingTheProfileThenTheUserWithTheirCompany() {
    var company = IdentityFactory.company();
    var user = IdentityFactory.owner(company.getId());
    when(userRepository.findBySubject(SUBJECT)).thenReturn(Optional.of(user));
    when(companyRepository.findById(company.getId())).thenReturn(Optional.of(company));

    var profile = service.getProfile(SUBJECT);

    assertEquals(Optional.of(UserProfile.of(user, company)), profile);
  }

  @Test
  @DisplayName("given an account that has not onboarded, when getting the profile then none")
  void givenAccountThatHasNotOnboardedWhenGettingTheProfileThenNone() {
    when(userRepository.findBySubject(SUBJECT)).thenReturn(Optional.empty());

    var profile = service.getProfile(SUBJECT);

    assertTrue(profile.isEmpty());
  }

  @Test
  @DisplayName("given a user whose company is missing, when getting the profile then it fails")
  void givenUserWhoseCompanyIsMissingWhenGettingTheProfileThenItFails() {
    var user = IdentityFactory.owner(IdentityFactory.company().getId());
    when(userRepository.findBySubject(SUBJECT)).thenReturn(Optional.of(user));
    when(companyRepository.findById(user.getCompanyId())).thenReturn(Optional.empty());

    assertThrows(IllegalStateException.class, () -> service.getProfile(SUBJECT));
  }
}
