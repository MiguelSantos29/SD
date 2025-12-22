package src;
import java.io.Serializable;

public class Venda implements Serializable {
    public long timestamp;
    public String produto;
    public int quantidade;
    public int preco;

    public Venda(long ts, String p, int q, int pre) {
        this.timestamp = ts;
        this.produto = p;
        this.quantidade = q;
        this.preco = pre;
    }
}

