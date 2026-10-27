# Especificacion del cliente

## Alcance y arquitectura

El cliente presenta la interfaz Swing, transforma los mensajes JSON del servidor
en modelos tipados, envía las órdenes del jugador y dibuja los snapshots recibidos.
La autoridad sobre movimiento, combate, consumo de café, victoria y ocupación de
zonas pertenece al servidor. El cliente no simula esas reglas.

La aplicación se organiza con una separación tipo MVC:

- `model` conserva el estado de presentación, datos de protocolo y geometría de
  apoyo para interacción y niebla.
- `view` construye pantallas Swing y representa el mapa y los snapshots.
- `controller` traduce interacciones del usuario en órdenes JSON. `Animator`
  conserva además la animación de demostración del mapa.
- `network` mantiene el socket TCP y valida/parsea mensajes entrantes.
- `config` centraliza constantes de presentación.

El punto de entrada está en `cr.ac.tec.jungleattack.client.Main`.
`NetworkClient` conecta a `100.124.139.105:12345`; los mensajes JSON se envían
como líneas UTF-8. El cliente acepta respuestas del protocolo implementado en
`ServerMessageParser` y presenta errores incompatibles sin reemplazar el último
estado válido.

## Alcance del diagrama

El diagrama único de clases aparece como bloque Mermaid en el `README.md`; su
fuente editable se conserva en `docs/diagrama.mmd`. Prioriza las clases
principales de cada capa, sus responsabilidades, las relaciones entre ellas y
los tipos del protocolo que explican el flujo de datos. Incluye herencia,
composición, implementación de interfaces y dependencias relevantes; omite
detalles internos de construcción de la interfaz y records privados auxiliares
para conservar la legibilidad. La especificacion describe responsabilidades
adicionales que no requieren aparecer como nodos en el diagrama. `model.Zone`
es un record legado sin consumidores en el flujo de produccion actual.

## Paquetes y clases

### `cr.ac.tec.jungleattack.client`

| Clase | Responsabilidad y operaciones principales |
|---|---|
| `Main` | `main(String[])` crea la caché, ventana y conexión TCP; conecta la acción de salir con el cierre y la reconexión. |

### `config`

| Clase | Responsabilidad y operaciones principales |
|---|---|
| `UIConstants` | Clase final de constantes estáticas para la ventana, zoom, animación, selección, energía y niebla. Constructor privado; no tiene estado de instancia. |

### `controller`

| Clase | Responsabilidad y operaciones principales |
|---|---|
| `GameController` | Extiende `MouseAdapter`; `setMessageSender`, `setInteractionEnabled` y `updateState` conectan el estado con la interacción. `mouseClicked` selecciona animales, envía `MOVE_ANIMALS`/`ATTACK` y solicita `VIEW_ZONE`. `onAutomaticZoneRequestRejected` permite reintentar el cambio de zona. |
| `Animator` | Animador Swing basado en `Timer`; `start` y `startAtSpeed` reciben puntos o una `Connection`, sprite y duración/velocidad. `stop` finaliza el recorrido. Emite cambios mediante `Consumer<State>`. `State` es un record anidado con posición, imagen, movimiento, visibilidad y orientación. |

### `model`

| Clase | Responsabilidad y operaciones principales |
|---|---|
| `GameState` | Estado observable de las fases `DISCONNECTED`, `HOME`, `WAITING`, `SELECTING`, `PLAYING` y `FINISHED`. `connected`, `disconnected`, `returnHome`, `report` y `apply` actualizan el modelo y notifican observadores. |
| `ModelObserver` | Interfaz funcional con `onStateChanged(GameState)`; recibe cambios del modelo. |
| `AnimalSpecies` | Enum de las siete especies. `spriteName()` obtiene el nombre del recurso; `find(String)` realiza una búsqueda estática. |
| `ClickableAnimal` | Record con ID, coordenadas y vida; proyección mínima para selección y comandos. |
| `Zone` | Record de modelo legado con ID, nombre y posición; actualmente no se consume en el flujo de producción. Distinto del DTO `ServerMessage.Zone`. |
| `Connection` | Record de un camino de demostración entre zonas, puntos y segmentos ocultos. Copia las listas para hacerlas inmutables; `reversed()` invierte el trayecto y `followedBy(Connection)` une conexiones compatibles. No define las rutas autoritativas de la partida real. |
| `MapZones` | Catálogo estático `ALL` de los centros de zona del contrato con el servidor. `at(double,double)` calcula la zona más cercana y `visibleArea(String,double,double)` produce la región geométrica de una zona. |

### `model.dto`

| Tipo | Responsabilidad y operaciones principales |
|---|---|
| `ServerMessage` | Record que combina `type` y un `Payload` sellado. |
| `Payload` | Interfaz sellada implementada por los tipos de respuesta admitidos. |
| `MatchCode`, `PlayerJoined`, `AnimalSelection` | Records para código de partida, rival y selección de especie disponible. |
| `Snapshot` | Record del estado de partida: datos del paquete propio, animales, zonas visibles y zona seleccionada, tazas y banderas. Copia las listas recibidas. |
| `Pack`, `Animal`, `Attack`, `Zone`, `CoffeeCup`, `Flag` | Records de datos contenidos en el snapshot. `Animal` mantiene `bot` y `attack` opcionales por compatibilidad del protocolo. |
| `GameOver`, `PackSummary`, `MatchCancelled`, `ServerError` | Records de resultado, resumen de paquetes, cancelación y error. `GameOver` copia su lista de resúmenes. |

