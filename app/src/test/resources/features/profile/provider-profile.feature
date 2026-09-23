# language: es
@us62
Característica: Ver mi perfil y consultar mis conexiones
  Como prestador registrado
  Quiero consultar mis datos y conexiones desde Perfil
  Para conocer el estado de mi cuenta

  Antecedentes:
    Dado que inicié sesión como prestador con mi perfil profesional completo

  Esquema del escenario: 01-PRF Ver mis datos desde la barra inferior
    Dado que estoy en "<Pestaña>"
    Cuando selecciono Perfil
    Entonces veo mi nombre, apellido, correo y rubro
    Y veo mi foto o un avatar alternativo si no está disponible
    Y Perfil queda seleccionado junto a Inicio y Mensajes

    Ejemplos:
      | Pestaña  |
      | Inicio   |
      | Mensajes |

  Esquema del escenario: 02-PRF Entender la carga o el error de mis datos
    Dado que la consulta de mis datos "<Situación>"
    Cuando abro Perfil
    Entonces veo "<Resultado>"
    Y las acciones de conexión no están disponibles todavía

    Ejemplos:
      | Situación               | Resultado                        |
      | Sigue pendiente         | Una indicación de carga          |
      | Falla por falta de red  | Un error con opción de reintentar |
      | Falla en el servicio    | Un error con opción de reintentar |

  Escenario: 03-PRF Reintentar la carga del perfil
    Dado que no se pudieron cargar mis datos y veo la opción de reintentar
    Y el servicio vuelve a estar disponible
    Cuando selecciono Reintentar
    Entonces veo mis datos actualizados
    Y desaparece el error

  Escenario: 04-PRF Volver a iniciar sesión cuando vence
    Dado que mi sesión venció
    Cuando intento consultar Perfil
    Entonces se me informa que debo volver a iniciar sesión
    Y mis datos privados dejan de estar visibles

  Escenario: 05-PRF Consultar mi verificación de identidad
    Dado que el servicio informa mi estado de identidad
    Cuando consulto Perfil
    Entonces veo ese estado de identidad
    Y veo la fecha de aprobación si existe
    Y la acción de identificación respeta el estado actual del perfil

  Escenario: 06-PRF Usar Perfil con conexiones pendientes
    Dado que todavía no verifiqué mi identidad ni conecté Mercado Pago
    Cuando consulto Perfil
    Entonces Mercado Pago aparece pendiente y puedo abrir su flujo de conexión
    Y Google Calendar aparece como "Próximamente" sin una acción de conexión
    Y puedo seguir usando Inicio y Mensajes

  Escenario: 07-PRF Conectar Mercado Pago desde Perfil
    Dado que abrí el flujo de conexión de Mercado Pago desde Perfil
    Y mi cuenta de Mercado Pago todavía no está conectada
    Cuando selecciono Conectar con Mercado Pago
    Entonces se abre la autorización oficial en el navegador
    Y la app no me solicita credenciales de Mercado Pago

  Esquema del escenario: 08-PRF Consultar el resultado al volver de Mercado Pago
    Dado que inicié la conexión desde Perfil
    Y el navegador terminó con "<Resultado>"
    Y el servicio informa que mi cuenta está "<Estado>"
    Cuando regreso a la app
    Entonces vuelvo a Perfil
    Y Mercado Pago aparece "<Estado>" según la nueva consulta al servicio

    Ejemplos:
      | Resultado                    | Estado    |
      | Autorización completada      | Conectada |
      | Autorización completada      | Pendiente |
      | Autorización cancelada       | Pendiente |
      | Autorización cancelada       | Conectada |
      | Navegador cerrado sin enlace | Pendiente |

  Escenario: 09-PRF Consultar una cuenta de Mercado Pago ya conectada
    Dado que el servicio confirma que Mercado Pago está conectado
    Cuando consulto mis conexiones en Perfil
    Entonces Mercado Pago aparece conectado
    Y no puedo iniciar otra autorización

  Escenario: 10-PRF Conservar mi perfil si falla la consulta de Mercado Pago
    Dado que mis datos personales se cargaron correctamente
    Y no se puede consultar el estado de Mercado Pago
    Cuando consulto mis conexiones en Perfil
    Entonces veo un error con una opción para reintentar esa consulta
    Y mis datos personales siguen visibles
    Y no se anuncia una conexión exitosa

  Esquema del escenario: 11-PRF Recuperar Perfil al volver a la pantalla
    Dado que estaba consultando Perfil
    Cuando "<Acción>"
    Entonces vuelvo a ver Perfil con mis datos actuales
    Y no se repite el registro ni se abre una autorización automáticamente

    Ejemplos:
      | Acción                                             |
      | Vuelvo a Perfil después de visitar otra pestaña     |
      | Regreso a la app después de dejarla en segundo plano |
      | Se recrea la pantalla con su estado guardado        |
