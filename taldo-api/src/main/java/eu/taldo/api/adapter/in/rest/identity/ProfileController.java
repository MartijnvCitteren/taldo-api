package eu.taldo.api.adapter.in.rest.identity;

import eu.taldo.api.adapter.in.rest.AuthenticatedUser;
import eu.taldo.api.adapter.in.rest.identity.mapper.request.NameChangeRequestMapper;
import eu.taldo.api.adapter.in.rest.identity.mapper.response.ProfileResponseMapper;
import eu.taldo.api.application.port.in.identity.ChangeNameUseCase;
import eu.taldo.api.application.port.in.identity.GetProfileUseCase;
import eu.taldo.api.identity.adapter.in.rest.MeApi;
import eu.taldo.api.identity.adapter.in.web.contract.NameChangeRequest;
import eu.taldo.api.identity.adapter.in.web.contract.ProfileResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * The logged-in user's own profile ({@code /me}). The user comes from the token. Requests and
 * responses are not logged: they carry names.
 */
@RestController
@RequiredArgsConstructor
public class ProfileController implements MeApi {
  private final NameChangeRequestMapper nameChangeRequestMapper;
  private final ChangeNameUseCase changeNameUseCase;
  private final GetProfileUseCase getProfileUseCase;
  private final ProfileResponseMapper profileResponseMapper;

  @Override
  public ResponseEntity<ProfileResponse> getProfile() {
    return ResponseEntity.ok(
        profileResponseMapper.toResponse(
            getProfileUseCase.getProfile(AuthenticatedUser.subject())));
  }

  @Override
  public ResponseEntity<Void> changeName(NameChangeRequest nameChangeRequest) {
    changeNameUseCase.changeName(
        nameChangeRequestMapper.toCommand(AuthenticatedUser.subject(), nameChangeRequest));
    return ResponseEntity.noContent().build();
  }
}
