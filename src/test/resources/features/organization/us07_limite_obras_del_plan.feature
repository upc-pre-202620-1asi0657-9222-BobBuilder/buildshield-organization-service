# language: es
@US07
Característica: US07 Límite de obras activas del plan
  Como administrador de la organización
  quiero que el plan contratado defina cuántas obras activas puedo tener
  para pagar según el número de obras que gestiono

  Antecedentes:
    Dado que la organización "Constructora Andina SAC" con RUC "20123456789" está registrada con el administrador "ana@andina.pe" y contraseña "Segura123"
    Y que "ana@andina.pe" inició sesión con la contraseña "Segura123"

  Escenario: El plan Plus admite cinco obras activas y rechaza la sexta
    Dado que la organización tiene el plan "PLUS" pagado
    Y que registré 5 obras activas
    Cuando registro la obra "Sexta" en "Av. Arequipa 900", "Lince", "Lima" desde el "2026-11-01" sin fecha de fin
    Entonces la operación es rechazada con estado 409 y código "WORKSITE_LIMIT_REACHED"
    Y el mensaje dice "Tu plan Plus permite hasta 5 obras activas"

  Escenario: Una obra terminada no cuenta para el límite del plan Plus
    Dado que la organización tiene el plan "PLUS" pagado
    Y que registré 5 obras activas
    Cuando registro la obra "Antigua" en "Av. Arequipa 900", "Lince", "Lima" del "2025-01-01" al "2025-12-31"
    Entonces la respuesta tiene estado 201

  Escenario: Reactivar una obra terminada también respeta el límite
    Dado que la organización tiene el plan "PLUS" pagado
    Y que registré la obra "Antigua" del "2025-01-01" al "2025-12-31"
    Y que registré 5 obras activas
    Cuando cambio la fecha de fin de la obra "Antigua" al "2027-12-31"
    Entonces la operación es rechazada con estado 409 y código "WORKSITE_LIMIT_REACHED"

  Escenario: El plan Pro no tiene límite de obras
    Dado que la organización tiene el plan "PRO" pagado
    Y que registré 5 obras activas
    Cuando registro la obra "Sexta" en "Av. Arequipa 900", "Lince", "Lima" desde el "2026-11-01" sin fecha de fin
    Entonces la respuesta tiene estado 201

  Escenario: La prueba gratuita tiene todas las funciones y no limita las obras
    Dado que registré 5 obras activas
    Cuando registro la obra "Sexta" en "Av. Arequipa 900", "Lince", "Lima" desde el "2026-11-01" sin fecha de fin
    Entonces la respuesta tiene estado 201

  Escenario: Con la suscripción vencida la cuenta queda en solo lectura
    Dado que registré la obra "Torre Norte" del "2026-11-01" al "2027-06-30"
    Y que la suscripción de la organización venció
    Cuando registro la obra "Torre Sur" en "Av. Arequipa 900", "Lince", "Lima" desde el "2026-11-01" sin fecha de fin
    Entonces la operación es rechazada con estado 402 y código "SUBSCRIPTION_READ_ONLY"
    Cuando consulto la obra "Torre Norte"
    Entonces la respuesta tiene estado 200
