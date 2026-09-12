# language: es
Característica: Completar perfil profesional del prestador
  Como prestador recién registrado en Auth0
  Quiero completar mi perfil profesional con nombre, apellido y rubro
  Para poder ofrecer mis servicios en la plataforma

  Escenario: 01-CPP Mostrar el formulario de perfil profesional luego de la autenticación
    Dado que el prestador acaba de completar el registro en Auth0
    Cuando la app navega al destino de perfil profesional
    Entonces el prestador ve un formulario que solicita nombre, apellido y rubro
    Y la lista de rubros se carga desde la API

  Escenario: 02-CPP Seleccionar un rubro de la lista de la API
    Dado que los rubros se cargaron correctamente
    Cuando el prestador selecciona un rubro del menú desplegable
    Entonces el rubro seleccionado se muestra como la opción actual
    Y el prestador puede cambiar la selección antes de enviar

  Escenario: 03-CPP Validar campo obligatorio de nombre antes del envío
    Dado que el prestador está en el formulario de perfil
    Cuando el prestador intenta continuar con el nombre vacío
    Entonces aparece un mensaje de validación junto al campo de nombre
    Y el formulario no se envía

  Escenario: 04-CPP Validar campo obligatorio de apellido antes del envío
    Dado que el prestador ingresó un nombre válido
    Cuando el prestador intenta continuar con el apellido vacío
    Entonces aparece un mensaje de validación junto al campo de apellido
    Y el formulario no se envía

  Escenario: 05-CPP Validar selección obligatoria de rubro antes del envío
    Dado que el prestador ingresó un nombre y apellido válidos
    Cuando el prestador intenta continuar sin seleccionar un rubro
    Entonces aparece un mensaje de validación para el campo de rubro
    Y el formulario no se envía

  Escenario: 06-CPP Reintentar tras falla en la carga de rubros
    Dado que la llamada a la API de rubros falla
    Cuando se muestra el formulario de perfil
    Entonces se muestra un mensaje de error amigable en lugar de la lista de rubros
    Y el prestador puede reintentar la carga de rubros

  @wip
  Escenario: 07-CPP Preservar los datos ingresados ante errores recuperables
    Dado que el prestador completó nombre, apellido y seleccionó un rubro
    Y la API de registro devuelve un error recuperable
    Cuando se muestra el error
    Entonces se preservan el nombre, apellido y la selección de rubro
    Y el prestador puede corregir y reintentar

  @wip
  Escenario: 08-CPP Manejar cuenta ya registrada con conflicto 409
    Dado que el correo del prestador ya está registrado en el backend
    Cuando se intenta el registro
    Entonces la app muestra un mensaje amigable indicando que la cuenta ya existe
    Y el prestador permanece en el formulario

  Escenario: 09-CPP Evitar envíos duplicados durante la carga
    Dado que el prestador envió el formulario
    Y el envío está en curso
    Cuando el prestador presiona nuevamente el botón de continuar
    Entonces no se realiza una segunda llamada a la API
    Y el botón permanece deshabilitado con un indicador de carga

  @wip
  Escenario: 10-CPP Enviar perfil válido y navegar tras el éxito
    Dado que el prestador ingresó nombre, apellido válidos y seleccionó un rubro
    Y los datos de foto y zonas de cobertura están disponibles
    Cuando el registro se completa exitosamente
    Entonces el prestador navega al paso de vinculación de Mercado Pago
    Y el formulario ya no es accesible mediante navegación hacia atrás
