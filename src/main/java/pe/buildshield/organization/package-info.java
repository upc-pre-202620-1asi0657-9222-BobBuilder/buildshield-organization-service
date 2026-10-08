/**
 * Microservicio Organización y Configuración (contexto de soporte): organizaciones, obras, almacenes,
 * catálogo de materiales y asignación de encargados. Publica en {@code buildshield.organization} y consume
 * {@code buildshield.iam}.
 *
 * <p>Capas: {@code interfaces} (REST) → {@code application} (casos de uso) → {@code domain} (Java puro);
 * {@code infrastructure} implementa los puertos (JPA, JDBC, Kafka). {@code shared} es el kernel compartido
 * copiado de buildshield-iam-service.
 */
package pe.buildshield.organization;
