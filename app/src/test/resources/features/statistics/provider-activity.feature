# language: es
Característica: Consultar mi actividad como prestador
  Como prestador
  Quiero conocer mis resultados y pendientes
  Para entender el trabajo realizado y lo que requiere atención

  @wip
  Escenario: 70.1-ACT Consultar mis resultados recientes
    Dado que conseguí y realicé trabajos durante los últimos 30 días
    Cuando abro Desempeño desde la barra inferior
    Entonces veo Actividad con el período consultado
    Y veo mis contrataciones, finalizaciones informadas y trabajos pagados por completo
    Y veo mis clientes atendidos separados en nuevos y recurrentes
    Y veo el valor pactado y el promedio de los trabajos finalizados en pesos argentinos
    Y se distingue el valor pactado del dinero cobrado

  @wip
  Escenario: 70.2-ACT Consultar un período sin actividad
    Dado que no tuve actividad durante el período consultado
    Cuando consulto mis resultados
    Entonces veo las cantidades y los importes totales en cero
    Y el promedio figura como no disponible

  @wip
  Escenario: 70.3-ACT Distinguir los pendientes actuales
    Dado que tengo solicitudes pendientes, trabajos programados y finalizados con saldo pendiente
    Y esos pendientes se originaron antes del período consultado
    Cuando consulto mi actividad
    Entonces veo esos pendientes separados de los resultados del período
    Y se indica que corresponden a mi situación actual

  @wip
  Escenario: 70.4-ACT Consultar otro período
    Dado que estoy consultando mi actividad
    Cuando elijo un período válido diferente
    Entonces veo los resultados y las fechas del período elegido
    Y mis pendientes actuales conservan su significado

  @wip
  Esquema del escenario: 70.5-ACT Corregir un período inválido
    Dado que estoy eligiendo las fechas de consulta
    Cuando selecciono un período "<periodo>"
    Entonces se explica cómo corregir las fechas
    Y no se reemplazan los últimos resultados válidos
    Ejemplos:
      | periodo                  |
      | con fechas invertidas    |
      | de más de 365 días       |
      | que termina en el futuro |

  @wip
  Esquema del escenario: 70.6-ACT Consultar la evolución
    Dado que tuve actividad e intervalos sin trabajos durante el período
    Cuando elijo ver la evolución por "<agrupacion>"
    Entonces distingo contrataciones, finalizaciones informadas y pagos completos
    Y puedo consultar sus cantidades incluyendo los intervalos en cero
    Ejemplos:
      | agrupacion |
      | día        |
      | semana     |
      | mes        |

  @wip
  Escenario: 70.7-ACT Comparar períodos sin inventar crecimiento
    Dado que algunas de mis métricas tienen resultados en el período anterior y otras no
    Cuando activo la comparación con el período anterior
    Entonces veo ambos períodos de igual duración y sus diferencias
    Y los porcentajes sin una base de comparación figuran como no disponibles
    Y mis pendientes actuales no se comparan con el pasado

  @wip
  Escenario: 70.8-ACT Recuperar una consulta que falló
    Dado que no se pudieron obtener mis resultados y veo una opción para reintentar
    Y la información vuelve a estar disponible
    Cuando reintento la consulta
    Entonces veo los resultados del mismo período solicitado
    Y durante la espera se informa que se están consultando
    Y el error anterior no se presenta como falta de actividad

  @wip
  Esquema del escenario: 70.9-ACT Retomar mi consulta
    Dado que elegí un período y estaba leyendo su evolución
    Cuando "<regreso>"
    Entonces continúo en Actividad con el período y las opciones elegidas
    Y conservo mi posición de lectura
    Ejemplos:
      | regreso                                     |
      | vuelvo a Desempeño después de ver Mensajes   |
      | giro el dispositivo mientras leo mis datos  |

  @wip
  Escenario: 70.10-ACT Proteger mis resultados al vencer la sesión
    Dado que mi sesión dejó de estar vigente
    Cuando intento consultar mi actividad
    Entonces se me solicita ingresar nuevamente
    Y mis estadísticas privadas no quedan visibles
