# language: es
Característica: Consultar mis cobros como prestador
  Como prestador
  Quiero conocer mis cobros verificados y los saldos pendientes
  Para entender de dónde provienen los importes de mis trabajos

  @wip
  Escenario: 71.1-COL Consultar mis cobros verificados
    Dado que tengo señas y saldos verificados durante el período elegido en Actividad
    Cuando abro Cobros dentro de Desempeño
    Entonces conservo el período elegido
    Y veo señas, saldos y su total en pesos argentinos sin comisiones
    Y se aclara que los importes no representan un saldo bancario

  @wip
  Escenario: 71.2-COL Consultar sin cobros recientes
    Dado que no tengo cobros verificados durante el período
    Y tengo trabajos con saldos pendientes
    Cuando consulto mis cobros
    Entonces veo los cobros del período en cero
    Y sigo viendo mis saldos pendientes actuales

  @wip
  Escenario: 71.3-COL Distinguir los saldos pendientes actuales
    Dado que tengo trabajos programados y finalizados con saldo pendiente
    Cuando consulto mis cobros
    Entonces veo por separado la cantidad y el saldo pendiente de cada grupo
    Y esos saldos no dependen del período consultado
    Y no incluyen señas ya cobradas ni comisiones

  @wip
  Escenario: 71.4-COL Consultar con la cuenta de cobros desconectada
    Dado que mi cuenta de Mercado Pago está desconectada
    Y tengo cobros verificados anteriores
    Cuando consulto mis cobros
    Entonces puedo ver los importes registrados sin conectar la cuenta

  @wip
  Escenario: 71.5-COL Entender el origen de mis cobros
    Dado que tengo varios cobros verificados durante el período
    Cuando consulto los movimientos
    Entonces veo la fecha de verificación, si es seña o saldo y el importe de cada movimiento
    Y veo sus referencias de propuesta y orden cuando están disponibles
    Y veo la cantidad y el importe total de todos los movimientos del período

  @wip
  Esquema del escenario: 71.6-COL Consultar movimientos por tipo
    Dado que tengo señas y saldos en el período
    Cuando elijo mostrar "<tipo>"
    Entonces veo solamente los movimientos correspondientes
    Y la cantidad y el importe total corresponden a todos los movimientos de ese tipo
    Ejemplos:
      | tipo  |
      | Señas |
      | Saldos |
      | Todos |

  @wip
  Escenario: 71.7-COL Continuar leyendo los movimientos
    Dado que todavía quedan movimientos del período por mostrar
    Cuando elijo cargar más
    Entonces veo los siguientes movimientos del mismo período y tipo sin duplicados
    Y conservo los que ya estaba leyendo
    Y la cantidad y el importe total no se limitan a los movimientos visibles

  @wip
  Escenario: 71.8-COL Recuperar los movimientos sin perder el resumen
    Dado que veo un resumen válido y falló la consulta de movimientos
    Y la información vuelve a estar disponible
    Cuando reintento consultar los movimientos
    Entonces veo los movimientos solicitados conservando sus filtros
    Y el resumen permanece disponible durante el reintento

  @wip
  Escenario: 71.9-COL Cambiar la consulta después de cargar más movimientos
    Dado que cargué más movimientos de un período
    Cuando elijo otro período
    Entonces veo su resumen y sus primeros movimientos
    Y no se mezclan con los movimientos del período anterior

  @wip
  Escenario: 71.10-COL Comparar la evolución de mis cobros
    Dado que tengo cobros verificados en distintos momentos
    Cuando consulto su evolución con comparación con el período anterior
    Entonces distingo señas, saldos y total por intervalo
    Y los intervalos sin cobros muestran cero
    Y veo las diferencias entre períodos de igual duración
    Y los porcentajes sin base figuran como no disponibles

  @wip
  Escenario: 71.11-COL Retomar la lectura de mis movimientos
    Dado que estaba leyendo movimientos con un período y tipo elegidos
    Cuando vuelvo a Cobros después de consultar Actividad
    Entonces conservo las opciones y mi posición de lectura
