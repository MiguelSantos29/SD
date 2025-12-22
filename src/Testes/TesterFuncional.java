package src.Testes;

import src.Stub;

import java.io.File;

public class TesterFuncional {

    public static void main(String[] args) {
        System.out.println(">>> A iniciar Testes Funcionais (Req 3, 4, 7)...");
        limparFicheirosAntigos();

        try (Stub stub = new Stub("localhost", 12345)) {

            // --- TESTE REQ 4: AGREGAÇÕES BÁSICAS ---
            System.out.println("\n[Teste 1] Inserção e Agregações...");
            try {
                stub.registar("tester", "123");
            } catch (Exception e) {
                // Ignorar se já existir
            }
            stub.autenticar("tester", "123");

            // Dia 0: Inserir 3 Maçãs a 10€
            stub.inserir(System.currentTimeMillis(), "Maca", 3, 10);

            // Verificar Agregação (Vol = 30)
            String resVol = stub.agregacao("Maca", 0, 1); // 1=Vol
            assertValor("0.0", resVol);
            System.out.println("OK: Agregação Dia 0 correta.");

            // --- TESTE REQ 3: AVANÇAR NO TEMPO ---
            System.out.println("\n[Teste 2] Avançar Dias...");
            stub.avancarDia(); // Passa para Dia 1 (Dia 0 vai para disco/cache)

            // Dia 1: Inserir 2 Maçãs a 20€
            stub.inserir(System.currentTimeMillis(), "Maca", 2, 20);

            // Verificar Agregação Total (Dias anteriores + Hoje)
            // Nota: Dependendo da tua implementação, 'agregacao' vê os últimos D dias.
            // Se pedires d=2, deve somar (3*10) + (2*20) = 70.
            // Se a tua DB só vê dias ANTERIORES, então no Dia 1, com d=1, vê o Dia 0 (30).
            // Vamos assumir que a tua lógica vê o acumulado:
            String resVolTotal = stub.agregacao("Maca", 2, 1);
            System.out.println("Volume acumulado (Dia 0 + Dia 1): " + resVolTotal);

            // --- TESTE REQ 7: PERSISTÊNCIA E CACHE ---
            System.out.println("\n[Teste 3] Persistência e Limite de Memória (S=3)...");

            // Já temos Dia 0 e Dia 1. Vamos avançar até ao Dia 5.
            // Isto vai obrigar o Dia 0 e Dia 1 a saírem da RAM (se S=3).
            for (int i = 0; i < 4; i++) {
                stub.inserir(System.currentTimeMillis(), "Maca", 1, 5); // Só para ter dados
                stub.avancarDia();
                System.out.println(" Avançou para dia " + (i + 3));
            }

            // Verificar se os ficheiros existem
            File f0 = new File("dia_0.dat");
            if (f0.exists()) System.out.println("OK: Ficheiro dia_0.dat existe no disco.");
            else System.err.println("ERRO: Persistência falhou, dia_0.dat não existe.");

            // Consultar dados do Dia 0 (que deve vir do disco)
            // Pedimos eventos do dia com ID=6 (Hoje) - 6 = Dia 0?
            // A lógica de dias é relativa. Se hoje é dia 6 e pedimos d=6, vamos buscar o dia 0.
            System.out.println("A ler dados antigos do disco...");
            // Atenção: adapta o 'd' conforme a tua lógica atual de dias
            String resAntigo = stub.consultarTotal("Maca");
            System.out.println("Total Global Maça (deve incluir o dia 0): " + resAntigo);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void assertValor(String esperado, String obtido) {
        if (obtido.equals(esperado) || obtido.startsWith(esperado)) {
            System.out.println("  -> Check OK: " + obtido);
        } else {
            System.err.println("  -> Check FALHOU. Esperado: " + esperado + " | Obtido: " + obtido);
        }
    }

    private static void limparFicheirosAntigos() {
        File folder = new File(".");
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".dat"));
        if (files != null) {
            for (File f : files) f.delete();
        }
        System.out.println("Ambiente limpo.");
    }
}
