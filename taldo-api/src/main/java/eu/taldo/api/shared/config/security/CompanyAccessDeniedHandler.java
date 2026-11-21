package eu.taldo.api.shared.config.security;

import eu.taldo.api.shared.Constants;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * Answers {@code 403} to an authenticated account that may not access company data: it has no Taldo
 * user yet, or its user waits for the owner's approval (ADR 0006, revision 2026-11-21, point 5).
 * The response only concerns the caller's own account. Logged at {@code info} with method and path:
 * a frontend that calls company endpoints before onboarding, not an attack.
 */
@Log4j2
@RequiredArgsConstructor
class CompanyAccessDeniedHandler implements AccessDeniedHandler {
  private final ProblemResponseWriter problemResponseWriter;

  @Override
  public void handle(
      HttpServletRequest request, HttpServletResponse response, AccessDeniedException exception)
      throws IOException {
    log.info("No company access for {} {}", request.getMethod(), request.getRequestURI());
    problemResponseWriter.write(
        request,
        response,
        HttpStatus.FORBIDDEN,
        Constants.COMPANY_ACCESS_DENIED_TITLE,
        Constants.COMPANY_ACCESS_DENIED_DETAIL);
  }
}
