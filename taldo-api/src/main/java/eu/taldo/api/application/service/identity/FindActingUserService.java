package eu.taldo.api.application.service.identity;

import eu.taldo.api.application.port.in.identity.ActingUser;
import eu.taldo.api.application.port.in.identity.FindActingUserUseCase;
import eu.taldo.api.application.port.out.identity.UserRepository;
import eu.taldo.api.domain.identity.User;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Resolves the acting user of a request from its access token's subject. Runs on every
 * authenticated request: one lookup by the unique, indexed subject.
 */
@Service
@RequiredArgsConstructor
public class FindActingUserService implements FindActingUserUseCase {
  private final UserRepository userRepository;

  @Override
  public Optional<ActingUser> findActingUser(String subject) {
    return userRepository
        .findBySubject(subject)
        .filter(User::mayAccessCompanyData)
        .map(user -> new ActingUser(user.getId(), user.getCompanyId()));
  }
}
