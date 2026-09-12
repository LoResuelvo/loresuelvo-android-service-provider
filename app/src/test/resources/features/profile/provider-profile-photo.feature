# language: es
Característica: Registro de prestador con foto de perfil
  Como prestador que completa su perfil profesional
  Quiero seleccionar, previsualizar y cargar una foto válida
  Para completar mi registro profesional en la plataforma

  Escenario: 01-PPH Seleccionar y visualizar una foto en el formulario existente
    Dado que el prestador completó nombre, apellido y seleccionó un rubro
    Cuando el prestador selecciona desde el dispositivo una foto JPEG legible de menos de 5 MiB
    Entonces aparece una vista previa de esa foto en la misma página que el nombre, apellido y rubro
    Y los datos existentes del formulario permanecen sin cambios
    Y el prestador puede cargar o reemplazar la foto sin abandonar la página

  Escenario: 02-PPH Reemplazar la foto antes del registro
    Dado el prestador tiene una foto seleccionada, confirmada o con una carga fallida en el formulario
    Y el prestador todavía no completó el registro
    Cuando el prestador selecciona otra foto válida mediante el control de reemplazo
    Entonces la vista previa muestra la nueva foto en el mismo formulario
    Y la nueva foto debe cargarse y confirmarse antes del registro
    Y el nombre, apellido, rubro y las zonas de cobertura seleccionadas permanecen sin cambios

  Escenario: 03-PPH Cancelar la selección de foto
    Dado el prestador abrió el selector del dispositivo desde el formulario
    Cuando el prestador cancela el selector sin elegir un archivo
    Entonces el prestador regresa al mismo formulario sin un error de validación
    Y la selección anterior y su estado de confirmación permanecen sin cambios
    Y los demás datos del formulario permanecen sin cambios

  Esquema del escenario: 04-PPH Aceptar los formatos admitidos hasta el límite exacto de tamaño
    Dado que el prestador está en el formulario de perfil
    Y una imagen legible tiene el formato "<Formato real>" y tamaño <Tamaño en bytes> bytes
    Cuando el prestador selecciona esa imagen desde el dispositivo
    Entonces la imagen es aceptada y aparece su vista previa
    Y la acción para cargar la foto queda disponible

    Ejemplos:
      | Formato real | Tamaño en bytes |
      | JPEG         | 1024            |
      | PNG          | 1024            |
      | WebP         | 1024            |
      | JPEG         | 5242880         |
      | PNG          | 5242880         |
      | WebP         | 5242880         |

  Esquema del escenario: 05-PPH Rechazar un archivo inválido sin intentar cargarlo
    Dado el prestador tiene una foto válida seleccionada en el formulario
    Y un nuevo archivo presenta la condición inválida "<Condición inválida>"
    Cuando el prestador selecciona ese archivo
    Entonces aparece un mensaje amigable de validación de la foto en el formulario
    Y no se intenta cargar el archivo inválido
    Y la foto válida anterior permanece seleccionada

    Ejemplos:
      | Condición inválida                                    |
      | Imagen GIF cuyo formato real no está admitido         |
      | Imagen JPEG, PNG o WebP de 5242881 bytes              |
      | Archivo de cero bytes                                 |
      | Contenido seleccionado que no puede leerse            |
      | Contenido corrupto declarado como una imagen admitida |

  Escenario: 06-PPH Cargar y confirmar la foto actual
    Dado hay una foto válida seleccionada en el formulario
    Y el prestador tiene una sesión autenticada válida
    Y la carga de la foto finalizará correctamente
    Cuando el prestador selecciona la acción para cargar la foto
    Entonces el formulario muestra el progreso de carga de forma accesible
    Y la foto actual queda lista para el registro únicamente después de una confirmación exitosa
    Y el formulario no se envía

  Esquema del escenario: 07-PPH Evitar operaciones duplicadas mientras hay una operación en curso
    Dado la operación "<Operación en curso>" está en curso
    Cuando el prestador repite una acción de carga o registro
    Entonces no se inicia una operación duplicada
    Y los controles de carga, reemplazo y registro que interfieren con la operación permanecen deshabilitados
    Y el formulario continúa mostrando el progreso de la operación

    Ejemplos:
      | Operación en curso       |
      | Solicitud de URL firmada |
      | Solicitud PUT firmada    |
      | Confirmación de la foto  |
      | Registro del prestador   |

  Esquema del escenario: 08-PPH Conservar el formulario ante una falla recuperable de carga
    Dado que el prestador completó nombre, apellido y seleccionó un rubro
    Y la carga de una foto válida está en curso
    Y la etapa "<Etapa fallida>" devuelve la falla recuperable "<Falla recuperable>"
    Cuando el intento de carga finaliza con esa falla
    Entonces el prestador permanece en el mismo formulario con un mensaje amigable de error de foto
    Y se preservan el nombre, apellido y la selección de rubro
    Y se conserva la foto seleccionada para reintentar o reemplazarla

    Ejemplos:
      | Etapa fallida            | Falla recuperable                                |
      | Solicitud de URL firmada | Falla de red o falla temporal del servidor       |
      | Solicitud PUT firmada    | Falla de red o falla temporal del almacenamiento |
      | Confirmación             | Falla de red o falla temporal del servidor       |

  Escenario: 09-PPH Reintentar una carga de foto fallida
    Dado el formulario muestra una falla recuperable de carga de foto
    Y el reintento será exitoso
    Cuando el prestador selecciona la acción para reintentar la carga de la foto
    Entonces se reanuda el progreso de carga de la imagen seleccionada actualmente
    Y una confirmación exitosa deja esa imagen lista para el registro
    Y el formulario no se envía

  Esquema del escenario: 10-PPH Impedir el registro hasta completar la foto y las zonas requeridas
    Dado que el prestador ingresó nombre, apellido válidos y seleccionó un rubro
    Y se cumple la condición de dependencia "<Condición de dependencia>"
    Cuando el prestador solicita el registro
    Entonces el formulario no se envía
    Y el formulario identifica el requisito de foto o cobertura incompleto mediante un mensaje amigable
    Y se conservan los datos actuales del formulario

    Ejemplos:
      | Condición de dependencia                                                     |
      | No hay una foto válida seleccionada                                          |
      | La foto seleccionada todavía no se cargó                                     |
      | La carga o confirmación de la foto falló                                     |
      | La foto de reemplazo todavía no se confirmó                                  |
      | La foto está confirmada pero no se seleccionó ninguna zona de cobertura real |

  @wip
  Escenario: 11-PPH Registrar el perfil con la foto confirmada y navegar tras el éxito
    Dado que el prestador ingresó nombre, apellido válidos y seleccionó un rubro
    Y hay zonas de cobertura reales seleccionadas provenientes de US-35.5
    Y la foto seleccionada actualmente se confirmó correctamente
    Y la API de registro responderá exitosamente
    Cuando se intenta el registro
    Entonces el perfil se registra con la foto confirmada actualmente
    Y el prestador navega al paso de vinculación de Mercado Pago
    Y el formulario ya no es accesible mediante navegación hacia atrás

  @wip
  Escenario: 12-PPH Mostrar la foto asociada al recuperar los datos del prestador
    Dado el registro finalizó correctamente con una foto confirmada y devolvió el identificador del prestador
    Y la selección local del dispositivo ya no está disponible
    Y la recuperación de sus datos devolverá la foto asociada
    Cuando la app vuelve a cargar los datos de ese prestador para mostrarlos
    Entonces se muestra la foto asociada al prestador desde la dirección proporcionada por el servidor
    Y su visualización no depende de la selección local anterior

  Esquema del escenario: 13-PPH Conservar el estado de la foto al recrear la pantalla
    Dado el prestador ingresó sus datos personales y seleccionó un rubro
    Y hay una foto legible seleccionada en el estado "<Estado previo de la foto>"
    Cuando se recrea la pantalla mientras se conserva la instancia que administra su estado
    Entonces se muestran los mismos datos del formulario y la vista previa de la foto
    Y se conserva el estado previo de confirmación de la foto
    Y no se inicia automáticamente una carga ni un registro

    Ejemplos:
      | Estado previo de la foto     | Estado restaurado esperado                              |
      | Seleccionada pero no cargada | Seleccionada y disponible para cargar                   |
      | Confirmada                   | Confirmada con el mismo identificador de archivo actual |

  @wip
  Escenario: 14-PPH Reintentar el registro sin volver a cargar la foto confirmada
    Dado que el prestador completó nombre, apellido y seleccionó un rubro
    Y la foto actual está confirmada y las zonas de cobertura requeridas están seleccionadas
    Y el registro devolvió una falla recuperable confirmada sin crear al prestador
    Y el siguiente intento de registro será exitoso
    Cuando el prestador reintenta el registro
    Entonces el registro reutiliza la foto confirmada sin volver a cargarla
    Y se preservan el nombre, apellido y la selección de rubro
    Y el prestador navega al paso de vinculación de Mercado Pago
