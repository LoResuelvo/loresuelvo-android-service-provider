# language: es
Característica: Visualizar turnos como prestador
  Antecedentes:
    Dado que inicié sesión como prestador

  Esquema del escenario: 01-PVT Acceder al mismo listado desde ambos accesos
    Dado que tengo órdenes de trabajo registradas
    Cuando abro Turnos desde "<acceso>"
    Entonces veo el listado completo de mis órdenes de trabajo
    Y las propuestas conservan su acceso separado
    Ejemplos:
      | acceso                                    |
      | Ver todos en Trabajos agendados de Inicio |
      | Turnos en Trabajos                         |

  Escenario: 02-PVT Conservar órdenes pasadas y ordenar determinísticamente
    Dado que la API devuelve estas órdenes y hoy es 26 de septiembre de 2026:
      | id | estado           | fecha                    |
      | 40 | paid             | 2026-09-20T12:00:00Z     |
      | 30 | scheduled        | 2026-09-25T12:00:00Z     |
      | 12 | scheduled        | 2026-10-05T12:00:00Z     |
      | 11 | awaiting_payment | 2026-10-05T12:00:00Z     |
    Cuando entro a Turnos
    Entonces veo las órdenes 40, 30, 11 y 12 en ese orden
    Y no veo propuestas pendientes o rechazadas como órdenes de trabajo

  Esquema del escenario: 03-PVT Mostrar la contraparte y los datos reales
    Dado que una orden de Ana Pérez tiene un monto de 1500050 centavos
    Y su fecha es "2026-10-05T00:30:00Z" y su motivo es "Reparar la canilla"
    Y su foto está "<foto>" y no tiene rubro
    Y uso español de Argentina y la zona "America/Argentina/Buenos_Aires"
    Cuando entro a Turnos
    Entonces veo Ana Pérez, ARS 15.000,50, el 4 de octubre a las 21:30 y el motivo
    Y veo "<avatar>" sin rubro ni espacio reservado para él
    Ejemplos:
      | foto         | avatar           |
      | disponible   | la foto de Ana   |
      | ausente      | las iniciales AP |
      | inaccesible  | las iniciales AP |

  Esquema del escenario: 04-PVT Distinguir estados publicados sin inferencias
    Dado que una orden pasada tiene estado "<estado>"
    Cuando entro a Turnos
    Entonces veo la etiqueta "<etiqueta>" con el tratamiento "<tratamiento>"
    Y su estado no cambia por tener fecha pasada
    Ejemplos:
      | estado           | etiqueta         | tratamiento      |
      | scheduled        | Confirmado       | principal        |
      | awaiting_payment | Pendiente de pago| error            |
      | paid             | Pagado           | superficie neutra|

  Escenario: 05-PVT Consultar el motivo completo en un resumen móvil
    Dado que el motivo de una orden supera la vista previa de la tarjeta
    Cuando elijo Ver detalles en esa tarjeta
    Entonces veo el motivo completo, consumidor, monto, fecha local y estado
    Y puedo elegir Ver conversación
    Y no necesito cargar un reporte de finalización para consultar el motivo
    Y no puedo pagar, aceptar, rechazar, calificar, cancelar o reprogramar

  Escenario: 06-PVT Abrir únicamente la conversación vinculada
    Dado que la orden 40 referencia la propuesta 12 del consumidor 7
    Y la propuesta 12 referencia la conversación 93
    Cuando elijo Ver conversación para la orden 40
    Entonces se abre la conversación 93
    Y no se crea una conversación ni se usan los IDs 40, 12 o 7 como chat

  Escenario: 07-PVT Recuperar un vínculo ausente sin abrir otro chat
    Dado que no se encontró la propuesta vinculada a una orden
    Cuando elijo Ver conversación para esa orden
    Entonces veo un aviso con una acción para reintentar
    Y permanezco en Turnos sin abrir ni crear otro chat

  Esquema del escenario: 08-PVT Recuperar errores al resolver el contacto
    Dado que la consulta de propuestas falló por "<causa>"
    Y una nueva consulta devuelve la propuesta vinculada a la conversación 93
    Cuando reintento Ver conversación
    Entonces se abre la conversación 93
    Y desaparece el aviso de error
    Ejemplos:
      | causa          |
      | falta de red   |
      | respuesta 500  |

  Escenario: 09-PVT Conservar en Inicio los turnos pendientes de evidencia y de pago
    Dado que el reloj indica "2026-09-26T12:00:00Z"
    Y tengo órdenes scheduled anteriores, iguales y posteriores a ese instante
    Y tengo órdenes futuras awaiting_payment y paid y propuestas pendientes
    Cuando abro Inicio
    Entonces Mis trabajos muestra las órdenes scheduled incluso pasadas y las awaiting_payment
    Y las ordena por fecha ascendente y luego por ID ascendente
    Y Ver todos permite consultar también las órdenes excluidas del resumen

  Escenario: 10-PVT Mostrar carga sin inventar un vacío
    Dado que la consulta de órdenes todavía no respondió
    Cuando entro a Turnos
    Entonces veo un indicador y texto de carga accesibles
    Y no veo un mensaje de lista vacía

  Escenario: 11-PVT Mostrar un vacío real
    Dado que la API devuelve una lista de órdenes vacía
    Cuando entro a Turnos
    Entonces veo el estado vacío con texto adaptado al prestador
    Y no veo un indicador de carga ni un error

  Esquema del escenario: 12-PVT Recuperar la carga del listado
    Dado que la consulta de órdenes falló por "<causa>" y veo error con Reintentar
    Y la próxima consulta devuelve mis órdenes
    Cuando elijo Reintentar
    Entonces veo las órdenes actualizadas y desaparece el error
    Ejemplos:
      | causa          |
      | falta de red   |
      | respuesta 500  |

  Escenario: 13-PVT Refrescar al reingresar conservando la posición
    Dado que abrí el chat desde una orden después de desplazar el listado
    Y la API cambió esa orden de scheduled a awaiting_payment
    Cuando vuelvo desde el chat
    Entonces veo el estado actualizado de esa orden
    Y conservo la posición del listado

  Esquema del escenario: 14-PVT Respetar Atrás y el origen del acceso
    Dado que entré a Turnos desde "<origen>" y abrí un resumen tras desplazarme
    Cuando cierro el resumen con Atrás
    Entonces veo la misma posición del listado
    Y el destino de regreso del listado sigue siendo "<origen>"
    Ejemplos:
      | origen   |
      | Inicio   |
      | Trabajos |

  Esquema del escenario: 15-PVT Recuperar una sesión inválida
    Dado que "<consulta>" responde 401
    Cuando realizo la acción que requiere esa consulta
    Entonces se utiliza el flujo de autenticación existente
    Y no quedan visibles órdenes ni vínculos de la sesión anterior
    Ejemplos:
      | consulta                          |
      | cargar órdenes                    |
      | resolver la propuesta para el chat|

  Esquema del escenario: 16-PVT Mantener paridad visual y accesibilidad
    Dado que uso "<idioma>" y tamaño de fuente "<fuente>"
    Y existen referencias equivalentes de consumidor para listado, tarjeta y resumen
    Cuando visualizo Turnos en sus estados con datos, carga, vacío y error
    Entonces su navegación y componentes coinciden con los patrones Android consumidor
    Y colores, tipografía, espaciado, avatares, badges y acciones cumplen la matriz visual
    Y todos los textos y controles son legibles y accesibles sin solapamientos
    Y las diferencias están justificadas sólo por rol, contrato API o accesibilidad
    Ejemplos:
      | idioma | fuente   |
      | es-AR  | normal   |
      | es-AR  | ampliada |
      | en     | normal   |
      | en     | ampliada |
