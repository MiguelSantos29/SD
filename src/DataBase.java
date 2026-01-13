package src;

import java.util.*;
import java.util.concurrent.locks.*;

public class DataBase {
    private final ReentrantLock lockAuth = new ReentrantLock();
    private final ReentrantLock lock = new ReentrantLock();

    // Módulos
    private final Memoria mem;
    private final GestorCondicoes conds;

    public DataBase(int dias, int s) {
        this.mem = new Memoria(dias, s);
        this.conds = new GestorCondicoes();
    }

    // --- AUTH ---
    public boolean registarUtilizador(String user, String pass) {
        lockAuth.lock();
        try {
            if (mem.utilizadores.containsKey(user)) return false;
            mem.utilizadores.put(user, pass);
            mem.salvarUtilizadores();
            return true;
        } finally {
            lockAuth.unlock();
        }
    }

    public boolean autenticarUtilizador(String user, String pass) {
        lockAuth.lock();
        try {
            String real = mem.utilizadores.get(user);
            return real != null && real.equals(pass);
        } finally {
            lockAuth.unlock();
        }
    }

    // --- ESCRITAS ---
    public void startNewDay() {
        lock.lock();
        try {
            System.out.println("[Novo Dia] Arquivando dia " + mem.diaCorrenteID);

            mem.arquivarDiaCorrente();

            // Resetar lógica de eventos
            conds.ultimoProdutoVendido = null;
            conds.contadorConsecutivas = 0;
            conds.notificarMudancaDia();
            conds.globalEventID = 0;
        } finally {
            lock.unlock();
        }
    }

    public void inserir(long ts, String produto, int qtd, int preco) {
        lock.lock();
        try {
            mem.adicionarVendaHoje(new Venda(ts, produto, qtd, preco));

            conds.registarVenda(produto);
            conds.notificarWaiters(mem, produto); // Passamos a memoria para checar simultaneas
            // tirei o lock daqui porque já estamos com o lock

        } finally {
            lock.unlock();
        }
    }

    // --- LEITURAS ---
    public long consultarTotalVendas(String produto) {
        long totalVol = 0;
        lock.lock();
        try {
            int maxDias = mem.calcularDiasParaVer();
            for (int i = 0; i <= maxDias; i++) {
                // Se i=0 (hoje), guarda na cache. Se i>0, só lê (false)
                Map<String, List<Venda>> mapa = mem.getVendasDoDia(i, false);

                if (mapa != null && mapa.containsKey(produto)) {
                    for (Venda v : mapa.get(produto)) totalVol += (long) v.quantidade * v.preco;
                }
            }
            return totalVol;
        } finally {
            lock.unlock();
        }
    }

    public double consultarAgregacao(String produto, int d, int tipo) {
        lock.lock();
        try {
            long totalQtd = 0; long totalVol = 0;
            int maxPreco = 0; int countVendas = 0;
            // i=1 -> Ontem
            for (int i = 1; i <= d; i++) {

                Map<String, List<Venda>> mapaDia = mem.getVendasDoDia(i, true); // Cache vai funcionar aqui
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
            Map<String, List<Venda>> mapaDia = mem.getVendasDoDia(d, true); // Cache vai funcionar aqui
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

    // --- EVENTOS (Waiters) ---

    public String esperarVendasConsecutivas(int n) throws InterruptedException {
        lock.lock();
        try {
            int diaInicial = mem.diaCorrenteID;

            // Snapshot do estado inicial (para a janela deslizante)
            String prodInicio = conds.ultimoProdutoVendido;
            int countInicio = (prodInicio != null) ? conds.contadorConsecutivas : 0;
            long entryID = conds.globalEventID;

            Condition myCond = lock.newCondition();
            GestorCondicoes.EsperaConsecutiva req = new GestorCondicoes.EsperaConsecutiva(n, myCond);
            conds.addConsecutiva(req);

            try {
                while (true) {
                    String res = conds.verificarConsecutivas(n, diaInicial, mem.diaCorrenteID, entryID, prodInicio, countInicio);

                    if (res == null) return null;
                    if (!res.equals("WAIT")) return res;

                    myCond.await();
                }
            } finally {
                conds.removeConsecutiva(req);
            }
        } finally {
            lock.unlock();
        }
    }

    public boolean esperarVendasSimultaneas(String p1, String p2) throws InterruptedException {
        lock.lock();
        try {
            int diaInicial = mem.diaCorrenteID;
            int qtdInicialP1 = mem.getQuantidadeVendasHoje(p1);
            int qtdInicialP2 = mem.getQuantidadeVendasHoje(p2);

            Condition myCond = lock.newCondition();
            GestorCondicoes.EsperaSimultanea req1 = new GestorCondicoes.EsperaSimultanea(p2, myCond);
            GestorCondicoes.EsperaSimultanea req2 = new GestorCondicoes.EsperaSimultanea(p1, myCond);
            conds.addSimultanea(p1, req1);
            conds.addSimultanea(p2, req2);

            try {
                while(true) {
                    if (mem.diaCorrenteID != diaInicial) return false;
                    if (mem.temVendaHoje(p1) && mem.temVendaHoje(p2)) {
                        int qtdAtualP1 = mem.getQuantidadeVendasHoje(p1);
                        int qtdAtualP2 = mem.getQuantidadeVendasHoje(p2);
                        // Faltava uma condicao na logica
                        if (((qtdAtualP1 > qtdInicialP1) && (qtdAtualP2 > qtdInicialP2))
                                && ((conds.ultultimoProdutoVendido.equals(p1) && conds.ultimoProdutoVendido.equals(p2))
                                ||(conds.ultultimoProdutoVendido.equals(p2) && conds.ultimoProdutoVendido.equals(p1)))) {
                            return true;
                        }
                    }

                    myCond.await();
                }
            } finally {
                conds.removeSimultanea(p1, req1);
                conds.removeSimultanea(p2, req2);
            }
        } finally {
            lock.unlock();
        }
    }
}