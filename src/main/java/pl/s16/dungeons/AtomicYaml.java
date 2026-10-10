package pl.s16.dungeons;

import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.io.IOException;

/** Manifest/schedule changes are written to a flushed temporary file before rename. */
final class AtomicYaml {
    private AtomicYaml() {}
    static void write(YamlConfiguration data, File file) throws IOException {
        DurableAtomicText.write(file.toPath(),data.saveToString());
    }
}
