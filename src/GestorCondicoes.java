package src;

import java.util.*;
import java.util.concurrent.locks.*;

public class GestorCondicoes {
    // Classes Internas
    public static class EsperaConsecutiva {
        int alvo; Condition cond;
        public EsperaConsecutiva(int a, Condition c) { this.alvo = a; this.cond = c; }
    }
    public static class EsperaSimultanea {
        String outroProduto; Condition cond;
        public EsperaSimultanea(String o, Condition c) { this.outroProduto = o; this.cond = c; }
    }

    // Estado dos Eventos
    private final List<EsperaConsecutiva> waitersConsecutivas = new ArrayList<>();
    private final Map<String, List<EsperaSimultanea>> waitersSimultaneas = new HashMap<>();

    // Contadores Globais
    public long globalEventID = 0;

    // Estado Consecutivas
    public String ultimoProdutoVendido = null;
    public int contadorConsecutivas = 0;

    // --- LÓGICA DE INSERÇÃO ---

    public void registarVenda(String produto) {
        globalEventID++;

        // Atualizar Consecutivas
        if (produto.equals(ultimoProdutoVendido)) {
            contadorConsecutivas++;
        } else {
            ultimoProdutoVendido = produto;
            contadorConsecutivas = 1;
        }
    }

    public void notificarWaiters(ReentrantLock lock, Memoria memoria, String produtoInserido) {
        for (EsperaConsecutiva req : waitersConsecutivas) {
            req.cond.signal();
        }
        List<EsperaSimultanea> lista = waitersSimultaneas.get(produtoInserido);
        if (lista != null) {
            for (EsperaSimultanea req : lista) {
                if (memoria.temVendaHoje(req.outroProduto)) {
                    req.cond.signal();
                }
            }
        }
    }

    public void notificarMudancaDia() {
        // Acordar toda a gente para verificarem que o dia mudou
        for (EsperaConsecutiva req : waitersConsecutivas) req.cond.signalAll();
        for (List<EsperaSimultanea> lista : waitersSimultaneas.values()) {
            for (EsperaSimultanea req : lista) req.cond.signalAll();
        }
    }

    // --- LÓGICA DE ESPERA (Complexa) ---

    // Retorna o produto se sucesso, null se dia mudou
    public String verificarConsecutivas(int n, int diaInicial, int diaAtual, long myEntryID, String prodInicio, int countInicio) {
        if (diaAtual != diaInicial) return null; // Erro: Dia mudou

        if (globalEventID > myEntryID && ultimoProdutoVendido != null) {
            if (contadorConsecutivas - countInicio == n || contadorConsecutivas == n) {
                return ultimoProdutoVendido;
            }
        }
        return "WAIT";
    }

    public void addConsecutiva(EsperaConsecutiva r) { waitersConsecutivas.add(r); }

    public void removeConsecutiva(EsperaConsecutiva r) { waitersConsecutivas.remove(r); }

    public void addSimultanea(String p, EsperaSimultanea r) {
        waitersSimultaneas.computeIfAbsent(p, k->new ArrayList<>()).add(r);
    }
    public void removeSimultanea(String p, EsperaSimultanea r) {
        List<EsperaSimultanea> l = waitersSimultaneas.get(p);
        if (l != null) l.remove(r);
    }
}
