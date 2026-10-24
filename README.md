# jungle-attack-client

## Cache de imagenes SVG

El cliente utiliza Java 25, Maven y Apache Batik 1.19.
Los ocho SVG se encuentran en `src/main/resources/assets`.

`SpriteCache` carga y rasteriza todos los recursos al crear una instancia,
conservando sus dimensiones originales y transparencia en `BufferedImage`.
Debe crearse una sola instancia al iniciar el cliente y reutilizarse en las
vistas. `getImage(nombre)` devuelve la imagen ya cargada; no vuelve a leer el SVG.
El resultado es un `Optional<BufferedImage>`: si el nombre no esta en la cache,
devuelve `Optional.empty()` para que quien lo solicito decida como continuar.
Las vistas no deben modificar las imagenes compartidas.

Los nombres deben coincidir exactamente con `SpriteCache.ASSET_NAMES`.
Si un recurso falta o no puede convertirse, se omite de la cache y su mensaje
queda disponible en `getLoadErrors()` para que el cliente lo muestre o registre.
`Main` crea la cache al iniciar y la pasa a `GameWindow`; `MapPanel` usa la cache
para dibujar el mapa. Las imagenes de animales quedan disponibles para las
vistas que se implementen despues.

Para ejecutar las pruebas de carga, reutilizacion y transparencia:

```shell
mvn test
```

## Cuadros normalizados de animales

`src/main/resources/assets/normalized` contiene 30 copias SVG de los siete
animales. Todos los lienzos miden 512 x 512 y tienen fondo transparente.
Los originales permanecen intactos. Solo se agregaron traslaciones y escalas
uniformes: no se cambiaron trazados, colores ni proporciones de cada dibujo.
La alineacion usa referencias del cuerpo o cabeza; las alas, patas y colas
pueden ocupar espacios distintos durante el movimiento.

`normalized/sequences.json` relaciona cada copia con su original e indica la
pose de reposo, el orden de reproduccion y una duracion inicial por cuadro.
Los numeros de las secuencias empiezan en 1 y corresponden al orden de
`frames`. Un cuadro puede repetirse para regresar suavemente a otra pose.
Las duraciones son una propuesta visual y no determinan la velocidad de
desplazamiento del animal por el mapa.

Se excluyeron las dos versiones con fondo y la tortuga duplicada. Algunas
poses originales difieren en anatomia, perspectiva o patron del caparazon;
la normalizacion no elimina esas diferencias porque implicaria redibujarlas.
Las poses iniciales del mono, perezoso, rana, serpiente y tortuga se reservan
para reposo, fuera del ciclo de movimiento.

`SpriteCache` utiliza Gson 2.13.2 para leer este archivo al iniciar. Conserva
los ocho recursos originales y carga ademas los 30 cuadros normalizados.
Por ejemplo, `getImage("normalized/mono/mono-2.svg")` devuelve un cuadro.
`getAnimation("mono")` devuelve un `Optional<SpriteAnimation>` con
`idleImage()`, `frames()` (imagenes en orden de reproduccion) y
`frameDurationMs()`. La lista no se puede modificar; las imagenes compartidas
tampoco deben modificarse. Repetir un cuadro reutiliza la misma imagen.

La carga valida dimensiones, identificadores unicos, nombres de archivos,
duraciones positivas e indices de reposo y secuencia. Un fallo se registra
en `getLoadErrors()`, que `Main` ya muestra por stderr. Una animacion con
cuadros faltantes no se publica; las demas animaciones validas y el mapa
siguen disponibles. Un manifiesto invalido no impide cargar los ocho
recursos originales.

La prueba visual descrita abajo reproduce la animacion del mono con un Timer.

## Conexiones visuales de prueba

`MapPanel` resalta en cian el recorrido desde el tunel de Cerro Zurqui hasta
el acceso sur de Volcan Poas, rodeando el crater por la izquierda. Sus puntos estan
en `src/main/resources/map/connections.json`, en coordenadas del mapa
original de 2400 x 1792. No constituyen aun el grafo definitivo ni definen
el centro de cada zona.

