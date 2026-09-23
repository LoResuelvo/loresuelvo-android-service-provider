# language: es
@us58_1
Característica: Iniciar o reintentar la identificación desde Perfil
  Como prestador registrado
  Quiero verificar mi identidad desde Perfil cuando mi estado lo permita
  Para identificarme sin repetir el registro

  Antecedentes:
    Dado que inicié sesión como prestador con mi perfil profesional completo

  Esquema del escenario: 01-PID Ofrecer la acción según el estado del perfil
    Dado que la consulta de mi perfil devuelve "<Estado>"
    Cuando abro Perfil
    Entonces la acción de identidad aparece "<Acción>"
    Y veo el estado informado y la fecha de aprobación si corresponde

    Ejemplos:
      | Estado        | Acción                                                     |
      | unverified    | Verificar identidad habilitada                             |
      | not_started   | Verificar identidad habilitada                             |
      | awaiting_user | Verificar identidad habilitada                             |
      | declined      | Reintentar habilitada                                      |
      | abandoned     | Reintentar habilitada                                      |
      | expired       | Reintentar habilitada                                      |
      | kyc_expired   | Reintentar habilitada                                      |
      | in_progress   | Verificar identidad deshabilitada                          |
      | in_review     | Verificar identidad deshabilitada                          |
      | resubmitted   | Verificar identidad deshabilitada                          |
      | approved      | Verificar identidad deshabilitada                          |
      | desconocido   | Verificar identidad deshabilitada con opción de recargar Perfil |

  Esquema del escenario: 02-PID Abrir un único intento permitido
    Dado que mi perfil permite "<Acción>"
    Y el servicio puede iniciar la identificación
    Cuando selecciono "<Acción>"
    Entonces veo que se está iniciando la identificación
    Y se abre Didit una sola vez
    Y la acción queda deshabilitada mientras el intento está activo

    Ejemplos:
      | Acción              |
      | Verificar identidad |
      | Reintentar          |

  Esquema del escenario: 03-PID Volver a Perfil y usar su estado actualizado
    Dado que inicié Didit desde Perfil
    Y la siguiente consulta de mi perfil devolverá "<Estado>"
    Cuando Didit devuelve "<Resultado>"
    Entonces vuelvo a Perfil y sus datos se recargan una sola vez
    Y la acción de identidad queda "<Acción>" según esa consulta
    Y puedo seguir usando Inicio y Mensajes sin esperar una aprobación

    Ejemplos:
      | Resultado  | Estado      | Acción        |
      | completado | in_review   | deshabilitada |
      | completado | approved    | deshabilitada |
      | cancelado  | unverified  | habilitada    |
      | cancelado  | in_progress | deshabilitada |
      | error      | declined    | habilitada    |
      | error      | in_progress | deshabilitada |
      | error      | approved    | deshabilitada |

  Escenario: 04-PID Recuperarse de un error al iniciar la identificación
    Dado que mi perfil permite iniciar la identificación
    Y la solicitud de inicio fallará por un problema de red
    Y la siguiente consulta de mi perfil devolverá "in_progress"
    Cuando selecciono "Verificar identidad"
    Entonces sigo en Perfil y veo un mensaje de error
    Y Didit no se abre
    Y mi perfil se recarga una sola vez y la acción queda deshabilitada
