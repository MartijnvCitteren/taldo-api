package eu.taldo.api.shared.config.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import tools.jackson.databind.json.JsonMapper;

/**
 * Writes an RFC 9457 problem response from the security filter chain, where no controller advice
 * runs. Same shape as the contracts' {@code ProblemDetails}, with fixed texts only: why a request
 * was refused goes to the log, not to the client.
 */
@RequiredArgsConstructor
class ProblemResponseWriter {
  private final JsonMapper jsonMapper;

  void write(
      HttpServletRequest request,
      HttpServletResponse response,
      HttpStatus status,
      String title,
      String detail)
      throws IOException {
    var problem = new LinkedHashMap<String, Object>();
    problem.put("title", title);
    problem.put("status", status.value());
    problem.put("detail", detail);
    problem.put("instance", request.getRequestURI());
    response.setStatus(status.value());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    jsonMapper.writeValue(response.getOutputStream(), problem);
  }
}
