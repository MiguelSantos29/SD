package src;

import java.io.*;
import java.util.*;
import java.util.concurrent.locks.*;

public class DataBase {

    private final int MAX_SERIES_MEMORIA = 3; // S
    private final ReentrantLock lock = new ReentrantLock();
    private final long retentionPeriod; // D

    private long simulatedOffset = 0;

    // Utilizadores
    private Map<String, String> utilizadores = new HashMap<>();

    // Cache LRU: Chave=ID Dia, Valor=Mapa Vendas. Remove o mais antigo se passar do limite.
    private final Map<Integer, Map<String, List<Venda>>> cacheSeries;

    // Dia Corrente (Sempre em RAM)
    private Map<String, List<Venda>> diaCorrenteVendas = new HashMap<>();
    private int diaCorrenteID = 0;

    // --- 5: NOTIFICAÇÕES ---
    private final Condition condicaoVenda = lock.newCondition();
    private String ultimoProdutoVendido = null;
    private int contadorConsecutivas = 0;

    public DataBase(int dias) {
        // Inicializar a Cache com ordem de acesso (LRU)
        this.cacheSeries = new LinkedHashMap<>(MAX_SERIES_MEMORIA, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Integer, Map<String, List<Venda>>> eldest) {
                return size() > MAX_SERIES_MEMORIA;
            }
        };

