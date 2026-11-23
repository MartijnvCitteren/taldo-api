package eu.taldo.api.adapter.out.persistence.vacancy;

import static eu.taldo.api.testSupport.VacancyFactory.COMPANY_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.taldo.api.adapter.out.persistence.vacancy.mapper.VacancyDescriptionJpaMapperImpl;
import eu.taldo.api.adapter.out.persistence.vacancy.mapper.VacancyJpaMapperImpl;
import eu.taldo.api.application.exception.VacancyModifiedConcurrentlyException;
import eu.taldo.api.domain.vacancy.valueobject.VacancyDescription;
import eu.taldo.api.domain.vacancy.valueobject.VacancyDescriptionSource;
import eu.taldo.api.testSupport.SeededAccounts;
import eu.taldo.api.testSupport.VacancyFactory;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

@ActiveProfiles("test")
@DataJpaTest
@Sql(SeededAccounts.SEED)
@Import({
  VacancyRepositoryAdapter.class,
  VacancyJpaMapperImpl.class,
  VacancyDescriptionJpaMapperImpl.class
})
class VacancyRepositoryAdapterTest {
  private static final UUID OTHER_COMPANY_ID =
      UUID.fromString("0199c9a0-0000-7000-8000-00000000b001");

  @Autowired private VacancyRepositoryAdapter vacancyRepositoryAdapter;
  @Autowired private VacancyDescriptionJpaRepository descriptionJpaRepository;
  @Autowired private TestEntityManager entityManager;

  @Test
  @DisplayName(
      "given a vacancy with a full description, when saved and reloaded then every field"
          + " round-trips")
  void givenVacancyWithFullDescriptionWhenSavedAndReloadedThenEveryFieldRoundTrips() {
    var description =
        new VacancyDescription(
            "Summary",
            "Job description",
            "Tasks",
            "What we offer",
            "About us",
            VacancyDescriptionSource.MANUAL);
    var vacancy = VacancyFactory.getFilledCoreVacancy().description(description).build();

    vacancyRepositoryAdapter.save(vacancy);
    var reloaded =
        vacancyRepositoryAdapter.findByIdAndCompanyId(vacancy.getId(), COMPANY_ID).orElseThrow();

    assertEquals(description, reloaded.getDescription());
    assertEquals(1, descriptionJpaRepository.findByVacancyId(vacancy.getId()).stream().count());
  }

  @Test
  @DisplayName(
      "given a vacancy with an existing description, when saved again with a different"
          + " description then the new content replaces the old and no duplicate row is created")
  void givenVacancyWithExistingDescriptionWhenSavedAgainThenReplacesWithoutDuplicateRow() {
    var firstDescription =
        new VacancyDescription(
            "Old summary", null, null, null, null, VacancyDescriptionSource.MANUAL);
    var vacancy = VacancyFactory.getFilledCoreVacancy().description(firstDescription).build();
    vacancyRepositoryAdapter.save(vacancy);

    var secondDescription =
        new VacancyDescription(
            "New summary",
            "New job description",
            null,
            null,
            null,
            VacancyDescriptionSource.MANUAL);
    var loaded =
        vacancyRepositoryAdapter.findByIdAndCompanyId(vacancy.getId(), COMPANY_ID).orElseThrow();
    loaded.setDescription(secondDescription);
    vacancyRepositoryAdapter.save(loaded);

    var reloaded =
        vacancyRepositoryAdapter.findByIdAndCompanyId(vacancy.getId(), COMPANY_ID).orElseThrow();
    assertEquals(secondDescription, reloaded.getDescription());
    // With the shared-PK relation (ADR 0005), this also proves a structural guarantee: a second
    // row for the same vacancy is not representable, since the description row's PK is the
    // vacancy's id, not the accidental result of an upsert lookup happening to succeed.
    assertEquals(1, descriptionJpaRepository.count());
  }

