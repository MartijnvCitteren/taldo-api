package eu.taldo.api.application.port.in.vacancy.command;

import eu.taldo.api.domain.vacancy.valueobject.HoursPerWeek;
import eu.taldo.api.domain.vacancy.valueobject.Language;
import eu.taldo.api.domain.vacancy.valueobject.Location;
import eu.taldo.api.domain.vacancy.valueobject.VacancyCategory;
import eu.taldo.api.domain.vacancy.valueobject.WorkplaceType;
import java.util.UUID;
import lombok.Builder;

@Builder
public record CreateCoreVacancyCommand(
    UUID companyId,
    String jobTitle,
    Language language,
    VacancyCategory category,
    Location location,
    WorkplaceType workplaceType,
    HoursPerWeek hoursPerWeek) {}
