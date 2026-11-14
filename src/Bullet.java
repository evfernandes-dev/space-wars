/**
 * Um tiro do jogo.
 *
 * O tiro do jogador sobe reto. Os tiros dos inimigos podem ir em qualquer
 * direção: guardamos dx e dy para permitir mira diagonal.
 */
public class Bullet {

    private int x;
    private int y;
    private final int dx;
    private final int dy;
    private final boolean doJogador;

    public Bullet(int x, int y, int dx, int dy, boolean doJogador) {
        this.x = x;
        this.y = y;
        this.dx = dx;
        this.dy = dy;
        this.doJogador = doJogador;
    }

    public void mover() {
        x += dx;
        y += dy;
    }

    public boolean foraDaTela(int largura, int altura) {
        return x < 0 || x >= largura || y < 0 || y >= altura;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public boolean isDoJogador() {
        return doJogador;
    }
}
