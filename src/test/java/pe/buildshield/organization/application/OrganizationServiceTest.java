package pe.buildshield.organization.application;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import pe.buildshield.organization.domain.model.Organization;
import pe.buildshield.organization.domain.model.OrganizationRepository;
import pe.buildshield.organization.domain.model.Ruc;
import pe.buildshield.organization.shared.error.ResourceNotFoundException;
import pe.buildshield.organization.shared.error.ValidationException;
import pe.buildshield.organization.shared.tenant.TenantContext;
import pe.buildshield.organization.shared.tenant.TenantInfo;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrganizationServiceTest {

    private static final UUID ANDINA = UUID.randomUUID();

    private final OrganizationRepository organizations = mock(OrganizationRepository.class);
    private final OrganizationService service = new OrganizationService(organizations);

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void registers_the_organization_informed_by_iam() {
        assertThat(service.registerFromIam(ANDINA, "20123456789", "Constructora Andina SAC")).isTrue();

        verify(organizations).save(any(Organization.class));
    }

    @Test
    void a_redelivered_event_does_not_register_it_twice() {
        when(organizations.findById(ANDINA)).thenReturn(Optional.of(andina()));

        assertThat(service.registerFromIam(ANDINA, "20123456789", "Constructora Andina SAC")).isFalse();

        verify(organizations, never()).save(any());
    }

    @Test
    void invalid_data_is_rejected_before_saving() {
        assertThatThrownBy(() -> service.registerFromIam(ANDINA, "123", "Andina"))
                .isInstanceOf(ValidationException.class);
        verify(organizations, never()).save(any());
    }

    @Test
    void the_user_sees_only_their_own_organization() {
        TenantContext.set(new TenantInfo(ANDINA, UUID.randomUUID(), "SITE_MANAGER"));
        when(organizations.findById(ANDINA)).thenReturn(Optional.of(andina()));

        OrganizationService.OrganizationView current = service.current();

        assertThat(current.id()).isEqualTo(ANDINA);
        assertThat(current.ruc()).isEqualTo("20123456789");
        assertThat(current.legalName()).isEqualTo("Constructora Andina SAC");
        assertThat(service.get(ANDINA)).isEqualTo(current);
        UUID other = UUID.randomUUID();
        assertThatThrownBy(() -> service.get(other))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasFieldOrPropertyWithValue("code", "ORGANIZATION_NOT_FOUND");
    }

    @Test
    void an_organization_not_yet_received_from_iam_is_a_404() {
        TenantContext.set(new TenantInfo(ANDINA, UUID.randomUUID(), "ADMINISTRATOR"));

        assertThatThrownBy(service::current).isInstanceOf(ResourceNotFoundException.class);
    }

    private static Organization andina() {
        return Organization.restore(ANDINA, new Ruc("20123456789"), "Constructora Andina SAC");
    }
}
