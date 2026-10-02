# language: es
@us57
Característica: Vincular Google Calendar desde Perfil
  Como prestador registrado
  Quiero vincular mi Google Calendar
  Para habilitar la integración de mis turnos con mi calendario

  Esquema del escenario: 57.1-CAL Consultar el estado del calendario
    Dado que soy un prestador autenticado con el calendario "<estado>"
    Cuando abro mi Perfil
    Entonces Google Calendar muestra "<mensaje>"
    Y ofrece "<accion>"
    Ejemplos:
      | estado          | mensaje            | accion                    |
      | disconnected    | Sin vincular       | Vincular Google Calendar  |
      | connected       | Vinculado          | ninguna acción            |
      | action_required | Requiere atención  | Reautorizar Google Calendar |

  Esquema del escenario: 57.2-CAL Vincular o reautorizar el calendario
    Dado que mi calendario está "<estado>"
    Y inicié el consentimiento oficial de Google desde Perfil
    Cuando autorizo el acceso a mi calendario
    Entonces la aplicación envía el código de autorización a la plataforma
    Ejemplos:
      | estado          |
      | disconnected    |
      | action_required |

  Esquema del escenario: 57.3-CAL Abandonar el consentimiento
    Dado que inicié la vinculación de Google Calendar desde Perfil
    Cuando "<resultado>" el consentimiento de Google
    Entonces regreso a Perfil sin mostrar una vinculación exitosa
    Y puedo reintentar y continuar usando la aplicación
    Ejemplos:
      | resultado          |
      | cancelo            |
      | deniego            |

  Esquema del escenario: 57.4-CAL Recuperarse de un error
    Dado que inicié la vinculación de Google Calendar desde Perfil
    Cuando ocurre "<error>"
    Entonces se informa el problema sin confirmar una vinculación inexistente
    Y se ofrece "<recuperacion>"
    Ejemplos:
      | error                                      | recuperacion               |
      | un fallo al abrir el consentimiento        | reintentar                 |
      | un fallo de red al enviar el código        | reintentar                 |
      | el rechazo del código por la plataforma    | iniciar otro consentimiento |
      | un fallo al refrescar el perfil            | reintentar la consulta     |
      | la expiración de mi sesión                 | iniciar sesión nuevamente  |

  Escenario: 57.5-CAL Evitar intentos simultáneos
    Dado que una vinculación de Google Calendar está en curso
    Cuando intento iniciarla nuevamente
    Entonces se mantiene un único intento con una indicación de carga
    Y no se duplica el consentimiento ni el envío del código

  @wip
  Esquema del escenario: 57.6-CAL Recuperar Perfil sin repetir la autorización
    Dado que inicié una vinculación de Google Calendar
    Cuando "<regreso>"
    Entonces Perfil recupera un estado coherente con la plataforma
    Y no abre automáticamente otro consentimiento ni reenvía un código consumido
    Ejemplos:
      | regreso                        |
      | regreso del consentimiento     |
      | reabro Perfil                  |
      | se recrea la pantalla          |
      | reinicio la aplicación         |

  @wip
  Escenario: 57.7-CAL Descartar un resultado de una sesión anterior
    Dado que inicié el consentimiento de Google con una sesión que ya finalizó
    Cuando llega el resultado de ese consentimiento
    Entonces no se vincula el calendario a otra sesión
    Y no se muestra información privada de la sesión anterior
