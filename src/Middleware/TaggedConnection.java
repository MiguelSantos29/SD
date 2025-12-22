package src.Middleware;
import java.io.*;
import java.net.Socket;
import java.util.concurrent.locks.ReentrantLock;

public class TaggedConnection implements AutoCloseable {
    private final Socket socket;
    private final DataInputStream dis;
    private final DataOutputStream dos;
    private final ReentrantLock sendLock = new ReentrantLock();
    private final ReentrantLock recvLock = new ReentrantLock();

    public static class Frame {
        public final int tag; // Isto será o Request ID
        public final byte[] data;

        public Frame(int tag, byte[] data) {
            this.tag = tag;
            this.data = data;
        }
    }

    public TaggedConnection(Socket socket) throws IOException {
        this.socket = socket;
        this.dis = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
        this.dos = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
    }

    public void send(Frame frame) throws IOException {
        send(frame.tag, frame.data);
    }

    public void send(int tag, byte[] data) throws IOException {
        sendLock.lock();
        try {
            // Protocolo: [4 bytes Tamanho] [4 bytes Tag/ID] [N bytes Dados]
            dos.writeInt(4 + data.length); // Tamanho total do conteúdo (Tag + Data)
            dos.writeInt(tag);
            dos.write(data);
            dos.flush();
        } finally {
            sendLock.unlock();
        }
    }

    // O método receive que o Demultiplexer chama na thread
    public Frame receive() throws IOException {
        recvLock.lock();
        try {
            int length = dis.readInt(); // Lê tamanho total
            int tag = dis.readInt();    // Lê a Tag (Request ID)

            // O tamanho dos dados é: TamanhoTotal - 4 bytes da tag
            byte[] data = new byte[length - 4];
            dis.readFully(data);

            return new Frame(tag, data);
        } finally {
            recvLock.unlock();
        }
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }
}

