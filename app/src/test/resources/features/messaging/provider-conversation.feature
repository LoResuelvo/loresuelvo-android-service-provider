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

  Scenario: 03-PCC Enviar un mensaje de texto
    Given que la conversación 42 está abierta con 3 mensajes previos
    And que la API aceptará el envío de un nuevo mensaje con contenido "Listo para empezar"
    When el prestador escribe "Listo para empezar" en el input y selecciona Enviar
    Then la pantalla agrega optimistamente una burbuja pendiente con ese texto
    And al confirmarse el envío la burbuja pendiente se reemplaza por la versión persistida por el servidor con id estable y timestamp autoritativo
    And el input bar vuelve a quedar vacío y habilitado

  Scenario: 04-PCC Un envío que falla por red queda pendiente con opción de reintentar
    Given que la conversación 42 está abierta con 3 mensajes previos
    And que el próximo envío del prestador fallará por red
    When el prestador escribe "Mañana a las 10" en el input y selecciona Enviar
    Then la pantalla agrega optimistamente una burbuja pendiente con ese texto
    And al fallar el envío la burbuja permanece con un indicador de fallo y un botón Reintentar

  Scenario: 05-PCC Reintentar un envío pendiente fallido confirma el mensaje
    Given que la conversación 42 está abierta con una burbuja pendiente en fallo por red
    And que el reintento del envío tendrá éxito
    When el prestador selecciona Reintentar en esa burbuja
    Then el envío se ejecuta una sola vez
    And al confirmarse la burbuja pendiente se reemplaza por la versión persistida por el servidor

  Scenario: 06-PCC No enviar un mensaje en blanco
    Given que la conversación 42 está abierta con 3 mensajes previos
    When el prestador escribe solo espacios en el input
    Then el botón Enviar permanece deshabilitado
    And ningún envío se dispara

  # =========================================================================
  # US-B — Adjuntar imágenes a un mensaje (galería + cámara + upload + retry)
  # =========================================================================

  @wip
  Scenario: 01-PCM Adjuntar una imagen de la galería como preview antes de enviar
    Given que la conversación 42 está abierta sin mensajes previos
    When el prestador selecciona una imagen JPEG de su galería
    Then la pantalla muestra una preview de esa imagen en la barra del input
    And el botón Enviar queda habilitado con esa imagen adjunta

  @wip
  Scenario: 02-PCM Adjuntar una foto recién tomada con la cámara como preview
    Given que la conversación 42 está abierta sin mensajes previos
    When el prestador toma una foto JPEG con la cámara del dispositivo
    Then la pantalla muestra una preview de esa foto en la barra del input
    And el botón Enviar queda habilitado con esa imagen adjunta

  @wip
  Scenario: 03-PCM Enviar una imagen con burbuja pendiente optimista
    Given que la conversación 42 está abierta sin mensajes previos
    And que el prestador tiene una imagen JPEG adjunta como preview
    And que la API aceptará el upload de la imagen y el envío del mensaje
    When el prestador selecciona Enviar
    Then la pantalla agrega optimistamente una burbuja pendiente con la miniatura de esa imagen
    And la preview local se descarta y el input bar queda vacío

  @wip
  Scenario: 04-PCM La imagen confirmada por el servidor reemplaza la burbuja pendiente
    Given que la conversación 42 está abierta sin mensajes previos
    And que el prestador está enviando una imagen JPEG
    When el servidor confirma el upload y la persistencia del mensaje
    Then la burbuja pendiente se reemplaza por la versión persistida con id estable y url de descarga

  @wip
  Scenario: 05-PCM Una subida de imagen fallida por red queda pendiente con retry
    Given que la conversación 42 está abierta sin mensajes previos
    And que el próximo upload de imagen del prestador fallará por red
    When el prestador selecciona Enviar con una imagen adjunta
    Then la pantalla agrega optimistamente una burbuja pendiente con la miniatura
    And al fallar el upload la burbuja permanece con un indicador de fallo y un botón Reintentar

  @wip
  Scenario: 06-PCM Reintentar una subida de imagen pendiente confirma el mensaje
    Given que la conversación 42 está abierta con una burbuja pendiente de imagen en fallo por red
    And que el reintento del upload tendrá éxito
    When el prestador selecciona Reintentar en esa burbuja
    Then el upload se ejecuta una sola vez
    And al confirmarse la burbuja pendiente se reemplaza por la versión persistida con url de descarga

  @wip
  Scenario: 07-PCM Renderizar la imagen recibida al abrir la conversación
    Given que la API devuelve el detalle de la conversación 42 con un mensaje confirmado del prestador que lleva una imagen JPEG adjunta
    When el prestador navega a la ruta de la conversación 42
    Then la pantalla muestra la miniatura de esa imagen en su burbuja con el contenido accesible correcto

  # =========================================================================
  # US-C — Adjuntar audios a un mensaje (grabar + enviar + reproducir)
  # =========================================================================

  @wip
  Scenario: 01-PCA Grabar y enviar un audio como mensaje
    Given que el permiso de micrófono del prestador está concedido
    And que la conversación 42 está abierta sin mensajes previos
    When el prestador graba un clip de audio WebM de 3 segundos
    And selecciona Enviar
    Then la pantalla agrega optimistamente una burbuja pendiente con el reproductor y la duración del clip
    And al confirmarse el envío la burbuja pendiente se reemplaza por la versión persistida con id estable y url de descarga

  @wip
  Scenario: 02-PCA Reproducir un audio recibido
    Given que la API devuelve el detalle de la conversación 42 con un mensaje confirmado del consumidor que lleva un audio WebM de 5 segundos adjunto
    When el prestador navega a la ruta de la conversación 42
    And selecciona reproducir sobre la burbuja de audio
    Then el reproductor muestra el contador avanzando hasta la duración total

  @wip
  Scenario: 03-PCA Cancelar una grabación de audio en curso
    Given que el permiso de micrófono del prestador está concedido
    And que la conversación 42 está abierta sin mensajes previos
    When el prestador inicia una grabación de audio
    And cancela la grabación mid-way
    Then ningún envío se dispara
    And el botón de micrófono vuelve a estar disponible para una nueva grabación
