package com.hermanli.careerpilot.demo.careerpilot;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class DemoReadOnlyFilterTest {

    private final DemoReadOnlyFilter filter = new DemoReadOnlyFilter();
    private final FilterChain chain = mock(FilterChain.class);

    @Test
    void allowsReadRequests() throws Exception {
        MockHttpServletRequest request = request("GET", "/api/resumes", "/api");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void allowsOnlyExplicitNonPersistentDemoPosts() throws Exception {
        MockHttpServletRequest request = request("POST", "/api/resumes/42/review", "/api");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void rejectsWriteRequestsWithSafeError() throws Exception {
        MockHttpServletRequest request = request("POST", "/api/resumes", "/api");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(response.getContentAsString()).contains("DEMO_READ_ONLY").doesNotContain("/api/resumes");
        verifyNoInteractions(chain);
    }

    private MockHttpServletRequest request(String method, String requestUri, String contextPath) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, requestUri);
        request.setContextPath(contextPath);
        return request;
    }
}
