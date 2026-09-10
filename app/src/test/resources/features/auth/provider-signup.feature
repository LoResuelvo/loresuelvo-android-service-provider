# language: es
Característica: Registro de prestadores mediante Auth0 Universal Login
  Como prestador no autenticado
  Quiero registrarme en LoResuelvo
  Para comenzar a ofrecer mis servicios profesionales

  Escenario: 01-PSU Iniciar el registro de prestador
    Dado que el prestador no tiene una sesión local
    Cuando el prestador selecciona "Registrarme" desde la pantalla de bienvenida
    Entonces la app inicia Auth0 Universal Login en modo de registro para la conexión de correo electrónico y contraseña del prestador
    Y la app nunca solicita ni almacena una contraseña por sí misma

  @wip
  Escenario: 02-PSU Completar el registro de prestador
    Dado que Auth0 devolverá credenciales válidas del prestador
    Cuando el registro del prestador finaliza correctamente
    Entonces la app persiste la sesión autenticada
    Y el token de acceso está disponible para las llamadas HTTP autenticadas
    Y la app continúa al formulario de perfil profesional

  @wip
  Escenario: 03-PSU Cancelar el registro de prestador
    Dado que el prestador comenzó sin una sesión local
    Cuando el prestador cancela el registro en Auth0
    Entonces la pantalla de bienvenida permanece visible
    Y no se persiste ninguna sesión

  @wip
  Escenario: 04-PSU Reintentar después de un error en el registro
    Dado que el registro en Auth0 fallará con un error recuperable del prestador
    Cuando finaliza el intento de registro
    Entonces la pantalla de bienvenida muestra un error localizado y amigable
    Y los controles de autenticación quedan disponibles para reintentar

  @wip
  Escenario: 05-PSU Evitar lanzamientos duplicados de autenticación
    Dado que un intento de registro del prestador sigue activo
    Cuando el prestador selecciona nuevamente una acción de autenticación
    Entonces no se inicia un segundo flujo de Auth0
    Y la pantalla de bienvenida muestra un estado de carga accesible