El mapa y el recorrido comparten `translate` y `scale`, por lo que se
mantienen alineados al redimensionar la ventana. El recorrido se prepara
una vez al crear el panel, no en cada repintado. El color y grosor estan en
`UIConstants`; el grosor esta expresado en unidades del mapa y tambien escala.
Un error del recurso se informa por stderr y el mapa sigue disponible.
Las conexiones visibles muestran el mono en reposo al inicio de la conexion.
Usa `getAnimation("mono").idleImage()` sin modificar la imagen del cache.
El centro de su lienzo coincide con el primer punto; `TEST_ANIMAL_SIZE`
define el lado mayor en unidades del mapa (48), conservando la proporcion.
La barra mide 36 x 5 unidades y se separa 4 unidades del sprite.
El radio de clic y del aro de seleccion es 24; el aro tiene grosor 2.
El animal se dibuja sobre el resaltado y comparte la escala del mapa.
Si falta la animacion, se informa por stderr y se conserva el mapa.
Sobre el mono visible se dibuja una barra de energia. Por ahora muestra
temporalmente la energia inicial completa del jugador (20/20); el maximo de
los bots es 10. La energia real se conectara al dato recibido del servidor.
El boton "Probar movimiento" inicia el recorrido a `TEST_MOVEMENT_SPEED`
unidades del mapa por segundo (100). La duracion depende de la longitud
completa de cada recorrido. `Animator` usa `javax.swing.Timer` cada 33 ms y
`System.nanoTime()` para interpolar segun el tiempo real transcurrido.
Los cuadros cambian segun `frameDurationMs`, independientemente de la
duracion del desplazamiento. Al llegar se detiene el Timer y vuelve la
pose de reposo; el boton permite reiniciar desde el comienzo.

El boton permanece desactivado durante el movimiento o si faltan los
recursos. Al retirar el panel se detiene el Timer. Inicio, actualizaciones
y parada se ejecutan en el hilo de Swing.

`Animator.start(listaDePuntos, animacion, duracionMs)` admite varios tramos.
Calcula sus longitudes con `Point2D.distance()` una sola vez al iniciar.
El avance total se reparte por longitud, manteniendo velocidad constante
entre tramos y pasando por cada esquina sin cortar en diagonal. La duracion
es la del recorrido completo; no se asigna la misma duracion a cada tramo.
El animador copia las coordenadas para que cambios externos no alteren
un movimiento en curso. Los puntos consecutivos repetidos se saltan;
un recorrido de longitud cero o no finita se rechaza con un mensaje por stderr.
La variante que recibe inicio y final conserva el comportamiento de un tramo.

`Animator.startAtSpeed(listaDePuntos, animacion, unidadesPorSegundo)` calcula
la duracion como longitud total / velocidad. El boton usa esta variante para
que rutas de distintas longitudes mantengan la misma velocidad en coordenadas
del mapa; una ruta del doble de longitud tarda el doble. Redimensionar la
ventana no cambia el tiempo de llegada. La velocidad debe ser finita y
positiva, y la duracion resultante debe ser representable en nanosegundos.
`start(..., duracionMs)` sigue disponible para recorridos con tiempo fijado.

`Animator.State.heading()` indica la direccion del tramo actual como
`Math.atan2(dy, dx)` en radianes: 0 derecha, PI/2 abajo, PI izquierda y
-PI/2 arriba; en tramos diagonales se usa el angulo exacto. Al terminar o
detenerse conserva la direccion del ultimo tramo recorrido.
Las imagenes del mono miran hacia abajo, por eso `MapPanel` lo gira al dibujar
con `AffineTransform.rotate(heading - PI/2)` alrededor de su centro. No se crean
ni modifican archivos de imagen. Antes de iniciar un recorrido se dibuja sin girar.

Las rutas en L y los tramos de distintas longitudes se verifican en pruebas unitarias.
Todavia no se calculan rutas de grafo ni se reciben ordenes de red.

El selector permite probar tambien Playa Conchal - Monteverde,
Monteverde - Cerro Zurqui y Cerro Zurqui - Braulio Carrillo. Los puntos
superiores siguen el camino y la curva del puente; las conexiones consecutivas
comparten los mismos puntos de llegada y salida.

Cada conexion declara `hiddenSegments`: numeros de tramos que quedan dentro
del tunel. El tramo 1 une el punto 1 con el punto 2; el tramo 2 une el punto
2 con el 3, etc. Una lista vacia significa recorrido completamente visible.
`Connection` conserva esa lista y `Animator.State.visible()` indica si debe
dibujarse el animal. El tiempo avanza normalmente dentro del tunel y el
resaltado tambien omite sus tramos ocultos.

