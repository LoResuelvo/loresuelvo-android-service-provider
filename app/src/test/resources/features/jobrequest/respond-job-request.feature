Feature: Responder solicitudes de trabajo
  Como prestador
  Quiero visualizar y responder solicitudes de trabajo pendientes
  Para aceptar o rechazar el contacto inicial con el consumidor

  @wip
  Scenario: 01-RST Abrir el detalle de una solicitud pendiente
    Given que Home muestra una solicitud de trabajo pendiente para el prestador
    When el prestador selecciona Ver solicitud
    Then la app muestra el nombre completo del consumidor, el título y la descripción completa de la solicitud
    And ofrece Continuar conversación y Rechazar solicitud

  @wip
  Scenario: 03-RST Mostrar una solicitud sin imágenes
    Given que el detalle de la solicitud no incluye imágenes de contexto
    When el prestador abre el detalle
    Then la app muestra todos los demás datos disponibles de la solicitud
    And no muestra una galería vacía ni una acción para abrir imágenes

  @wip
  Scenario: 10-RST Cerrar el detalle sin responder
    Given que el prestador está viendo el detalle de una solicitud pendiente
    When cierra el detalle o utiliza Atrás
    Then regresa al mismo Home sin enviar una aceptación ni un rechazo
    And la solicitud continúa visible entre las pendientes

  @wip
  Scenario: 11-RST Recuperar el detalle después de una recreación
    Given que el prestador abrió una solicitud pendiente y todavía no la respondió
    When la actividad se recrea
    Then la app recupera el detalle usando el identificador de la solicitud
    And no repite una aceptación, un rechazo ni una navegación anterior
