package pe.buildshield.organization.application;

import org.junit.jupiter.api.Test;
import pe.buildshield.organization.shared.error.ConflictException;
import pe.buildshield.organization.shared.error.ErrorDetail;
import pe.buildshield.organization.shared.error.ResourceNotFoundException;
import pe.buildshield.organization.domain.model.Location;
import pe.buildshield.organization.domain.model.Worksite;
import pe.buildshield.organization.domain.model.WorksiteRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorksiteServiceTest {

    private final pe.buildshield.organization.shared.messaging.OutboxWriter outbox =
            mock(pe.buildshield.organization.shared.messaging.OutboxWriter.class);
    private static final Location LIMA = new Location("Av. Javier Prado 123", "San Isidro", "Lima", -12.09, -77.04);
    private static final UUID ID = UUID.randomUUID();

    private final WorksiteRepository worksites = mock(WorksiteRepository.class);
    private final SiteVisibility visibility = mock(SiteVisibility.class);
    private final SubscriptionPlans plans = mock(SubscriptionPlans.class);
    private final WorksiteService service = new WorksiteService(worksites, visibility, plans, outbox,
            java.time.Clock.fixed(java.time.Instant.parse("2026-10-08T12:00:00Z"), java.time.ZoneOffset.UTC));

    @Test
    void registers_and_returns_the_saved_worksite() {
        when(worksites.save(any())).thenAnswer(invocation -> withId(invocation.getArgument(0)));

        WorksiteService.WorksiteView view = service.register(new WorksiteService.RegisterWorksite(
                "Torre Norte", LIMA, LocalDate.of(2026, 11, 1), LocalDate.of(2027, 6, 30)));

        assertThat(view.id()).isEqualTo(ID);
        assertThat(view.name()).isEqualTo("Torre Norte");
        verify(outbox).append(eq("LocationRegistered"), eq(ID),
                eq(new OrganizationEvents.LocationChanged(ID, "WORKSITE", "Torre Norte", "Av. Javier Prado 123", true,
                        "San Isidro", "Lima", null, LocalDate.of(2026, 11, 1), LocalDate.of(2027, 6, 30))));
    }

    @Test
    void get_returns_404_when_not_found_or_not_visible() {
        Worksite worksite = existing();
        when(worksites.findById(ID)).thenReturn(Optional.of(worksite));
        when(visibility.canSee(worksite)).thenReturn(false);

        assertThatThrownBy(() -> service.get(ID)).isInstanceOf(ResourceNotFoundException.class)
                .hasFieldOrPropertyWithValue("code", "WORKSITE_NOT_FOUND");
        assertThatThrownBy(() -> service.get(UUID.randomUUID())).isInstanceOf(ResourceNotFoundException.class);

        when(visibility.canSee(worksite)).thenReturn(true);
        assertThat(service.get(ID).name()).isEqualTo("Torre Norte");
    }

    @Test
    void list_returns_what_the_requester_can_see() {
        when(visibility.visibleWorksites()).thenReturn(List.of(existing()));

        assertThat(service.list()).extracting(WorksiteService.WorksiteView::name).containsExactly("Torre Norte");
    }

    @Test
    void update_merges_partial_location_and_dates() {
        when(worksites.findById(ID)).thenReturn(Optional.of(existing()));
        when(worksites.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        WorksiteService.WorksiteView view = service.update(ID, new WorksiteService.UpdateWorksite(
                null, "Av. Javier Prado 456", null, null, null, null, null, LocalDate.of(2027, 12, 31)));

        assertThat(view.location()).isEqualTo(new Location("Av. Javier Prado 456", "San Isidro", "Lima", -12.09, -77.04));
        assertThat(view.endDate()).isEqualTo(LocalDate.of(2027, 12, 31));
        verify(outbox).append(eq("LocationUpdated"), eq(ID), any());
    }

    @Test
    void update_can_replace_coordinates_together_and_ignores_empty_location() {
        when(worksites.findById(ID)).thenReturn(Optional.of(existing()));
        when(worksites.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        WorksiteService.WorksiteView moved = service.update(ID, new WorksiteService.UpdateWorksite(
                "Torre Norte II", null, null, null, -12.1, -77.1, null, null));
        WorksiteService.WorksiteView same = service.update(ID, new WorksiteService.UpdateWorksite(
                null, null, null, null, null, null, null, null));

        assertThat(moved.location().latitude()).isEqualTo(-12.1);
        assertThat(moved.name()).isEqualTo("Torre Norte II");
        assertThat(same.location().address()).isEqualTo("Av. Javier Prado 123");
    }

    @Test
    void update_of_an_unknown_worksite_is_404() {
        assertThatThrownBy(() -> service.update(ID, new WorksiteService.UpdateWorksite(
                "X", null, null, null, null, null, null, null)))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(worksites, never()).save(any());
    }

    @Test
    void the_plus_plan_rejects_a_sixth_active_worksite_with_a_clear_message() {
        when(plans.current()).thenReturn(Optional.of(new SubscriptionPlans.PlanLimits("PLUS", "ACTIVE", 5)));
        when(worksites.countActive(LocalDate.of(2026, 10, 8))).thenReturn(5L);

        assertThatThrownBy(() -> service.register(new WorksiteService.RegisterWorksite("Sexta", LIMA,
                LocalDate.of(2026, 11, 1), null)))
                .isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("code", "WORKSITE_LIMIT_REACHED")
                .hasMessageContaining("Tu plan Plus permite hasta 5 obras activas")
                .satisfies(ex -> assertThat(((ConflictException) ex).getDetails())
                        .extracting(ErrorDetail::message).containsExactly("PLUS", "5", "5"));
        verify(plans).lockActiveWorksiteQuota();
        verify(worksites, never()).save(any());
        verify(outbox, never()).append(any(), any(), any());
    }

    @Test
    void the_plus_plan_accepts_the_fifth_active_worksite_and_finished_ones_do_not_count() {
        when(plans.current()).thenReturn(Optional.of(new SubscriptionPlans.PlanLimits("PLUS", "ACTIVE", 5)));
        when(worksites.countActive(LocalDate.of(2026, 10, 8))).thenReturn(4L);
        when(worksites.save(any())).thenAnswer(invocation -> withId(invocation.getArgument(0)));

        assertThat(service.register(new WorksiteService.RegisterWorksite("Quinta", LIMA, LocalDate.of(2026, 11, 1),
                null)).id()).isEqualTo(ID);

        when(worksites.countActive(LocalDate.of(2026, 10, 8))).thenReturn(5L);
        assertThat(service.register(new WorksiteService.RegisterWorksite("Ya terminada", LIMA,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 9, 30))).id()).isEqualTo(ID);
    }

    @Test
    void pro_the_free_trial_and_an_unknown_plan_have_no_limit() {
        when(worksites.countActive(any())).thenReturn(50L);
        when(worksites.save(any())).thenAnswer(invocation -> withId(invocation.getArgument(0)));
        WorksiteService.RegisterWorksite command = new WorksiteService.RegisterWorksite("Otra", LIMA,
                LocalDate.of(2026, 11, 1), null);

        when(plans.current()).thenReturn(Optional.empty());
        service.register(command);
        when(plans.current()).thenReturn(Optional.of(new SubscriptionPlans.PlanLimits("PRO", "ACTIVE", null)));
        service.register(command);
        when(plans.current()).thenReturn(Optional.of(new SubscriptionPlans.PlanLimits("FREE", "TRIAL", 5)));
        service.register(command);

        verify(plans, never()).lockActiveWorksiteQuota();
        verify(worksites, times(3)).save(any());
    }

    @Test
    void reactivating_a_finished_worksite_counts_against_the_limit_but_editing_an_active_one_does_not() {
        Worksite finished = Worksite.restore(ID, "Torre Norte", LIMA, LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 9, 30), 0L);
        when(plans.current()).thenReturn(Optional.of(new SubscriptionPlans.PlanLimits("PLUS", "ACTIVE", 5)));
        when(worksites.countActive(LocalDate.of(2026, 10, 8))).thenReturn(5L);
        when(worksites.findById(ID)).thenReturn(Optional.of(finished));

        assertThatThrownBy(() -> service.update(ID, new WorksiteService.UpdateWorksite(null, null, null, null, null,
                null, null, LocalDate.of(2027, 3, 31)))).isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("code", "WORKSITE_LIMIT_REACHED");

        when(worksites.findById(ID)).thenReturn(Optional.of(existing()));
        when(worksites.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        assertThat(service.update(ID, new WorksiteService.UpdateWorksite("Renombrada", null, null, null, null, null,
                null, null)).name()).isEqualTo("Renombrada");
        verify(plans, times(1)).lockActiveWorksiteQuota();
    }

    @Test
    void plan_limits_know_their_name_and_read_only_state() {
        assertThat(new SubscriptionPlans.PlanLimits("PLUS", "EXPIRED", 5).readOnly()).isTrue();
        assertThat(new SubscriptionPlans.PlanLimits("PRO", "ACTIVE", null).readOnly()).isFalse();
        assertThat(new SubscriptionPlans.PlanLimits("PRO", "ACTIVE", null).planName()).isEqualTo("Pro");
        assertThat(new SubscriptionPlans.PlanLimits(null, "ACTIVE", null).planName()).isEmpty();
        assertThat(new SubscriptionPlans.PlanLimits("", "ACTIVE", null).planName()).isEmpty();
    }

    private static Worksite existing() {
        return Worksite.restore(ID, "Torre Norte", LIMA, LocalDate.of(2026, 11, 1), LocalDate.of(2027, 6, 30), 0L);
    }

    private static Worksite withId(Worksite worksite) {
        return Worksite.restore(ID, worksite.name(), worksite.location(), worksite.startDate(), worksite.endDate(), 0L);
    }
}
