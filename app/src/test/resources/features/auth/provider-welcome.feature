# language: es
Característica: Pantalla inicial de bienvenida para prestadores
  Como prestador no autenticado
  Quiero ver la pantalla de bienvenida de LoResuelvo
  Para entender la propuesta de valor y registrarme o iniciar sesión

  @wip
  Escenario: 01-PWB Mostrar branding y propuesta de valor
    Dado que el prestador no tiene una sesión local
    Cuando abre la app
    Entonces ve el logo y el nombre de LoResuelvo
    Y ve el botón "Iniciar sesión" en la barra superior
    Y ve el badge "Profesionales certificados"
    Y ve el título "Ofrecé tus servicios profesionales"
    Y ve el subtítulo explicativo

  @wip
  Escenario: 02-PWB Mostrar los 3 pasos de cómo funciona
    Dado que el prestador no tiene una sesión local
    Cuando abre la app
    Entonces ve el paso 1 "Publicá tus servicios" con su descripción
    Y ve el paso 2 "Recibí solicitudes" con su descripción
    Y ve el paso 3 "Cobrá tu trabajo" con su descripción

  @wip
  Escenario: 03-PWB Mostrar las acciones de autenticación
    Dado que el prestador no tiene una sesión local
    Cuando abre la app
    Entonces ve el botón "Registrarme" como acción principal
    Y ve el botón "Continuar con Google" como acción alternativa
    Y ve el texto legal de términos y política de privacidad

  @wip
  Escenario: 04-PWB Los chips de categorías se cargan desde el backend
    Dado que el backend responde GET /categories con 6 categorías
    Cuando el prestador abre la app
    Entonces ve los chips con los nombres de las 6 categorías

  @wip
  Escenario: 05-PWB Los chips muestran error si el backend falla
    Dado que el backend falla al responder GET /categories
    Cuando el prestador abre la app
    Entonces los chips muestran el mensaje de error de carga