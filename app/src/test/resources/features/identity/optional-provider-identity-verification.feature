# language: es
Característica: Ofrecer verificación de identidad opcional al prestador
  Como prestador que acaba de completar su registro
  Quiero identificarme opcionalmente con Didit
  Para continuar mi incorporación sin que el resultado bloquee mi cuenta

  Escenario: 01-PIV Mostrar la verificación opcional después del registro
    Dado que un prestador autenticado envía un perfil profesional válido y la API crea el prestador
    Cuando el registro se completa exitosamente
    Entonces el perfil completado no puede volver a enviarse
    Y la app muestra antes de Mercado Pago que la cuenta ya fue creada y la verificación de identidad es opcional
    Y las acciones visibles son "Verificar ahora" y "Más tarde"

  @wip
  Escenario: 02-PIV Posponer la verificación sin crear una sesión
    Dado que el paso de identidad opcional está visible y no hay una solicitud activa
    Cuando el prestador selecciona "Más tarde"
    Entonces la app no solicita una sesión de identidad ni abre el SDK
    Y navega una sola vez al flujo existente de Mercado Pago
    Y Atrás no permite reabrir el perfil completado ni el paso opcional

  @wip
  Escenario: 03-PIV Iniciar una única verificación nativa
    Dado que el paso opcional está visible y el endpoint autenticado devolverá una sesión temporal válida
    Cuando el prestador selecciona "Verificar ahora"
    Entonces la app envía una sola solicitud autenticada y sin cuerpo para crear la sesión
    Y muestra una carga accesible y deshabilita ambas acciones
    Y abre una sola vez el SDK nativo usando únicamente el session_token recibido
    Y los toques repetidos, la recomposición y las señales duplicadas no crean otra solicitud ni apertura

  @wip
  Esquema del escenario: 04-PIV Continuar sin inferir el estado de identidad
    Dado que hay un intento explícito del SDK activo que informará un resumen <resumen>
    Cuando el intento del SDK se completa
    Entonces la app navega una sola vez a Mercado Pago
    Y no repite el registro, consulta el estado, hace polling, espera la aprobación ni actualiza una aprobación local
    Y el resumen no se trata como el estado de identidad autoritativo del prestador

    Ejemplos:
      | resumen   |
      | aprobado  |
      | pendiente |
      | rechazado |

  @wip
  Escenario: 05-PIV Recuperarse de la cancelación del SDK
    Dado que hay un intento explícito del SDK activo
    Cuando el prestador cancela el flujo del SDK
    Entonces la app vuelve al paso opcional y muestra un mensaje de cancelación localizado
    Y vuelve a habilitar el reintento y "Más tarde"
    Y la cuenta permanece creada y Mercado Pago continúa accesible
    Y no inventa ni persiste un estado de identidad

  @wip
  Esquema del escenario: 06-PIV Recuperarse de un fallo del SDK
    Dado que un intento explícito del SDK fallará por <fallo>
    Cuando el SDK devuelve el fallo
    Entonces la app muestra un error localizado seguro para el tipo de fallo
    Y vuelve a habilitar el reintento y "Más tarde"
    Y no expone detalles sin procesar del SDK
    Y la cuenta permanece creada y Mercado Pago continúa accesible

    Ejemplos:
      | fallo                    |
      | permiso de cámara denegado |
      | sesión vencida           |
      | problema de red          |
      | SDK no inicializado      |
      | error desconocido        |

  @wip
  Esquema del escenario: 07-PIV Recuperarse de un fallo al crear la sesión
    Dado que el paso opcional está visible y el endpoint de sesión devolverá <respuesta>
    Cuando el prestador selecciona "Verificar ahora"
    Entonces la app no abre el SDK sin un token válido
    Y aplica la recuperación segura correspondiente a <respuesta>
    Y la cuenta creada no vuelve a registrarse

    Ejemplos:
      | respuesta          |
      | error de transporte|
      | respuesta inválida|
      | 403                |
      | 409                |
      | 5xx                |
      | 401                |

  @wip
  Esquema del escenario: 08-PIV Preservar el flujo durante cambios de ciclo de vida
    Dado que el prestador está <estado>
    Cuando Android <evento>
    Entonces la app no repite el registro ni abre automáticamente otra sesión de Didit
    Y conserva o resuelve el destino seguro definido para el ciclo de vida
    Y la interfaz no queda permanentemente ocupada después de un fallo recuperable

    Ejemplos:
      | estado                              | evento                  |
      | en el paso opcional                 | recrea la Activity       |
      | solicitando una sesión             | vuelve de segundo plano  |
      | dentro de un intento explícito SDK | inicia un proceso nuevo  |

  @wip
  Escenario: 09-PIV Mantener el paso localizado, accesible y adaptable
    Dado que la pantalla opcional se muestra en un idioma compatible y con ajustes de accesibilidad
    Cuando el prestador lee y opera el paso
    Entonces todos los textos y errores de la app provienen de recursos localizados
    Y los títulos, el progreso, los errores y las acciones tienen semántica y objetivos táctiles significativos
    Y la pantalla sigue siendo utilizable en anchos compactos y expandidos, paisaje, fuente grande, tema oscuro y RTL
    Y el SDK conserva la responsabilidad por su accesibilidad y localización internas
