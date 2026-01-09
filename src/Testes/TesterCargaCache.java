package src.Testes;

import src.DataBase;
import java.io.File;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static java.lang.Thread.sleep;

public class TesterCargaCache {

    // Configurações
    static final int TOTAL_DIAS_SIMULACAO = 6;
    static final int CACHE_SIZE = 3; // Cache muito pequena para forçar trocas
    static final int VENDAS_POR_DIA = 500;
    static final String PRODUTO_TESTE = "Banana";

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== INÍCIO TESTE DE CARGA E CACHE ===");

        // 1. Limpar ambiente antigo
        limparFicheirosAntigos();

        // 2. Iniciar Base de Dados (Retenção 10 dias, Cache apenas 3 dias)
        System.out.println(">>> A iniciar DB com Cache Size = " + CACHE_SIZE);
        DataBase db = new DataBase(10, CACHE_SIZE);

        // 3. Povoar a Base de Dados (Dias 0 a 5)
        long inicioPovoamento = System.currentTimeMillis();
        Random r = new Random();

        for (int dia = 0; dia < TOTAL_DIAS_SIMULACAO; dia++) {
            System.out.print("Simulando Dia " + dia + "...");

            // Inserir 500 Bananas com preços variados
            for (int v = 0; v < VENDAS_POR_DIA; v++) {
                db.inserir(System.currentTimeMillis(), PRODUTO_TESTE, 1, r.nextInt(10) + 1);
            }

            // Avançar para o próximo dia (Arquiva o dia atual)
            db.startNewDay();
            System.out.println(" [OK]");
        }
        long fimPovoamento = System.currentTimeMillis();
        System.out.println("Povoamento concluído em " + (fimPovoamento - inicioPovoamento) + "ms.");
        System.out.println("Estado Esperado da Cache (LRU): Deve conter os dias mais recentes (ex: 3, 4, 5).");

        // --- TESTE 1: CONSULTA TOTAL (SCAN - parâmetro false) ---

        System.out.println("\n>>> TESTE 1: Consultar Total (Scan Completo)");
        long t1 = System.currentTimeMillis();

        long total = db.consultarTotalVendas(PRODUTO_TESTE);

        long t2 = System.currentTimeMillis();
        System.out.println("Total Calculado: " + total + " (Tempo: " + (t2 - t1) + "ms)");
        System.out.println("VALIDAÇÃO: Se o tempo foi baixo e não viste logs de 'Cache Eviction' massivo, funcionou.");

        // --- TESTE 2: CONSULTA AGREGAÇÃO (LOAD - parâmetro true) ---

        System.out.println("\n>>> TESTE 2: Consultar Agregação Dia Antigo (Dia 1)");
        System.out.println("    (Isto deve forçar uma leitura de disco e atualizar a cache)");

        Set<String> produtos = new HashSet<>();
        produtos.add(PRODUTO_TESTE);

        long t3 = System.currentTimeMillis();
        // Consultar dia 1 (histórico)
        db.consultarEventos(produtos, 4);
        long t4 = System.currentTimeMillis();
        System.out.println("    Tempo da 1ª consulta (Frio/Disco): " + (t4 - t3) + "ms");

        // --- TESTE 3: REPETIR A CONSULTA (CACHE HIT) ---

        sleep(1000);
        System.out.println("\n>>> TESTE 3: Repetir Agregação Dia 1 (Cache Hit)");
        long t5 = System.currentTimeMillis();
        db.consultarEventos(produtos, 4);
        long t6 = System.currentTimeMillis();
        System.out.println("    Tempo da 2ª consulta (Quente/RAM): " + (t6 - t5) + "ms");

        if ((t6 - t5) < (t4 - t3)) {
            System.out.println("\n[SUCESSO] A Cache funcionou! A segunda leitura foi muito mais rápida.");
        } else {
            System.out.println("\n[AVISO] Tempos semelhantes. Verifica se a cache está a guardar.");
        }

        System.exit(0); // Aciona o Shutdown Hook para limpar tudo
    }

    private static void limparFicheirosAntigos() {
        File folder = new File(".");
        File[] files = folder.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.getName().startsWith("dia_") && f.getName().endsWith(".dat")) {
                    f.delete();
                }
            }
        }
    }
}