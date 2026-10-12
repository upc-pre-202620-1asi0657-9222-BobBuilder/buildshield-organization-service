package pe.buildshield.organization.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.buildshield.organization.application.SubscriptionPlans.PlanLimits;
import pe.buildshield.organization.shared.error.ConflictException;
import pe.buildshield.organization.shared.error.ErrorDetail;
import pe.buildshield.organization.shared.error.ResourceNotFoundException;
import pe.buildshield.organization.shared.messaging.OutboxWriter;
import pe.buildshield.organization.domain.model.Location;
import pe.buildshield.organization.domain.model.Worksite;
import pe.buildshield.organization.domain.model.WorksiteRepository;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * US14: registro y mantenimiento de obras, dentro de la organización del contexto. Cada cambio publica
 * {@code LocationRegistered} o {@code LocationUpdated} (tipo WORKSITE) por el outbox, en la misma transacción.
 *
 * <p>US07: el plan Plus admite hasta 5 obras activas (sin fecha de fin o con fin no vencido). Al registrar una obra
 * activa, o al reactivar una terminada cambiando su fecha de fin, se rechaza con 409 si se supera el límite. El plan
 * sale de la copia local de la suscripción (eventos de {@code buildshield.subscription}); Pro y la prueba gratuita no
 * tienen límite.
 */
@Service
public class WorksiteService {

    public static final String WORKSITE_LIMIT_REACHED = "WORKSITE_LIMIT_REACHED";

    private final WorksiteRepository worksites;
    private final SiteVisibility visibility;
    private final SubscriptionPlans plans;

    private final OutboxWriter outbox;
    private final Clock clock;

    public WorksiteService(WorksiteRepository worksites, SiteVisibility visibility, SubscriptionPlans plans,
            OutboxWriter outbox, Clock clock) {
        this.worksites = worksites;
        this.visibility = visibility;
        this.plans = plans;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Transactional
    public WorksiteView register(RegisterWorksite command) {
        Worksite worksite = Worksite.register(command.name(), command.location(), command.startDate(), command.endDate());
        if (worksite.activeOn(today())) {
            requireRoomForAnotherActiveWorksite();
        }
        return WorksiteView.of(publish(OrganizationEvents.LOCATION_REGISTERED, worksites.save(worksite)));
    }

    /** 404 si no existe, es de otra organización o quien consulta no tiene acceso a ella. */
    @Transactional(readOnly = true)
    public WorksiteView get(UUID id) {
        return WorksiteView.of(visibleWorksite(id));
    }

    @Transactional(readOnly = true)
    public List<WorksiteView> list() {
        return visibility.visibleWorksites().stream().map(WorksiteView::of).toList();
    }

    @Transactional
    public WorksiteView update(UUID id, UpdateWorksite command) {
        Worksite worksite = worksites.findById(id).orElseThrow(() -> notFound(id));
        LocalDate today = today();
        boolean wasActive = worksite.activeOn(today);
        worksite.update(command.name(), command.mergedLocation(worksite.location()), command.startDate(),
                command.endDate());
        if (!wasActive && worksite.activeOn(today)) {
            requireRoomForAnotherActiveWorksite();
        }
        return WorksiteView.of(publish(OrganizationEvents.LOCATION_UPDATED, worksites.save(worksite)));
    }

    /**
     * Con el plan Plus, una obra activa más no puede superar su límite. El bloqueo por organización evita que dos
     * altas simultáneas cuenten la misma cantidad y pasen las dos.
     */
    private void requireRoomForAnotherActiveWorksite() {
        PlanLimits plan = plans.current().orElse(null);
        if (plan == null || !plan.limitsActiveWorksites()) {
            return;
        }
        plans.lockActiveWorksiteQuota();
        long active = worksites.countActive(today());
        if (active >= plan.maxActiveWorksites()) {
            throw new ConflictException(WORKSITE_LIMIT_REACHED, "Tu plan " + plan.planName() + " permite hasta "
                    + plan.maxActiveWorksites() + " obras activas. Termina una obra (fecha de fin) o cambia al plan Pro "
                    + "para registrar más.", List.of(
                    new ErrorDetail("plan", plan.plan()),
                    new ErrorDetail("maxActiveWorksites", Integer.toString(plan.maxActiveWorksites())),
                    new ErrorDetail("activeWorksites", Long.toString(active))));
        }
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    private Worksite publish(String eventType, Worksite worksite) {
        outbox.append(eventType, OrganizationEvents.LOCATION, worksite.id(),
                OrganizationEvents.LocationChanged.of(worksite, LocalDate.now(clock)));
        return worksite;
    }

    private Worksite visibleWorksite(UUID id) {
        return worksites.findById(id).filter(visibility::canSee).orElseThrow(() -> notFound(id));
    }

    static ResourceNotFoundException notFound(UUID id) {
        return new ResourceNotFoundException("WORKSITE_NOT_FOUND", "La obra " + id + " no existe");
    }

    public record RegisterWorksite(String name, Location location, LocalDate startDate, LocalDate endDate) {
    }

    /** Campos nulos: no cambian. Las coordenadas se cambian juntas. */
    public record UpdateWorksite(String name, String address, String district, String city, Double latitude,
            Double longitude, LocalDate startDate, LocalDate endDate) {

        Location mergedLocation(Location current) {
            if (address == null && district == null && city == null && latitude == null && longitude == null) {
                return null;
            }
            boolean newCoordinates = latitude != null || longitude != null;
            return new Location(
                    address != null ? address : current.address(),
                    district != null ? district : current.district(),
                    city != null ? city : current.city(),
                    newCoordinates ? latitude : current.latitude(),
                    newCoordinates ? longitude : current.longitude());
        }
    }

    public record WorksiteView(UUID id, String name, Location location, LocalDate startDate, LocalDate endDate) {

        static WorksiteView of(Worksite worksite) {
            return new WorksiteView(worksite.id(), worksite.name(), worksite.location(), worksite.startDate(),
                    worksite.endDate());
        }
    }
}
