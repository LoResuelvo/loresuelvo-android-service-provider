Feature: Bandeja de mensajes del prestador
  Como prestador autenticado
  Quiero visualizar mis conversaciones y solicitudes de contacto
  Para gestionar de manera organizada la comunicación con consumidores

  Scenario: 01-PMI Abrir Mensajes desde la navegación principal
    Given que el prestador autenticado se encuentra en Home
    When selecciona Mensajes en la barra de navegación inferior
    Then la app muestra la bandeja de mensajes y marca Mensajes como destino seleccionado
    And la barra permanece disponible en Home y Mensajes sin mostrarse en destinos de detalle o autenticación

  Scenario: 02-PMI Mostrar los resúmenes de conversación del prestador
    Given que la API devolverá conversaciones asociadas a la cuenta del prestador
    When la bandeja termina de cargar
    Then muestra todas las conversaciones en el orden recibido desde la API
    And cada fila muestra el nombre completo del consumidor y su foto o sus iniciales como alternativa
    And cada fila muestra el extracto disponible del último mensaje y una fecha u hora relativa

  Scenario: 03-PMI Distinguir una solicitud pendiente de aceptación
    Given que la API devolverá una conversación pendiente y una conversación activa
    When la bandeja muestra ambas conversaciones
    Then la conversación pendiente exhibe un distintivo Pendiente de aceptación
    And la conversación activa no exhibe ese distintivo

  Scenario: 04-PMI Mostrar una bandeja vacía instructiva
    Given que la API no devuelve conversaciones para la cuenta del prestador
    When la bandeja termina de cargar
    Then muestra un estado vacío que explica que los mensajes aparecerán al recibir o aceptar solicitudes
    And no muestra una lista vacía ni un error

  Scenario: 05-PMI Mostrar la carga inicial de conversaciones
    Given que la consulta de conversaciones permanece en curso
    When el prestador abre la bandeja de mensajes
    Then muestra un indicador de carga accesible hasta que la consulta finaliza
    And no muestra simultáneamente contenido vacío, datos anteriores ni un error

  Scenario: 06-PMI Reintentar una consulta fallida
    Given que la consulta de conversaciones falló por red o por un error del servidor y el siguiente intento tendrá éxito
    When el prestador selecciona Reintentar
    Then la app ejecuta nuevamente la misma consulta una sola vez
    And muestra las conversaciones recuperadas sin duplicar solicitudes en curso

  @wip
  Scenario: 07-PMI Abrir la ruta de una conversación seleccionada
    Given que la bandeja muestra una conversación con un identificador válido
    When el prestador selecciona esa conversación
    Then la app navega una sola vez a la ruta de conversación identificada por conversation_id
    And al volver regresa a la bandeja de mensajes conservada
