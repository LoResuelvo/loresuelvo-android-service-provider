# language: es
Característica: Consultar la conversión de mis propuestas
  Como prestador
  Quiero saber cómo avanzaron las propuestas que emití
  Para entender cuántas se convierten en contrataciones y trabajos pagados

  @wip
  Escenario: 73.1-CON Entender el avance de mis propuestas
    Dado que emití 20 propuestas en los últimos 30 días
    Y de esas propuestas 12 se contrataron, 9 tienen finalización informada y 8 se pagaron por completo
    Cuando abro Conversión de propuestas desde Actividad
    Entonces veo las cuatro etapas con sus cantidades y porcentajes sobre las 20 propuestas
    Y puedo consultar cuánto avanzó cada etapa respecto de la anterior y cuántas propuestas representa
    Y veo 8 propuestas sin contratación observada sin considerarlas rechazadas
    Y veo cuándo se consultó la información y que las propuestas todavía pueden avanzar

  @wip
  Escenario: 73.2-CON Consultar las propuestas emitidas en otro período
    Dado que emití propuestas durante septiembre y algunas se contrataron en octubre
    Cuando elijo consultar las propuestas emitidas en septiembre
    Entonces veo solamente el avance de esas propuestas, incluidas las contrataciones de octubre
    Y veo el período elegido y se aclara que corresponde a la emisión de las propuestas
    Y los resultados anteriores se reemplazan sin mezclarse con esta consulta

  @wip
  Esquema del escenario: 73.3-CON Interpretar la falta de avances
    Dado que emití <emitidas> propuestas en el período y ninguna se contrató
    Cuando consulto su conversión
    Entonces veo <emitidas> propuestas emitidas y cero en las etapas siguientes
    Y el porcentaje de contratación se muestra como "<porcentaje>"
    Y los avances entre etapas sin propuestas de partida figuran como no disponibles
    Ejemplos:
      | emitidas | porcentaje       |
      | 0        | no disponible    |
      | 5        | cero por ciento  |

  @wip
  Escenario: 73.4-CON Distinguir solicitudes de contrataciones
    Dado que recibí 5 solicitudes en el período, acepté 3 y tengo 2 pendientes
    Y todavía no emití propuestas durante ese período
    Cuando consulto la conversión de mis propuestas
    Entonces sigo viendo mis 5 solicitudes, las 3 aceptadas y las 2 pendientes
    Y veo una aceptación del 60 por ciento, correspondiente a 3 de 5 solicitudes
    Y las solicitudes aparecen separadas de las propuestas
    Y aceptar una solicitud no se presenta como una contratación

  @wip
  Escenario: 73.5-CON Recuperar los resultados sin cambiar mi consulta
    Dado que no se pudieron consultar mis resultados y se informó el problema sin mostrar ceros inventados
    Y la información vuelve a estar disponible
    Cuando elijo reintentar
    Entonces veo los resultados del período que había elegido

  @wip
  Esquema del escenario: 73.6-CON Corregir un período que no se puede consultar
    Dado que estoy viendo resultados y elegí "<periodo>"
    Cuando intento consultar ese período
    Entonces se explica qué debo corregir
    Y puedo ajustar las fechas sin perder mi última consulta válida
    Ejemplos:
      | periodo                        |
      | un inicio posterior al final   |
      | una fecha final futura         |
      | un intervalo mayor a 365 días   |

  @wip
  Escenario: 73.7-CON Volver a Actividad sin perder el contexto
    Dado que estaba leyendo Actividad con sus opciones elegidas
    Y desde allí abrí Conversión de propuestas y elegí otro período para ese detalle
    Cuando vuelvo a Actividad
    Entonces retomo las opciones y la posición que tenía en Actividad
    Y el detalle de conversión conserva su propio período y posición para la próxima consulta