La entrada lateral izquierda y la salida derecha se conectan mediante un
punto interior compartido de Cerro Zurqui. Al terminar la entrada el mono
queda oculto en reposo; la salida empieza oculta y lo muestra al salir.
Un mensaje informa cuando esta dentro. El selector permanece desactivado
durante el movimiento y cambiar de conexion reinicia la prueba en su origen.
No hay encadenamiento automatico ni navegacion de juego todavia.
La salida frontal hacia Poas comienza en ese mismo punto interior (1508,308).
Su primer tramo queda oculto hasta la boca frontal (1508,364); luego sigue
visible hasta el acceso norte de Poas (1508,668), rodea el crater por el
terreno exterior izquierdo y llega al punto sur compartido (1510,1160).
El rodeo no representa un camino dibujado y no modifica la imagen original.

El selector incluye tambien Bahia Ballena - Cerro Chirripo,
Cerro Chirripo - Volcan Poas y Volcan Poas - Volcan Arenal.
Estas tres conexiones usan la via horizontal al sur de las zonas y los
accesos verticales existentes: los caminos de media altura terminan contra
las montanas o el crater y no son una ruta continua.
Sus puntos compartidos son Bahia Ballena (380,1140), Chirripo (910,1150),
Poas (1510,1160) y Arenal (2110,1100). El acceso final a Arenal sigue el
camino oscuro, sin subir por el volcan. No contienen tramos ocultos.

Las conexiones Bahia Ballena - Manuel Antonio, Cerro Chirripo - Tortuguero
y Volcan Poas - Tamarindo conservan los puntos de origen de las conexiones
centrales y siguen sus accesos hacia el sur. Sus destinos son Manuel Antonio
(330,1450), Tortuguero (910,1526) y Tamarindo (1510,1440).
El acceso a Tortuguero une los dos tramos de tierra por la pequena separacion
de cesped entre ellos, sin cruzar los canales. Estas conexiones son visibles
durante todo el recorrido y no modifican la imagen del mapa.

El selector completa las 14 conexiones visuales con Braulio Carrillo - Volcan
Arenal, Manuel Antonio - Tortuguero, Tortuguero - Tamarindo y Volcan Arenal -
Cerro de la Muerte. Braulio - Arenal rodea el volcan por el cesped del lado
izquierdo y bordea el letrero antes de llegar al acceso sur, sin ocultar al mono.
Las dos conexiones inferiores regresan por los accesos a la via horizontal
de y=1230: no cruzan el agua ni el letrero de Hoteles.
El destino de Cerro de la Muerte (1840,1510) esta en el camino oscuro al pie
de la elevacion, no en la plataforma de las antenas, que no tiene una subida
dibujada. Se conservan los puntos compartidos de las zonas ya incorporadas.
Las 14 conexiones comparten un unico punto por zona, incluidos Zurqui y Poas.
El selector "Ida / Vuelta" permite probar cada conexion en ambos sentidos.
"Ida" va de la primera zona del nombre a la segunda; "Vuelta" hace lo contrario.
Cambiar el sentido reinicia la prueba en el extremo correspondiente, sin
iniciar movimiento automaticamente. El selector queda bloqueado durante el
movimiento y se habilita al terminar o detener el recorrido.
`Connection.reversed()` intercambia las zonas, invierte los puntos y transforma
cada tramo oculto s en N - s, donde N es la cantidad de puntos. Se preparan ambos
sentidos al cargar el panel, sin duplicar conexiones en el JSON. Se conservan
la velocidad y las imagenes originales del JSON y de los sprites.
El selector incluye ademas "Monteverde - Zurqui - Poas (continuo)", una prueba
fija que une dos conexiones existentes con `Connection.followedBy()`.
La union exige que coincidan la zona y las coordenadas del enlace; si no,
informa el error y no crea el recorrido. El punto compartido se incluye una
sola vez y se desplazan los numeros de tramos ocultos de la segunda conexion.
El animador recibe una sola polilinea: mantiene la velocidad, no reinicia
la animacion en Zurqui y conserva el ocultamiento durante todo el tunel.
Tambien puede probarse en vuelta. Las 14 conexiones originales del JSON no
se alteran ni se duplican. No hay BFS, seleccion automatica de rutas ni
decisiones del servidor; sigue pendiente el calculo de rutas de juego.

## Niebla de guerra

El mapa esta dividido en 12 zonas rectangulares de 600 de ancho, en 4 columnas
y 3 filas (las de la fila inferior miden 592 de alto). Sus limites estan en
`src/main/resources/map/zones.json`, no en el codigo. Cada punto de inicio y
final de las conexiones esta dentro del rectangulo de su zona; por eso Bahia
Ballena usa (380,1140), en la arena sobre el camino, y baja por x=380 hasta y=1230.

