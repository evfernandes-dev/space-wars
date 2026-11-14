/**
 * A nave do jogador.
 *
 * Agora ela voa livremente pelo campo: sobe, desce, vai para os lados.
 * Guarda também vidas, recarga do tiro e invulnerabilidade.
 */
public class Player {

    private int x;
    private int y;
    private int vidas;
    private int cooldownTiro;
    private int invulneravelTicks;

    public Player(int x, int y) {
        this.x = x;
        this.y = y;
        this.vidas = 3;
        this.cooldownTiro = 0;
        this.invulneravelTicks = 0;
    }

    public void mover(int dx, int dy, int largura, int altura) {
        x = limitar(x + dx, 1, largura - 2);
        y = limitar(y + dy, 1, altura - 2);
    }

    /**
     * Cria um tiro para cima, se o cooldown permitir.
     * Retorna null quando ainda está recarregando.
     */
    public Bullet atirar() {
        if (cooldownTiro > 0) {
            return null;
        }

        cooldownTiro = 4;
        return new Bullet(x, y - 1, 0, -1, true);
    }

    // Chamado uma vez por tick do jogo
    public void atualizarTick() {
        if (cooldownTiro > 0) {
            cooldownTiro--;
        }

        if (invulneravelTicks > 0) {
            invulneravelTicks--;
        }
    }

    public void perderVida() {
        if (vidas > 0) {
            vidas--;
        }

        // Fica invulnerável por 25 ticks (mais ou menos 1,5 segundo)
        invulneravelTicks = 25;
    }

    public boolean estaInvulneravel() {
        return invulneravelTicks > 0;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getVidas() {
        return vidas;
    }

    private static int limitar(int valor, int minimo, int maximo) {
        return Math.max(minimo, Math.min(maximo, valor));
    }
}
