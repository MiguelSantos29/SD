package src;

import java.io.*;
import java.util.*;

public class Memoria {
    private final int maxSeriesMemoria;
    private final long retentionPeriod;

    // Dados
    public Map<String, String> utilizadores = new HashMap<>();

    private final Map<Integer, Map<String, List<Venda>>> cacheSeries;

    public Map<String, List<Venda>> diaCorrenteVendas = new HashMap<>();
    public int diaCorrenteID = 0;

    public Memoria(int dias, int s) {
        this.maxSeriesMemoria = s;
        this.retentionPeriod = (long) dias * 24 * 60 * 60 * 1000;

        // Cache LRU
        this.cacheSeries = new LinkedHashMap<>(maxSeriesMemoria, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Integer, Map<String, List<Venda>>> eldest) {
                return size() > maxSeriesMemoria;
            }
        };
        Runtime.getRuntime().addShutdownHook(new Thread(this::limparPersistencia));
        carregarEstadoUtilizadores();
    }

    // --- GESTÃO DE DIAS ---

    public void arquivarDiaCorrente() {
        salvarDiaEmDisco(diaCorrenteID, diaCorrenteVendas);
        cacheSeries.put(diaCorrenteID, new HashMap<>(diaCorrenteVendas));
        diaCorrenteID++;
        diaCorrenteVendas = new HashMap<>();
    }

    public void adicionarVendaHoje(Venda v) {
        diaCorrenteVendas.computeIfAbsent(v.produto, k -> new ArrayList<>()).add(v);
    }

    public boolean temVendaHoje(String prod) {
        return diaCorrenteVendas.containsKey(prod) && !diaCorrenteVendas.get(prod).isEmpty();
    }

    // --- LEITURA INTELIGENTE (REQ 7) ---

    public int calcularDiasParaVer() {
        return (int) (retentionPeriod / (24 * 60 * 60 * 1000));
    }

    public Map<String, List<Venda>> getVendasDoDia(int d, boolean guardarNaCache) {
        int targetDayID = diaCorrenteID - d;
        if (targetDayID < 0) return null;

        // 1. RAM
        if (cacheSeries.containsKey(targetDayID)) {
            return cacheSeries.get(targetDayID);
        }

        // 2. DISCO
        Map<String, List<Venda>> doDisco = carregarDiaDoDisco(targetDayID);
        if (doDisco != null) {
            if (guardarNaCache || cacheSeries.size() < maxSeriesMemoria) {
                cacheSeries.put(targetDayID, doDisco);
            }
            return doDisco;
        }
        return null;
    }

    // --- PERSISTÊNCIA ---

    private void salvarDiaEmDisco(int diaID, Map<String, List<Venda>> dados) {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream("dia_" + diaID + ".dat"))) {
            oos.writeObject(dados);
        } catch (IOException e) { e.printStackTrace(); }
    }

    private Map<String, List<Venda>> carregarDiaDoDisco(int diaID) {
        File f = new File("dia_" + diaID + ".dat");
        if (!f.exists()) return null;
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(f))) {
            return (Map<String, List<Venda>>) ois.readObject();
        } catch (Exception e) { return null; }
    }

    // --- UTILIZADORES ---

    public void salvarUtilizadores() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream("users.dat"))) {
            oos.writeObject(utilizadores);
        } catch (IOException e) { e.printStackTrace(); }
    }

    private void carregarEstadoUtilizadores() {
        File f = new File("users.dat");
        if (!f.exists()) return;
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(f))) {
            this.utilizadores = (Map<String, String>) ois.readObject();
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void limparPersistencia() {
        File folder = new File(".");
        File[] files = folder.listFiles();
        if (files != null) {
            for (File f : files) {
                String n = f.getName();
                if (n.startsWith("dia_") && n.endsWith(".dat")) {
                    f.delete();
                }
            }
        }
    }
}