`FogOfWar.paint` cubre de negro todo el mapa excepto la zona visible, usando
`Area.subtract`. Para que el borde no sea recto, dentro de la zona dibuja
anillos redondeados cada vez mas transparentes (`FOG_EDGE_WIDTH`,
`FOG_EDGE_STEPS` y `FOG_EDGE_ARC` en `UIConstants`). La niebla se dibuja encima
del mapa, de las rutas y del animal, sin modificar ninguna imagen.

Las instrucciones indican que el jugador ve una sola zona a la vez y que la
visibilidad la decide el servidor. Mientras no exista la conexion, el selector
"Sin niebla (todo el mapa)" / zona permite probarla manualmente; la primera
opcion conserva las pruebas de rutas sobre el mapa completo.

## Clics: seleccion y comandos para la integracion

`GameWindow` crea un `GameController` y lo conecta al `MapPanel`.
El clic izquierdo selecciona un animal propio vivo; Ctrl + clic izquierdo
agrega o quita un miembro. Un clic izquierdo en espacio vacio limpia la
seleccion, salvo que se mantenga Ctrl. La seleccion se resalta en amarillo.
El clic derecho sobre otro animal visible solicita ATTACK; sobre espacio
libre solicita MOVE_ANIMALS. No se puede atacar un animal propio.
Los clics fuera del mapa o de la zona mostrada no producen comandos.
Si varios animales estan cerca, se toma el mas cercano dentro de
`ANIMAL_CLICK_RADIUS`, usando distancias al cuadrado, sin raiz cuadrada.

El controlador serializa con Gson los sobres `type` / `payload`:

```json
{"type":"MOVE_ANIMALS","payload":{"animalIds":[1,2],"targetX":910.0,"targetY":316.0}}
{"type":"ATTACK","payload":{"animalIds":[1,2],"targetId":21}}
{"type":"VIEW_ZONE","payload":{"zone":"Monteverde"}}
```

Los destinos usan coordenadas del mapa original, no de la ventana.
Se conservan decimales para no perder precision al deshacer la escala.
El integrador debe confirmar que el receptor admite numeros JSON decimales.
El cambio de zona solo ofrece los nombres recibidos en `visibleZones`;
solicitar una zona no la revela hasta recibir `selectedZone` del servidor.
Los cambios de estado no reenvian comandos y eliminan de la seleccion
los animales muertos, ausentes o que ya no estan en la zona mostrada.

### Punto de conexion para el integrante de red

No se implementaron sockets ni se modificaron `NetworkClient` o `MessageSender`.
El integrador obtiene `gameWindow.getController()` y configura:

```java
controller.setMessageSender(conexion::enviarLineaJson);
```

El fragmento anterior es un esquema: `conexion` y `enviarLineaJson` deben
ser el objeto y metodo reales de envio, con su manejo de errores.
La funcion recibe un objeto JSON
serializado, sin salto de linea. La capa de red agrega el delimitador
y utiliza UTF-8. Entregar una orden a esta funcion no confirma que el
servidor la haya aceptado.

Con cada GAME_STARTED o STATE_UPDATE, convertir los animales recibidos
a `ClickableAnimal(id, x, y, alive)` y llamar en el hilo de Swing:

```java
SwingUtilities.invokeLater(() -> controller.updateState(
        ownAnimals, visibleAnimals, visibleZoneNames, selectedZoneName));
```

Son las listas propias y visibles, los nombres de `visibleZones` y el
nombre de `selectedZone`, no los identificadores internos de `zones.json`.
El controlador guarda copias de las listas y valida IDs no repetidos,
coordenadas finitas y nombres de zonas conocidos.
`ClickableAnimal` solo contiene datos para identificar objetivos de clic;
no reemplaza al DTO completo ni al modelo del juego.

El dibujo de los sprites de la partida debe usar las mismas posiciones
que se entreguen al controlador. Esta etapa no implementa ese renderizado:
el mono y las rutas existentes siguen siendo una demostracion local,
sin IDs inventados, y no representan a los animales del servidor.
Hasta recibir datos reales, los clics indican que faltan datos.
Si falta la funcion de envio, se informa que la orden no fue enviada.
El cliente no calcula ataques, rutas ni cambios de energia por hacer clic.

Pruebas de esta parte:

```shell
mvn -Dtest=GameControllerTest,MapPanelTest test
```