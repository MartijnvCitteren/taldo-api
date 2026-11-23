package eu.taldo.api.adapter.out.persistence.vacancy;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface VacancyJpaRepository extends JpaRepository<VacancyJpaEntity, UUID> {

  Optional<VacancyJpaEntity> findByIdAndCompanyId(UUID id, UUID companyId);

  boolean existsByIdAndCompanyId(UUID id, UUID companyId);
}
