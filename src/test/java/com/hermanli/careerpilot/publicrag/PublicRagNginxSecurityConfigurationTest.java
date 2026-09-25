package com.hermanli.careerpilot.publicrag;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PublicRagNginxSecurityConfigurationTest {

    @Test
    void publicRagPathsUseOneExactTrustedIpRateLimitBoundary() throws IOException {
        String nginx = Files.readString(Path.of("careerpilot-frontend", "nginx.conf"));
        String publicRagProxy = Files.readString(Path.of(
                "careerpilot-frontend",
                "proxy_public_rag_params"
        ));

        assertThat(nginx)
                .contains(
                        "default $binary_remote_addr;",
                        "limit_req_zone $careerpilot_public_rag_ip_key zone=public_rag_per_ip:10m rate=2r/m;",
                        "location = /api/rag/resume/validate {",
                        "location = /api/rag/resume/review {",
                        "limit_req zone=public_rag_per_ip burst=1 nodelay;",
                        "limit_req_status 429;",
                        "client_body_timeout 10s;",
                        "include /etc/nginx/proxy_public_rag_params;",
                        "PUBLIC_RAG_IP_RATE_LIMITED"
                )
                .doesNotContain("location /api/rag/");
        assertThat(count(nginx, "limit_req zone=public_rag_per_ip burst=1 nodelay;")).isEqualTo(2);
        assertThat(count(nginx, "limit_req_status 429;")).isEqualTo(2);
        assertThat(publicRagProxy).contains(
                "proxy_connect_timeout 5s;",
                "proxy_send_timeout 15s;",
                "proxy_read_timeout 30s;",
                "proxy_buffering off;",
                "proxy_cache off;"
        );
    }

    private int count(String value, String fragment) {
        return (value.length() - value.replace(fragment, "").length()) / fragment.length();
    }
}
