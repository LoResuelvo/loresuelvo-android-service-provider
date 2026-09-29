# language: es
Característica: Consultar el detalle y la evidencia de una orden como prestador

  Antecedentes:
    Dado que estoy autenticado como prestador

  Esquema del escenario: 01-PDO Abrir el detalle actualizado desde un trabajo agendado
    Dado que la orden 42 de la propuesta 10 aparece en "<origen>"
    Y pertenece al consumidor Ana Pérez sin fotografía de perfil
    Y su detalle vigente informa 123456 centavos, fecha con zona horaria, descripción completa y estado scheduled
    Cuando elijo Ver detalle de la orden 42
    Entonces veo la misma pantalla de detalle con Ana Pérez y su avatar de respaldo
    Y veo el monto de ARS 1234,56, la fecha y hora local, la descripción original completa y el estado Confirmado
    Y no veo rubro del consumidor, evidencia de finalización ni fecha de pago
    Y no veo acciones para pagar ni escribir una reseña
    Ejemplos:
      | origen                      |
      | Turnos                      |
      | trabajos agendados de Inicio |

  Escenario: 02-PDO Abrir la orden directamente desde el chat
    Dado que estoy en la conversación 70 con Ana Pérez
    Y esa conversación tiene vinculada la orden 42 de la propuesta 10
    Y veo la acción Ver detalle de la orden en la barra superior del chat
    Cuando elijo Ver detalle de la orden
    Entonces veo el detalle vigente de la orden 42 con la identidad de su consumidor
    Y no necesito abrir el detalle de una propuesta
    Y no se usa el número de la propuesta ni de la conversación como número de orden

  Esquema del escenario: 04-PDO Consultar la evidencia disponible de la entrega
    Dado que mi orden está en estado "<estado>"
    Y su detalle contiene "<evidencia>"
    Cuando abro el detalle de la orden
    Entonces veo "<resultado>"
    Y conservo los datos válidos del servicio y su descripción original separada de la entrega
    Y no se inventan fechas, fotografías ni descripciones ausentes
    Ejemplos:
      | estado           | evidencia                                       | resultado                                                        |
      | awaiting_payment | reporte con descripción, fecha y una foto        | Evidencia de finalización con descripción, fecha local y esa foto |
      | paid             | reporte con descripción, fecha y tres fotos      | Evidencia de finalización con las tres fotos en el orden recibido |
      | awaiting_payment | ningún reporte disponible                       | un aviso de evidencia no disponible                              |
      | paid             | reporte con descripción y fecha, pero sin fotos | la descripción y fecha del reporte con un aviso de fotos ausentes |
      | scheduled        | reporte residual de una respuesta antigua        | sólo los datos del servicio sin evidencia ni fecha de pago        |

  Esquema del escenario: 05-PDO Ampliar una fotografía y conservar el lugar de regreso
    Dado que abrí una orden con tres fotografías desde "<origen>" después de desplazar su contenido
    Y estoy en "<vista>"
    Cuando "<acción>"
    Entonces veo "<resultado>"
    Y se conserva el contexto de regreso con el origen, la pestaña y la posición del listado o chat
    Ejemplos:
      | origen   | vista                            | acción                       | resultado                                                  |
      | Turnos   | el detalle                       | selecciono la segunda foto   | esa fotografía a tamaño completo con carga y cierre visible |
      | Inicio   | el visor de la segunda fotografía| pulso Atrás                  | el detalle de la misma orden, con el visor cerrado          |
      | Turnos   | el visor de la segunda fotografía| elijo Cerrar                 | el detalle de la misma orden, con el visor cerrado          |
      | chat     | el visor de la segunda fotografía| se recrea la pantalla        | el visor de esa misma fotografía y la misma orden           |
      | Turnos   | el detalle sin visor             | pulso Atrás                  | el listado de Turnos en su posición anterior                |
      | chat     | el detalle sin visor             | elijo Volver                 | la conversación de origen en su posición anterior          |

  Esquema del escenario: 06-PDO Consultar el pago y la reseña recibida
    Dado que mi orden paid tiene "<pago>" y "<reseña>"
    Cuando abro su detalle
    Entonces veo el estado Pagado y "<resultado>"
    Y no puedo pagar, crear ni editar la reseña del consumidor
    Ejemplos:
      | pago                    | reseña                     | resultado                                                        |
      | fecha de pago informada | calificación 5 y comentario | fecha local del pago, calificación 5 y comentario completo         |
      | fecha de pago informada | ninguna reseña              | fecha local del pago y aviso de que aún no hay reseña              |
      | fecha de pago ausente   | ninguna reseña              | aviso de que aún no hay reseña, sin inventar fecha ni calificación |

  Esquema del escenario: 07-PDO Consultar el resultado vigente al regresar al detalle
    Dado que abrí una orden scheduled cuyo turno ya comenzó y podía informar finalización
    Y "<situación>"
    Cuando regreso al detalle de esa orden
    Entonces veo "<resultado>" confirmado por una nueva consulta
    Y Turnos e Inicio conservan su navegación y reflejan el estado confirmado al regresar
    Ejemplos:
      | situación                                                              | resultado                                                   |
      | informé la finalización con el formulario existente de US-26            | Pendiente de pago y la evidencia registrada sin otro reporte |
      | cancelé el formulario existente de US-26 sin enviar                      | Confirmado y la posibilidad de informar finalización         |
      | dejé la app en segundo plano y la orden pasó a paid con reporte y reseña | Pagado, la evidencia y la reseña recibida                     |

  @wip
  Esquema del escenario: 08-PDO Recuperar una consulta fallida sin mostrar datos de otra orden
    Dado que estaba viendo otra orden y la consulta de la orden elegida obtiene "<respuesta>"
    Cuando abro el detalle de la orden elegida
    Entonces mientras espero veo carga sin los datos de la orden anterior
    Y después veo "<recuperación>"
    Y no se muestran datos privados de una sesión anterior ni se da una orden por pagada por error
    Ejemplos:
      | respuesta             | recuperación                                     |
      | error de red          | aviso de conexión con la opción Reintentar        |
      | error del servidor    | aviso de error con la opción Reintentar           |
      | acceso denegado 403    | aviso de falta de permisos y una salida accesible |
      | orden inexistente 404 | aviso de orden no disponible y una salida accesible|
      | sesión inválida 401   | el flujo de autenticación sin detalle ni visor privados |

  @wip
  Escenario: 09-PDO Recuperar una fotografía cuya dirección temporal dejó de funcionar
    Dado que veo una orden con reporte y tres fotografías
    Y una fotografía falla al cargar mientras las otras siguen disponibles
    Y una nueva consulta de la misma orden devuelve direcciones temporales vigentes
    Cuando elijo Reintentar la fotografía fallida
    Entonces se consulta nuevamente el detalle y puedo ver la fotografía con su dirección vigente
    Y se conservan el reporte, el orden de las fotos y la selección del visor si estaba abierto
    Y las demás fotografías y los datos del servicio siguen utilizables
