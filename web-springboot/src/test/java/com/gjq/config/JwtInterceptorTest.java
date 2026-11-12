package com.gjq.config;

import com.gjq.utils.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtInterceptorTest {

    @Mock
    private JwtUtil jwtUtil;

    private JwtInterceptor jwtInterceptor;

    @BeforeEach
    void setUp() {
        jwtInterceptor = new JwtInterceptor(jwtUtil);
    }

    @Test
    void preHandleAcceptsValidBearerToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(jwtUtil.validateToken("valid-token")).thenReturn(true);
        when(jwtUtil.getUserIdFromToken("valid-token")).thenReturn(9L);
        when(jwtUtil.getUsernameFromToken("valid-token")).thenReturn("alice");

        boolean allowed = jwtInterceptor.preHandle(request, response, new Object());

        assertThat(allowed).isTrue();
        assertThat(request.getAttribute("userId")).isEqualTo(9L);
        assertThat(request.getAttribute("username")).isEqualTo("alice");
    }

    @Test
    void preHandleRejectsMissingBearerToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = jwtInterceptor.preHandle(request, response, new Object());

        assertThat(allowed).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("\"code\":401");
    }
}
