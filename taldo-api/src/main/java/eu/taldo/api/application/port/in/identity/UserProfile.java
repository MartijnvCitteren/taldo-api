package eu.taldo.api.application.port.in.identity;

import eu.taldo.api.domain.identity.Company;
import eu.taldo.api.domain.identity.User;
import eu.taldo.api.domain.identity.valueobject.EmailAddress;
import eu.taldo.api.domain.identity.valueobject.JobRole;
import eu.taldo.api.domain.identity.valueobject.Membership;
import eu.taldo.api.domain.identity.valueobject.UserStatus;

/**
 * An onboarded user's own profile, as they see it: a read-only snapshot of the user and the name of
 * their company. Only immutable values, so no caller can change the aggregates through it.
 */
public record UserProfile(
    String firstName,
    String lastName,
    EmailAddress email,
    JobRole jobRole,
    Membership membership,
    UserStatus status,
    String companyName) {

  public static UserProfile of(User user, Company company) {
    return new UserProfile(
        user.getFirstName(),
        user.getLastName(),
        user.getEmail(),
        user.getJobRole(),
        user.getMembership(),
        user.getStatus(),
        company.getName());
  }
}
