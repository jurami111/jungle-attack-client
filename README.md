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