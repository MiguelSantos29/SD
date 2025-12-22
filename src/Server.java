package src;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Server {
    private static final int PORTA = 12345;
    private static final int DIAS = 5;

    public static void main(String[] args) {
        try {
            DataBase db = new DataBase(DIAS);
            // Abrir o Socket
            ServerSocket ss = new ServerSocket(PORTA);
            System.out.println("Servidor iniciado na porta " + PORTA + "...");

            while (true) {
                // Aceitar Cliente
                Socket socket = ss.accept();
                System.out.println("Novo cliente conectado: " + socket.getInetAddress());

                // (Thread-per-connection)
                Thread worker = new Thread(new ServerWorker(socket, db));
                worker.start();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static class ServerWorker implements Runnable {
        private final Socket socket;
        private final DataBase db;

        public ServerWorker(Socket socket, DataBase db) {
            this.socket = socket;
            this.db = db;
        }

        @Override
        public void run() {
            try (TaggedConnection conn = new TaggedConnection(socket)) {

                // --- CORREÇÃO: O ciclo while(true) é obrigatório aqui! ---
                while (true) {
                    // Bloqueia aqui à espera do próximo pedido do cliente
                    TaggedConnection.Frame req = conn.receive();
                    new Thread(() -> {
                        try {
                            DataInputStream dis = new DataInputStream(new ByteArrayInputStream(req.data));
                            ByteArrayOutputStream baos = new ByteArrayOutputStream();
                            DataOutputStream dos = new DataOutputStream(baos);
                            int op = dis.readByte();

                            switch (op) {
                                case 0: // Auth
                                    String uAuth = dis.readUTF();
                                    String pAuth = dis.readUTF();
                                    boolean authOk = db.autenticarUtilizador(uAuth, pAuth);

                                    dos.writeByte(3);
                                    dos.writeBoolean(authOk);
                                    dos.writeUTF(authOk ? "Login com sucesso" : "Utilizador ou password errados");
                                    break;

                                case 1: // Inserir
                                    long ts = dis.readLong();
                                    String prod = dis.readUTF();
                                    int qtd = dis.readInt();
                                    int preco = dis.readInt();

                                    db.inserir(ts, prod, qtd, preco);

                                    dos.writeByte(3);
                                    dos.writeBoolean(true);
                                    dos.writeUTF("Venda registada.");
                                    break;

                                case 2: // Consultar Total
                                    String pCons = dis.readUTF();
                                    long total = db.consultarTotalVendas(pCons);

                                    dos.writeByte(3);
                                    dos.writeBoolean(true);
                                    dos.writeUTF(String.valueOf(total));
                                    break;

                                case 3: // Avançar Dia
                                    db.startNewDay();
                                    dos.writeByte(3);
                                    dos.writeBoolean(true);
                                    dos.writeUTF("Novo dia iniciado.");
                                    break;

                                case 4: // Agregação Avançada
                                    String pAg = dis.readUTF();
                                    int diasAg = dis.readInt();
                                    int tipoAg = dis.readInt();
                                    double res = db.consultarAgregacao(pAg, diasAg, tipoAg);

                                    dos.writeByte(3);
                                    dos.writeBoolean(true);
                                    dos.writeUTF(String.valueOf(res));
                                    break;

                                case 5: // Consultar Eventos
                                    int nProds = dis.readInt();
                                    Set<String> prods = new HashSet<>();
                                    for (int i = 0; i < nProds; i++) prods.add(dis.readUTF());
                                    int dia = dis.readInt();

                                    List<Venda> lista = db.consultarEventos(prods, dia);

                                    dos.writeByte(6); // Tag de Lista
                                    dos.writeBoolean(true);
                                    dos.writeUTF("Lista obtida");
                                    dos.writeInt(lista.size());
                                    for (Venda v : lista) {
                                        dos.writeLong(v.timestamp);
                                        dos.writeUTF(v.produto);
                                        dos.writeInt(v.quantidade);
                                        dos.writeInt(v.preco);
                                    }
                                    break;
                                case 7: // Registar (Ficou com o 7 para não conflitar com o Stub antigo)
                                    String uReg = dis.readUTF();
                                    String pReg = dis.readUTF();
                                    boolean regOk = db.registarUtilizador(uReg, pReg);

                                    dos.writeByte(3);
                                    dos.writeBoolean(regOk);
                                    dos.writeUTF(regOk ? "Registado com sucesso" : "Utilizador ja existe");
                                    break;
                                case 8: // Req 5: Vendas Simultâneas (Bloqueante)
                                    String p1 = dis.readUTF();
                                    String p2 = dis.readUTF();

                                    // Isto vai BLOQUEAR a thread até acontecer ou o dia acabar
                                    boolean simResult = db.esperarVendasSimultaneas(p1, p2);

                                    dos.writeByte(3); // Tag Resposta
                                    dos.writeBoolean(simResult);
                                    dos.writeUTF(simResult ? "Vendas detetadas!" : "Dia acabou sem vendas simultaneas.");
                                    break;

                                case 9: // Req 5: Vendas Consecutivas (Bloqueante)
                                    int nConsec = dis.readInt();

                                    // Bloqueia até acontecer
                                    String prodConsec = db.esperarVendasConsecutivas(nConsec);

                                    dos.writeByte(3);
                                    if (prodConsec != null) {
                                        dos.writeBoolean(true);
                                        dos.writeUTF(prodConsec); // Retorna o nome do produto
                                    } else {
                                        dos.writeBoolean(false);
                                        dos.writeUTF("Dia acabou sem sequencia.");
                                    }
                                    break;

                                default:
                                    dos.writeByte(3);
                                    dos.writeBoolean(false);
                                    dos.writeUTF("Operacao desconhecida");
                            }

                            // Enviar a Resposta
                            dos.flush();
                            byte[] responseData = baos.toByteArray();

                            // Envia a resposta mantendo o ID original (req.tag)
                            conn.send(req.tag, responseData);
                        } catch (IOException | InterruptedException d) {
                            System.out.println("Erro ao processar pedido" + d.getMessage());
                        }
                    }).start();
                }
            } catch (EOFException e) {
                System.out.println("Cliente desligou-se (EOF).");
            } catch (IOException e) {
                System.out.println("Erro na conexão: " + e.getMessage());
            }
        }
    }
}
