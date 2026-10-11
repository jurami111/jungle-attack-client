package cr.ac.tec.jungleattack.client.view.utils; // es parte de las utilidades de la vista del juego 

import java.awt.image.BufferedImage; // imagen que java puede dibujar
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.batik.transcoder.TranscoderException;
import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.image.ImageTranscoder;

public final class SpriteCache { // clase que maneja la caché de sprites del juego
    public static final List<String> ASSET_NAMES = List.of( // nombres de los archivos SVG de los assets del juego
            "Yiguirro-A.svg",
            "Tortuga-marina.svg",
            "Serpiente-Terciopelo.svg",
            "Rana-verde.svg",
            "Peresozo-A.svg",
            "Mono-titi (1).svg",
            "Miroposa-morpho.svg",
            "Mapa.svg");

    private final Map<String, BufferedImage> images; // mapa que almacena las imágenes cargadas
    private final Map<String, String> loadErrors; // mapa que almacena los errores de carga de los assets

    public SpriteCache() { // constructor que carga todas las imágenes SVG en la caché
        Map<String, BufferedImage> loaded = new HashMap<>(); //  loaded mapa temporal para cargar las imágenes 
        Map<String, String> errors = new HashMap<>();
        for (String name : ASSET_NAMES) {
            SvgLoadResult result = loadSvg(name); // carga el SVG y devuelve el resultado
            if (result.image().isPresent()) {
                loaded.put(name, result.image().get()); 
            } else {
                errors.put(name, result.error().orElse("Unknown SVG loading error"));
            }
        }
        images = Map.copyOf(loaded);
        loadErrors = Map.copyOf(errors);
    }

    public Optional<BufferedImage> getImage(String name) { // obtiene la imagen del sprite desde la caché
        return Optional.ofNullable(images.get(name));
    }

    public Map<String, String> getLoadErrors() { 
        return loadErrors;
    }

    private static SvgLoadResult loadSvg(String name) { // carga un archivo SVG y devuelve el resultado (imagen o error)
        URL resource = SpriteCache.class.getResource("/assets/" + name);
        if (resource == null) { // si no se encuentra el recurso SVG, devuelve un error
            return new SvgLoadResult(
                    Optional.empty(),
                    Optional.of("SVG resource not found: /assets/" + name)); //se devuelve un mensaje de error
        }

        try (InputStream stream = resource.openStream()) { // se encarga de cargar, validar, tranformar el SVG en una imagen BufferedImage
            BufferedImageTranscoder transcoder = new BufferedImageTranscoder();
            TranscoderInput input = new TranscoderInput(stream);
            input.setURI(resource.toExternalForm());
            transcoder.transcode(input, null); 
            if (transcoder.image == null) { 
                return new SvgLoadResult( 
                        Optional.empty(),
                        Optional.of("SVG produced no image: " + name));
            }
            return new SvgLoadResult(Optional.of(transcoder.image), Optional.empty());
        } catch (IOException | TranscoderException exception) {
            return new SvgLoadResult(
                    Optional.empty(),
                    Optional.of("Could not load SVG " + name + ": " + exception.getMessage()));
        }
    }

    private record SvgLoadResult(Optional<BufferedImage> image, Optional<String> error) { 
    }//esta da el resultado de la carga del SVG, ya sea la imagen o el error

    private static final class BufferedImageTranscoder extends ImageTranscoder {
        private BufferedImage image;// almacena la imagen resultante del transcoder

        @Override//
        public BufferedImage createImage(int width, int height) { // crea una nueva imagen BufferedImage con las dimensiones especificadas
            return new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        }

        @Override
        public void writeImage(BufferedImage image, TranscoderOutput output) {
            this.image = image; // guarda la imagen resultante del transcoder
        }
    }
}