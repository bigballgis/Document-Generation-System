package com.docgen.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for OnlyOffice Document Server HTTP callbacks (unauthenticated entry points).
 */
@Component
@ConfigurationProperties(prefix = "onlyoffice.callback")
public class OnlyOfficeCallbackProperties {

    /**
     * Optional list of CIDR blocks for {@link jakarta.servlet.http.HttpServletRequest#getRemoteAddr()}.
     * When non-empty, POSTs to OnlyOffice callback paths must match at least one entry.
     * When empty, no network-level restriction is applied (legacy behavior).
     */
    private List<String> allowedSourceCidrs = new ArrayList<>();

    public List<String> getAllowedSourceCidrs() {
        return allowedSourceCidrs;
    }

    public void setAllowedSourceCidrs(List<String> allowedSourceCidrs) {
        this.allowedSourceCidrs = allowedSourceCidrs != null ? allowedSourceCidrs : new ArrayList<>();
    }
}
