# language: es
Característica: Consultar mi reputación como prestador
  Como prestador
  Quiero conocer cómo valoran mis clientes mis trabajos
  Para entender mi calificación y cuántos trabajos la respaldan

  Escenario: 72.1-REP Conocer mi reputación real
    Dado que tengo 30 trabajos pagados y 24 recibieron una reseña
    Cuando abro Reputación dentro de Mi desempeño
    Entonces veo mi calificación promedio y las 24 reseñas que la respaldan
    Y veo cuántas calificaciones recibí de cada cantidad de estrellas
    Y veo que 24 de mis 30 trabajos pagados tienen reseña, con una cobertura del 80 por ciento
    Y se aclara que la información corresponde a toda mi trayectoria

  Esquema del escenario: 72.2-REP Entender una reputación todavía sin reseñas
    Dado que tengo <trabajos> trabajos pagados y ninguno recibió una reseña
    Cuando consulto mi reputación
    Entonces veo que todavía no tengo calificaciones
    Y veo cero reseñas y cero calificaciones de cada cantidad de estrellas
    Y la cobertura se muestra como "<cobertura>"
    Ejemplos:
      | trabajos | cobertura    |
      | 0        | no disponible |
      | 3        | cero por ciento |

  Escenario: 72.3-REP Leer lo que recibí de mis clientes
    Dado que recibí calificaciones con y sin comentario escrito
    Cuando consulto mis reseñas
    Entonces veo el trabajo y la calificación correspondientes a cada reseña
    Y veo el comentario solamente cuando fue escrito
    Y no se agregan nombres, fechas ni opiniones que no fueron informados
    Y no se presentan como las reseñas más recientes

  @wip
  Escenario: 72.4-REP Seguir leyendo mis reseñas
    Dado que estoy leyendo mis reseñas y quedan otras por mostrar
    Cuando elijo cargar más reseñas
    Entonces se agregan las siguientes sin repetir trabajos
    Y conservo las reseñas anteriores y mi posición de lectura
    Y los indicadores siguen representando toda mi trayectoria

  @wip
  Esquema del escenario: 72.5-REP Recuperar una consulta que falló
    Dado que falló "<consulta>" y se informó el problema sin mostrar resultados inventados
    Y la información vuelve a estar disponible
    Cuando elijo reintentar
    Entonces puedo continuar "<lectura>"
    Y conservo la información válida que ya estaba leyendo
    Ejemplos:
      | consulta                   | lectura                     |
      | la consulta de reputación  | desde las primeras reseñas   |
      | la carga de más reseñas    | desde las siguientes reseñas |

  @wip
  Escenario: 72.6-REP Actualizar mi reputación
    Dado que ya cargué varias reseñas y recibí una nueva calificación
    Cuando actualizo mi reputación
    Entonces veo los indicadores actualizados y las primeras reseñas de la nueva consulta
    Y no se mezclan con las reseñas cargadas anteriormente
    Y veo cuándo se consultó la información

  @wip
  Escenario: 72.7-REP Retomar la lectura de mi reputación
    Dado que estaba leyendo mis reseñas y fui a otra sección de Desempeño
    Cuando vuelvo a Reputación
    Entonces retomo mi posición de lectura
    Y Actividad y Cobros conservan sus propias opciones
    Y Reputación sigue mostrando toda mi trayectoria sin pedir un período
