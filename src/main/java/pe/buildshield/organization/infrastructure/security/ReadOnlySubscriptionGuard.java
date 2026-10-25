package pe.buildshield.organization.infrastructure.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import pe.buildshield.organization.infrastructure.persistence.JdbcSubscriptionProjection;
import pe.buildshield.organization.shared.error.ErrorResponse;
import pe.buildshield.organization.shared.error.ErrorResponseWriter;
import pe.buildshield.organization.shared.tenant.TenantContext;

import java.util.Set;

/**
 * US10, defensa en profundidad: con la suscripción vencida (copia local {@code subscription_projection} en
 * {@code EXPIRED}) la cuenta queda en solo lectura y organization rechaza toda operación que cambie estado (POST,
 * PUT, PATCH y DELETE) con 402 {@code SUBSCRIPTION_READ_ONLY}. Las lecturas siguen permitidas. El primer corte lo
 * hace el API Gateway; esta regla cubre a quien llegue al servicio sin pasar por él.
 */
@Configuration(proxyBeanMethods = false)
class ReadOnlySubscriptionGuard implements WebMvcConfigurer {

    static final String SUBSCRIPTION_READ_ONLY = "SUBSCRIPTION_READ_ONLY";
    static final String MESSAGE = "La suscripción de tu organización venció: la cuenta está en solo lectura. "
            + "El administrador puede pagar un plan para volver a registrar cambios.";

    private static final Set<String> WRITES = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final ObjectProvider<JdbcSubscriptionProjection> subscriptions;
    private final ObjectProvider<ErrorResponseWriter> errors;

    /** Proveedores: en las pruebas de la capa web (sin base de datos) la regla no se aplica. */
    ReadOnlySubscriptionGuard(ObjectProvider<JdbcSubscriptionProjection> subscriptions,
            ObjectProvider<ErrorResponseWriter> errors) {
        this.subscriptions = subscriptions;
        this.errors = errors;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
                    throws Exception {
                return allows(request, response);
            }
        }).addPathPatterns("/api/v1/**");
    }

    boolean allows(HttpServletRequest request, HttpServletResponse response) throws java.io.IOException {
        if (!WRITES.contains(request.getMethod())) {
            return true;
        }
        var tenant = TenantContext.current();
        JdbcSubscriptionProjection projection = subscriptions.getIfAvailable();
        if (tenant.isEmpty() || projection == null || projection.find(tenant.get().organizationId())
                .filter(plan -> plan.readOnly()).isEmpty()) {
            return true;
        }
        errors.getObject().write(response, HttpStatus.PAYMENT_REQUIRED.value(),
                ErrorResponse.of(SUBSCRIPTION_READ_ONLY, MESSAGE));
        return false;
    }
}
