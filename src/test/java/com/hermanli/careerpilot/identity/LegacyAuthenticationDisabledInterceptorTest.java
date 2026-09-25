package com.hermanli.careerpilot.identity;

import com.hermanli.careerpilot.api.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class LegacyAuthenticationDisabledInterceptorTest {

    private final LegacyAuthenticationDisabledInterceptor interceptor =
            new LegacyAuthenticationDisabledInterceptor();

    @Test
    void hidesMappedLegacyAuthenticationControllers() {
        assertThrows(
                ResourceNotFoundException.class,
                () -> interceptor.preHandle(
                        new MockHttpServletRequest(),
                        new MockHttpServletResponse(),
                        mock(HandlerMethod.class)
                )
        );
    }

    @Test
    void leavesUnmappedPathsToNormalNotFoundHandling() {
        assertThat(interceptor.preHandle(
                new MockHttpServletRequest(),
                new MockHttpServletResponse(),
                new Object()
        )).isTrue();
    }
}
