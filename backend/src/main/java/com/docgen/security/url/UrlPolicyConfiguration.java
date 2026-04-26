package com.docgen.security.url;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(UrlPolicyProperties.class)
public class UrlPolicyConfiguration {

    @Bean
    public OutboundUrlPolicy outboundUrlPolicy(UrlPolicyProperties properties) {
        return new OutboundUrlPolicy(properties);
    }
}
