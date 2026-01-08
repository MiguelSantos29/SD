package src.Testes;

import src.Stub;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class Tester {
    private static final int NUM_CLIENTES = 50;
    private static final int OPS_POR_CLIENTE = 100;



    public static void main(String[] args) throws InterruptedException {
        String[] ListaDeCompras = {"Maca", "Banana", "Laranja", "TV", "Radio"};
        System.out.println(">>> A iniciar Teste de Carga...");
        Set<String> listaProdutos = new HashSet<>();
        for (int i=0; i<ListaDeCompras.length; i++)
            listaProdutos.add(ListaDeCompras[i]);

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
                        if (r.nextInt()%100 < 99) {
                            stub.inserir(System.currentTimeMillis(), ListaDeCompras[r.nextInt(ListaDeCompras.length)], 1, 10);
                        } else {
                            stub.consultarTotal(ListaDeCompras[r.nextInt(ListaDeCompras.length)]);
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
        // Avançar um dia e consultar eventos
        try (Stub stub = new Stub("localhost", 12345)) {
            stub.avancarDia();
            List<Stub.ResumoVenda> lista = stub.consultarEventos(listaProdutos, 1);

            System.out.println("--- Resumo de Eventos ---");
            System.out.printf("%-15s | %-10s | %-10s | %-10s%n", "Produto", "Preço", "Qtd Total", "Nº Vendas");
            System.out.println("---------------------------------------------------------");

            for(Stub.ResumoVenda v : lista) {
                System.out.printf("%-15s | %-10d | %-10d | %-10d%n", v.produto, v.preco, v.qtdTotal, v.numVendas);
            }
        } catch (Exception e) {
            System.out.println("Erro ao avançar dia: " + e.getMessage());
        }

        long fim = System.currentTimeMillis();
        double tempo = (fim - inicio) / 1000.0;

        System.out.println("\n=== RESULTADOS ===");
        System.out.println("Tempo Total: " + tempo + "s");
        System.out.println("Pedidos Sucesso: " + sucessos.get());
        System.out.println("Pedidos Erro: " + erros.get());
        System.out.println("Throughput: " + (sucessos.get() / tempo) + " ops/sec");
    }
}