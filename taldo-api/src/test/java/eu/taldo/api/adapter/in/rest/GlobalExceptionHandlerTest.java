package eu.taldo.api.adapter.in.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import ch.qos.logback.core.read.ListAppender;
import eu.taldo.api.shared.Constants;
import eu.taldo.api.vacancy.adapter.in.web.contract.ProblemDetails;
import eu.taldo.api.vacancy.adapter.in.web.contract.ProblemDetailsErrorsInner;
import eu.taldo.api.vacancy.adapter.in.web.contract.VacancyCoreRequest;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;

class GlobalExceptionHandlerTest {

  private static final String REJECTION_REASON =
      "An error occurred while attempting to decode the Jwt: Jwt expired at 2026-10-18T18:49:55Z";

  private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
  private final Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
  private final ListAppender<ILoggingEvent> logged = new ListAppender<>();

  @BeforeEach
  void captureLog() {
    logged.start();
    logger.addAppender(logged);
  }

  @AfterEach
  void releaseLog() {
    logger.detachAppender(logged);
  }

  @Test
  @DisplayName(
      "given field errors, when handling the exception, then the problem detail lists every invalid"
          + " field with its constraint message")
  void givenFieldErrorsWhenHandleThenProblemDetailsListsEveryInvalidField() {
    var bindingResult = bindingResult();
    bindingResult.rejectValue("jobTitle", "Size", "size must be between 0 and 200");
    bindingResult.rejectValue("minHoursPerWeek", "Min", "must be greater than 0");

    var response =
        handler.handleMethodArgumentNotValidException(exception(bindingResult), request());

    assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    assertEquals(MediaType.APPLICATION_PROBLEM_JSON, response.getHeaders().getContentType());

    ProblemDetails body = response.getBody();
    assertEquals(URI.create("about:blank"), body.getType());
    assertEquals("Validation failed", body.getTitle());
    assertEquals(HttpStatus.BAD_REQUEST.value(), body.getStatus());
    assertEquals(URI.create("/api/v1/vacancy"), body.getInstance());
    assertEquals(
        "The request failed validation on one or more fields, see 'errors' for details.",
        body.getDetail());

    assertEquals(
        List.of("jobTitle", "minHoursPerWeek"),
        body.getErrors().stream().map(ProblemDetailsErrorsInner::getField).toList());
    assertEquals("size must be between 0 and 200", messageFor(body, "jobTitle"));
    assertEquals("must be greater than 0", messageFor(body, "minHoursPerWeek"));
  }

  @Test
  @DisplayName(
      "given a class-level error, when handling the exception, then it is reported under the object"
          + " name")
  void givenClassLevelErrorWhenHandleThenReportedUnderObjectName() {
    var bindingResult = bindingResult();
    bindingResult.reject("HoursRange", "maxHoursPerWeek must be greater than minHoursPerWeek");

    var body =
        handler
            .handleMethodArgumentNotValidException(exception(bindingResult), request())
            .getBody();

    assertEquals(1, body.getErrors().size());
    assertEquals("vacancyCoreRequest", body.getErrors().getFirst().getField());
    assertEquals(
        "maxHoursPerWeek must be greater than minHoursPerWeek",
        body.getErrors().getFirst().getMessage());
  }

  @Test
  @DisplayName(
      "given an operation that is not built yet, when handling then returns a 501 problem detail"
          + " naming the operation")
  void givenEndpointNotImplementedWhenHandleThenReturns501ProblemDetailNamingTheOperation() {
    var response =
        handler.handleEndpointNotImplementedException(
            new EndpointNotImplementedException("getVacancy"), request());

    assertEquals(HttpStatus.NOT_IMPLEMENTED, response.getStatusCode());
    assertEquals(MediaType.APPLICATION_PROBLEM_JSON, response.getHeaders().getContentType());

    ProblemDetails body = response.getBody();
    assertEquals("Not implemented", body.getTitle());
    assertEquals(HttpStatus.NOT_IMPLEMENTED.value(), body.getStatus());
    assertEquals("Operation is not implemented yet: getVacancy", body.getDetail());
    assertEquals(URI.create("/api/v1/vacancy"), body.getInstance());
  }

