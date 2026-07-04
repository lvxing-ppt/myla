package com.myla.gateway.channel;

import com.myla.gateway.core.spi.CommunicationChannel;
import com.myla.gateway.core.context.DriverConfig;
import com.myla.gateway.core.model.ConnectionError;
import lombok.extern.slf4j.Slf4j;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.function.Consumer;

@Slf4j
public class TcpChannel implements CommunicationChannel {
    private ServerSocket serverSocket;
    private Socket socket;
    private InputStream in;
    private OutputStream out;
    private Consumer<byte[]> messageListener;
    private Consumer<ConnectionError> errorListener;
    private volatile boolean running;

    @Override public String getChannelType() { return "TCP"; }

    @Override
    public void open(DriverConfig.ChannelConfig config) {
        try {
            serverSocket = new ServerSocket(config.getPort());
            running = true;
            log.info("TCP channel listening on port {}", config.getPort());
            new Thread(() -> {
                while (running) {
                    try {
                        socket = serverSocket.accept();
                        in = socket.getInputStream();
                        out = socket.getOutputStream();
                        byte[] buf = new byte[65536]; int n;
                        while (running && (n = in.read(buf)) > 0) {
                            byte[] data = new byte[n];
                            System.arraycopy(buf, 0, data, 0, n);
                            if (messageListener != null) messageListener.accept(data);
                        }
                    } catch (IOException e) {
                        if (running && errorListener != null) {
                            ConnectionError err = new ConnectionError();
                            err.setChannelType("TCP"); err.setMessage(e.getMessage());
                            errorListener.accept(err);
                        }
                    }
                }
            }, "tcp-chan").start();
        } catch (IOException e) {
            throw new RuntimeException("TCP channel open failed on port " + config.getPort(), e);
        }
    }

    @Override public void close() {
        running = false;
        try { if (in != null) in.close(); } catch (IOException ignored) {}
        try { if (out != null) out.close(); } catch (IOException ignored) {}
        try { if (socket != null) socket.close(); } catch (IOException ignored) {}
        try { if (serverSocket != null) serverSocket.close(); } catch (IOException ignored) {}
    }

    @Override public boolean isOpen() { return running; }
    @Override
    public void send(byte[] data) {
        try { out.write(data); out.flush(); }
        catch (IOException e) { throw new RuntimeException("Send failed", e); }
    }
    @Override public void setMessageListener(Consumer<byte[]> l) { this.messageListener = l; }
    @Override public void setErrorListener(Consumer<ConnectionError> l) { this.errorListener = l; }
}
