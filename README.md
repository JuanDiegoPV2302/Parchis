# Parchís · Ejercicio 2 · Dado virtual con MVC

Proyecto Maven (Java 17). Se abre directo en NetBeans (*File → Open Project*).
Clase principal: `parchis.vista.VistaDado` (abre dos ventanas de jugador, el historial y un panel de pruebas).

## Clases (solo las de la sección «Patrón MVC» del PDF)

| Paquete | Clase | Papel |
|---|---|---|
| `parchis.modelo` | `ModeloDado` | **Modelo.** Dado 1–6, regla del 6 (máx. 3 seguidas), tablero, fichas, capturas, meta exacta y turnos. No importa `javax.swing` ni `java.awt`. |
| `parchis.modelo` | `IModeloDadoLectura` | Lo que la **vista** puede consultar (incluye `agregarObservador`). |
| `parchis.modelo` | `IModeloDado` | Lo que el **controlador** puede pedir: `lanzar()`, `reiniciarTurno()`, `moverFicha()`. |
| `parchis.modelo` | `ObservadorDado` | Observer propio: `actualizar(IModeloDadoLectura)`. |
| `parchis.vista` | `VistaDado` | Vista 1: tablero, fichas y **dado dibujado con Graphics**, más los botones «Lanzar dado», «Mover ficha» y «Nuevo turno». |
| `parchis.vista` | `VistaHistorial` | Vista 2 del mismo modelo: historial de tiros. |
| `parchis.controlador` | `ControlDado` | Valida el turno y traduce los clics en operaciones del modelo. |

## Cómo se cumple cada punto del ejercicio

- **Botón «Lanzar» y valor 1–6:** `ModeloDado.lanzar()`.
- **Regla del 6:** con 6 se vuelve a tirar, máximo tres veces seguidas (`MAX_TIRADAS_CONSECUTIVAS`).
- **Dos vistas del mismo modelo:** `VistaDado` (Graphics) y `VistaHistorial`, suscritas con `ObservadorDado`.
- **«Nuevo turno»:** llama a `ControlDado.reiniciarTurno()` → `IModeloDado.reiniciarTurno()`.
- **Modelo sin Swing:** se prueba con JUnit sin abrir ventanas (`ModeloDadoTest`).
- **Interfaces segregadas:** `VistaDado` recibe `IModeloDadoLectura`; `ControlDado` recibe `IModeloDado`.

## Mecánicas del Parchís

Un dado · con 5 sale una ficha de la base · caer sobre una rival la regresa a su base ·
con 6 se vuelve a tirar (máx. 3 seguidas) · a la meta se entra con el número exacto ·
conteo de fichas en meta · gana quien mete sus 4 fichas.

## Cómo se juega

1. Fernando (verde) empieza. Clic en el dado o en «Lanzar dado».
2. Clic en una ficha elegible (tiene halo) y «Mover ficha» (pide confirmación).
3. Cuando el turno termina se habilita «Nuevo turno». Si prefieres que el turno pase solo,
   cambia `TURNO_AUTOMATICO` a `true` en `ModeloDado`.
4. El panel de pruebas permite programar la siguiente tirada (por ejemplo, 6 para ver la regla del 6).

Ejecutar pruebas: `mvn test`. Ejecutar la aplicación: `mvn compile exec:java`
(o «Run» en NetBeans).
