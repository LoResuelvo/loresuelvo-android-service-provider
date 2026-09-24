Feature: Enviar una propuesta de servicio desde el chat

  Scenario: 01-PSP Abrir una propuesta desde un chat activo
    Given que estoy en un chat activo con un consumidor
    When elijo Crear propuesta de servicio entre las acciones del chat
    Then el formulario de propuesta identifica a ese consumidor
    And ofrece monto, fecha, hora, motivo de la visita y duración estimada

  Scenario: 02-PSP Ocultar la creación de propuestas en un chat que no está activo
    Given que el chat con el consumidor no está activo
    When abro las acciones del chat
    Then Crear propuesta de servicio no está disponible

  Scenario: 03-PSP Explicar los campos inválidos de la propuesta
    Given que mi propuesta contiene un campo obligatorio inválido
    When intento continuar a la confirmación
    Then ese campo explica qué debo corregir
    And no se envía ninguna propuesta

  Scenario: 04-PSP Revisar antes de enviar
    Given que mi propuesta contiene datos válidos de la visita
    When elijo Enviar propuesta
    Then se me pide confirmar la propuesta
    And todavía no se ha enviado ninguna propuesta

  Scenario: 05-PSP Volver de la confirmación a la edición
    Given que estoy revisando la confirmación de una propuesta
    When cancelo la confirmación
    Then puedo seguir editando la misma propuesta
    And no se envía ninguna propuesta

  @wip
  Scenario: 06-PSP Cerrar sin enviar
    Given que estoy editando una propuesta sin enviar
    When cierro el formulario
    Then vuelvo al mismo chat sin enviar una propuesta

  @wip
  Scenario: 07-PSP Enviar una propuesta correctamente
    Given que una propuesta válida espera mi confirmación
    And el servicio confirmará su creación
    When confirmo el envío
    Then vuelvo al chat con la confirmación Propuesta enviada
    And el formulario se limpia después de crear una propuesta pendiente

  @wip
  Scenario: 08-PSP Evitar envíos duplicados
    Given que mi propuesta confirmada todavía se está enviando
    When intento enviarla otra vez
    Then el envío sigue en curso con una sola solicitud

  @wip
  Scenario: 09-PSP Conservar el formulario ante una interrupción
    Given que tengo abierto el formulario de una propuesta
    When vuelvo después de una recreación de la Activity o de pasar la app a segundo plano
    Then los datos de la propuesta y el estado del envío se conservan de forma segura
    And no se inicia un nuevo envío automáticamente

  @wip
  Scenario: 10-PSP Explicar el requisito de conectar la cuenta de pagos
    Given que la API requiere una cuenta de pagos conectada para mi propuesta
    When confirmo el envío
    Then puedo abrir el flujo existente de conexión con Mercado Pago desde Perfil
    And los datos de mi propuesta siguen disponibles sin reenvío automático

  @wip
  Scenario: 11-PSP Explicar el rechazo de una propuesta
    Given que la API rechazará mi propuesta válida
    When confirmo el envío
    Then veo una explicación en el idioma de la app y conservo los datos de mi propuesta
    And debo resolver el motivo del rechazo antes de volver a enviarla explícitamente

  @wip
  Scenario: 12-PSP Explicar un resultado de envío incierto
    Given que el servicio no devolverá un resultado confiable sobre la creación
    When confirmo el envío de una propuesta válida
    Then se me informa que la propuesta puede haberse creado
    And los datos de mi propuesta siguen disponibles sin reenvío automático
