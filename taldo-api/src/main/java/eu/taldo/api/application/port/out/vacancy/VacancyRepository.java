package eu.taldo.api.application.port.out.vacancy;

import eu.taldo.api.application.exception.VacancyModifiedConcurrentlyException;
import eu.taldo.api.domain.vacancy.Vacancy;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public interface VacancyRepository {
  /**
   * Saves a new vacancy, or the changes to a loaded one.
   *
   * @throws VacancyModifiedConcurrentlyException when the vacancy was changed after it was loaded
   */
  void save(Vacancy vacancy);

  /**
   * Finds a vacancy of a company. A vacancy of another company is not found, the same as one that
   * does not exist, so its existence is never revealed (ADR 0006, revision 2026-11-21, point 4).
   * There is deliberately no finder by id alone.
   */
  Optional<Vacancy> findByIdAndCompanyId(UUID id, UUID companyId);

  /** Whether a vacancy exists for the company; cheaper than loading it for a frequent check. */
  boolean existsByIdAndCompanyId(UUID id, UUID companyId);
}
