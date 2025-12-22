package src;

import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

public class Tester {
    private static final int NUM_CLIENTES = 50; // Muita carga!
    private static final int OPS_POR_CLIENTE = 200;

    public static void main(String[] args) throws InterruptedException {
        System.out.println(">>> A iniciar Teste de Carga...");

        Thread[] clients = new Thread[NUM_CLIENTES];
        AtomicInteger sucessos = new AtomicInteger(0);
        AtomicInteger erros = new AtomicInteger(0);

        long inicio = System.currentTimeMillis();

        for (int i = 0; i < NUM_CLIENTES; i++) {
            final int id = i;
            clients[i] = new Thread(() -> {
                // Cada thread simula um cliente novo com a sua conexão
                try (Stub stub = new Stub("localhost", 12345)) {

                    // Registar (pode falhar se já existir, ignoramos erro aqui)
                    try { stub.registar("user" + id, "pass"); } catch (Exception _) {}

                    // Login
                    stub.autenticar("user" + id, "pass");

                    Random r = new Random();
                    for (int j = 0; j < OPS_POR_CLIENTE; j++) {
                        if (r.nextBoolean()) {
                            stub.inserir(System.currentTimeMillis(), "Prod"+r.nextInt(10), 1, 10);
                        } else {
                            stub.consultarTotal("Prod"+r.nextInt(10));
                        }
                        sucessos.incrementAndGet();
                    }
                } catch (Exception e) {
                    System.out.println("Erro na thread " + id + ": " + e.getMessage());
                    erros.incrementAndGet();
                }
            });
            clients[i].start();
        }

        // Esperar por todos
        for (Thread t : clients) t.join();

        long fim = System.currentTimeMillis();
        double tempo = (fim - inicio) / 1000.0;

        System.out.println("\n=== RESULTADOS ===");
        System.out.println("Tempo Total: " + tempo + "s");
        System.out.println("Pedidos Sucesso: " + sucessos.get());
        System.out.println("Pedidos Erro: " + erros.get());
        System.out.println("Throughput: " + (sucessos.get() / tempo) + " ops/sec");
    }
}