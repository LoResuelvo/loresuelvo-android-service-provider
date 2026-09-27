# language: es
Característica: Informar finalización con evidencia como prestador
  Antecedentes:
    Dado que inicié sesión como prestador

  Esquema del escenario: 01-PIF Abrir el reporte según la orden vigente
    Dado que consulto el resumen de un turno real de Ana Pérez desde Turnos
    Y la consulta vigente de esa orden indica "<situación>"
    Cuando intento abrir Informar finalización
    Entonces veo "<resultado>" para esa misma orden
    Y conservo el acceso al motivo completo y a su conversación desde el resumen
    Y no se registra ninguna finalización
    Ejemplos:
      | situación                                               | resultado                                  |
      | soy el asignado, está scheduled y aún no llegó el turno  | una explicación de que todavía no se puede |
      | soy el asignado, está scheduled y llegó la hora del turno| el formulario con consumidor y trabajo     |
      | soy el asignado, está scheduled y el turno ya pasó       | el formulario con consumidor y trabajo     |
      | la orden ya está awaiting_payment con reporte           | el estado vigente sin ofrecer otro reporte |
      | la orden ya está paid con reporte                       | el estado vigente sin ofrecer otro reporte |
      | no soy el prestador asignado                            | un aviso de falta de permisos              |

  Esquema del escenario: 02-PIF Administrar las fotografías sin perder el formulario
    Dado que escribí la descripción de entrega
    Y el formulario contiene "<selección inicial>"
    Cuando realizo "<acción>" sobre las fotografías
    Entonces veo "<resultado>"
    Y conservo la descripción y el orden relativo de las fotografías restantes
    Ejemplos:
      | selección inicial | acción                                    | resultado                                    |
      | ninguna foto      | seleccionar una foto JPEG válida          | la vista previa de la foto                   |
      | una foto válida   | seleccionar una foto PNG y una WebP válidas| las tres vistas previas en orden de selección |
      | tres fotos válidas| quitar la segunda foto                    | sólo la primera y la tercera foto            |
      | tres fotos válidas| intentar agregar una cuarta foto          | un aviso de máximo tres fotos                |
      | una foto válida   | seleccionar un archivo vacío              | un aviso de archivo inválido                 |
      | una foto válida   | seleccionar un formato no admitido        | un aviso de formato no admitido              |
      | una foto válida   | seleccionar una foto mayor a 5 MiB        | un aviso de tamaño excedido                  |
      | una foto válida   | seleccionar un archivo inaccesible        | un aviso para seleccionar otra foto          |

  Esquema del escenario: 03-PIF Impedir un reporte incompleto
    Dado que abrí el formulario de una orden habilitada
    Y el borrador tiene "<problema>"
    Cuando intento confirmar la finalización
    Entonces el envío permanece bloqueado con una explicación del problema
    Y no se registra un reporte ni se pierde el resto del borrador
    Ejemplos:
      | problema                                   |
      | descripción vacía                          |
      | descripción formada sólo por espacios      |
      | ninguna fotografía                         |
      | una fotografía todavía sin confirmar       |
      | identificadores de archivo repetidos       |

  Esquema del escenario: 04-PIF Recuperar la carga de una fotografía
    Dado que tengo una foto confirmada y otra cuya carga falló en "<etapa>"
    Y veo el estado de cada foto y la opción de reintentar la fallida
    Y el próximo intento de esa carga puede completarse
    Cuando reintento la fotografía fallida
    Entonces ambas fotografías quedan confirmadas en su orden original
    Y se conserva la descripción sin volver a subir la foto ya confirmada
    Y no se registra la finalización hasta que la confirme
    Ejemplos:
      | etapa                         |
      | preparación de la subida      |
      | transferencia del archivo      |
      | confirmación del archivo       |

  Esquema del escenario: 05-PIF Confirmar una única finalización y actualizar el turno
    Dado que soy el prestador asignado de una orden scheduled cuyo turno ya comenzó
    Y escribí una descripción válida y tengo <cantidad> fotografías confirmadas
    Y la API registra el reporte y devuelve la orden actualizada como awaiting_payment
    Cuando confirmo la finalización con un doble toque
    Entonces se envía un solo reporte con la descripción y las fotografías en su orden
    Y veo la confirmación de éxito y el turno como Pendiente de pago
    Y se actualizan Turnos y la actividad afectada de Inicio
    Y no se ofrece otro reporte ni se marca la orden como Pagado localmente
    Ejemplos:
      | cantidad |
      | 1        |
      | 3        |

  Esquema del escenario: 06-PIF Recuperar un rechazo del reporte
    Dado que tengo un borrador válido para una orden que estaba habilitada
    Y la API rechaza el reporte con "<respuesta>"
    Cuando confirmo la finalización
    Entonces veo "<recuperación>"
    Y no se informa un éxito ni se repite automáticamente el envío
    Ejemplos:
      | respuesta                     | recuperación                                                    |
      | 400 por datos inválidos        | los datos conservados y un aviso para corregirlos                |
      | 401 por sesión inválida        | el flujo de autenticación sin datos privados de la sesión previa |
      | 403 por falta de permisos      | un aviso de falta de permisos sin permitir otro envío            |
      | 404 por orden inexistente      | un aviso de orden no disponible y la opción de volver a Turnos   |
      | 409 por fecha o estado vigente | la orden consultada nuevamente y la explicación correspondiente  |
      | 409 por reporte existente      | la orden consultada nuevamente sin ofrecer otro reporte          |

  Esquema del escenario: 07-PIF Resolver un envío de resultado incierto antes de reintentar
    Dado que envié un reporte y la conexión se interrumpió sin conocer el resultado
    Y la consulta posterior de esa misma orden obtiene "<resultado de consulta>"
    Cuando se reconcilia el estado de la orden
    Entonces veo "<recuperación>"
    Y no se envía automáticamente otro reporte
    Ejemplos:
      | resultado de consulta                     | recuperación                                                     |
      | el reporte ya está registrado             | la finalización registrada y el estado vigente sin otro envío     |
      | sigue habilitada y no tiene reporte       | el borrador conservado y la opción de confirmar un nuevo intento  |
      | no se pudo consultar el estado            | el borrador conservado y la opción de reintentar sólo la consulta  |

  Esquema del escenario: 08-PIF Proteger el borrador y la salida del formulario
    Dado que tengo una descripción y fotografías seleccionadas en el formulario
    Y todavía no confirmé la finalización
    Cuando ocurre "<evento>"
    Entonces obtengo "<resultado>"
    Y no se registra una finalización ni se duplican las cargas
    Ejemplos:
      | evento                                                | resultado                                                           |
      | roto el dispositivo                                   | el mismo borrador con el estado de sus fotografías                   |
      | regreso del selector sin elegir nuevas fotos          | el mismo borrador sin cambios                                       |
      | se recrea el proceso y los archivos siguen disponibles | el borrador restaurado sin dar cargas incompletas por confirmadas    |
      | se recrea el proceso y una foto dejó de ser accesible   | la descripción y fotos disponibles con un aviso para reseleccionarla |
      | elijo Cancelar                                        | el resumen de la misma orden sin cambios                             |
      | vuelvo con Atrás una vez cerrado el teclado            | el resumen de la misma orden sin cambios                             |

  Esquema del escenario: 09-PIF Mantener la identidad visual y accesibilidad del flujo
    Dado que uso "<idioma>" y tamaño de fuente "<fuente>"
    Y tengo las referencias Android consumidor y del resumen de US-55
    Cuando recorro el resumen, el formulario vacío y con fotos, la carga, el error y el éxito
    Entonces veo la misma familia de colores, tipografía, espaciado y navegación móvil
    Y los campos, fotos, acciones y estados tienen textos y controles accesibles
    Y el teclado y el desplazamiento permiten acceder a validaciones y acciones sin solapamientos
    Y las diferencias visuales se justifican por el rol, el contrato de la API o accesibilidad
    Ejemplos:
      | idioma | fuente   |
      | es-AR  | normal   |
      | es-AR  | ampliada |
      | en     | normal   |
      | en     | ampliada |
