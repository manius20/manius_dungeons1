package pl.s16.dungeons;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Objects;

/**
 * Write a complete UTF-8 manifest through a fully flushed temp file.
 * Directory fsync is OS-dependent, so this is not a substitute for server/world backups.
 */
final class DurableAtomicText {
    private DurableAtomicText() {}
    static void write(Path destination,String content) throws IOException {
        Objects.requireNonNull(destination);Objects.requireNonNull(content);
        Path absolute=destination.toAbsolutePath();
        Path parent=absolute.getParent();
        Files.createDirectories(parent);
        if(Files.isSymbolicLink(absolute))throw new IOException("Nie zapisuje przez symlink: "+absolute);
        Path temp=Files.createTempFile(parent,absolute.getFileName().toString(),".tmp");
        try {
            try(FileChannel channel=FileChannel.open(temp,StandardOpenOption.WRITE,StandardOpenOption.TRUNCATE_EXISTING)) {
                ByteBuffer data=StandardCharsets.UTF_8.encode(content);
                while(data.hasRemaining())channel.write(data);
                channel.force(true);
            }
            try {Files.move(temp,absolute,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
            catch(AtomicMoveNotSupportedException ex){Files.move(temp,absolute,StandardCopyOption.REPLACE_EXISTING);}
        }finally{Files.deleteIfExists(temp);}
    }
}
