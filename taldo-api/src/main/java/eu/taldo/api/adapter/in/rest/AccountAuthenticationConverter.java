package eu.taldo.api.adapter.in.rest;

import eu.taldo.api.application.port.in.identity.FindActingUserUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.stereotype.Component;

/**
 * Turns a validated access token into an {@link AccountAuthentication}. The token only says who the
 * account is ({@code sub}); whether it may access company data, and which company, comes from
 * Taldo's own user (ADR 0006, revision 2026-11-21, point 1). Rights do not come from the token, so
 * the realm's roles are not read.
 */
@Component
@RequiredArgsConstructor
public class AccountAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {
  private final FindActingUserUseCase findActingUserUseCase;

  /**
   * @throws InvalidBearerTokenException when the token has no subject, which the realm's {@code
   *     sub} mapper should always add: answered with {@code 401} like any other unusable token
   */
  @Override
  public AbstractAuthenticationToken convert(Jwt token) {
    var subject = token.getSubject();
    if (subject == null) {
      throw new InvalidBearerTokenException("The access token has no subject");
    }
    return findActingUserUseCase
        .findActingUser(subject)
        .map(actingUser -> AccountAuthentication.withCompanyAccess(token, actingUser))
        .orElseGet(() -> AccountAuthentication.withoutCompanyAccess(token));
  }
}
