package src.Testes;

import src.Stub;
import java.io.DataOutputStream;
import java.net.Socket;

public class TesterRobustez {

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== TESTE DE ROBUSTEZ (CLIENTE LENTO/MUDO) ===");
        System.out.println("Cenário: Um cliente envia muitos pedidos mas NUNCA lê as respostas.");
        System.out.println("Objetivo: O buffer TCP enche, a thread do servidor bloqueia no 'write'.");
        System.out.println("Sucesso se: O 'Cliente Bom' conseguir trabalhar normalmente enquanto o 'Mau' está pendurado.\n");

        // 1. Iniciar o "Cliente Mau" (O Sabotador)
        Thread mauCliente = new Thread(() -> {
            try {
                System.out.println("[Mau] A conectar...");
                Socket s = new Socket("localhost", 12345);
                DataOutputStream out = new DataOutputStream(s.getOutputStream());

                // Enviar pedidos infinitamente sem nunca ler nada
                // OpCode 2 = INSERIR (Gera uma resposta simples de ACK)
                int tag = 1;
                System.out.println("[Mau] A bombardear o servidor (sem ler respostas)...");
                while (true) {
                    // Frame: Tag (4 bytes) + Payload
                    // Payload: OpCode (4 bytes) + Args

                    // Frame (Exemplo genérico, adapta se o teu TaggedConnection for diferente):
                    // [4 bytes Tag] [4 bytes OpCode] [8 bytes TS] [UTF Prod] [4 bytes Qtd] [4 bytes Preço]

                    // Vamos enviar lixo estruturado suficiente para encher o buffer
                    out.writeInt(tag++); // Tag
                    out.writeInt(2); // OpCode Inserir
                    out.writeLong(System.currentTimeMillis());
                    out.writeUTF("Lixo");
                    out.writeInt(1);
                    out.writeInt(10);
                    out.flush();

                    // NÃO FAZEMOS in.read()! O buffer do SO vai encher.

                    if (tag % 1000 == 0) {
                        System.out.println("[Mau] Já enviei " + tag + " pedidos e ignorei todas as respostas.");
                    }
                    // Sem sleep para encher rápido!
                }
            } catch (Exception e) {
                System.out.println("[Mau] Morreu (Provavelmente o servidor fechou a conexão): " + e.getMessage());
            }
        });

        mauCliente.start();

        // Dar tempo ao cliente mau para encher os buffers (TCP Window Full)
        System.out.println(">>> A esperar 2 segundos para o buffer encher...");
        Thread.sleep(2000);

        // 2. O "Cliente Bom" tenta trabalhar
        System.out.println("\n>>> [Bom] A tentar conectar e fazer uma operação...");

        try (Stub stub = new Stub("localhost", 12345)) {
            // Tenta login
            String login = stub.autenticar("admin", "admin"); // Ou registar

            // Tenta uma inserção
            stub.inserir(System.currentTimeMillis(), "Banana", 10, 10);

            System.out.println("[Bom] SUCESSO! Consegui inserir uma venda.");
            System.out.println("CONCLUSÃO: O servidor é ROBUSTO. O bloqueio de um cliente não afetou o outro.");

        } catch (Exception e) {
            System.err.println("[Bom] FALHA! O cliente bom não conseguiu comunicar.");
            System.err.println("CAUSA PROVÁVEL: O servidor está a bloquear num Lock global enquanto tenta escrever para o cliente mau.");
            e.printStackTrace();
        }

        System.exit(0);
    }
}
