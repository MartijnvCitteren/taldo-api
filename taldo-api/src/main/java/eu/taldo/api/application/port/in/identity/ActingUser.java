package eu.taldo.api.application.port.in.identity;

import java.util.UUID;

/**
 * The user a request acts for, and the company whose data it may reach (ADR 0006, revision
 * 2026-11-21, point 4). Only exists for a user with access to company data.
 */
public record ActingUser(UUID userId, UUID companyId) {}