        this.retentionPeriod = (long) dias * 24 * 60 * 60 * 1000;
        carregarEstadoUtilizadores(); // Carrega apenas users
    }

    // --- GESTÃO DE UTILIZADORES ---

    public boolean registarUtilizador(String user, String pass) {
        lock.lock();
        try {
            if (utilizadores.containsKey(user)) return false;
            utilizadores.put(user, pass);
            salvarUtilizadores(); // Persistir logo
            return true;
        } finally {
            lock.unlock();
        }
    }

    public boolean autenticarUtilizador(String user, String pass) {
        lock.lock();
        try {
            String passwordReal = utilizadores.get(user);
            return passwordReal != null && passwordReal.equals(pass);
        } finally {
            lock.unlock();
        }
    }

    // --- GESTÃO DE TEMPO E DIAS ---

    public void startNewDay() {
        lock.lock();
        try {
            // Guardar o dia que acabou no disco
            salvarDiaEmDisco(diaCorrenteID, diaCorrenteVendas);

            // Colocar na cache (pode expulsar um antigo da RAM)
            cacheSeries.put(diaCorrenteID, new HashMap<>(diaCorrenteVendas));

            // Resetar estado para o novo dia
            diaCorrenteID++;
            diaCorrenteVendas = new HashMap<>();

            simulatedOffset += 24 * 60 * 60 * 1000L;
            ultimoProdutoVendido = null;
            contadorConsecutivas = 0;

            System.out.println("[Novo Dia] Dia " + (diaCorrenteID-1) + " arquivado. Iniciando Dia " + diaCorrenteID);

            condicaoVenda.signalAll(); // Avisar waiters que o dia mudou
        } finally {
            lock.unlock();
        }
    }

    // --- INSERÇÕES ---
    public void inserir(long ts, String produto, int qtd, int preco) {
        lock.lock();
        try {
            Venda v = new Venda(ts, produto, qtd, preco);
            diaCorrenteVendas.computeIfAbsent(produto, k -> new ArrayList<>()).add(v);

            // Req 5: Consecutivas
            if (produto.equals(ultimoProdutoVendido)) {
                contadorConsecutivas++;
            } else {
                ultimoProdutoVendido = produto;
                contadorConsecutivas = 1;
            }

            condicaoVenda.signalAll(); // Req 5: Acorda waiters

        } finally {
            lock.unlock();
        }
    }

    // --- CONSULTAS (Adaptadas para ler do Disco/Cache) ---

    // Agora calcula on-demand percorrendo os dias -> (Lazy Loading)
    public long consultarTotalVendas(String produto) {
        long totalVol = 0;
        lock.lock();
        try {
            int diasParaVer = (int) (retentionPeriod / (24 * 60 * 60 * 1000));
            for (int i = 0; i <= diasParaVer; i++) {
                Map<String, List<Venda>> mapaDia = getVendasDoDia(i); // Busca ao Disco/Cache

                if (mapaDia != null && mapaDia.containsKey(produto)) {
                    List<Venda> lista = mapaDia.get(produto);
                    for (Venda v : lista) {
                        totalVol += (long) v.quantidade * v.preco;
                    }
                }
            }
            return totalVol;
        } finally {
            lock.unlock();
        }
    }

    // tipo: 0=Qtd, 1=Vol, 2=Media, 3=Max
    public double consultarAgregacao(String produto, int d, int tipo) {
        lock.lock();
        try {
            long totalQtd = 0;
            long totalVol = 0;
            int maxPreco = 0;
            int countVendas = 0;

            // Percorre os últimos d dias "excluindo o dia corrente".

            for (int i = 1; i <= d; i++) {
                Map<String, List<Venda>> mapaDia = getVendasDoDia(i); // Busca ao Disco/Cache

                if (mapaDia != null && mapaDia.containsKey(produto)) {
                    List<Venda> lista = mapaDia.get(produto);
                    for (Venda v : lista) {
                        totalQtd += v.quantidade;
                        totalVol += (long) v.quantidade * v.preco;
                        if (v.preco > maxPreco) maxPreco = v.preco;
                        countVendas++;
                    }
                }
            }

            switch (tipo) {
                case 0: return totalQtd;
                case 1: return totalVol;
                case 2: return countVendas == 0 ? 0 : (double) totalVol / totalQtd; // Preço médio ponderado? Ou media simples dos preços unitários?
                // Se for media simples dos preços unitários, a lógica seria diferente. Assumi media ponderada (Volume/Qtd)
                case 3: return maxPreco;
                default: return 0;
            }
        } finally {
            lock.unlock();
        }
    }

    public List<Venda> consultarEventos(Set<String> produtos, int d) {
        lock.lock();
        try {
            // d=1 -> Dia anterior (ontem)
            Map<String, List<Venda>> mapaDia = getVendasDoDia(d);
            List<Venda> resultado = new ArrayList<>();

            if (mapaDia != null) {
                for (String prod : produtos) {
                    if (mapaDia.containsKey(prod)) {
                        resultado.addAll(mapaDia.get(prod));
                    }
                }
            }
            return resultado;
        } finally {
            lock.unlock();
        }
    }

    // --- REQ 5: NOTIFICAÇÕES ---

    public boolean esperarVendasSimultaneas(String p1, String p2) throws InterruptedException {
        lock.lock();
        try {
            int diaInicial = this.diaCorrenteID;

            while (true) {
                if (this.diaCorrenteID != diaInicial) return false; // Dia mudou

                if (temVendaHoje(p1) && temVendaHoje(p2)) {
                    return true;
                }
                condicaoVenda.await();
            }
        } finally {
            lock.unlock();
        }
    }

    public String esperarVendasConsecutivas(int n) throws InterruptedException {
        lock.lock();
        try {
            int diaInicial = this.diaCorrenteID;
            while (true) {
                if (this.diaCorrenteID != diaInicial) return null;

                if (contadorConsecutivas >= n && ultimoProdutoVendido != null) {
                    return ultimoProdutoVendido;
                }
                condicaoVenda.await();
            }
        } finally {
            lock.unlock();
        }
    }

    private boolean temVendaHoje(String prod) {
        return diaCorrenteVendas.containsKey(prod) && !diaCorrenteVendas.get(prod).isEmpty();
    }
    // --- GESTÃO DE DISCO (REQ 7) ---

    // Obtém o mapa de vendas de um dia passado (RAM Cache ou Disco)
    // d=1 significa 1 dia atrás (diaCorrenteID - 1)
    private Map<String, List<Venda>> getVendasDoDia(int d) {
        int targetDayID = diaCorrenteID - d;
        if (targetDayID < 0) return null; // Antes do inicio dos tempos

        // Verifica Cache
        if (cacheSeries.containsKey(targetDayID)) {
            return cacheSeries.get(targetDayID);
        }

        // Verifica Disco
        Map<String, List<Venda>> doDisco = carregarDiaDoDisco(targetDayID);
        if (doDisco != null) {
            cacheSeries.put(targetDayID, doDisco); // Mete na cache (LRU limpa se cheio)
            return doDisco;
        }

        return null;
    }

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

    private void salvarUtilizadores() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream("users.dat"))) {
            oos.writeObject(utilizadores);
        } catch (IOException e) { e.printStackTrace(); }
    }

    public void carregarEstadoUtilizadores() {
        File f = new File("users.dat");
        if (!f.exists()) return;

        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(f))) {
            this.utilizadores = (Map<String, String>) ois.readObject();
        } catch (Exception e) { e.printStackTrace(); }
    }
}