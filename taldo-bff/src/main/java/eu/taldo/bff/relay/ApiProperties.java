package eu.taldo.bff.relay;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Where taldo-api lives, as the BFF reaches it: in production a private address no browser can
 * reach (ADR 0009, section 4).
 *
 * @param baseUrl scheme, host and port of taldo-api, without its context path
 */
@ConfigurationProperties(prefix = "taldo.api")
record ApiProperties(String baseUrl) {}
