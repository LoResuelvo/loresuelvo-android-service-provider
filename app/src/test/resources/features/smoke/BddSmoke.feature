# language: es
Característica: Smoke BDD
  Como agente de CI
  Quiero que el pipeline BDD se ejecute sin pasos sin definir
  Para validar la infra Cucumber en cada commit

  Escenario: El runner BDD carga los features sin errores
    Dado que el pipeline BDD está configurado
    Cuando se carga el feature de smoke
    Entonces el runner termina sin pasos sin definir