  @Test
  @DisplayName(
      "given an unsupported content type, when handling then returns a 415 problem detail with the"
          + " supported content types in the Accept header")
  void
      givenUnsupportedContentTypeWhenHandleThenReturns415WithSupportedContentTypesInAcceptHeader() {
    var supportedMediaTypes = List.of(MediaType.APPLICATION_JSON);
    var exception =
        new HttpMediaTypeNotSupportedException(
            MediaType.TEXT_PLAIN, supportedMediaTypes, HttpMethod.PATCH);

    var response = handler.handleHttpMediaTypeNotSupportedException(exception, request());

    assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, response.getStatusCode());
    assertEquals(MediaType.APPLICATION_PROBLEM_JSON, response.getHeaders().getContentType());
    assertEquals(supportedMediaTypes, response.getHeaders().getAccept());
    assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE.value(), response.getBody().getStatus());
  }

  @Test
  @DisplayName("given an unexpected exception, when handling then returns a 500 problem detail")
  void givenUnexpectedExceptionWhenHandleThenReturns500ProblemDetail() {
    var response = handler.handleException(new RuntimeException("boom"), request());

    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    assertEquals(MediaType.APPLICATION_PROBLEM_JSON, response.getHeaders().getContentType());
    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), response.getBody().getStatus());
  }

  @Test
  @DisplayName(
      "given a rejected token, when handling then 401 with the invalid_token challenge and a problem"
          + " without the reason, and the reason is logged at warn with method and path")
  void givenRejectedTokenWhenHandleThen401WithInvalidTokenChallengeAndReasonLoggedAtWarn() {
    var response =
        handler.handleOAuth2AuthenticationException(
            new InvalidBearerTokenException(REJECTION_REASON), request());

    assertUnauthorized(response, Constants.REJECTED_TOKEN_CHALLENGE);
    var event = logged.list.getFirst();
    assertEquals(Level.WARN, event.getLevel());
    assertEquals(
        "Rejected access token for POST /api/v1/vacancy: " + REJECTION_REASON,
        event.getFormattedMessage());
  }

  @Test
  @DisplayName(
      "given no token, when handling then 401 with a plain bearer challenge, logged at info with"
          + " method and path")
  void givenNoTokenWhenHandleThen401WithPlainChallengeLoggedAtInfo() {
    var response =
        handler.handleInsufficientAuthenticationException(
            new InsufficientAuthenticationException("Full authentication is required"), request());

    assertUnauthorized(response, Constants.NO_TOKEN_CHALLENGE);
    var event = logged.list.getFirst();
    assertEquals(Level.INFO, event.getLevel());
    assertEquals("No access token for POST /api/v1/vacancy", event.getFormattedMessage());
  }

  @Test
  @DisplayName(
      "given an unexpected authentication failure, when handling then 401 without a reason, and the"
          + " failure is logged as error with its stack trace")
  void givenUnexpectedAuthenticationFailureWhenHandleThen401AndFailureLoggedAsError() {
    var failure = new AuthenticationServiceException("Keycloak's signing keys are unavailable");

    var response = handler.handleAuthenticationException(failure, request());

    assertUnauthorized(response, Constants.NO_TOKEN_CHALLENGE);
    var event = logged.list.getFirst();
    assertEquals(Level.ERROR, event.getLevel());
    assertEquals("Authentication failed for POST /api/v1/vacancy", event.getFormattedMessage());
    assertSame(failure, ((ThrowableProxy) event.getThrowableProxy()).getThrowable());
  }

  @Test
  @DisplayName(
      "given a request with a query string, when handling a missing token then the query is not"
          + " logged")
  void givenRequestWithQueryStringWhenHandleMissingTokenThenQueryNotLogged() {
    var request = request();
    request.setQueryString("email=jane@acme.nl");

    handler.handleInsufficientAuthenticationException(
        new InsufficientAuthenticationException("Full authentication"), request);

    assertEquals(
        "No access token for POST /api/v1/vacancy", logged.list.getFirst().getFormattedMessage());
  }

  @Test
  @DisplayName(
      "given an account without company access, when handling then 403 with the company access"
          + " problem, logged at info")
  void givenNoCompanyAccessWhenHandleThen403AndLoggedAtInfo() {
    var response =
        handler.handleAccessDeniedException(new AccessDeniedException("Access Denied"), request());

    assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    assertEquals(MediaType.APPLICATION_PROBLEM_JSON, response.getHeaders().getContentType());
    assertEquals(Constants.COMPANY_ACCESS_DENIED_TITLE, response.getBody().getTitle());
    assertEquals(Constants.COMPANY_ACCESS_DENIED_DETAIL, response.getBody().getDetail());
    var event = logged.list.getFirst();
    assertEquals(Level.INFO, event.getLevel());
    assertEquals("No company access for POST /api/v1/vacancy", event.getFormattedMessage());
  }

  private void assertUnauthorized(ResponseEntity<ProblemDetails> response, String challenge) {
    assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    assertEquals(challenge, response.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE));
    assertEquals(MediaType.APPLICATION_PROBLEM_JSON, response.getHeaders().getContentType());
    assertEquals(Constants.UNAUTHORIZED_TITLE, response.getBody().getTitle());
    assertEquals(Constants.UNAUTHORIZED_DETAIL, response.getBody().getDetail());
    assertEquals(URI.create("/api/v1/vacancy"), response.getBody().getInstance());
  }

  private BeanPropertyBindingResult bindingResult() {
    return new BeanPropertyBindingResult(new VacancyCoreRequest(), "vacancyCoreRequest");
  }

  private MethodArgumentNotValidException exception(BindingResult bindingResult) {
    return new MethodArgumentNotValidException(methodParameter(), bindingResult);
  }

  private MethodParameter methodParameter() {
    try {
      return new MethodParameter(
          GlobalExceptionHandlerTest.class.getDeclaredMethod("validated", VacancyCoreRequest.class),
          0);
    } catch (NoSuchMethodException e) {
      throw new IllegalStateException(e);
    }
  }

  @SuppressWarnings("unused")
  private void validated(VacancyCoreRequest request) {}

  private MockHttpServletRequest request() {
    return new MockHttpServletRequest("POST", "/api/v1/vacancy");
  }

  private String messageFor(ProblemDetails body, String field) {
    return body.getErrors().stream()
        .filter(error -> field.equals(error.getField()))
        .findFirst()
        .orElseThrow()
        .getMessage();
  }
}
