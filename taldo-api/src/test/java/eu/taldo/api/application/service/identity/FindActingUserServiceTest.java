package eu.taldo.api.application.service.identity;

import static eu.taldo.api.testSupport.IdentityFactory.SUBJECT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import eu.taldo.api.application.port.in.identity.ActingUser;
import eu.taldo.api.application.port.out.identity.UserRepository;
import eu.taldo.api.testSupport.IdentityFactory;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FindActingUserServiceTest {
  private static final UUID COMPANY_ID = UUID.randomUUID();

  @Mock private UserRepository userRepository;

  @InjectMocks private FindActingUserService service;

  @Test
  @DisplayName(
      "given an active user, when finding the acting user then it is that user in their company")
  void givenActiveUserWhenFindingTheActingUserThenItIsThatUserInTheirCompany() {
    var owner = IdentityFactory.owner(COMPANY_ID);
    when(userRepository.findBySubject(SUBJECT)).thenReturn(Optional.of(owner));

    var actingUser = service.findActingUser(SUBJECT);

    assertEquals(Optional.of(new ActingUser(owner.getId(), COMPANY_ID)), actingUser);
  }

  @Test
  @DisplayName("given a user pending approval, when finding the acting user then there is none")
  void givenUserPendingApprovalWhenFindingTheActingUserThenThereIsNone() {
    when(userRepository.findBySubject(SUBJECT))
        .thenReturn(Optional.of(IdentityFactory.member(COMPANY_ID)));

    var actingUser = service.findActingUser(SUBJECT);

    assertTrue(actingUser.isEmpty());
  }

  @Test
  @DisplayName("given an account without user, when finding the acting user then there is none")
  void givenAccountWithoutUserWhenFindingTheActingUserThenThereIsNone() {
    when(userRepository.findBySubject(SUBJECT)).thenReturn(Optional.empty());

    var actingUser = service.findActingUser(SUBJECT);

    assertTrue(actingUser.isEmpty());
  }
}
