package studio.save;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/** Shared Jackson {@link ObjectMapper} for .pflow / .pftool files. */
public final class JsonCodec {

    private static final ObjectMapper MAPPER;
    static {
        MAPPER = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .configure(SerializationFeature.INDENT_OUTPUT, true);
    }

    private JsonCodec() {}

    public static <T> T read(Path file, Class<T> type) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            return MAPPER.readValue(in, type);
        }
    }

    public static <T> T read(InputStream in, Class<T> type) throws IOException {
        return MAPPER.readValue(in, type);
    }

    public static void write(Path file, Object value) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        MAPPER.writeValue(file.toFile(), value);
    }
}
