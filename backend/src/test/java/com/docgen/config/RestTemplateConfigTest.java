package com.docgen.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

class RestTemplateConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(RestTemplateConfig.class);

    @Test
    void restTemplateBean_usesConfiguredTimeouts() {
        contextRunner
                .withPropertyValues(
                        "http.client.connect-timeout-ms=1234",
                        "http.client.read-timeout-ms=5678")
                .run(context -> {
                    RestTemplate restTemplate = context.getBean(RestTemplate.class);
                    assertThat(restTemplate.getRequestFactory()).isInstanceOf(SimpleClientHttpRequestFactory.class);

                    SimpleClientHttpRequestFactory factory =
                            (SimpleClientHttpRequestFactory) restTemplate.getRequestFactory();
                    assertThat(readPrivateIntField(factory, "connectTimeout")).isEqualTo(1234);
                    assertThat(readPrivateIntField(factory, "readTimeout")).isEqualTo(5678);
                });
    }

    private static int readPrivateIntField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getInt(target);
    }
}
