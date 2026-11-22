package eu.taldo.api.adapter.in.rest;

import eu.taldo.api.application.port.in.identity.ActingUser;
import eu.taldo.api.shared.config.security.SecurityConfig;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.AbstractOAuth2TokenAuthenticationToken;

/**
 * A validated access token and, when the account may access company data, the user it acts as. Only
 * such an authentication carries the {@link SecurityConfig#COMPANY_ACCESS} authority. The acting
 * user is kept as its two ids, so the authentication stays serializable as Spring Security expects.
 */
public final class AccountAuthentication extends AbstractOAuth2TokenAuthenticationToken<Jwt> {
  private final @Nullable UUID userId;
  private final @Nullable UUID companyId;

  private AccountAuthentication(Jwt token, @Nullable ActingUser actingUser) {
    super(
        token,
        actingUser == null
            ? List.of()
            : List.of(new SimpleGrantedAuthority(SecurityConfig.COMPANY_ACCESS)));
    this.userId = actingUser == null ? null : actingUser.userId();
    this.companyId = actingUser == null ? null : actingUser.companyId();
    setAuthenticated(true);
  }

  static AccountAuthentication withCompanyAccess(Jwt token, ActingUser actingUser) {
    return new AccountAuthentication(token, actingUser);
  }

  static AccountAuthentication withoutCompanyAccess(Jwt token) {
    return new AccountAuthentication(token, null);
  }

  /** The user the request acts as; empty when the account may not access company data. */
  public Optional<ActingUser> getActingUser() {
    if (userId == null || companyId == null) {
      return Optional.empty();
    }
    return Optional.of(new ActingUser(userId, companyId));
  }

  /** The account's id at the identity provider: the token's {@code sub}. */
  @Override
  public String getName() {
    return getToken().getSubject();
  }

  @Override
  public Map<String, Object> getTokenAttributes() {
    return getToken().getClaims();
  }
}
