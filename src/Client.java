package src;

import java.io.*;
import java.util.*;

public class Client {

    public static void main(String[] args) throws IOException {
        // Usamos o try-with-resources para fechar tudo no fim
        try (Stub stub = new Stub("localhost", 12345)) {
            Scanner sc = new Scanner(System.in);
            System.out.println(">>> Conectado ao servidor (Via Demultiplexer)!");

            // --- 1. AUTENTICAÇÃO ---
            boolean autenticado = false;
            while (!autenticado) {
                System.out.println("\n--- BEM-VINDO ---");
                System.out.println("1. Login");
                System.out.println("2. Registar");
                System.out.println("0. Sair");
                System.out.print("Opção: ");

                if (!sc.hasNextInt()) break;
                int op1 = sc.nextInt();

                if (op1 == 0) return;

                if (op1 == 1 || op1 == 2) {
                    System.out.print("Username: ");
                    String user = sc.next();
                    System.out.print("Password: ");
                    String pass = sc.next();

                    try {
                        if (op1 == 1) { // LOGIN
                            String msg = stub.autenticar(user, pass);
                            System.out.println(">> " + msg);
                            autenticado = true; // Sai do loop e vai para o menu principal
                        } else { // REGISTAR
                            String msg = stub.registar(user, pass);
                            System.out.println(">> " + msg);
                            System.out.println(">> Agora faça login para entrar.");
                        }
                    } catch (Exception e) {
                        System.out.println("ERRO: " + e.getMessage());
                    }
                } else {
                    System.out.println("Opção inválida.");
                }

                if (autenticado){
                    // --- 2. MENU PRINCIPAL ---
                    boolean running = true;
                    while (running) {
                        System.out.println("\n--- MENU ---");
                        System.out.println("1. Inserir Venda");
                        System.out.println("2. Consultar Total");
                        System.out.println("3. Avançar Dia");
                        System.out.println("4. Agregações Avançadas");
                        System.out.println("5. Eventos do Dia D");
                        System.out.println("6. Esperar Vendas Simultâneas (Bloqueante)");
                        System.out.println("7. Esperar Vendas Consecutivas (Bloqueante)");
                        System.out.println("0. Sair");
                        System.out.println("Diga a sua opção:");

                        if (!sc.hasNextInt()) break;
                        int op = sc.nextInt();

                        try {
                            switch (op) {
                                case 1:
                                    System.out.print("Produto: ");
                                    String prod = sc.next();
                                    System.out.print("Quantidade: ");
                                    int qtd = sc.nextInt();
                                    System.out.print("Preço (inteiro): ");
                                    int preco = sc.nextInt();

                                    String resIns = stub.inserir(System.currentTimeMillis(), prod, qtd, preco);
                                    System.out.println("RESPOSTA: " + resIns);
                                    break;

                                case 2:
                                    System.out.print("Produto a consultar: ");
                                    String pConsulta = sc.next();
                                    String resCons = stub.consultarTotal(pConsulta);
                                    System.out.println("RESPOSTA: " + resCons);
                                    break;

                                case 3: // Avançar Dia
                                    String resDia = stub.avancarDia();
                                    System.out.println("RESPOSTA: " + resDia);
                                    break;

                                case 4: // Agregações Avançadas
                                    System.out.print("Produto: ");
                                    String pAdv = sc.next();
                                    System.out.print("Dias (d): ");
                                    int d = sc.nextInt();
                                    System.out.println("Tipo: 0=Qtd, 1=Vol, 2=Media, 3=Max");
                                    int tipo = sc.nextInt();

                                    String resAg = stub.agregacao(pAdv, d, tipo);
                                    System.out.println("RESPOSTA: " + resAg);
                                    break;

                                case 5: // Eventos do Dia D
                                    System.out.print("Quantos produtos?: ");
                                    int n = sc.nextInt();
                                    Set<String> prods = new HashSet<>();
                                        for(int i=0; i<n; i++) {
                                            System.out.print("Prod " + (i+1) + ": ");
                                            prods.add(sc.next());
                                        }
                                    System.out.print("Dia (1=Ontem, etc): ");
                                    int diaE = sc.nextInt();

                                    // Agora recebemos a lista compacta
                                    List<Stub.ResumoVenda> lista = stub.consultarEventos(prods, diaE);

                                        System.out.println("--- Resumo de Eventos ---");
                                        System.out.printf("%-15s | %-10s | %-10s | %-10s%n", "Produto", "Preço", "Qtd Total", "Nº Vendas");
                                        System.out.println("---------------------------------------------------------");

                                    for(Stub.ResumoVenda v : lista) {
                                        System.out.printf("%-15s | %-10d | %-10d | %-10d%n", v.produto, v.preco, v.qtdTotal, v.numVendas);
                                    }
                                    break;
                                case 6:
                                    System.out.print("Prod 1: "); String s1 = sc.next();
                                    System.out.print("Prod 2: "); String s2 = sc.next();
                                    new Thread(() -> {
                                        try {
                                            System.out.println("[Background] A vigiar vendas de " + s1 + " e " + s2 + "...");
                                            String res = stub.esperarSimultaneas(s1, s2);
                                            System.out.println("!!! NOTIFICAÇÃO !!! " + res);
                                        } catch (Exception e) {
                                            System.out.println("[Background] Erro na vigilância: " + e.getMessage());
                                        }
                                    }).start();
                                    break;

                                case 7:
                                    System.out.print("Nº Consecutivas: "); int nC = sc.nextInt();
                                    new Thread(() -> {
                                        try {
                                            String res = stub.esperarConsecutivas(nC);
                                            System.out.println("!!! NOTIFICAÇÃO !!! " + res);
                                        } catch (Exception e) {
                                            System.out.println("[Background] Erro na vigilância: " + e.getMessage());
                                        }
                                    }).start();
                                    break;

                                case 0:
                                    autenticado = false;
                                    running = false;
                                    break;

                                default:
                                    System.out.println("Opção inválida.");
                            }
                        } catch (Exception e) {
                            System.out.println("<< ERRO NO PEDIDO: " + e.getMessage());
                        }
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
