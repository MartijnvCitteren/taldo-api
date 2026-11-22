package eu.taldo.api.adapter.in.rest;

import eu.taldo.api.shared.Constants;
import eu.taldo.api.vacancy.adapter.in.web.contract.ProblemDetails;
import eu.taldo.api.vacancy.adapter.in.web.contract.ProblemDetailsErrorsInner;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.List;
import lombok.extern.log4j.Log4j2;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Translates exceptions from every REST controller into the {@code ProblemDetails} representation
 * of the API contract. Aggregate-specific exceptions are handled by their own advice classes (e.g.
 * {@code VacancyExceptionHandler}); this class only covers cross-cutting concerns that apply to
 * every controller.
 *
 * <p>Ordered at lowest precedence so its catch-all {@code Exception} handler is only consulted
 * after every aggregate-specific advice has had a chance to handle the exception more precisely:
 * Spring picks the best-matching handler within the first applicable advice bean it visits, not the
 * best match across all advice beans, so an unordered catch-all here would shadow more specific
 * handlers declared in other advice classes.
 */
@Log4j2
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class GlobalExceptionHandler {

  /**
   * Turns bean-validation failures on a request body into an RFC 9457 problem response. Every
   * violation is reported individually under {@code errors}, naming the offending field so the
   * client can point the user at the exact input that needs fixing. The rejected value itself is
   * deliberately not echoed back: this advice applies to every controller, so it would eventually
   * reflect (and log) personal data from candidate-facing payloads.
   *
   * <p>Returns a {@link ResponseEntity} rather than the body plus {@code @ResponseStatus}, so that
   * the {@code application/problem+json} content type the contract declares for error responses is
   * set explicitly instead of being left to content negotiation.
   */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ProblemDetails> handleMethodArgumentNotValidException(
      MethodArgumentNotValidException ex, HttpServletRequest request) {

    List<ProblemDetailsErrorsInner> errors =
        ex.getBindingResult().getAllErrors().stream().map(this::toError).toList();

    var problemDetails =
        new ProblemDetails(Constants.VALIDATION_FAILED_TITLE, HttpStatus.BAD_REQUEST.value());
    problemDetails.setDetail(Constants.VALIDATION_FAILED_DETAIL);
    problemDetails.setInstance(URI.create(request.getRequestURI()));
    problemDetails.setErrors(errors);

    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(problemDetails);
  }

  private ProblemDetailsErrorsInner toError(ObjectError error) {
    if (error instanceof FieldError fieldError) {
      return new ProblemDetailsErrorsInner(fieldError.getField(), fieldError.getDefaultMessage());
    }
    return new ProblemDetailsErrorsInner(error.getObjectName(), error.getDefaultMessage());
  }

  /**
   * Turns a call to a contract operation that is not built yet into an RFC 9457 501 problem
   * response, naming the operation, so clients can tell "not built yet" apart from a real failure.
   */
  @ExceptionHandler(EndpointNotImplementedException.class)
  ResponseEntity<ProblemDetails> handleEndpointNotImplementedException(
      EndpointNotImplementedException ex, HttpServletRequest request) {
    var problemDetails =
        new ProblemDetails(Constants.NOT_IMPLEMENTED_TITLE, HttpStatus.NOT_IMPLEMENTED.value());
    problemDetails.setDetail(ex.getMessage());
    problemDetails.setInstance(URI.create(request.getRequestURI()));
    return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(problemDetails);
  }

  /**
   * Turns a body that cannot be read into the request type (malformed JSON, or a value outside an
   * enum such as a lower-case {@code language}) into an RFC 9457 400 problem response. Without this
   * the catch-all below would answer a client mistake with a 500. The parser's message is not
   * echoed: it can repeat the rejected input.
   */
  @ExceptionHandler(HttpMessageNotReadableException.class)
  ResponseEntity<ProblemDetails> handleHttpMessageNotReadableException(
      HttpMessageNotReadableException ex, HttpServletRequest request) {
    log.warn("Unreadable request body: {}", ex.getClass().getSimpleName());
    var problemDetails =
        new ProblemDetails(Constants.VALIDATION_FAILED_TITLE, HttpStatus.BAD_REQUEST.value());
    problemDetails.setDetail(Constants.UNREADABLE_REQUEST_DETAIL);
    problemDetails.setInstance(URI.create(request.getRequestURI()));
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(problemDetails);
  }

  /**
   * Turns a request body with a content type the operation does not accept (e.g. {@code text/plain}
   * on an operation that takes {@code application/json}, or no content type at all) into an RFC
   * 9457 415 problem response. Spring rejects such a request before any controller runs, so without
   * this the catch-all below would answer a client mistake with a 500. The {@code Accept} header
   * lists the content types the operation does take, as RFC 9110 (section 15.5.16) recommends, so
   * the client can correct the request without reading the contract.
   */
  @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
  ResponseEntity<ProblemDetails> handleHttpMediaTypeNotSupportedException(
      HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
    log.warn("Unsupported request content type: {}", ex.getContentType());
    var problemDetails =
        new ProblemDetails(
            Constants.UNSUPPORTED_MEDIA_TYPE_TITLE, HttpStatus.UNSUPPORTED_MEDIA_TYPE.value());
    problemDetails.setDetail(Constants.UNSUPPORTED_MEDIA_TYPE_DETAIL);
    problemDetails.setInstance(URI.create(request.getRequestURI()));
    return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .headers(headers -> headers.setAccept(ex.getSupportedMediaTypes()))
        .body(problemDetails);
  }

  /**
   * Answers a rejected access token (expired, forged, malformed, wrong audience) with {@code 401}
   * and {@code WWW-Authenticate: Bearer error="invalid_token"} (RFC 6750). Why it was rejected is
   * logged at {@code warn}, never sent: it only helps someone probing with forged tokens. The
   * security filter chain hands these exceptions here ({@code SecurityConfig}).
   */
  @ExceptionHandler(OAuth2AuthenticationException.class)
  ResponseEntity<ProblemDetails> handleOAuth2AuthenticationException(
      OAuth2AuthenticationException ex, HttpServletRequest request) {
    log.warn(
        "Rejected access token for {} {}: {}",
        request.getMethod(),
        request.getRequestURI(),
        ex.getMessage());
    return unauthorized(request, Constants.REJECTED_TOKEN_CHALLENGE);
  }

  /** Answers a request without an access token with {@code 401} and a plain bearer challenge. */
  @ExceptionHandler(InsufficientAuthenticationException.class)
  ResponseEntity<ProblemDetails> handleInsufficientAuthenticationException(
      InsufficientAuthenticationException ex, HttpServletRequest request) {
    log.info("No access token for {} {}", request.getMethod(), request.getRequestURI());
    return unauthorized(request, Constants.NO_TOKEN_CHALLENGE);
  }

  /**
   * Answers any other authentication failure with {@code 401}, logged at {@code error}: none is
   * expected. Keycloak being unreachable never gets here: Spring Security rethrows that failure and
   * the container answers a bare {@code 500}.
   */
  @ExceptionHandler(AuthenticationException.class)
  ResponseEntity<ProblemDetails> handleAuthenticationException(
      AuthenticationException ex, HttpServletRequest request) {
    log.error("Authentication failed for {} {}", request.getMethod(), request.getRequestURI(), ex);
    return unauthorized(request, Constants.NO_TOKEN_CHALLENGE);
  }

  private ResponseEntity<ProblemDetails> unauthorized(
      HttpServletRequest request, String challenge) {
    var problemDetails =
        new ProblemDetails(Constants.UNAUTHORIZED_TITLE, HttpStatus.UNAUTHORIZED.value());
    problemDetails.setDetail(Constants.UNAUTHORIZED_DETAIL);
    problemDetails.setInstance(URI.create(request.getRequestURI()));
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .header(HttpHeaders.WWW_AUTHENTICATE, challenge)
        .body(problemDetails);
  }

  /**
   * Answers {@code 403} to an authenticated account without company access: it has no Taldo user
   * yet, or its user waits for approval (ADR 0006, revision 2026-11-21, point 5). The only source
   * today is the {@code COMPANY_ACCESS} rule in {@code SecurityConfig}; logged at {@code info}, as
   * a frontend that calls company endpoints before onboarding is not an attack.
   */
  @ExceptionHandler(AccessDeniedException.class)
  ResponseEntity<ProblemDetails> handleAccessDeniedException(
      AccessDeniedException ex, HttpServletRequest request) {
    log.info("No company access for {} {}", request.getMethod(), request.getRequestURI());
    var problemDetails =
        new ProblemDetails(Constants.COMPANY_ACCESS_DENIED_TITLE, HttpStatus.FORBIDDEN.value());
    problemDetails.setDetail(Constants.COMPANY_ACCESS_DENIED_DETAIL);
    problemDetails.setInstance(URI.create(request.getRequestURI()));
    return ResponseEntity.status(HttpStatus.FORBIDDEN)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(problemDetails);
  }

  /**
   * Fallback for every exception not handled more specifically, so failures (e.g. a database error
   * during save) surface as RFC 9457 Problem Details instead of Spring's default error response. No
   * internal details are leaked in {@code detail}.
   */
  @ExceptionHandler(Exception.class)
  ResponseEntity<ProblemDetails> handleException(Exception ex, HttpServletRequest request) {
    log.error("Unhandled exception while processing request", ex);
    var problemDetails =
        new ProblemDetails(
            Constants.INTERNAL_SERVER_ERROR_TITLE, HttpStatus.INTERNAL_SERVER_ERROR.value());
    problemDetails.setDetail(Constants.INTERNAL_SERVER_ERROR_DETAIL);
    problemDetails.setInstance(URI.create(request.getRequestURI()));

    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(problemDetails);
  }
}
