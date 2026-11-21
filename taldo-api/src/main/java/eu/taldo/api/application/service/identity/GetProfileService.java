package eu.taldo.api.application.service.identity;

import eu.taldo.api.application.port.in.identity.GetProfileUseCase;
import eu.taldo.api.application.port.in.identity.UserProfile;
import eu.taldo.api.application.port.out.identity.CompanyRepository;
import eu.taldo.api.application.port.out.identity.UserRepository;
import eu.taldo.api.domain.identity.Company;
import eu.taldo.api.domain.identity.User;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Reads the logged-in user's own profile from Taldo's copy (ADR 0006, section 5), without calling
 * the identity provider. Also answers for an account that has not onboarded or waits for approval:
 * the frontend uses it to decide what to show next.
 */
@Service
@RequiredArgsConstructor
public class GetProfileService implements GetProfileUseCase {
  private final UserRepository userRepository;
  private final CompanyRepository companyRepository;

  @Override
  public Optional<UserProfile> getProfile(String subject) {
    return userRepository
        .findBySubject(subject)
        .map(user -> UserProfile.of(user, getCompany(user)));
  }

  /** A user's company always exists: {@code app_user.company_id} is a foreign key. */
  private Company getCompany(User user) {
    return companyRepository
        .findById(user.getCompanyId())
        .orElseThrow(() -> new IllegalStateException("The company of " + user + " does not exist"));
  }
}
