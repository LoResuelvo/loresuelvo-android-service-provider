# language: es
Característica: Registrar prestador con zonas de cobertura
  Como prestador que completa su perfil profesional
  Quiero seleccionar las zonas donde puedo brindar servicios
  Para recibir solicitudes dentro de mi área de cobertura

  Escenario: 01-PCZ Mostrar el progreso de carga sin seleccionar una zona implícita
    Dado un prestador autenticado sin perfil completo y una carga de zonas pendiente
    Cuando el prestador abre el formulario de perfil
    Entonces la sección de cobertura muestra el progreso de carga
    Y ninguna zona queda seleccionada implícitamente
    Y el registro no puede enviarse hasta que el catálogo esté disponible
    Y los demás campos del perfil permanecen disponibles

  Escenario: 02-PCZ Mostrar las zonas disponibles en el orden del servidor
    Dado que la API devolverá zonas disponibles con identificadores y nombres legibles
    Cuando finaliza la carga del catálogo de zonas
    Entonces cada nombre disponible aparece una sola vez en el orden del servidor
    Y los nombres se muestran en lugar de los identificadores o referencias del mapa
    Y ninguna zona queda seleccionada inicialmente en ningún entorno

  Esquema del escenario: 03-PCZ Mostrar un error recuperable al fallar el catálogo
    Dado que la carga de zonas devolverá una "<Falla>"
    Cuando finaliza la carga del catálogo de zonas
    Entonces aparece un mensaje amigable y una acción para reintentar
    Y no se solicita el registro del prestador
    Y los demás datos del formulario permanecen sin cambios

    Ejemplos:
      | Falla              |
      | falla de red       |
      | falla del servidor |

  Escenario: 04-PCZ Reintentar la carga sin perder los datos del perfil
    Dado que hay un error de catálogo visible y el formulario contiene datos
    Y el siguiente intento devolverá zonas disponibles
    Cuando el prestador reintenta la carga de zonas
    Entonces se muestra el progreso y luego las zonas disponibles
    Y se conservan nombre, apellido, rubro y foto confirmada

  Escenario: 05-PCZ Informar que no hay zonas disponibles
    Dado que la API devolverá un catálogo de zonas vacío
    Cuando finaliza la carga del catálogo de zonas
    Entonces aparece un mensaje amigable y una acción para volver a cargar
    Y el registro permanece bloqueado
    Y no se agrega ningún identificador predeterminado

  Escenario: 06-PCZ Seleccionar una o varias zonas disponibles
    Dado un catálogo disponible con ninguna o una zona seleccionada
    Cuando el prestador marca otra zona disponible
    Entonces la zona elegida queda seleccionada una sola vez
    Y las selecciones anteriores permanecen
    Y se admiten zonas no contiguas

  Escenario: 07-PCZ Quitar una zona antes del registro
    Dado que el prestador seleccionó dos zonas
    Cuando el prestador desmarca una zona
    Entonces solamente esa zona se elimina de la selección
    Y la otra zona permanece seleccionada

  @wip
  Escenario: 08-PCZ Evitar una zona duplicada ante eventos repetidos
    Dado que una zona ya está seleccionada
    Cuando se recibe nuevamente el mismo evento de selección marcada
    Entonces el identificador aparece una sola vez en la selección
    Y no puede enviarse duplicado al registro

  @wip
  Escenario: 09-PCZ Exigir al menos una zona de cobertura
    Dado que el catálogo está disponible y los demás datos requeridos son válidos
    Y no hay ninguna zona seleccionada
    Cuando el prestador envía el formulario
    Entonces aparece una validación localizada junto a la sección de cobertura
    Y no se solicita el registro ni se navega a otra pantalla

  @wip
  Esquema del escenario: 10-PCZ Registrar exactamente las zonas seleccionadas
    Dado un formulario válido con "<Selección>" proveniente del catálogo
    Y la API de registro responderá exitosamente
    Cuando el prestador envía el formulario
    Entonces se envían una vez exactamente los identificadores seleccionados
    Y el prestador avanza a la vinculación de Mercado Pago
    Y el formulario no es accesible mediante navegación hacia atrás

    Ejemplos:
      | Selección                 |
      | una zona                  |
      | varias zonas no contiguas |

  @wip
  Esquema del escenario: 11-PCZ Corregir una selección rechazada por la API
    Dado un formulario válido cuya selección será rechazada por "<Motivo>"
    Cuando el prestador envía el formulario
    Entonces aparece un mensaje localizado para corregir la cobertura
    Y el prestador permanece en el formulario con sus datos y foto confirmada
    Y puede corregir o volver a cargar la selección sin un reenvío automático

    Ejemplos:
      | Motivo                   |
      | falta de zonas           |
      | zona inexistente         |
      | zona no disponible       |
      | zona seleccionada dos veces |

  @wip
  Esquema del escenario: 12-PCZ Conciliar la selección con un catálogo actualizado
    Dado que un rechazo de cobertura está visible y existe una selección previa
    Y la nueva carga del catálogo "<Resultado>"
    Cuando el prestador vuelve a cargar las zonas
    Entonces se conservan las selecciones que siguen disponibles
    Y se informa cualquier selección eliminada cuando la carga es exitosa
    Y se conservan los demás datos sin reenviar el formulario
    Y el registro permanece bloqueado si la carga falla

    Ejemplos:
      | Resultado                                      |
      | elimina una zona no disponible y conserva otra |
      | falla                                           |

  @wip
  Escenario: 13-PCZ Volver al inicio cuando vence la sesión al cargar zonas
    Dado un formulario autenticado cuya carga de zonas devolverá un error 401
    Cuando finaliza la carga del catálogo de zonas
    Entonces se elimina la sesión y el prestador vuelve a la pantalla de bienvenida
    Y no se solicita el registro ni se repite la carga automáticamente

  @wip
  Escenario: 14-PCZ Evitar cambios y envíos duplicados durante el registro
    Dado que un registro válido está en curso
    Cuando se reciben más acciones de envío o selección de cobertura
    Entonces existe solamente la solicitud de registro original
    Y su selección enviada permanece sin cambios
    Y los controles muestran el estado ocupado

  @wip
  Escenario: 15-PCZ Conservar la selección al recrear la actividad
    Dado un formulario disponible con zonas seleccionadas y datos ingresados
    Cuando se recrea la actividad por un cambio de configuración
    Entonces se conservan la selección y los datos del formulario
    Y no se solicita otro registro ni se selecciona otra zona

  @wip
  Escenario: 16-PCZ Sincronizar la lista con las regiones del mapa
    Dado un mapa nativo y un catálogo disponibles
    Cuando el prestador marca o desmarca una zona desde la lista
    Entonces la región y la fila correspondiente muestran el mismo estado
    Y las demás selecciones permanecen
    Y el resumen de nombres y cantidad coincide con la selección

  @wip
  Escenario: 17-PCZ Seleccionar una zona tocando una región disponible
    Dado un mapa nativo y un catálogo disponibles
    Cuando el prestador toca una región disponible
    Entonces la zona se alterna una sola vez en la selección y en la lista
    Y una región que no pertenece al catálogo no puede agregarse

  @wip
  Escenario: 18-PCZ Continuar desde la lista cuando el mapa no está disponible
    Dado un catálogo disponible y zonas seleccionadas
    Cuando el mapa informa que no puede cargar o mostrar sus límites
    Entonces aparece un aviso localizado que no bloquea el formulario
    Y la lista conserva la selección y permite completar el registro
    Y se envían exactamente los identificadores seleccionados
