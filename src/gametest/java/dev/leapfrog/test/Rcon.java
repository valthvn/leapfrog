package dev.leapfrog.test;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/** Minimal Source RCON client, to drive the 26.3 test server from inside the client test. */
final class Rcon implements AutoCloseable {
    private final Socket socket;
    private final DataInputStream in;
    private final OutputStream out;
    private int id;

    Rcon(String host, int port, String password) throws IOException {
        socket = new Socket(host, port);
        socket.setSoTimeout(10_000);
        in = new DataInputStream(socket.getInputStream());
        out = socket.getOutputStream();
        send(3, password);
        read();
    }

    synchronized String cmd(String command) {
        try {
            send(2, command);
            return read();
        } catch (IOException e) {
            throw new RuntimeException("RCON: " + command, e);
        }
    }

    private void send(int type, String body) throws IOException {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buf = ByteBuffer.allocate(14 + b.length).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(10 + b.length).putInt(++id).putInt(type).put(b).put((byte) 0).put((byte) 0);
        out.write(buf.array());
        out.flush();
    }

    private String read() throws IOException {
        byte[] lenBytes = new byte[4];
        in.readFully(lenBytes);
        int len = ByteBuffer.wrap(lenBytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
        byte[] data = new byte[len];
        in.readFully(data);
        return new String(data, 8, Math.max(0, len - 10), StandardCharsets.UTF_8);
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }
}
