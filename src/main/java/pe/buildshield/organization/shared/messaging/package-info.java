/**
 * Eventos de integración entre microservicios: sobre JSON común ({@link pe.buildshield.organization.shared.messaging.EventEnvelope}),
 * Transactional Outbox ({@link pe.buildshield.organization.shared.messaging.OutboxWriter} y
 * {@link pe.buildshield.organization.shared.messaging.OutboxPublisher}) y consumo idempotente
 * ({@link pe.buildshield.organization.shared.messaging.IdempotentEventProcessor}). Entrega al menos una vez.
 */
package pe.buildshield.organization.shared.messaging;
