package com.hermanli.careerpilot.publicrag;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
@ConfigurationProperties(prefix = "careerpilot.public-rag.auth")
public class PublicRagAuthProperties {

    private static final Logger log = LoggerFactory.getLogger(PublicRagAuthProperties.class);

    private boolean enabled;
    private String issuer = "";
    private List<String> authorizedParties = new ArrayList<>();

    @PostConstruct
    void validate() {
        if (!enabled) {
            log.info("Clerk authentication is disabled for public Resume Review.");
            return;
        }

        issuer = normalizeOrigin(issuer, false);
        if (authorizedParties == null || authorizedParties.isEmpty()) {
            throw new IllegalStateException("At least one Clerk authorized party is required.");
        }
        authorizedParties = authorizedParties.stream()
                .map(value -> normalizeOrigin(value, true))
                .distinct()
                .toList();
        // Only public origin values are logged; no token, secret, or identity value is included.
        log.info(
                "Clerk authentication is enabled for public Resume Review: issuer={}, authorizedParties={}",
                issuer,
                authorizedParties
        );
    }

    private String normalizeOrigin(String value, boolean allowLocalHttp) {
        try {
            URI uri = URI.create(value == null ? "" : value.trim());
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            boolean localHttp = allowLocalHttp && "http".equals(scheme)
                    && ("localhost".equals(host) || "127.0.0.1".equals(host));
            boolean safeScheme = "https".equals(scheme) || localHttp;
            boolean originOnly = uri.getUserInfo() == null
                    && uri.getQuery() == null
                    && uri.getFragment() == null
                    && (uri.getPath() == null || uri.getPath().isEmpty() || "/".equals(uri.getPath()));
            if (!safeScheme || host.isBlank() || !originOnly) {
                throw new IllegalArgumentException();
            }
            return scheme + "://" + host + (uri.getPort() < 0 ? "" : ":" + uri.getPort());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Invalid Clerk origin configuration.");
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public List<String> getAuthorizedParties() {
        return List.copyOf(authorizedParties);
    }

    public void setAuthorizedParties(List<String> authorizedParties) {
        this.authorizedParties = authorizedParties == null ? List.of() : new ArrayList<>(authorizedParties);
    }
}
