package src.Testes;

import src.Stub;

public class TesterConcSeq {

    public static void main(String[] args) {
        System.out.println(">>> A iniciar Testes de Concorrência (Req 5)...");

        // Thread que vai ficar BLOQUEADA à espera
        Thread clienteEspera = new Thread(() -> {
            try (Stub stub = new Stub("localhost", 12345)) {
                try {
                    stub.registar("waiter", "123");
                } catch (Exception e){
                    // Ignorar se já existir
                }
                stub.autenticar("waiter", "123");

                System.out.println("[Waiter] Vou pedir vendas simultâneas de TV e Radio...");
                long inicio = System.currentTimeMillis();

                String res = stub.esperarSimultaneas("TV", "Radio");

                long fim = System.currentTimeMillis();
                System.out.println("[Waiter] DESBLOQUEEI! Resposta: " + res);
                System.out.println("[Waiter] Tempo de espera: " + (fim - inicio) + "ms");

            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        // Thread que vai ficar BLOQUEADA à espera
        Thread clienteEspera1 = new Thread(() -> {
            try (Stub stub = new Stub("localhost", 12345)) {
                try {
                    stub.registar("waiter1", "123");
                } catch (Exception e){
                    // Ignorar se já existir
                }
                stub.autenticar("waiter1", "123");

                System.out.println("[Waiter] Vou pedir vendas consecutivas de Radio...");
                long inicio = System.currentTimeMillis();

                String res = stub.esperarConsecutivas(2);

                long fim = System.currentTimeMillis();
                System.out.println("[Waiter] DESBLOQUEEI! Resposta: " + res);
                System.out.println("[Waiter] Tempo de espera: " + (fim - inicio) + "ms");

            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        clienteEspera.start();
        clienteEspera1.start();

        // Thread Principal (Vendedor)
        try (Stub stub = new Stub("localhost", 12345)) {
            try{
            stub.registar("vendedor", "123");
            } catch (Exception e){
                // Ignorar se já existir
            }
            stub.autenticar("vendedor", "123");

            // Dar tempo para a outra thread chegar ao servidor e bloquear
            Thread.sleep(2000);
            System.out.println("\n[Vendedor] Vou vender TV...");
            stub.inserir(System.currentTimeMillis(), "TV", 1, 500);

            Thread.sleep(2000);
            System.out.println("[Vendedor] Vou vender Radio (Isto deve desbloquear o Waiter)...");
            stub.inserir(System.currentTimeMillis(), "Radio", 1, 100);

            Thread.sleep(2000);
            System.out.println("[Vendedor] Vou vender Radio (Isto deve desbloquear o Waiter)...");
            stub.inserir(System.currentTimeMillis(), "Radio", 1, 100);

            // Esperar que a thread acabe
            clienteEspera.join();
            clienteEspera1.join();
            System.out.println("\n>>> Teste Concluído com Sucesso!");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}