  @Test
  @DisplayName(
      "given a vacancy with no description, when saved and reloaded then description is null and"
          + " no description row exists")
  void givenVacancyWithNoDescriptionWhenSavedAndReloadedThenDescriptionIsNullAndNoRowExists() {
    var vacancy = VacancyFactory.getFilledCoreVacancy().build();

    vacancyRepositoryAdapter.save(vacancy);
    var reloaded =
        vacancyRepositoryAdapter.findByIdAndCompanyId(vacancy.getId(), COMPANY_ID).orElseThrow();

    assertNull(reloaded.getDescription());
    assertTrue(descriptionJpaRepository.findByVacancyId(vacancy.getId()).isEmpty());
  }

  @Test
  @DisplayName(
      "given a vacancy with an existing description, when saved again with description set to"
          + " null then the description row is deleted")
  void givenVacancyWithExistingDescriptionWhenSavedAgainWithNullDescriptionThenRowIsDeleted() {
    var description =
        new VacancyDescription("Summary", null, null, null, null, VacancyDescriptionSource.MANUAL);
    var vacancy = VacancyFactory.getFilledCoreVacancy().description(description).build();
    vacancyRepositoryAdapter.save(vacancy);

    var loaded =
        vacancyRepositoryAdapter.findByIdAndCompanyId(vacancy.getId(), COMPANY_ID).orElseThrow();
    loaded.setDescription(null);
    vacancyRepositoryAdapter.save(loaded);
    // Flush/clear required to bypass the persistence-context cache and verify the explicit
    // deleteById call in the adapter actually removed the row from the database (orphanRemoval
    // does not fire on freshly-built transient graphs, so the deletion is explicit, not cascaded).
    entityManager.flush();
    entityManager.clear();

    var reloaded =
        vacancyRepositoryAdapter.findByIdAndCompanyId(vacancy.getId(), COMPANY_ID).orElseThrow();
    assertNull(reloaded.getDescription());
    assertTrue(descriptionJpaRepository.findByVacancyId(vacancy.getId()).isEmpty());
  }

  @Test
  @DisplayName(
      "given a vacancy with a contact person and an offer, when saved and reloaded then both"
          + " round-trip unchanged")
  void givenVacancyWithContactPersonAndOfferWhenSavedAndReloadedThenBothRoundTrip() {
    var vacancy =
        VacancyFactory.getFilledCoreVacancy()
            .contactPerson(VacancyFactory.CONTACT_PERSON)
            .offer(VacancyFactory.OFFER)
            .build();

    vacancyRepositoryAdapter.save(vacancy);
    entityManager.flush();
    entityManager.clear();

    var reloaded =
        vacancyRepositoryAdapter.findByIdAndCompanyId(vacancy.getId(), COMPANY_ID).orElseThrow();
    assertEquals(vacancy.getContactPerson(), reloaded.getContactPerson());
    assertEquals(vacancy.getOffer(), reloaded.getOffer());
  }

  @Test
  @DisplayName(
      "given a vacancy without a contact person and an offer, when saved and reloaded then both"
          + " are null")
  void givenVacancyWithoutContactPersonAndOfferWhenSavedAndReloadedThenBothAreNull() {
    var vacancy = VacancyFactory.getFilledCoreVacancy().build();

    vacancyRepositoryAdapter.save(vacancy);
    entityManager.flush();
    entityManager.clear();

    var reloaded =
        vacancyRepositoryAdapter.findByIdAndCompanyId(vacancy.getId(), COMPANY_ID).orElseThrow();
    assertNull(reloaded.getContactPerson());
    assertNull(reloaded.getOffer());
  }

