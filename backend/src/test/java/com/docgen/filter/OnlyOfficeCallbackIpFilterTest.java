package com.docgen.filter;

import com.docgen.config.OnlyOfficeCallbackProperties;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class OnlyOfficeCallbackIpFilterTest {

    @Test
    void emptyCidrList_alwaysContinuesChain() throws Exception {
        OnlyOfficeCallbackProperties props = new OnlyOfficeCallbackProperties();
        OnlyOfficeCallbackIpFilter filter = new OnlyOfficeCallbackIpFilter(props);

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/templates/1/onlyoffice-callback");
        req.setRemoteAddr("192.168.0.1");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, resp, chain);

        verify(chain).doFilter(req, resp);
    }

    @Test
    void nonCallbackPath_skipsEnforcementEvenWithCidrs() throws Exception {
        OnlyOfficeCallbackProperties props = new OnlyOfficeCallbackProperties();
        props.setAllowedSourceCidrs(List.of("10.0.0.0/8"));
        OnlyOfficeCallbackIpFilter filter = new OnlyOfficeCallbackIpFilter(props);

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/templates/1/onlyoffice-url");
        req.setRemoteAddr("192.168.0.1");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, resp, chain);

        verify(chain).doFilter(req, resp);
    }

    @Test
    void callbackOutsideCidrBlocks_returns403() throws Exception {
        OnlyOfficeCallbackProperties props = new OnlyOfficeCallbackProperties();
        props.setAllowedSourceCidrs(List.of("10.0.0.0/8"));
        OnlyOfficeCallbackIpFilter filter = new OnlyOfficeCallbackIpFilter(props);

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/templates/5/onlyoffice-callback");
        req.setRemoteAddr("192.168.0.1");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, resp, chain);

        verify(chain, never()).doFilter(req, resp);
        assertThat(resp.getStatus()).isEqualTo(403);
    }

    @Test
    void callbackInsideCidrBlock_continuesChain() throws Exception {
        OnlyOfficeCallbackProperties props = new OnlyOfficeCallbackProperties();
        props.setAllowedSourceCidrs(List.of("10.0.0.0/8"));
        OnlyOfficeCallbackIpFilter filter = new OnlyOfficeCallbackIpFilter(props);

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/templates/5/onlyoffice-callback");
        req.setRemoteAddr("10.2.3.4");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, resp, chain);

        verify(chain).doFilter(req, resp);
    }

    @Test
    void compositeSegmentCallbackPath_detected() {
        assertThat(OnlyOfficeCallbackIpFilter.isOnlyOfficeCallbackPath(
                "/api/composite-templates/12/segments/intro/onlyoffice-callback")).isTrue();
    }

    @Test
    void mainTemplateCallbackPath_detected() {
        assertThat(OnlyOfficeCallbackIpFilter.isOnlyOfficeCallbackPath(
                "/api/templates/99/onlyoffice-callback")).isTrue();
    }

    @Test
    void nonCallbackPath_notDetected() {
        assertThat(OnlyOfficeCallbackIpFilter.isOnlyOfficeCallbackPath("/api/templates/1/sign")).isFalse();
    }
}
