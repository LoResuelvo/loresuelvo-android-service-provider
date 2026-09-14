Feature: Responder solicitudes de trabajo
  Como prestador
  Quiero visualizar y responder solicitudes de trabajo pendientes
  Para aceptar el contacto inicial y continuar la conversación con el consumidor

  Scenario: 01-RST Abrir el detalle de una solicitud pendiente
    Given que Home muestra una solicitud de trabajo pendiente para el prestador
    When el prestador selecciona Ver solicitud
    Then la app muestra el nombre completo del consumidor, el título y la descripción completa de la solicitud
    And ofrece Continuar conversación

  Scenario: 02-RST Visualizar las imágenes de contexto
    Given que el detalle de la solicitud incluye imágenes de contexto
    When el prestador selecciona una miniatura
    Then la app muestra esa imagen en pantalla completa con una descripción accesible
    And permite cerrarla para volver al mismo detalle sin responder la solicitud

  Scenario: 04-RST Aceptar una solicitud pendiente
    Given que el prestador está viendo una solicitud pendiente
    When selecciona Continuar conversación
    Then la app muestra progreso y bloquea la acción mientras envía una única aceptación
    And la solicitud aceptada deja de aparecer entre las pendientes de Home

  Scenario: 06-RST Reintentar una aceptación que falló temporalmente
    Given que la aceptación falló por red o por un error del servidor
    When el prestador selecciona Reintentar
    Then la app envía nuevamente una sola aceptación
    And conserva visibles los datos de la solicitud hasta recibir confirmación

  Scenario: 09-RST Resolver una solicitud que ya no está pendiente al aceptar
    Given que la API rechaza la aceptación porque la solicitud ya no está pendiente
    When el prestador selecciona Continuar conversación
    Then la app informa que la solicitud ya no está disponible
    And no presenta la respuesta como exitosa

  Scenario: 05-RST Abrir la conversación aceptada
    Given que la API aceptó la solicitud y activó la conversación vinculada
    When la app procesa la confirmación de aceptación
    Then navega inmediatamente a la ruta de conversación identificada por conversation_id
    And la ruta muestra un destino temporal hasta que la US de chat entregue la conversación real
    And Atrás no vuelve al detalle de una solicitud ya respondida

  Scenario: 03-RST Mostrar una solicitud sin imágenes
    Given que el detalle de la solicitud no incluye imágenes de contexto
    When el prestador abre el detalle
    Then la app muestra todos los demás datos disponibles de la solicitud
    And no muestra una galería vacía ni una acción para abrir imágenes

  Scenario: 10-RST Cerrar el detalle sin responder
    Given que el prestador está viendo el detalle de una solicitud pendiente
    When cierra el detalle o utiliza Atrás
    Then regresa al mismo Home sin enviar una aceptación
    And la solicitud continúa visible entre las pendientes

  Scenario: 11-RST Recuperar el detalle después de una recreación
    Given que el prestador abrió una solicitud pendiente y todavía no la respondió
    When la actividad se recrea
    Then la app recupera el detalle usando el identificador de la solicitud
    And no repite una aceptación ni una navegación anterior
