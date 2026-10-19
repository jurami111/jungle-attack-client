package cr.ac.tec.jungleattack.client.view.utils; // es parte de las utilidades de la vista del juego 

import java.awt.image.BufferedImage; // imagen que java puede dibujar
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;// lector de flujo de entrada para leer el manifiesto JSON
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.batik.transcoder.TranscoderException;
import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.image.ImageTranscoder;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.Strictness;

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
    private final Map<String, SpriteAnimation> animations;
    private static final String MANIFEST = "normalized/sequences.json";

    public SpriteCache() { // constructor que carga todas las imágenes SVG en la caché
        this(SpriteCache.class.getResourceAsStream("/assets/" + MANIFEST));
    }

    SpriteCache(InputStream manifestStream) {
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
        Map<String, SpriteAnimation> prepared = new HashMap<>();
        loadAnimations(manifestStream, loaded, errors, prepared);
        images = Map.copyOf(loaded);
        loadErrors = Map.copyOf(errors);
        animations = Map.copyOf(prepared);
    }

    public Optional<BufferedImage> getImage(String name) { // obtiene la imagen del sprite desde la caché
        return Optional.ofNullable(images.get(name));
    }

    public Map<String, String> getLoadErrors() { 
        return loadErrors;
    }

    public Optional<SpriteAnimation> getAnimation(String animalId) {// obtiene la animación del sprite desde la caché
        return Optional.ofNullable(animations.get(animalId));
    }

    private static void loadAnimations(InputStream stream,// carga las animaciones de los sprites desde el manifiesto JSON
            Map<String, BufferedImage> loaded, Map<String, String> errors,
            Map<String, SpriteAnimation> prepared) {
        if (stream == null) {
            errors.put(MANIFEST, "Animation manifest not found: /assets/" + MANIFEST);
            return;
        }

        AnimationManifest manifest;
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) { // lector de flujo de entrada para leer el manifiesto JSON
            manifest = new GsonBuilder().setStrictness(Strictness.STRICT).create()
                    .fromJson(reader, AnimationManifest.class);//
        } catch (IOException | JsonParseException exception) {
            errors.put(MANIFEST, "Could not read animation manifest: " + exception.getMessage());
            return;
        }
        if (manifest == null || manifest.canvasWidth() == null || manifest.canvasWidth() <= 0
                || manifest.canvasHeight() == null || manifest.canvasHeight() <= 0
                || manifest.animals() == null || manifest.animals().isEmpty()) {
            errors.put(MANIFEST, "Manifest requires positive canvas dimensions and a nonempty animal list");
            return;
        }

        Map<String, Integer> idCounts = new HashMap<>();
        for (AnimalDefinition animal : manifest.animals()) {
            if (animal != null && animal.id() != null) {
                idCounts.merge(animal.id(), 1, Integer::sum);
            }
        }
        for (int i = 0; i < manifest.animals().size(); i++) {
            AnimalDefinition animal = manifest.animals().get(i);
            String key = MANIFEST + "#animal-" + (i + 1);
            String validationError = validateAnimal(animal);
            if (validationError != null) {
                errors.put(key, validationError);
                continue;
            }
            if (idCounts.get(animal.id()) > 1) {
                errors.put(key, "Duplicate animal id: " + animal.id());
                continue;
            }
            List<BufferedImage> frames = new ArrayList<>();// almacena los fotogramas cargados del animal
            boolean complete = true;
            for (FrameDefinition frame : animal.frames()) {
                String name = "normalized/" + frame.file();
                BufferedImage image = loaded.get(name);
                if (image == null) {
                    SvgLoadResult result = loadSvg(name);// carga la imagen SVG correspondiente al nombre del archivo
                    if (result.image().isEmpty()) {
                        errors.put(name, result.error().orElse("Unknown SVG loading error"));
                        complete = false;
                        continue;
                    }
                    image = result.image().get();
                    if (image.getWidth() != manifest.canvasWidth()
                            || image.getHeight() != manifest.canvasHeight()) {
                        errors.put(name, "SVG dimensions do not match the manifest canvas");
                        complete = false;
                        continue;
                    }
                    loaded.put(name, image);
                }
                frames.add(image);
            }
            if (!complete) {
                errors.put(key, "Animation unavailable because one or more frames could not load: " + animal.id());
                continue;
            }
            List<BufferedImage> ordered = new ArrayList<>();
            for (int number : animal.sequence()) {
                ordered.add(frames.get(number - 1));
            }
            prepared.put(animal.id(), new SpriteAnimation(
                    frames.get(animal.idleFrame() - 1), ordered, animal.frameDurationMs()));
        }
    }

    private static String validateAnimal(AnimalDefinition animal) {
        if (animal == null || animal.id() == null || animal.id().isBlank()) {
            return "Animal requires a nonblank id";
        }
        if (animal.frames() == null || animal.frames().isEmpty()
                || animal.sequence() == null || animal.sequence().isEmpty()) {
            return "Animal requires frames and a nonempty sequence: " + animal.id();
        }
        if (animal.frameDurationMs() == null || animal.frameDurationMs() <= 0) {
            return "Frame duration must be positive: " + animal.id();
        }
        if (!isFrameNumber(animal.idleFrame(), animal.frames().size())) {
            return "Invalid idle frame: " + animal.id();
        }
        for (Integer number : animal.sequence()) {
            if (!isFrameNumber(number, animal.frames().size())) {
                return "Invalid sequence frame: " + animal.id();
            }
        }
        var names = new HashSet<String>();
        for (FrameDefinition frame : animal.frames()) {
            if (frame == null || frame.file() == null
                    || !frame.file().matches("[A-Za-z0-9_-]+/[A-Za-z0-9_-]+\\.svg")
                    || !names.add(frame.file())) {
                return "Invalid or duplicate frame file: " + animal.id();
            }
        }
        return null;
    }

    private static boolean isFrameNumber(Integer number, int count) {
        return number != null && number >= 1 && number <= count;
    }

    private record AnimationManifest(Integer canvasWidth, Integer canvasHeight,
            List<AnimalDefinition> animals) {
    }

    private record AnimalDefinition(String id, Integer idleFrame, Integer frameDurationMs,
            List<Integer> sequence, List<FrameDefinition> frames) {
    }

    private record FrameDefinition(String file) {
    }

    private static SvgLoadResult loadSvg(String name) { // carga un archivo SVG y devuelve el resultado (imagen o error)
        URL resource = SpriteCache.class.getResource("/assets/" + name);
        if (resource == null) { // si no se encuentra el recurso SVG, devuelve un error
            return new SvgLoadResult(
                    Optional.empty(),
                    Optional.of("SVG resource not found: /assets/" + name)); //se devuelve un mensaje de error
        }

        try (InputStream stream = resource.openStream()) { // se encarga de cargar, validar, tranformar el SVG en una imagen BufferedImage
            BufferedImageTranscoder transcoder = new BufferedImageTranscoder();// se crea una nuevo convertido 
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