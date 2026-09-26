# language: es
Característica: Visualizar propuestas de servicio como prestador
  Antecedentes:
    Dado que inicié sesión como prestador

  Escenario: 01-PVP Abrir las propuestas pendientes desde Trabajos
    Dado que mis propuestas son:
      | id | estado   | fecha de creación    | fecha de visita      |
      | 10 | pending  | 2026-09-20T12:00:00Z | 2026-09-25T12:00:00Z |
      | 11 | pending  | 2026-09-21T12:00:00Z | 2026-10-05T12:00:00Z |
      | 12 | pending  | 2026-09-21T12:00:00Z | 2026-10-06T12:00:00Z |
      | 13 | accepted | 2026-09-22T12:00:00Z | 2026-10-07T12:00:00Z |
      | 14 | rejected | 2026-09-23T12:00:00Z | 2026-10-08T12:00:00Z |
    Y hoy es 26 de septiembre de 2026
    Cuando elijo "Ver todas" en la sección "Trabajos" de Inicio
    Entonces la pestaña "Pendientes" está seleccionada
    Y veo las pestañas "Pendientes", "Aceptadas" y "Rechazadas"
    Y veo las propuestas 12, 11 y 10 en ese orden
    Y no veo las propuestas 13 ni 14

  Esquema del escenario: 02-PVP Filtrar propuestas por estado
    Dado que Trabajos contiene propuestas pendientes, aceptadas y rechazadas
    Y las propuestas de cada estado tienen fechas de creación distintas y fechas de creación iguales
    Cuando selecciono "<pestaña>"
    Entonces veo solamente las propuestas con estado "<estado>"
    Y aparecen primero las de creación más reciente y, en caso de empate, las de mayor ID
    Y cada tarjeta muestra el estado "<etiqueta>"
    Ejemplos:
      | pestaña    | estado   | etiqueta  |
      | Pendientes | pending  | Pendiente |
      | Aceptadas  | accepted | Aceptada  |
      | Rechazadas | rejected | Rechazada |

  Esquema del escenario: 03-PVP Ver el consumidor y la propuesta en una tarjeta
    Dado que una propuesta pendiente para la consumidora "Ana Pérez" tiene un monto de 1500050 centavos
    Y su visita es el "2026-10-05T00:30:00Z" y su motivo es "Reparar la canilla de la cocina"
    Y la foto de perfil de Ana está "<foto>"
    Y mi configuración regional es español de Argentina y mi zona horaria es "America/Argentina/Buenos_Aires"
    Cuando abro Trabajos
    Entonces la tarjeta muestra "Ana Pérez", ARS 15.000,50, el 4 de octubre de 2026 a las 21:30, el motivo y "Pendiente"
    Y la tarjeta muestra "<avatar>"
    Y el nombre de la consumidora no tiene un rubro ni un espacio vacío reservado para él
    Ejemplos:
      | foto                     | avatar           |
      | disponible               | la foto de Ana   |
      | ausente                  | las iniciales AP |
      | inaccesible por un error | las iniciales AP |

  Esquema del escenario: 04-PVP Ver el detalle completo de una propuesta
    Dado que la propuesta 12 tiene un motivo más largo que la vista previa de su tarjeta
    Y su duración estimada es de <minutos> minutos
    Y puedo ver la propuesta 12 en "<vista>"
    Cuando abro el detalle de la propuesta 12
    Entonces veo el consumidor, el motivo completo, el monto, la fecha y hora local de la visita y el estado
    Y veo la duración "<duración>"
    Y puedo elegir "Ver conversación"
    Y no puedo aceptar, rechazar, pagar ni calificar la propuesta
    Ejemplos:
      | vista            | minutos | duración          |
      | Trabajos         | 45      | 45 minutos        |
      | Trabajos         | 60      | 1 hora            |
      | resumen del chat | 90      | 1 hora 30 minutos |

  Escenario: 05-PVP Volver al mismo lugar del historial
    Dado que abrí un detalle desde la pestaña "Aceptadas" después de desplazarme hasta la propuesta 42
    Cuando cierro el detalle con la acción Atrás
    Entonces la pestaña "Aceptadas" sigue seleccionada
    Y la propuesta 42 permanece en la misma posición visible

  Escenario: 06-PVP Abrir la conversación correspondiente
    Dado que el detalle abierto corresponde a la propuesta 12 para el consumidor 7 y la conversación 93
    Cuando elijo "Ver conversación"
    Entonces se abre la conversación 93
    Y no se abre la conversación 12 ni la conversación 7

  Escenario: 07-PVP Mostrar la propuesta más reciente de este chat
    Dado que las propuestas son:
      | id | id conversación | fecha de creación    | estado   |
      | 20 | 93              | 2026-09-20T12:00:00Z | accepted |
      | 21 | 93              | 2026-09-21T12:00:00Z | rejected |
      | 22 | 93              | 2026-09-21T12:00:00Z | pending  |
      | 99 | 94              | 2026-09-22T12:00:00Z | pending  |
    Cuando abro la conversación 93
    Entonces su resumen muestra el monto, la fecha y hora local de visita, el motivo y el estado pendiente de la propuesta 22
    Y puedo abrir el detalle de la propuesta 22
    Y las propuestas 20, 21 y 99 no aparecen en el resumen

  Escenario: 08-PVP Usar un chat sin propuestas
    Dado que la conversación 93 tiene mensajes y ninguna propuesta
    Cuando abro la conversación 93
    Entonces veo sus mensajes y puedo escribir un mensaje
    Y no se muestra un resumen de propuesta

  Esquema del escenario: 09-PVP Actualizar las propuestas al regresar
    Dado que anteriormente vi la propuesta 12 como pendiente en "<vista>"
    Y salí de esa vista
    Y el servidor ahora informa que la propuesta 12 está aceptada
    Cuando regreso a "<vista>"
    Entonces veo "<resultado>"
    Ejemplos:
      | vista           | resultado                                           |
      | Trabajos        | la pestaña Pendientes sin la propuesta 12            |
      | conversación 93 | el resumen de la propuesta 12 con el estado Aceptada |

  @wip
  Escenario: 10-PVP Actualizar el chat después de enviar una propuesta
    Dado que estoy confirmando una propuesta nueva en la conversación 93 mediante el flujo de creación existente
    Y la creación finalizará correctamente con la propuesta 23
    Y la próxima consulta devolverá la propuesta 23 como la más reciente de la conversación 93
    Cuando confirmo el envío de la propuesta
    Entonces el resumen del chat muestra la propuesta 23 del listado actualizado
    Y se conserva la confirmación de envío exitoso existente

  @wip
  Esquema del escenario: 11-PVP Mostrar la carga sin indicar que no hay propuestas
    Dado que la consulta de propuestas todavía no terminó
    Cuando abro "<vista>"
    Entonces veo un indicador de carga de propuestas
    Y no veo un mensaje de que no hay propuestas
    Ejemplos:
      | vista           |
      | Trabajos        |
      | conversación 93 |

  @wip
  Esquema del escenario: 12-PVP Mostrar una pestaña sin propuestas
    Dado que el servidor devolvió un listado sin propuestas con estado "<estado>"
    Cuando selecciono "<pestaña>"
    Entonces veo un mensaje que indica que esta pestaña no tiene propuestas
    Y las tres pestañas de estado siguen disponibles
    Ejemplos:
      | pestaña    | estado   |
      | Pendientes | pending  |
      | Aceptadas  | accepted |
      | Rechazadas | rejected |

  @wip
  Esquema del escenario: 13-PVP Mostrar un error al cargar propuestas
    Dado que la carga de propuestas fallará por "<error>"
    Y los mensajes de la conversación 93 están disponibles
    Cuando abro "<vista>"
    Entonces veo un error de propuestas con una acción para reintentar
    Y no veo un mensaje de que no hay propuestas
    Ejemplos:
      | vista           | error        |
      | Trabajos        | sin conexión |
      | Trabajos        | HTTP 500     |
      | conversación 93 | sin conexión |
      | conversación 93 | HTTP 500     |

  @wip
  Esquema del escenario: 14-PVP Reintentar una consulta de propuestas fallida
    Dado que "<vista>" muestra un error de carga de propuestas
    Y la próxima consulta devolverá la propuesta 12 para la conversación 93
    Cuando reintento cargar las propuestas
    Entonces el error se reemplaza por la propuesta 12 en "<vista>"
    Ejemplos:
      | vista           |
      | Trabajos        |
      | conversación 93 |

  @wip
  Esquema del escenario: 15-PVP Ocultar las propuestas privadas al vencer la sesión
    Dado que anteriormente cargué mis propuestas en "<vista>"
    Y mi sesión venció
    Cuando regreso a "<vista>"
    Entonces ingreso al flujo de autenticación existente
    Y mis propuestas anteriores y su detalle dejan de estar visibles
    Y la acción Atrás no permite volver a esas vistas privadas
    Ejemplos:
      | vista           |
      | Trabajos        |
      | conversación 93 |

  @wip
  Escenario: 17-PVP Seguir enviando mensajes cuando falla la carga de propuestas
    Dado que la conversación 93 muestra sus mensajes y un error de carga de propuestas
    Y el envío de mensajes está disponible
    Cuando envío el mensaje "Llegaré a las 9"
    Entonces el mensaje aparece como enviado en la conversación 93
    Y la acción para reintentar la carga de propuestas sigue disponible
