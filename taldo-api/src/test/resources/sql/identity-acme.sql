-- Two onboarded companies, each linked to its Keycloak organization:
-- - acme.nl, with its founder as owner and organization member, and a colleague who waits for the
--   founder's approval;
-- - initech.nl, with its founder, to check that a company never reaches another company's data.
-- Runs before every test method that needs them and clears what an earlier test left behind:
-- vacancies first (their company_id is a foreign key to company), then users, then companies.
delete from vacancy_description_generation;
delete from vacancy_description_jpa_entity;
delete from vacancy_jpa_entity;
delete from app_user;
delete from company;

insert into company
  (id, name, email_domain, keycloak_organization_id, created_at, last_modified_at, last_modified_by)
values
  ('0199c9a0-0000-7000-8000-00000000a001', 'Acme', 'acme.nl', '7d4e1c2b-0000-4000-8000-00000000a0a0',
   TIMESTAMP '2026-01-01 09:00:00', TIMESTAMP '2026-01-01 09:00:00', '0199c9a0-0000-7000-8000-00000000a002'),
  ('0199c9a0-0000-7000-8000-00000000b001', 'Initech', 'initech.nl', '7d4e1c2b-0000-4000-8000-00000000b0b0',
   TIMESTAMP '2026-01-01 09:00:00', TIMESTAMP '2026-01-01 09:00:00', '0199c9a0-0000-7000-8000-00000000b002');

insert into app_user
  (id, keycloak_subject, first_name, last_name, email, job_role, company_id, membership, status,
   organization_member, created_at, last_modified_at)
values
  ('0199c9a0-0000-7000-8000-00000000a002', '5b9c3f0e-0000-4000-8000-00000000a003', 'Anna', 'de Vries',
   'anna@acme.nl', 'FOUNDER_OR_DIRECTOR', '0199c9a0-0000-7000-8000-00000000a001', 'OWNER', 'ACTIVE', true,
   TIMESTAMP '2026-01-01 09:00:00', TIMESTAMP '2026-01-01 09:00:00'),
  ('0199c9a0-0000-7000-8000-00000000a004', '5b9c3f0e-0000-4000-8000-00000000a005', 'Piet', 'Jansen',
   'piet@acme.nl', 'RECRUITER', '0199c9a0-0000-7000-8000-00000000a001', 'MEMBER', 'PENDING_APPROVAL', false,
   TIMESTAMP '2026-01-02 09:00:00', TIMESTAMP '2026-01-02 09:00:00'),
  ('0199c9a0-0000-7000-8000-00000000b002', '5b9c3f0e-0000-4000-8000-00000000b003', 'Bram', 'Visser',
   'bram@initech.nl', 'HIRING_MANAGER', '0199c9a0-0000-7000-8000-00000000b001', 'OWNER', 'ACTIVE', true,
   TIMESTAMP '2026-01-01 09:00:00', TIMESTAMP '2026-01-01 09:00:00');
