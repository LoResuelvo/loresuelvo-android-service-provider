# language: es
@US-20
Característica: Recibir avisos de mensajes y novedades del servicio
  Como prestador
  Quiero enterarme de mis mensajes y servicios desde el teléfono
  Para atenderlos aunque no esté usando LoResuelvo

  @wip @batch1
  Esquema del escenario: 20.1-PUSH Recibir un aviso de un mensaje del consumidor
    Dado que tengo una sesión activa y permití los avisos en este teléfono
    Y estoy "<situacion>"
    Cuando llega el aviso de un mensaje con "<contenido>" de un consumidor
    Entonces veo un único aviso de nuevo mensaje que permite abrir esa conversación
    Y el aviso no expone el contenido del mensaje ni datos personales
    Ejemplos:
      | situacion                         | contenido   |
      | usando otra aplicación            | texto       |
      | con la pantalla bloqueada         | fotografías |
      | sin LoResuelvo en ejecución       | audio       |
      | leyendo otra conversación         | texto       |

  @wip @batch2
  Esquema del escenario: 20.2-PUSH Recibir las novedades importantes de mis servicios
    Dado que tengo una sesión activa y permití los avisos en este teléfono
    Y no estoy usando LoResuelvo
    Cuando llega el aviso de "<novedad>" de uno de mis servicios
    Entonces veo un aviso de "<aviso>" que permite abrir la orden correspondiente
    Y el aviso no expone nombres, direcciones ni importes
    Ejemplos:
      | novedad                                  | aviso                 |
      | una contratación con seña aprobada       | propuesta aceptada    |
      | un turno dentro de las próximas 24 horas | turno próximo         |
      | la aprobación del saldo final            | pago final confirmado |

  @wip @batch1
  Escenario: 20.3-PUSH Seguir usando la aplicación sin permitir avisos
    Dado que rechacé el permiso para recibir avisos
    Cuando vuelvo a abrir LoResuelvo
    Entonces puedo seguir consultando mis mensajes y servicios
    Y no se vuelve a pedir el permiso automáticamente
    Y desde Perfil puedo abrir los ajustes de notificaciones del teléfono

  @wip @batch2
  Escenario: 20.4-PUSH Recuperar la recepción después de una interrupción
    Dado que no se pudo habilitar la recepción de avisos por falta de conexión
    Y conservé mi sesión y el permiso para recibirlos
    Cuando vuelvo a usar LoResuelvo con conexión disponible
    Entonces el teléfono vuelve a quedar habilitado para recibir los próximos avisos de mi cuenta
    Y puedo seguir usando la aplicación durante la recuperación

  @wip @batch1
  Escenario: 20.5-PUSH Leer el chat abierto sin un aviso adicional
    Dado que estoy leyendo mi conversación con Ana
    Cuando llega el aviso de un nuevo mensaje de Ana
    Entonces la conversación se actualiza sin una notificación adicional del teléfono
    Y conservo mi posición de lectura y la respuesta que estaba escribiendo

  @wip @batch1
  Esquema del escenario: 20.6-PUSH Evitar avisos repetidos o fuera de tiempo
    Dado que tengo una sesión activa y permití los avisos
    Cuando llega "<aviso>"
    Entonces no aparece una nueva notificación ni vuelve a sonar una anterior
    Ejemplos:
      | aviso                                     |
      | un aviso que ya recibí                    |
      | un aviso cuyo plazo para mostrarse venció |

  @wip @batch2
  Esquema del escenario: 20.7-PUSH Abrir el mensaje o servicio desde su aviso
    Dado que tengo un aviso vigente de "<aviso>" de mi cuenta actual
    Y LoResuelvo está "<estado>"
    Cuando toco ese aviso
    Entonces veo "<destino>" con la información actual de mi cuenta
    Y puedo volver a la aplicación sin abrir pantallas repetidas
    Ejemplos:
      | aviso                 | estado  | destino                      |
      | nuevo mensaje         | cerrada | la conversación              |
      | nuevo mensaje         | abierta | la conversación              |
      | propuesta aceptada    | cerrada | la orden generada            |
      | turno próximo         | abierta | la orden del turno           |
      | pago final confirmado | cerrada | la orden con el saldo pagado |

  @wip @batch2
  Esquema del escenario: 20.8-PUSH Resolver un aviso que no puedo abrir
    Dado que recibí un aviso y "<situacion>"
    Cuando toco ese aviso
    Entonces "<resultado>"
    Y no veo información privada de otra cuenta ni datos inventados
    Ejemplos:
      | situacion                         | resultado                                          |
      | perdí la conexión                 | se informa el problema y puedo reintentar           |
      | el recurso ya no está disponible  | se informa que no está disponible y puedo volver    |
      | ya no tengo acceso al recurso     | se informa que no tengo acceso y puedo volver       |
      | mi sesión ya no está activa       | se solicita iniciar sesión sin abrir el aviso viejo |

  @wip @batch1
  Esquema del escenario: 20.9-PUSH Dejar de recibir avisos al cerrar sesión
    Dado que tengo avisos visibles de mi cuenta
    Y el teléfono está "<conexion>"
    Cuando confirmo el cierre de sesión
    Entonces desaparecen los avisos de mi cuenta en este teléfono
    Y los avisos que lleguen después para esa sesión no se muestran ni abren datos privados
    Y el cierre local se completa aunque no se pueda contactar a LoResuelvo
    Ejemplos:
      | conexion      |
      | con conexión  |
      | sin conexión  |

  @wip @batch2
  Escenario: 20.10-PUSH Recibir solo los avisos de la cuenta actual
    Dado que cerré mi sesión sin conexión y después ingresé con otra cuenta de prestador
    Y la nueva cuenta quedó habilitada para recibir avisos en este teléfono
    Cuando llega un aviso pendiente de la cuenta anterior
    Entonces no se muestra ese aviso ni permite entrar a la cuenta anterior
    Y sigo pudiendo recibir los avisos de mi cuenta actual