  @Test
  @DisplayName(
      "given two copies of the same vacancy loaded at the same version, when both are saved then"
          + " the second save fails as a concurrent modification")
  void givenTwoCopiesAtSameVersionWhenBothSavedThenSecondSaveFailsAsConcurrentModification() {
    var vacancy = VacancyFactory.getFilledCoreVacancy().build();
    vacancyRepositoryAdapter.save(vacancy);
    var firstCopy =
        vacancyRepositoryAdapter.findByIdAndCompanyId(vacancy.getId(), COMPANY_ID).orElseThrow();
    var secondCopy =
        vacancyRepositoryAdapter.findByIdAndCompanyId(vacancy.getId(), COMPANY_ID).orElseThrow();
    firstCopy.setJobTitle("Account Manager");
    vacancyRepositoryAdapter.save(firstCopy);
    secondCopy.setJobTitle("Key Account Manager");

    assertThrows(
        VacancyModifiedConcurrentlyException.class,
        () -> vacancyRepositoryAdapter.save(secondCopy));
  }

  @Test
  @DisplayName(
      "given a loaded vacancy, when it is changed and saved then the reloaded vacancy has the next"
          + " version")
  void givenLoadedVacancyWhenChangedAndSavedThenReloadedVacancyHasNextVersion() {
    var vacancy = VacancyFactory.getFilledCoreVacancy().build();
    vacancyRepositoryAdapter.save(vacancy);
    var loaded =
        vacancyRepositoryAdapter.findByIdAndCompanyId(vacancy.getId(), COMPANY_ID).orElseThrow();
    loaded.setJobTitle("Account Manager");

    vacancyRepositoryAdapter.save(loaded);

    var reloaded =
        vacancyRepositoryAdapter.findByIdAndCompanyId(vacancy.getId(), COMPANY_ID).orElseThrow();
    assertEquals(loaded.getVersion() + 1, reloaded.getVersion());
  }

  @Test
  @DisplayName(
      "given a description with every text at its contract maximum length, when saved and"
          + " reloaded then every text round-trips unchanged")
  void givenDescriptionAtMaximumLengthsWhenSavedAndReloadedThenEveryTextRoundTrips() {
    var description =
        new VacancyDescription(
            "s".repeat(1000),
            "j".repeat(5000),
            "t".repeat(5000),
            "w".repeat(2500),
            "a".repeat(2500),
            VacancyDescriptionSource.MANUAL);
    var vacancy = VacancyFactory.getFilledCoreVacancy().description(description).build();

    vacancyRepositoryAdapter.save(vacancy);
    entityManager.flush();
    entityManager.clear();

    var reloaded =
        vacancyRepositoryAdapter.findByIdAndCompanyId(vacancy.getId(), COMPANY_ID).orElseThrow();
    assertEquals(description, reloaded.getDescription());
  }

  @Test
  @DisplayName(
      "given a saved vacancy, when found with another company then it is not found, and does not"
          + " exist for that company")
  void givenSavedVacancyWhenFoundWithAnotherCompanyThenNotFound() {
    var vacancy = VacancyFactory.getFilledCoreVacancy().build();
    vacancyRepositoryAdapter.save(vacancy);

    var found = vacancyRepositoryAdapter.findByIdAndCompanyId(vacancy.getId(), OTHER_COMPANY_ID);

    assertTrue(found.isEmpty());
    assertFalse(vacancyRepositoryAdapter.existsByIdAndCompanyId(vacancy.getId(), OTHER_COMPANY_ID));
  }

  @Test
  @DisplayName(
      "given a saved vacancy, when found with its own company then it keeps that company, and"
          + " exists for it")
  void givenSavedVacancyWhenFoundWithItsOwnCompanyThenItKeepsThatCompany() {
    var vacancy = VacancyFactory.getFilledCoreVacancy().build();
    vacancyRepositoryAdapter.save(vacancy);

    var found = vacancyRepositoryAdapter.findByIdAndCompanyId(vacancy.getId(), COMPANY_ID);

    assertEquals(COMPANY_ID, found.orElseThrow().getCompanyId());
    assertTrue(vacancyRepositoryAdapter.existsByIdAndCompanyId(vacancy.getId(), COMPANY_ID));
  }
}
