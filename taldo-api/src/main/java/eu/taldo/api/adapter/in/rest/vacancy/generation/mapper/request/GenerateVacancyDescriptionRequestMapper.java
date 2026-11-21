package eu.taldo.api.adapter.in.rest.vacancy.generation.mapper.request;

import eu.taldo.api.application.port.in.vacancy.generation.command.StartVacancyDescriptionGenerationCommand;
import eu.taldo.api.vacancy.adapter.in.web.contract.GenerateVacancyDescriptionRequest;
import java.util.UUID;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(
    componentModel = "spring",
    unmappedSourcePolicy = ReportingPolicy.IGNORE,
    unmappedTargetPolicy = ReportingPolicy.WARN)
public interface GenerateVacancyDescriptionRequestMapper {

  @Mapping(target = "companyId", source = "companyId")
  @Mapping(target = "vacancyId", source = "id")
  StartVacancyDescriptionGenerationCommand toCommand(
      UUID companyId, UUID id, GenerateVacancyDescriptionRequest request);
}
