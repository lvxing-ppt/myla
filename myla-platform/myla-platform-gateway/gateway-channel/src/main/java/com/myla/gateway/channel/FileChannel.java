package com.myla.gateway.channel;

import com.myla.gateway.core.spi.CommunicationChannel;
import com.myla.gateway.core.context.DriverConfig;
import com.myla.gateway.core.model.ConnectionError;
import lombok.extern.slf4j.Slf4j;
import java.io.IOException;
import java.nio.file.*;
import java.util.Set;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.regex.Pattern;

@Slf4j
public class FileChannel implements CommunicationChannel {
    private Consumer<byte[]> messageListener;
    private Consumer<ConnectionError> errorListener;
    private volatile boolean running;
    private ScheduledExecutorService scheduler;
    private final Set<String> processed = ConcurrentHashMap.newKeySet();

    @Override public String getChannelType() { return "FILE"; }

    @Override
    public void open(DriverConfig.ChannelConfig c) {
        running = true;
        scheduler = Executors.newSingleThreadScheduledExecutor();
        Path dir = Paths.get(c.getDirectory());
        try { Files.createDirectories(dir); } catch (IOException e) { throw new RuntimeException(e); }
        scheduler.scheduleWithFixedDelay(() -> {
            try {
                Pattern p = Pattern.compile(c.getFilePattern() != null ? c.getFilePattern() : ".*\\.txt");
                try (DirectoryStream<Path> s = Files.newDirectoryStream(dir)) {
                    for (Path f : s) {
                        String fn = f.getFileName().toString();
                        if (p.matcher(fn).matches() && processed.add(fn)) {
                            if (messageListener != null) messageListener.accept(Files.readAllBytes(f));
                            Path arch = Paths.get(c.getDirectory(), "archive", fn);
                            Files.createDirectories(arch.getParent());
                            Files.move(f, arch, StandardCopyOption.REPLACE_EXISTING);
                        }
                    }
                }
            } catch (IOException e) {
                if (running && errorListener != null) {
                    ConnectionError err = new ConnectionError();
                    err.setChannelType("FILE"); err.setMessage(e.getMessage());
                    errorListener.accept(err);
                }
            }
        }, 0, c.getPollIntervalMs(), TimeUnit.MILLISECONDS);
    }

    @Override public void close() { running = false; if (scheduler != null) scheduler.shutdown(); }
    @Override public boolean isOpen() { return running; }
    @Override public void send(byte[] data) { throw new UnsupportedOperationException("FileChannel read-only"); }
    @Override public void setMessageListener(Consumer<byte[]> l) { this.messageListener = l; }
    @Override public void setErrorListener(Consumer<ConnectionError> l) { this.errorListener = l; }
}
