package src;

import src.Middleware.Demultiplexer;
import src.Middleware.TaggedConnection;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class Stub implements AutoCloseable {
    private final Demultiplexer demux;
    private final Random geradorIds = new Random();

    public Stub(String host, int port) throws IOException {
        Socket s = new Socket(host, port);
        // Usa a TaggedConnection atualizada que sabe lidar com Frames/Tags
        TaggedConnection tc = new TaggedConnection(s);
        this.demux = new Demultiplexer(tc);
        this.demux.start();
    }

    // Método genérico para enviar pedido e esperar resposta
    private DataInputStream sendAndReceive(int opCode, StreamWriter writer) throws Exception {
        // Preparar os dados da aplicação (A "Carta")
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);

        dos.writeByte(opCode); // Escreve o tipo de operação (0, 1, 2...)
        if (writer != null) writer.write(dos); // Escreve os argumentos
        dos.flush();

        byte[] payload = baos.toByteArray();

        // Enviar via Demultiplexer (O "Envelope")
        int reqId = geradorIds.nextInt(); // Gera ID único para este pedido
        demux.send(reqId, payload);

        // Bloquear à espera da resposta com este ID
        byte[] responseData = demux.receive(reqId);

        // Devolver stream para leitura
        return new DataInputStream(new ByteArrayInputStream(responseData));
    }

    // --- MÉTODOS DE NEGÓCIO ---

    public String registar(String user, String pass) throws Exception {
        DataInputStream dis = sendAndReceive(7, dos -> {
            dos.writeUTF(user);
            dos.writeUTF(pass);
        });
        return lerRespostaSimples(dis);
    }

    public String autenticar(String user, String pass) throws Exception {
        DataInputStream dis = sendAndReceive(0, dos -> {
            dos.writeUTF(user);
            dos.writeUTF(pass);
        });
        return lerRespostaSimples(dis);
    }

    public String inserir(long ts, String prod, int qtd, int preco) throws Exception {
        DataInputStream dis = sendAndReceive(1, dos -> {
            dos.writeLong(ts);
            dos.writeUTF(prod);
            dos.writeInt(qtd);
            dos.writeInt(preco);
        });
        return lerRespostaSimples(dis);
    }

    public String consultarTotal(String prod) throws Exception {
        DataInputStream dis = sendAndReceive(2, dos -> dos.writeUTF(prod));
        return lerRespostaSimples(dis);
    }

    public String avancarDia() throws Exception {
        DataInputStream dis = sendAndReceive(3, null); // Sem argumentos
        return lerRespostaSimples(dis);
    }

    public String agregacao(String prod, int dias, int tipo) throws Exception {
        DataInputStream dis = sendAndReceive(4, dos -> {
            dos.writeUTF(prod);
            dos.writeInt(dias);
            dos.writeInt(tipo);
        });
        return lerRespostaSimples(dis);
    }

    // Criar uma classe auxiliar estática dentro do Stub ou fora
    public static class ResumoVenda {
        public String produto;
        public int preco;
        public int qtdTotal;
        public int numVendas;

        public ResumoVenda(String p, int pr, int q, int n) {
            this.produto = p; this.preco = pr; this.qtdTotal = q; this.numVendas = n;
        }
    }

    // Alterar o método consultarEventos para devolver List<ResumoVenda>
    public List<ResumoVenda> consultarEventos(Set<String> prods, int dia) throws Exception {
        DataInputStream dis = sendAndReceive(5, dos -> {
            dos.writeInt(prods.size());
            for (String p : prods) dos.writeUTF(p);
            dos.writeInt(dia);
        });

        int tag = dis.readByte();
        if (tag != 6) {
            boolean suc = dis.readBoolean();
            String msg = dis.readUTF();
            throw new RuntimeException("Erro servidor: " + msg);
        }

        dis.readBoolean(); // Sucesso
        dis.readUTF();     // Msg

        int numEntradas = dis.readInt();
        List<ResumoVenda> lista = new ArrayList<>();

        for(int i=0; i<numEntradas; i++) {
            String p = dis.readUTF();
            int preco = dis.readInt();
            int qtd = dis.readInt();
            int num = dis.readInt();
            lista.add(new ResumoVenda(p, preco, qtd, num));
        }
        return lista;
    }

    public String esperarSimultaneas(String p1, String p2) throws Exception {
        DataInputStream dis = sendAndReceive(8, dos -> {
            dos.writeUTF(p1);
            dos.writeUTF(p2);
        });
        return lerRespostaSimples(dis);
    }

    public String esperarConsecutivas(int n) throws Exception {
        DataInputStream dis = sendAndReceive(9, dos -> dos.writeInt(n));
        // Tratamento especial porque pode retornar nome do produto
        dis.readByte(); // Tag 3
        boolean suc = dis.readBoolean();
        String msg = dis.readUTF(); // Nome do produto ou msg de erro
        if (!suc) return "Falhou: " + msg;
        return "Sucesso! Produto: " + msg;
    }

    private String lerRespostaSimples(DataInputStream dis) throws IOException {
        dis.readByte(); // Ignorar Tag 3
        boolean suc = dis.readBoolean();
        String msg = dis.readUTF();
        if (!suc) throw new RuntimeException(msg); // Lança erro se falhou
        return msg;
    }

    @Override
    public void close() throws Exception {
        demux.close();
    }

    // Interface funcional auxiliar
    interface StreamWriter { void write(DataOutputStream dos) throws IOException; }
}
