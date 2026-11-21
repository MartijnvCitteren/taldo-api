package eu.taldo.api.adapter.out.persistence.vacancy;

import eu.taldo.api.adapter.out.persistence.BaseJpaEntity;
import eu.taldo.api.adapter.out.persistence.identity.CompanyJpaEntity;
import eu.taldo.api.domain.vacancy.valueobject.HoursPerWeek;
import eu.taldo.api.domain.vacancy.valueobject.Language;
import eu.taldo.api.domain.vacancy.valueobject.Location;
import eu.taldo.api.domain.vacancy.valueobject.VacancyCategory;
import eu.taldo.api.domain.vacancy.valueobject.VacancyStatus;
import eu.taldo.api.domain.vacancy.valueobject.WorkplaceType;
import eu.taldo.api.shared.gdpr.PersonalData;
import eu.taldo.api.shared.gdpr.ProcessData;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

/**
 * JPA entity for vacancies. Holds the inverse (non-owning) side of the {@code @OneToOne} relation
 * to {@code VacancyDescriptionJpaEntity}, with cascade and orphanRemoval enabled so saving or
 * clearing the description cascades through this entity. Description is fetched EAGER (see ADR
 * 0005) since it is always loaded as part of the single aggregate.
 *
 * <p>The contact person is four flat columns rather than an embeddable: its name, phone number and
 * e-mail address are personal data and its role is not, and anonymization needs that split per
 * column.
 *
 * <p>A vacancy belongs to one company: {@code company_id} is a foreign key to {@code company},
 * mapped as a lazy {@code @ManyToOne} so the database guards the relation, with an index because
 * every query filters on it. The domain references the company by id only, because company and
 * vacancy are separate aggregates.
 */
@Getter
@Setter
@Entity
@Table(indexes = {@Index(name = "ix_vacancy_company_id", columnList = "company_id")})
public class VacancyJpaEntity extends BaseJpaEntity {
  @Id private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(
      name = "company_id",
      nullable = false,
      foreignKey = @ForeignKey(name = "fk_vacancy_company"))
  private CompanyJpaEntity company;

  /**
   * Optimistic lock. Not null with a default of 0, so Hibernate's schema update can add the column
   * to a table that already has rows. A null version in a mapped entity means "not saved yet" to
   * Spring Data, so a new vacancy is inserted and a loaded one is updated with a version check.
   */
  @Version
  @ColumnDefault("0")
  @Column(nullable = false)
  private Long version;

  @ProcessData private String jobTitle;

  @ProcessData
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 2)
  private Language language;

  @OneToOne(
      mappedBy = "vacancy",
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      fetch = FetchType.EAGER)
  private VacancyDescriptionJpaEntity description;

  @ProcessData
  @Enumerated(EnumType.STRING)
  private VacancyCategory category;

  @ProcessData @Embedded private Location location;

  @ProcessData
  @Enumerated(EnumType.STRING)
  private WorkplaceType workplaceType;

  @ProcessData @Embedded private HoursPerWeek hoursPerWeek;

  @ProcessData
  @Enumerated(EnumType.STRING)
  private VacancyStatus status;

  @PersonalData
  @Column(length = 35)
  private String contactPersonName;

  @ProcessData
  @Column(length = 50)
  private String contactPersonRole;

  @PersonalData
  @Column(length = 16)
  private String contactPersonPhone;

  @PersonalData
  @Column(length = 75)
  private String contactPersonEmail;

  @ProcessData @Embedded private OfferJpaEmbeddable offer;
}
