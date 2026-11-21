package eu.taldo.api.adapter.in.rest;

import static eu.taldo.api.testSupport.IdentityFactory.SUBJECT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import eu.taldo.api.application.port.in.identity.ActingUser;
import eu.taldo.api.application.port.in.identity.FindActingUserUseCase;
import eu.taldo.api.shared.config.security.SecurityConfig;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

@ExtendWith(MockitoExtension.class)
class AccountAuthenticationConverterTest {
  private static final ActingUser ACTING_USER =
      new ActingUser(UUID.randomUUID(), UUID.randomUUID());

  @Mock private FindActingUserUseCase findActingUserUseCase;

  @InjectMocks private AccountAuthenticationConverter converter;

  @Test
  @DisplayName(
      "given an account with an acting user, when converted then it has company access as that"
          + " user")
  void givenAccountWithActingUserWhenConvertedThenItHasCompanyAccessAsThatUser() {
    when(findActingUserUseCase.findActingUser(SUBJECT)).thenReturn(Optional.of(ACTING_USER));

    var authentication = (AccountAuthentication) converter.convert(token(SUBJECT));

    assertEquals(List.of(SecurityConfig.COMPANY_ACCESS), authorities(authentication));
    assertEquals(Optional.of(ACTING_USER), authentication.getActingUser());
    assertEquals(SUBJECT, authentication.getName());
    assertTrue(authentication.isAuthenticated());
  }

  @Test
  @DisplayName(
      "given an account without an acting user, when converted then it is authenticated without"
          + " company access")
  void givenAccountWithoutActingUserWhenConvertedThenAuthenticatedWithoutCompanyAccess() {
    when(findActingUserUseCase.findActingUser(SUBJECT)).thenReturn(Optional.empty());

    var authentication = (AccountAuthentication) converter.convert(token(SUBJECT));

    assertEquals(List.of(), authorities(authentication));
    assertTrue(authentication.getActingUser().isEmpty());
    assertTrue(authentication.isAuthenticated());
  }

  @Test
  @DisplayName("given a token without subject, when converted then rejected as invalid token")
  void givenTokenWithoutSubjectWhenConvertedThenRejectedAsInvalidToken() {
    var tokenWithoutSubject = token(null);

    assertThrows(InvalidBearerTokenException.class, () -> converter.convert(tokenWithoutSubject));
  }

  private static Jwt token(String subject) {
    var issuedAt = Instant.parse("2026-11-21T09:00:00Z");
    var builder =
        Jwt.withTokenValue("token")
            .header("alg", "RS256")
            .claim("azp", "taldo-web")
            .issuedAt(issuedAt)
            .expiresAt(issuedAt.plusSeconds(600));
    return subject == null ? builder.build() : builder.subject(subject).build();
  }

  private static List<String> authorities(AccountAuthentication authentication) {
    return authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
  }
}
