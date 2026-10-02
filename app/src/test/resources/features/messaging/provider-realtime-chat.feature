# language: es
Característica: Mantener actualizada la conversación con el consumidor
  Como prestador
  Quiero recibir los mensajes del consumidor mientras uso la aplicación
  Para coordinar el servicio sin interrumpir mi lectura

  Antecedentes:
    Dado que inicié sesión como prestador

  @wip @batch1
  Esquema del escenario: 01-PRC Recibir un mensaje en la conversación abierta
    Dado que estoy al final de una conversación activa con Ana
    Cuando Ana me envía un mensaje de "<contenido>"
    Entonces veo el nuevo mensaje de Ana sin salir de la conversación
    Y conservo el historial ordenado y la hora de cada mensaje
    Y puedo consultar su contenido con las acciones habituales
    Ejemplos:
      | contenido   |
      | texto       |
      | fotografías |
      | audio       |

  @wip @batch1
  Escenario: 02-PRC Mantener separados los mensajes de cada consumidor
    Dado que tengo abierta la conversación con Ana
    Y tengo otra conversación con Bruno
    Cuando Bruno me envía un mensaje
    Entonces la conversación abierta conserva únicamente los mensajes de Ana y los míos
    Y la bandeja refleja el nuevo mensaje en la conversación con Bruno
    Y no aparece un aviso de nuevo mensaje dentro de la conversación con Ana

  @wip @batch1
  Escenario: 03-PRC Leer información adicional antes de aceptar una solicitud
    Dado que tengo abierta una solicitud de Ana que todavía no acepté
    Cuando Ana me envía información adicional por el chat
    Entonces puedo leer esa información sin volver a abrir la solicitud
    Y se indica que debo aceptar la solicitud para responder
    Y no puedo enviar mensajes ni adjuntos hasta aceptarla

  @wip @batch2
  Escenario: 04-PRC Seguir la conversación desde su último mensaje
    Dado que estoy leyendo el último mensaje de Ana
    Cuando Ana envía un nuevo mensaje
    Entonces veo el mensaje nuevo al final de la conversación
    Y no necesito desplazarme para encontrarlo

  @wip @batch2
  Escenario: 05-PRC Continuar leyendo mensajes anteriores sin interrupciones
    Dado que estoy leyendo mensajes anteriores de Ana
    Y tengo una respuesta escrita sin enviar
    Cuando Ana envía un nuevo mensaje
    Entonces conservo mi posición de lectura y mi respuesta escrita
    Y veo el aviso Nuevo mensaje

  @wip @batch2
  Escenario: 06-PRC Ir a los mensajes nuevos cuando termino de leer
    Dado que estoy leyendo mensajes anteriores de Ana
    Y veo el aviso Nuevo mensaje porque recibí dos mensajes más
    Cuando selecciono Nuevo mensaje
    Entonces veo el mensaje más reciente de Ana
    Y el aviso desaparece
    Y los dos mensajes recibidos permanecen en la conversación

  @wip @batch3
  Esquema del escenario: 07-PRC Recuperar la conversación después de una interrupción
    Dado que tengo una respuesta escrita sin enviar
    Y Ana me envió mensajes mientras "<interrupción>"
    Cuando vuelvo a usar la conversación con conexión disponible
    Entonces aparecen los mensajes recibidos durante la interrupción sin recargar manualmente
    Y cada mensaje aparece una sola vez en orden cronológico
    Y conservo mi respuesta escrita mientras siga abierta la misma sesión de la aplicación
    Y la bandeja refleja la actividad recuperada
    Ejemplos:
      | interrupción                   |
      | mi conexión estaba interrumpida |
      | estaba usando otra aplicación  |

  @wip @batch3
  Escenario: 08-PRC Recuperar una actualización que no pudo completarse
    Dado que veo el historial de Ana y una respuesta escrita sin enviar
    Y no se pudo recuperar la actividad reciente de esa conversación
    Cuando elijo reintentar con conexión disponible
    Entonces veo el historial actualizado sin mensajes repetidos
    Y conservo mi respuesta escrita
    Y desaparece el aviso de actualización pendiente

  @wip @batch3
  Escenario: 09-PRC Mantener privadas las conversaciones al cambiar de cuenta
    Dado que cerré mi sesión de prestador mientras tenía abierta una conversación
    Y después ingresé con otra cuenta de prestador
    Cuando llega un mensaje dirigido a mi cuenta anterior
    Entonces no veo ese mensaje ni el historial privado de la cuenta anterior
    Y puedo consultar las conversaciones de mi cuenta actual
