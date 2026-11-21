package eu.taldo.api.adapter.in.rest.identity.mapper.response;

import eu.taldo.api.application.port.in.identity.UserProfile;
import eu.taldo.api.identity.adapter.in.web.contract.ProfileResponse;
import eu.taldo.api.identity.adapter.in.web.contract.ProfileStatus;
import java.util.Optional;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * Maps the logged-in user's profile to the contract. An account without a Taldo user only gets the
 * status {@code ONBOARDING_REQUIRED}. The status, job role and membership enums match by name.
 */
@Mapper(
    componentModel = "spring",
    unmappedSourcePolicy = ReportingPolicy.IGNORE,
    unmappedTargetPolicy = ReportingPolicy.WARN)
public interface ProfileResponseMapper {

  default ProfileResponse toResponse(Optional<UserProfile> profile) {
    return profile
        .map(this::toResponse)
        .orElseGet(() -> new ProfileResponse(ProfileStatus.ONBOARDING_REQUIRED));
  }

  @Mapping(target = "email", source = "email.value")
  ProfileResponse toResponse(UserProfile profile);
}
