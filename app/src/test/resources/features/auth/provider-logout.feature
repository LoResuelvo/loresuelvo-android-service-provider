# language: es
Característica: Cerrar sesión desde Perfil
  Como prestador autenticado
  Quiero cerrar mi sesión con confirmación
  Para finalizar el acceso a mi cuenta desde el dispositivo

  Escenario: 5.1-LOG Solicitar confirmación desde Perfil
    Dado que estoy en mi Perfil con una sesión activa
    Y el botón rojo "Cerrar sesión" está al final del contenido
    Cuando pulso "Cerrar sesión"
    Entonces se muestra un popup redondeado que pregunta si quiero cerrar sesión
    Y ofrece "Volver" y "Cerrar sesión"
    Y mi sesión permanece activa hasta confirmar

  Escenario: 5.2-LOG Volver sin cerrar sesión
    Dado que está abierto el popup de confirmación
    Cuando pulso "Volver"
    Entonces se cierra el popup y permanezco en Perfil
    Y mi sesión permanece activa sin iniciar el cierre externo

  Esquema del escenario: 5.3-LOG Confirmar el cierre de sesión
    Dado que inicié sesión con "<metodo>"
    Y está abierto el popup de confirmación
    Y Auth0 puede completar el cierre de sesión
    Cuando confirmo "Cerrar sesión"
    Entonces se elimina mi sesión local y se cierra la sesión de Auth0
    Y se muestra Welcome sin acceso a pantallas privadas con Atrás
    Y la próxima apertura de la aplicación muestra Welcome
    Ejemplos:
      | metodo              |
      | email y contraseña  |
      | Google              |

  Escenario: 5.4-LOG Mantener el cierre local si falla Auth0
    Dado que está abierto el popup de confirmación
    Y Auth0 no puede completar el cierre externo
    Cuando confirmo "Cerrar sesión"
    Entonces se elimina igualmente mi sesión local y se muestra Welcome
    Y las pantallas privadas permanecen bloqueadas
    Y se informa que el cierre externo quedó pendiente y se permite reintentarlo
