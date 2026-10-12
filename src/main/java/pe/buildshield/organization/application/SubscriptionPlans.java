package pe.buildshield.organization.application;

import java.util.Optional;

/**
 * Plan vigente de la organización del contexto, según la copia local alimentada por los eventos de Suscripción y
 * Pagos (sin llamadas síncronas a subscription-service).
 */
public interface SubscriptionPlans {

    /** Plan de la organización del contexto; vacío si todavía no llegó ningún evento de su suscripción. */
    Optional<PlanLimits> current();

    /**
     * Serializa, hasta el fin de la transacción en curso, las altas y reactivaciones de obras de la organización del
     * contexto: dos pedidos simultáneos no pueden pasar ambos el límite.
     */
    void lockActiveWorksiteQuota();

    /**
     * Plan y estado de la suscripción.
     *
     * @param plan               FREE, PLUS o PRO
     * @param status             TRIAL, ACTIVE o EXPIRED
     * @param maxActiveWorksites obras activas permitidas; nulo = ilimitadas
     */
    record PlanLimits(String plan, String status, Integer maxActiveWorksites) {

        public static final String EXPIRED = "EXPIRED";

        /** La prueba gratuita tiene todas las funciones: el límite rige solo para un plan pagado. */
        public boolean limitsActiveWorksites() {
            return maxActiveWorksites != null && !"FREE".equals(plan);
        }

        public boolean readOnly() {
            return EXPIRED.equals(status);
        }

        /** Nombre visible del plan: Plus, Pro o Free. */
        public String planName() {
            return plan == null || plan.isEmpty() ? "" : plan.charAt(0) + plan.substring(1).toLowerCase();
        }
    }
}