Los records DTO transportan datos; no implementan las reglas de juego.

### `network`

| Clase | Responsabilidad y operaciones principales |
|---|---|
| `NetworkClient` | Implementa `AutoCloseable`. `start()` inicia conexión y lectura; el hilo escritor vacía la cola de salida. `close()` cierra el socket y detiene los hilos. Entrega los mensajes a `ClientPanel` en el hilo de Swing. |
| `ServerMessageParser` | `parse(String)` valida la estructura JSON, los tipos y los campos requeridos antes de crear un `ServerMessage`. Devuelve el record anidado `ParseResult`, con mensaje o error opcionales. |

### `view`

| Clase | Responsabilidad y operaciones principales |
|---|---|
| `GameWindow` | Ventana principal (`JFrame`); crea `ClientPanel` y expone el panel y el controlador. |
| `ClientPanel` | Panel de flujo de pantallas (`JPanel`): inicio, sala, selección, partida y resultados. Recibe líneas con `receiveServerLine`, acepta callbacks de conexión con `setMessageSender` y `setLeaveHandler`, y actualiza la pantalla ante desconexión/error. |
| `MapPanel` | Panel Swing para mapa de demostración y partida real. `showSnapshot`, `showGameZones`, `showSelection` y `clearGame` actualizan la representación; `toMapPoint` convierte clics de pantalla en coordenadas globales. Incluye zoom, niebla y dibujo de entidades; no calcula sus movimientos reales. |

### `view.utils`

| Clase | Responsabilidad y operaciones principales |
|---|---|
| `SpriteCache` | Carga y conserva imágenes SVG y animaciones. `getImage`, `getAnimation` y `getLoadErrors` exponen recursos y errores de carga. |
| `SpriteAnimation` | Record de imagen quieta, cuadros y duración; copia la lista de cuadros. |
| `EnergyBar` | Utilidad estática `paint(...)` para dibujar la barra de energía. |
| `FogOfWar` | Utilidad estática `paint(...)` para dibujar la región oculta y el borde difuminado en el cliente. No decide qué zonas puede autorizar el servidor. |

## Visibilidad y reglas de interacción del cliente

El servidor determina las zonas ocupadas y entrega las permitidas en cada
snapshot. El cliente muestra una zona a la vez, permite solicitar otra zona
ocupada y dibuja la niebla fuera de la zona seleccionada. Los centros locales
de `MapZones` deben coincidir con `MapFactory` en el servidor.

El clic izquierdo selecciona animales propios visibles; mantener `Ctrl` permite
alternar selecciones. El clic derecho sobre un destino envía `MOVE_ANIMALS`, o
sobre un objetivo visible envía `ATTACK`. Al explorar, el controlador puede
solicitar automáticamente `VIEW_ZONE` cuando sus exploradores seleccionados
aparecen en otra zona ocupada. Los comandos no mueven animales localmente: la
vista espera los siguientes snapshots del servidor.

El botón `Salir` abandona la sesión cerrando el socket actual; el servidor
detecta la desconexión. `Main` inicia una conexión nueva para permitir crear o
unirse a otra partida sin cerrar la ventana.

## Diseño orientado a objetos

- **Herencia:** `GameWindow` extiende `JFrame`; `ClientPanel` y `MapPanel`
  extienden `JPanel`; `GameController` extiende `MouseAdapter`.
- **Interfaces:** `ModelObserver` es funcional; `ServerMessage.Payload` es
  sellada; `NetworkClient` implementa `AutoCloseable`. `Consumer` y `Runnable`
  sirven como callbacks inyectados para envío y salida.
- **Polimorfismo:** `GameController` sobrescribe `mouseClicked`; los componentes
  sobrescriben operaciones de Swing como `paintComponent`/`removeNotify`; el
  `switch` sobre el payload sellado aplica cada tipo de mensaje.
- **Estado estático:** `UIConstants` y `MapZones.ALL` exponen configuración
  compartida; métodos de utilidades y búsquedas (`EnergyBar.paint`,
  `FogOfWar.paint`, `AnimalSpecies.find`) son estáticos. No se usa un singleton.
- **Patrones:** `GameState` más `ModelObserver` implementan Observer para avisar
  cambios a la interfaz. La separación modelo/controlador/vista es tipo MVC.
  Los callbacks de envío y salida permiten inyectar la conexión y la acción de
  reconexión; no se usan patrones de creación de rutas en el cliente de partida.

## Límites con el servidor

El cliente envía `CREATE_MATCH`, `JOIN_MATCH`, `SELECT_ANIMAL`, `MOVE_ANIMALS`,
`ATTACK` y `VIEW_ZONE`. Recibe los tipos validados por `ServerMessageParser`:
`MATCH_CREATED`, `MATCH_JOINED`, `PLAYER_JOINED`, `ANIMAL_SELECTION`,
`GAME_STARTED`, `STATE_UPDATE`, `GAME_OVER`, `MATCH_CANCELLED` y `ERROR`.
El formato JSON y las reglas de autoridad se mantienen coordinados con la
documentación del servidor.
