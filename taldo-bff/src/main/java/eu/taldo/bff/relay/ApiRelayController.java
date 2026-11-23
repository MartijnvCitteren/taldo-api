package eu.taldo.bff.relay;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Relays the frontend's API calls to taldo-api with the user's access token (ADR 0009, section 1).
 * The paths are the same on both sides ({@code /api/v1/**}), so the frontend calls the API as if it
 * were there. The relay adds no rules: taldo-api decides every answer, and the relay passes it on
 * as it is.
 *
 * <p>Only a short list of headers crosses in either direction. The browser's cookies (the session)
 * and the CSRF header never reach taldo-api; the access token never reaches the browser. A {@code
 * Location} that points into taldo-api is made relative, because the browser cannot reach that
 * address.
 */
@Log4j2
@RestController
@RequiredArgsConstructor
class ApiRelayController {
  private static final String RELAYED_PATHS = "/api/v1/**";
  private static final List<String> RELAYED_REQUEST_HEADERS =
      List.of(HttpHeaders.CONTENT_TYPE, HttpHeaders.ACCEPT, HttpHeaders.ACCEPT_LANGUAGE);
  private static final List<String> RELAYED_RESPONSE_HEADERS =
      List.of(HttpHeaders.CONTENT_TYPE, HttpHeaders.ACCEPT, HttpHeaders.RETRY_AFTER);

  private final AccessTokens accessTokens;
  private final RestClient apiClient;
  private final ApiProperties apiProperties;

  /** A read: safe, so it needs no CSRF token. */
  @GetMapping(RELAYED_PATHS)
  ResponseEntity<byte[]> relayRead(
      HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
    return relay(request, response, authentication, null);
  }

  /**
   * A change: Spring Security has checked the CSRF token before this method runs. The methods are
   * the ones taldo-api's contracts use.
   */
  @RequestMapping(
      path = RELAYED_PATHS,
      method = {RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE})
  ResponseEntity<byte[]> relayChange(
      HttpServletRequest request,
      HttpServletResponse response,
      Authentication authentication,
      @RequestBody(required = false) byte @Nullable [] body) {
    return relay(request, response, authentication, body);
  }

  private ResponseEntity<byte[]> relay(
      HttpServletRequest request,
      HttpServletResponse response,
      Authentication authentication,
      byte @Nullable [] body) {
    var accessToken = accessTokens.currentAccessToken(authentication, request, response);
    var apiRequest =
        apiClient
            .method(HttpMethod.valueOf(request.getMethod()))
            .uri(apiUri(request))
            .headers(headers -> copyRequestHeaders(request, headers))
            .headers(headers -> headers.setBearerAuth(accessToken));
    if (body != null) {
      apiRequest.body(body);
    }
    return apiRequest.exchange((clientRequest, apiResponse) -> toResponse(apiResponse), true);
  }

  @ExceptionHandler(SessionEndedException.class)
  ResponseEntity<Void> handleSessionEnded(SessionEndedException ended, HttpServletRequest request) {
    log.info(
        "Session ended for {} {}: {}",
        request.getMethod(),
        request.getRequestURI(),
        ended.getMessage());
    var session = request.getSession(false);
    if (session != null) {
      session.invalidate();
    }
    SecurityContextHolder.clearContext();
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
  }

  @ExceptionHandler(IdentityProviderUnavailableException.class)
  ResponseEntity<ProblemDetail> handleIdentityProviderUnavailable(
      IdentityProviderUnavailableException unavailable) {
    log.warn("Cannot renew an access token", unavailable);
    return problem(HttpStatus.SERVICE_UNAVAILABLE, "Login service unavailable. Try again later.");
  }

  @ExceptionHandler(ResourceAccessException.class)
  ResponseEntity<ProblemDetail> handleApiUnreachable(ResourceAccessException unreachable) {
    log.warn("taldo-api cannot be reached", unreachable);
    return problem(HttpStatus.BAD_GATEWAY, "The service is unavailable. Try again later.");
  }

  /** The same path and raw query on taldo-api; the query is passed on without re-encoding it. */
  private URI apiUri(HttpServletRequest request) {
    var query = request.getQueryString();
    return URI.create(
        apiProperties.baseUrl() + request.getRequestURI() + (query == null ? "" : "?" + query));
  }

  private static void copyRequestHeaders(HttpServletRequest request, HttpHeaders headers) {
    for (var name : RELAYED_REQUEST_HEADERS) {
      var value = request.getHeader(name);
      if (value != null) {
        headers.set(name, value);
      }
    }
  }

  private ResponseEntity<byte[]> toResponse(
      RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse apiResponse) throws IOException {
    var headers = new HttpHeaders();
    RELAYED_RESPONSE_HEADERS.forEach(name -> {
      var values = apiResponse.getHeaders().get(name);
      if (values != null) {
        headers.put(name, values);
      }
    });
    var location = apiResponse.getHeaders().getLocation();
    if (location != null) {
      headers.setLocation(toBrowserLocation(location));
    }
    return ResponseEntity.status(apiResponse.getStatusCode())
        .headers(headers)
        .body(apiResponse.getBody().readAllBytes());
  }

  /** A location inside taldo-api becomes a path on the browser's own origin. */
  private URI toBrowserLocation(URI location) {
    var apiLocation = location.toString();
    if (apiLocation.startsWith(apiProperties.baseUrl())) {
      return URI.create(apiLocation.substring(apiProperties.baseUrl().length()));
    }
    return location;
  }

  private static ResponseEntity<ProblemDetail> problem(HttpStatus status, String detail) {
    return ResponseEntity.status(status).body(ProblemDetail.forStatusAndDetail(status, detail));
  }
}
