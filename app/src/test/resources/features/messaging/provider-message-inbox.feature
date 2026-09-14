Feature: Home principal del prestador
  Como prestador registrado y con el perfil completo
  Quiero acceder a una pantalla principal con un resumen de mi actividad
  Para consultar rápidamente mis solicitudes, trabajos y acciones disponibles

  @wip
  Scenario: 01-PHO Resolver una sesión vigente de prestador al abrir la app
    Given que existe una sesión local vigente y la API devolverá un perfil completo con rol provider
    When el prestador abre la aplicación
    Then la app muestra un estado de carga hasta resolver el perfil autenticado
    And navega directamente a Home sin mostrar Welcome ni el registro profesional momentáneamente

  @wip
  Scenario: 02-PHO Mostrar la identidad del prestador en Home
    Given que la API devuelve el nombre, apellido, rubro y foto del prestador autenticado
    When la app muestra Home
    Then Home muestra el nombre completo, el rubro y la foto del prestador
    And la foto tiene una alternativa accesible con las iniciales si no puede cargarse

  @wip
  Scenario: 03-PHO Dirigir al registro cuando el perfil no existe
    Given que existe una sesión local pero la API responde 404 al consultar el perfil autenticado
    When la app resuelve el destino privado inicial
    Then navega al registro profesional sin mostrar Home
    And conserva la sesión para completar el registro

  @wip
  Scenario: 04-PHO Impedir Home a una cuenta consumidora
    Given que existe una sesión local y la API devuelve un perfil con rol consumer
    When la app resuelve el destino privado inicial
    Then impide el acceso a Home y muestra un mensaje amigable indicando que la cuenta no corresponde a un prestador
    And ofrece volver a Welcome cerrando la sesión local

  @wip
  Scenario: 05-PHO Cerrar una sesión vencida
    Given que existe una sesión local pero la API rechaza el token con 401
    When la app resuelve el destino privado inicial
    Then elimina la sesión local y muestra Welcome
    And ninguna pantalla privada permanece accesible mediante Atrás
    
  @wip
  Scenario: 06-PHO Informar un error temporal sin cerrar la sesión
    Given que existe una sesión local y la consulta del perfil falla por red o por un error 5xx
    When la app resuelve el destino privado inicial
    Then muestra un mensaje amigable con una acción para reintentar
    And conserva la sesión sin mostrar Welcome, el registro profesional ni Home

  @wip
  Scenario: 07-PHO Reintentar la recuperación del perfil
    Given que la recuperación del perfil falló temporalmente y la siguiente consulta devolverá un prestador completo
    When el prestador selecciona Reintentar
    Then la app consulta nuevamente el perfil autenticado y muestra Home
    And no solicita una nueva autenticación

  @wip
  Scenario: 08-PHO Sincronizar el perfil registrado antes de Home
    Given que el registro profesional finalizó y el prestador completó o decidió omitir el paso opcional de Mercado Pago
    When el prestador continúa a Home
    Then la app vuelve a consultar el perfil autenticado antes de mostrar Home
    And el registro profesional y Mercado Pago no quedan accesibles mediante Atrás

  @wip
  Scenario: 09-PHO Mostrar la actividad disponible del prestador
    Given que la API devuelve solicitudes pendientes y trabajos agendados del prestador autenticado
    When la app termina de cargar la actividad de Home
    Then cada solicitud muestra el consumidor, el título y la descripción disponibles
    And cada trabajo muestra el consumidor, la descripción y la fecha y hora programadas
    And el resumen muestra las cantidades reales de solicitudes pendientes y trabajos agendados
    And Home ofrece accesos visibles a Solicitudes, Trabajos agendados y Mercado Pago

  @wip
  Scenario: 10-PHO Mostrar estados de actividad vacíos
    Given que la API no devuelve solicitudes pendientes ni trabajos agendados para el prestador
    When la app termina de cargar la actividad de Home
    Then muestra estados vacíos claros para Solicitudes y Trabajos agendados
    And muestra ambas cantidades en cero sin presentar un error ni inventar actividad

  @wip
  Scenario: 11-PHO Reintentar una sección de actividad que falló
    Given que una sección de actividad falló temporalmente y la siguiente consulta devolverá datos
    When el prestador reintenta esa sección desde Home
    Then la app actualiza la sección con la respuesta más reciente
    And conserva la identidad, la sesión y las demás secciones disponibles

  @wip
  Scenario: 12-PHO Evitar destinos duplicados al recrear la aplicación
    Given que la app ya resolvió Home para una sesión vigente
    When la actividad rota o se recrea el proceso
    Then la app conserva un único destino Home y un estado de navegación coherente
    And no agrega Welcome, el registro profesional ni otra Home al historial