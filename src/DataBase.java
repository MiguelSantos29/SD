package src;

import java.io.*;
import java.util.*;
import java.util.concurrent.locks.*;

public class DataBase {

    private final int maxSeriesMemoria; // S
    private final ReentrantLock lock = new ReentrantLock();
    private final long retentionPeriod; // D

    // Utilizadores
    private Map<String, String> utilizadores = new HashMap<>();

    // Cache LRU: Chave=ID Dia, Valor=Mapa Vendas. Remove o mais antigo se passar do limite.
    private final Map<Integer, Map<String, List<Venda>>> cacheSeries;

    // Dia Corrente (Sempre em RAM)
    private Map<String, List<Venda>> diaCorrenteVendas = new HashMap<>();
    private int diaCorrenteID = 0;

    // --- 5: NOTIFICAÇÕES ---
    // Consecutivas: Produto -> Lista de quem espera
    private final List<EsperaConsecutiva> waitersConsecutivas = new ArrayList<>();

    // Simultâneas: Produto -> Lista de quem espera por este produto (+ o par dele)
    private final Map<String, List<EsperaSimultanea>> waitersSimultaneas = new HashMap<>();
    private String ultimoProdutoVendido = null;
    private int contadorConsecutivas = 0;

    public DataBase(int dias, int s) {
        // Inicializar a Cache com ordem de acesso (LRU)
        this.maxSeriesMemoria = s;
        this.cacheSeries = new LinkedHashMap<>(maxSeriesMemoria, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Integer, Map<String, List<Venda>>> eldest) {
                return size() > maxSeriesMemoria;
            }
        };

        this.retentionPeriod = (long) dias * 24 * 60 * 60 * 1000;
        carregarEstadoUtilizadores(); // Carrega apenas users
    }

    // --- CLASSES PARA GERIR OS SIGNALS ---
    private static class EsperaConsecutiva {
        int alvo; // Quantas quer (ex: 3)
        Condition cond;
        public EsperaConsecutiva(int a, Condition c) { this.alvo = a; this.cond = c; }
    }

    private static class EsperaSimultanea {
        String outroProduto; // O par que falta
        Condition cond;
        public EsperaSimultanea(String outro, Condition c) { this.outroProduto = outro; this.cond = c; }
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
            cacheSeries.put(diaCorrenteID, new HashMap<>(diaCorrenteVendas));
            diaCorrenteID++;
            diaCorrenteVendas = new HashMap<>();
            ultimoProdutoVendido = null;
            contadorConsecutivas = 0;

            System.out.println("[Novo Dia] Dia " + (diaCorrenteID-1) + " arquivado. Iniciando Dia " + diaCorrenteID);

            for (EsperaConsecutiva req : waitersConsecutivas) {
                req.cond.signal(); // Avisar todos que o dia mudou
            }

            for (List <EsperaSimultanea> lista : waitersSimultaneas.values()) {
                for (EsperaSimultanea req : lista) {
                    req.cond.signal(); // Avisar todos que o dia mudou
                }
            }
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

            for (EsperaConsecutiva req : waitersConsecutivas) {
                // Se vendemos "Banana" 3 vezes e o cliente queria 2, acordamo-lo!
                if (contadorConsecutivas >= req.alvo) {
                    req.cond.signal();
                }
            }

            // --- NOTIFICAR SIMULTÂNEAS (Cirúrgico) ---
            List<EsperaSimultanea> listaSim = waitersSimultaneas.get(produto);
            if (listaSim != null) {
                for (EsperaSimultanea req : listaSim) {
                    // Verificamos se O OUTRO produto já existe hoje
                    if (temVendaHoje(req.outroProduto)) {
                        req.cond.signal(); // Acorda SÓ esta thread
                    }
                }
            }

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
            long totalQtd = 0; long totalVol = 0;
            int maxPreco = 0; int countVendas = 0;
            // i=0 -> Hoje || i=1 -> Ontem
            for (int i = 1; i < d; i++) {
                Map<String, List<Venda>> mapaDia = getVendasDoDia(i);
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
                case 2: return countVendas == 0 ? 0 : (double) totalVol / totalQtd; // Média
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

    public String esperarVendasConsecutivas(int n) throws InterruptedException {
        lock.lock();
        try {
            int diaInicial = this.diaCorrenteID;
            Condition minhaCond = lock.newCondition();
            EsperaConsecutiva req = new EsperaConsecutiva(n, minhaCond);

            waitersConsecutivas.add(req);

            try {
                while (true) {
                    if (this.diaCorrenteID != diaInicial) {
                        return null;
                    }

                    // Se o objetivo foi atingido (N vezes o mesmo produto)
                    if (contadorConsecutivas >= n && ultimoProdutoVendido != null) {
                        return ultimoProdutoVendido; // Sucesso! Retorna "Banana"
                    }

                    minhaCond.await();
                }
            } finally {
                waitersConsecutivas.remove(req);
            }
        } finally {
            lock.unlock();
        }
    }

    public boolean esperarVendasSimultaneas(String p1, String p2) throws InterruptedException {
        lock.lock();
        try {
            int diaInicial = this.diaCorrenteID;
            Condition minhaCond = lock.newCondition();
            EsperaSimultanea req1 = new EsperaSimultanea(p2, minhaCond); // Se vender p1, verifica p2
            EsperaSimultanea req2 = new EsperaSimultanea(p1, minhaCond); // Se vender p2, verifica p1

            // Registar interesse nos dois produtos
            waitersSimultaneas.computeIfAbsent(p1, k -> new ArrayList<>()).add(req1);
            waitersSimultaneas.computeIfAbsent(p2, k -> new ArrayList<>()).add(req2);

            while (true) {
                if (this.diaCorrenteID != diaInicial) {
                    // Limpar registos
                    removerSimultanea(p1, req1);
                    removerSimultanea(p2, req2);
                    return false;
                }

                if (temVendaHoje(p1) && temVendaHoje(p2)) {
                    removerSimultanea(p1, req1);
                    removerSimultanea(p2, req2);
                    return true;
                }
                minhaCond.await();
            }
        } finally {
            lock.unlock();
        }
    }

    // Auxiliar para limpar
    private void removerSimultanea(String prod, EsperaSimultanea req) {
        List<EsperaSimultanea> l = waitersSimultaneas.get(prod);
        if (l != null) l.remove(req);
    }

    private boolean temVendaHoje(String prod) {
        return diaCorrenteVendas.containsKey(prod) && !diaCorrenteVendas.get(prod).isEmpty();
    }
    // --- GESTÃO DE DISCO (REQ 7) ---
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