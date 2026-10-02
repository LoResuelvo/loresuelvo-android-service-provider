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

  Scenario Outline: 07-PCC Restringir todas las acciones del compositor fuera de Active
    Given que la conversación 42 tiene estado "<estado>"
    When el prestador intenta enviar adjuntar grabar y reintentar
    Then ninguna operación del compositor se inicia

    Examples:
      | estado      |
      | Pending     |
      | Rejected    |
      | Unsupported |
      | Loading     |
      | Error       |

  Scenario: 08-PCC Enviar varias veces durante una respuesta demorada
    Given que la conversación 42 está abierta sin mensajes previos
    And que la API aceptará el envío de un nuevo mensaje con contenido "Listo"
    When el prestador selecciona Enviar varias veces con el texto "Listo"
    Then el envío se ejecuta una sola vez

  Scenario: 09-PCC Reintentar varias veces preserva un borrador nuevo
    Given que la conversación 42 está abierta con una burbuja pendiente en fallo por red
    And que el reintento del envío tendrá éxito
    When el prestador reintenta varias veces con un borrador nuevo "Otro mensaje"
    Then el envío se ejecuta una sola vez
    And el borrador nuevo "Otro mensaje" permanece después de la confirmación

  Scenario Outline: 10-PCC No exponer una conversación inaccesible
    Given que la operación "<operacion>" de la conversación 42 devuelve "<fallo>"
    When el prestador ejecuta esa operación
    Then la conversación queda inaccesible sin mensajes ni compositor

    Examples:
      | operacion | fallo        |
      | detalle   | Unauthorized |
      | detalle   | Forbidden    |
      | detalle   | NotFound     |
      | envio     | Unauthorized |
      | envio     | Forbidden    |
      | envio     | NotFound     |
      | detalle   | WrongId      |

  Scenario: 11-PCC Restaurar el proceso carga solo el estado del servidor
    Given que la conversación 42 está abierta con una burbuja pendiente en fallo por red
    When se restaura un nuevo modelo de la conversación 42
    Then no se reenvía ni se restaura la burbuja local fallida

  # =========================================================================
  # US-B — Adjuntar imágenes a un mensaje (galería + cámara + upload + retry)
  # =========================================================================

  Scenario: 01-PCM Adjuntar una imagen de la galería como preview antes de enviar
    Given que la conversación 42 está abierta sin mensajes previos
    When el prestador selecciona una imagen JPEG de su galería
    Then la pantalla muestra una preview de esa imagen en la barra del input
    And el botón Enviar queda habilitado con esa imagen adjunta

  Scenario: 02-PCM Adjuntar una foto recién tomada con la cámara como preview
    Given que la conversación 42 está abierta sin mensajes previos
    When el prestador toma una foto JPEG con la cámara del dispositivo
    Then la pantalla muestra una preview de esa foto en la barra del input
    And el botón Enviar queda habilitado con esa imagen adjunta

  Scenario: 03-PCM Enviar una imagen con burbuja pendiente optimista
    Given que la conversación 42 está abierta sin mensajes previos
    And que el prestador tiene una imagen JPEG adjunta como preview
    And que la API aceptará el upload de la imagen y el envío del mensaje
    When el prestador selecciona Enviar
    Then la pantalla agrega optimistamente una burbuja pendiente con la miniatura de esa imagen
    And la preview local se descarta y el input bar queda vacío

  Scenario: 04-PCM La imagen confirmada por el servidor reemplaza la burbuja pendiente
    Given que la conversación 42 está abierta sin mensajes previos
    And que el prestador está enviando una imagen JPEG
    When el servidor confirma el upload y la persistencia del mensaje
    Then la burbuja pendiente se reemplaza por la versión persistida con id estable y url de descarga

  Scenario: 05-PCM Una subida de imagen fallida por red queda pendiente con retry
    Given que la conversación 42 está abierta sin mensajes previos
    And que el próximo upload de imagen del prestador fallará por red
    When el prestador selecciona Enviar con una imagen adjunta
    Then la pantalla agrega optimistamente una burbuja pendiente con la miniatura
    And al fallar el upload la burbuja permanece con un indicador de fallo y un botón Reintentar

  Scenario: 06-PCM Reintentar una subida de imagen pendiente confirma el mensaje
    Given que la conversación 42 está abierta con una burbuja pendiente de imagen en fallo por red
    And que el reintento del upload tendrá éxito
    When el prestador selecciona Reintentar en esa burbuja
    Then el upload se ejecuta una sola vez
    And al confirmarse la burbuja pendiente se reemplaza por la versión persistida con url de descarga

  Scenario: 07-PCM Renderizar la imagen recibida al abrir la conversación
    Given que la API devuelve el detalle de la conversación 42 con un mensaje confirmado del prestador que lleva una imagen JPEG adjunta
    When el prestador navega a la ruta de la conversación 42
    Then la pantalla muestra la miniatura de esa imagen en su burbuja con el contenido accesible correcto

  Scenario Outline: 08-PCM Seleccionar hasta tres formatos de imagen permitidos
    Given que la conversación 42 está abierta sin mensajes previos
    When el prestador selecciona <cantidad> imágenes de tipo "<tipo>"
    Then quedan <cantidad> previews listas para enviar

    Examples:
      | cantidad | tipo       |
      | 1        | image/jpeg |
      | 2        | image/png  |
      | 3        | image/webp |

  Scenario: 09-PCM Rechazar una cuarta imagen conservando las seleccionadas
    Given que la conversación 42 tiene tres imágenes seleccionadas
    When el prestador agrega una cuarta imagen
    Then las tres previews originales permanecen y se informa el límite

  Scenario: 10-PCM Reemplazar y descartar una preview individual
    Given que la conversación 42 tiene tres imágenes seleccionadas
    When el prestador reemplaza la segunda imagen y descarta la primera
    Then quedan la imagen reemplazada y la tercera imagen

  Scenario Outline: 11-PCM Rechazar archivos inválidos antes de subir
    Given que la conversación 42 está abierta sin mensajes previos
    When el prestador selecciona una imagen "<archivo>"
    Then se informa un fallo local sin iniciar un envío

    Examples:
      | archivo      |
      | vacía        |
      | no soportada |
      | ilegible     |
      | mayor a 5MiB |

  Scenario: 12-PCM Aceptar exactamente cinco MiB
    Given que la conversación 42 está abierta sin mensajes previos
    When el prestador selecciona una imagen de exactamente cinco MiB
    Then queda una preview lista para enviar

  Scenario: 13-PCM Cancelar selección preserva el borrador
    Given que la conversación 42 está abierta sin mensajes previos
    When el prestador cancela la selección con el borrador "Otro mensaje"
    Then ningún envío se dispara
    And el borrador "Otro mensaje" permanece

  Scenario: 14-PCM Evitar envíos duplicados de imágenes demoradas
    Given que la conversación 42 tiene tres imágenes seleccionadas
    And que la API aceptará el upload de la imagen y el envío del mensaje
    When el prestador selecciona Enviar imágenes varias veces
    Then el upload se ejecuta una sola vez

  Scenario: 15-PCM Reintentar imágenes preserva el borrador nuevo
    Given que la conversación 42 está abierta con una burbuja pendiente de imagen en fallo por red
    And que el reintento del upload tendrá éxito
    When el prestador reintenta varias veces con un borrador nuevo "Otro mensaje"
    Then el upload se ejecuta una sola vez
    And el borrador nuevo "Otro mensaje" permanece después de la confirmación

  # =========================================================================
  # US-C — Adjuntar audios a un mensaje (grabar + enviar + reproducir)
  # =========================================================================

  Scenario: 01-PCA Grabar y enviar un audio como mensaje
    Given que el permiso de micrófono del prestador está concedido
    And que la conversación 42 está abierta sin mensajes previos
    When el prestador graba un clip de audio WebM de 3 segundos
    And selecciona Enviar
    Then la pantalla agrega optimistamente una burbuja pendiente con el reproductor y la duración del clip
    And al confirmarse el envío la burbuja pendiente se reemplaza por la versión persistida con id estable y url de descarga

  Scenario: 02-PCA Reproducir un audio recibido
    Given que la API devuelve el detalle de la conversación 42 con un mensaje confirmado del consumidor que lleva un audio WebM de 5 segundos adjunto
    When el prestador navega a la ruta de la conversación 42
    And selecciona reproducir sobre la burbuja de audio
    Then el reproductor muestra el contador avanzando hasta la duración total

  Scenario: 03-PCA Cancelar una grabación de audio en curso
    Given que el permiso de micrófono del prestador está concedido
    And que la conversación 42 está abierta sin mensajes previos
    When el prestador inicia una grabación de audio
    And cancela la grabación mid-way
    Then ningún envío se dispara
    And el botón de micrófono vuelve a estar disponible para una nueva grabación

  Scenario Outline: 04-PCA Validar el audio antes de iniciar la subida
    Given que el permiso de micrófono del prestador está concedido
    And que la conversación 42 está abierta sin mensajes previos
    When el prestador termina un audio "<archivo>"
    Then el audio se "<resultado>" sin iniciar un envío

    Examples:
      | archivo          | resultado |
      | WebM válido      | acepta    |
      | cinco MiB        | acepta    |
      | 300 segundos     | acepta    |
      | mayor a cinco MiB| rechaza   |
      | 301 segundos     | rechaza   |
      | duración cero    | rechaza   |
      | muy corto        | rechaza   |
      | AAC              | rechaza   |

  Scenario: 05-PCA Denegar el permiso del micrófono
    Given que la conversación 42 está abierta sin mensajes previos
    When se deniega el permiso del micrófono
    Then se informa el permiso faltante sin iniciar la grabación

  Scenario: 06-PCA Interrumpir la grabación al salir de la conversación
    Given que el permiso de micrófono del prestador está concedido
    And que la conversación 42 está grabando un audio
    When la conversación pasa a segundo plano
    Then la grabación se cancela sin enviar ni conservar un archivo

  Scenario: 07-PCA Detener la grabación al alcanzar cinco minutos
    Given que el permiso de micrófono del prestador está concedido
    And que la conversación 42 está grabando un audio
    When transcurren 300 segundos de grabación
    Then queda una preview de audio sin enviar

  Scenario: 08-PCA Reproducir y pausar la preview antes de enviar
    Given que la conversación 42 tiene una preview de audio válida
    When el prestador reproduce y pausa la preview
    Then la posición de reproducción permanece y ningún envío se dispara

  Scenario: 09-PCA Descartar la preview de audio
    Given que la conversación 42 tiene una preview de audio válida
    When el prestador descarta la preview
    Then no queda audio pendiente ni reproducción ni archivo local

  Scenario: 10-PCA Reintentar un audio conservando el borrador nuevo
    Given que la conversación 42 tiene un audio cuyo envío falló
    When el prestador reintenta el audio varias veces con el borrador "Otro mensaje"
    Then se inicia un solo reintento y el borrador nuevo permanece

  Scenario: 11-PCA Buscar una posición y cambiar de audio recibido
    Given que la conversación 42 contiene dos audios recibidos
    When el prestador busca una posición y reproduce el segundo audio
    Then solamente el segundo audio está activo
