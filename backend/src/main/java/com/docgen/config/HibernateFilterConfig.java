package com.docgen.config;

import com.docgen.repository.TenantAwareEntityManager;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Configures a Spring MVC interceptor that enables the Hibernate
 * {@code tenantFilter} on every incoming request.
 * <p>
 * Entity classes should be annotated with:
 * <pre>
 * {@literal @}FilterDef(name = "tenantFilter",
 *            parameters = @ParamDef(name = "tenantId", type = Long.class))
 * {@literal @}Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
 * </pre>
 * This configuration ensures the filter is activated before any JPA query
 * executes, using the tenant ID from {@code TenantContext}.
 */
@Component
public class HibernateFilterConfig implements WebMvcConfigurer {

    private final TenantFilterInterceptor tenantFilterInterceptor;

    public HibernateFilterConfig(TenantFilterInterceptor tenantFilterInterceptor) {
        this.tenantFilterInterceptor = tenantFilterInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(tenantFilterInterceptor)
                .addPathPatterns("/api/**");
    }

    /**
     * Interceptor that enables the Hibernate tenant filter before each
     * controller method executes.
     */
    @Component
    static class TenantFilterInterceptor implements HandlerInterceptor {

        private final TenantAwareEntityManager tenantAwareEntityManager;

        TenantFilterInterceptor(TenantAwareEntityManager tenantAwareEntityManager) {
            this.tenantAwareEntityManager = tenantAwareEntityManager;
        }

        @Override
        public boolean preHandle(HttpServletRequest request,
                                 HttpServletResponse response,
                                 Object handler) {
            tenantAwareEntityManager.enableTenantFilter();
            return true;
        }
    }
}
