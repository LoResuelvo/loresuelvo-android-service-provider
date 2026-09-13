Feature: Conectar la cuenta de Mercado Pago del prestador
  Como prestador registrado con perfil profesional completo
  Quiero conectar mi cuenta de Mercado Pago
  Para poder cobrar señas y saldos de mis servicios

  Scenario: 01-MPC Mostrar la cuenta pendiente de conexión
    Given que el prestador está autenticado y completó su perfil profesional
    And la API informa que la cuenta está pendiente de conexión
    When el prestador abre la pantalla de Mercado Pago
    Then la app muestra que la cuenta está pendiente de conexión
    And ofrece conectar la cuenta o continuar sin conectarla

  Scenario Outline: 02-MPC Restringir la conexión a prestadores con perfil completo
    Given que la cuenta autenticada corresponde a <situacion>
    When la app evalúa la disponibilidad de la conexión de Mercado Pago
    Then no ofrece iniciar la autorización
    And muestra una indicación de <orientacion>

    Examples:
      | situacion                           | orientacion                         |
      | un prestador con perfil incompleto | completar el perfil profesional     |
      | una cuenta que no es de prestador  | utilizar una cuenta de prestador    |

  Scenario: 03-MPC Solicitar autenticación cuando no hay sesión
    Given que no hay una sesión autenticada
    When se intenta abrir la pantalla de Mercado Pago
    Then la app solicita iniciar sesión
    And no inicia una autorización de Mercado Pago

  Scenario: 04-MPC Abrir la autorización oficial en el navegador
    Given que el prestador puede conectar su cuenta de Mercado Pago
    And la API devuelve una URL de autorización válida
    When el prestador selecciona Conectar con Mercado Pago
    Then la app abre la URL de autorización en el navegador
    And no solicita credenciales de Mercado Pago dentro de LoResuelvo

  Scenario: 05-MPC Evitar solicitudes de conexión duplicadas
    Given que una solicitud de conexión está en curso
    When el prestador vuelve a seleccionar Conectar con Mercado Pago
    Then no se solicita otra autorización a la API
    And no se abre otro flujo en el navegador

  Scenario: 06-MPC Confirmar la conexión al regresar de la autorización
    Given que el prestador autorizó el acceso en Mercado Pago
    And la API confirma el estado connected
    When el prestador regresa a la app mediante el enlace de éxito
    Then la app consulta el estado actualizado con la API
    And muestra que la cuenta está conectada y puede recibir pagos

  Scenario: 07-MPC No confirmar una conexión que la API aún no reconoce
    Given que el prestador regresa mediante el enlace de éxito
    And la API informa que la cuenta sigue pendiente de conexión
    When la app verifica el estado de la cuenta
    Then no muestra la conexión como exitosa
    And permite volver a consultar el estado o continuar sin conectar

  @wip
  Scenario: 08-MPC Recuperarse de una autorización cancelada
    Given que el prestador canceló la autorización en Mercado Pago
    And la API informa que la cuenta sigue pendiente de conexión
    When el prestador regresa a la app mediante el enlace de cancelación
    Then la app muestra que la conexión no se completó
    And permite reintentar o continuar sin conectar la cuenta

  @wip
  Scenario: 09-MPC Recuperarse del cierre del navegador
    Given que el prestador cerró el navegador sin completar la autorización
    And la API informa que la cuenta sigue pendiente de conexión
    When el prestador vuelve a la app
    Then la app consulta el estado y conserva la cuenta pendiente de conexión
    And permite reintentar o continuar sin conectar la cuenta

  @wip
  Scenario: 10-MPC Continuar a Home sin conectar Mercado Pago
    Given que el prestador completó su perfil profesional
    And su cuenta de Mercado Pago está pendiente de conexión
    When el prestador selecciona Continuar sin conectar
    Then la app permite acceder a Home
    And no muestra la cuenta como conectada

  Scenario: 11-MPC Mostrar una cuenta que ya está conectada
    Given que el prestador está autenticado y completó su perfil profesional
    And la API confirma que su cuenta ya está connected
    When el prestador abre la pantalla de Mercado Pago
    Then la app muestra que la cuenta puede recibir pagos
    And no ofrece iniciar otra autorización
    And ofrece continuar a Home

  @wip
  Scenario Outline: 12-MPC Solicitar una nueva sesión cuando la API rechaza la autenticación
    Given que la API rechaza la sesión vencida del prestador
    When la app intenta <accion>
    Then muestra un mensaje amigable y solicita iniciar sesión nuevamente
    And no confirma la conexión ni abre el navegador

    Examples:
      | accion                               |
      | consultar el estado de la cuenta     |
      | solicitar la autorización de conexión|

  @wip
  Scenario Outline: 13-MPC Informar un error temporal sin perder la recuperación
    Given que ocurre <fallo>
    When el prestador intenta <accion>
    Then la app muestra un mensaje amigable sin confirmar una nueva conexión
    And ofrece <recuperacion>

    Examples:
      | fallo                                      | accion                    | recuperacion                                      |
      | un error de red al consultar el estado     | consultar el estado       | reintentar la consulta del estado                 |
      | un error del servidor al consultar         | consultar el estado       | reintentar la consulta del estado                 |
      | un error temporal al solicitar autorización| conectar la cuenta        | reintentar la conexión tras verificar el estado   |
      | la imposibilidad de abrir el navegador     | conectar la cuenta        | reintentar la apertura tras verificar el estado   |

  @wip
  Scenario: 14-MPC Reintentar la verificación sin repetir la autorización
    Given que la consulta de estado falló al regresar del navegador
    And la siguiente consulta a la API confirma connected
    When el prestador selecciona Reintentar verificación
    Then la app vuelve a consultar el estado y muestra la cuenta conectada
    And no solicita ni abre otra autorización

  @wip
  Scenario: 15-MPC Consultar nuevamente el estado al volver a abrir la app
    Given que el prestador conserva una sesión autenticada y un perfil completo
    And la API informa un estado distinto al observado en la sesión anterior
    When el prestador vuelve a abrir la aplicación
    Then la app consulta nuevamente el estado de Mercado Pago
    And actualiza el estado de conexión con la respuesta recibida

  @wip
  Scenario Outline: 16-MPC Recuperar el flujo tras un cambio del ciclo de vida
    Given que el prestador inició una autorización de Mercado Pago
    When ocurre <evento> y el prestador vuelve al flujo
    Then la app verifica el estado con la API antes de confirmar la conexión
    And permite continuar o reintentar sin abrir automáticamente otra autorización

    Examples:
      | evento                                      |
      | una rotación del dispositivo                |
      | el paso de la app a segundo plano            |
      | la recreación del proceso de la aplicación   |
