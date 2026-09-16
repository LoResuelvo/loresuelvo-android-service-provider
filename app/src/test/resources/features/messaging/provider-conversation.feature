Feature: Conversación del prestador con un consumidor
  Como prestador autenticado
  Quiero leer y enviar mensajes dentro de una conversación
  Para coordinar el trabajo con el consumidor que aceptó mi postulación

  Scenario: 01-PCC Abrir una conversación con mensajes previos
    Given que la API devuelve el detalle de la conversación 42 con 3 mensajes (1 del consumidor, 2 del prestador)
    When el prestador navega a la ruta de la conversación 42
    Then la pantalla muestra los 3 mensajes en orden cronológico con el remitente y el avatar correctos
    And el header exhibe el nombre completo del consumidor como título
    And el input bar está vacío y habilitado

  Scenario: 02-PCC Abrir una conversación sin mensajes
    Given que la API devuelve el detalle de la conversación 42 con 0 mensajes
    When el prestador navega a la ruta de la conversación 42
    Then la pantalla no muestra burbujas ni un estado de error
    And el input bar está vacío y habilitado

  @wip
  Scenario: 03-PCC Enviar un mensaje de texto
    Given que la conversación 42 está abierta con 3 mensajes previos
    And que la API aceptará el envío de un nuevo mensaje con contenido "Listo para empezar"
    When el prestador escribe "Listo para empezar" en el input y selecciona Enviar
    Then la pantalla agrega optimistamente una burbuja pendiente con ese texto
    And al confirmarse el envío la burbuja pendiente se reemplaza por la versión persistida por el servidor con id estable y timestamp autoritativo
    And el input bar vuelve a quedar vacío y habilitado

  @wip
  Scenario: 04-PCC Un envío que falla por red queda pendiente con opción de reintentar
    Given que la conversación 42 está abierta con 3 mensajes previos
    And que el próximo envío del prestador fallará por red
    When el prestador escribe "Mañana a las 10" en el input y selecciona Enviar
    Then la pantalla agrega optimistamente una burbuja pendiente con ese texto
    And al fallar el envío la burbuja permanece con un indicador de fallo y un botón Reintentar

  @wip
  Scenario: 05-PCC Reintentar un envío pendiente fallido confirma el mensaje
    Given que la conversación 42 está abierta con una burbuja pendiente en fallo por red
    And que el reintento del envío tendrá éxito
    When el prestador selecciona Reintentar en esa burbuja
    Then el envío se ejecuta una sola vez
    And al confirmarse la burbuja pendiente se reemplaza por la versión persistida por el servidor

  @wip
  Scenario: 06-PCC No enviar un mensaje en blanco
    Given que la conversación 42 está abierta con 3 mensajes previos
    When el prestador escribe solo espacios en el input
    Then el botón Enviar permanece deshabilitado
    And ningún envío se dispara
