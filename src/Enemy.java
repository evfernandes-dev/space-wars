import java.util.ArrayList;
import java.util.Random;

/**
 * Um inimigo do jogo.
 *
 * A ideia é deixar esta classe bem burrinha: ela só sabe andar e atirar.
 * QUEM decide para onde ir e quando atirar é o Ollama.
 * O Game manda o plano pelo método pensar() e o inimigo só obedece.
 */
public class Enemy {

    // Sortear coisas pequenas (espiar para o lado, atraso de tiro)
    private static final Random SORTE = new Random();

    private int x;
    private int y;
    private final boolean boss;
    private final String nome;
    private final String personalidade;
    private final int pontos;

    private int hp;
    private final int hpMax;

    // Quantos ticks o inimigo espera antes de andar 1 casa (menor = mais rápido)
    private final int velocidade;

    // O plano atual, escrito pelo Ollama
    private int alvoX;
    private int alvoY;
    private int rajada;
    private String pensamento;

    // Quando o Game deve pedir um novo plano ao Ollama
    public long proximaDecisaoEm;

    private int contadorMovimento;
    private int contadorTiro;

    public Enemy(int x, int y, int fase, boolean boss, String nome, String personalidade) {
        this.x = x;
        this.y = y;
        this.boss = boss;
        this.nome = nome;
        this.personalidade = personalidade;

        if (boss) {
            this.hp = 45 + fase * 12;
            this.velocidade = 2;
            this.pontos = 1000 + fase * 100;
        } else {
            // Inimigo comum morre com 1 tiro. O perigo é o comportamento dele.
            this.hp = 1;
            this.velocidade = Math.max(2, 5 - fase / 2);
            this.pontos = 100;
        }

        this.hpMax = this.hp;
        this.alvoX = x;
        this.alvoY = y;
        this.pensamento = "entrando em posição";
        this.proximaDecisaoEm = System.currentTimeMillis();
        this.contadorMovimento = this.velocidade;
        this.contadorTiro = 8 + SORTE.nextInt(10);
    }

    /**
     * Recebe o plano novo que o Ollama mandou.
     * Só é chamado pela thread principal do jogo, então não precisa de truque.
     */
    public void pensar(int alvoX, int alvoY, int rajada, String pensamento) {
        this.alvoX = alvoX;
        this.alvoY = alvoY;
        this.rajada = rajada;
        this.pensamento = pensamento;
    }

    /**
     * Roda um tick do inimigo.
     * Devolve um tiro quando for a hora de atirar, ou null.
     *
     * previsaoX/previsaoY = onde o jogador deve estar daqui a pouco
     * (o Game calcula isso para a mira não ser sempre atrasada).
     */
    public Bullet atualizar(int largura, int altura, int previsaoX, int previsaoY,
                            ArrayList<Enemy> todos) {

        // Anda um pouco, se já passou o tempo de espera
        if (contadorMovimento > 0) {
            contadorMovimento--;
        }

        if (contadorMovimento == 0) {
            andar(largura, altura, todos);
            contadorMovimento = velocidade;
        }

        // Conta o tempo para o próximo tiro da rajada
        if (contadorTiro > 0) {
            contadorTiro--;
        }

        // Só atira se o plano mandou atirar
        if (rajada > 0 && contadorTiro == 0) {
            rajada--;
            contadorTiro = 4 + SORTE.nextInt(3);
            return mirar(previsaoX, previsaoY);
        }

        return null;
    }

    // Dá um passo na direção do alvo, sem passar por cima de outro inimigo
    private void andar(int largura, int altura, ArrayList<Enemy> todos) {
        int passoX = Integer.compare(alvoX, x);
        int passoY = Integer.compare(alvoY, y);

        // Vai primeiro pelo lado que está mais longe
        if (Math.abs(alvoX - x) >= Math.abs(alvoY - y)) {
            if (passoX != 0 && livre(x + passoX, y, todos)) {
                x += passoX;
            } else if (passoY != 0 && livre(x, y + passoY, todos)) {
                y += passoY;
            }
        } else {
            if (passoY != 0 && livre(x, y + passoY, todos)) {
                y += passoY;
            } else if (passoX != 0 && livre(x + passoX, y, todos)) {
                x += passoX;
            }
        }

        // Chegou no alvo? Dá uma espiada para o lado para não ficar plantado
        if (passoX == 0 && passoY == 0) {
            int olhada = SORTE.nextInt(3) - 1;

            if (olhada != 0 && livre(x + olhada, y, todos)) {
                x += olhada;
            }
        }

        x = limitar(x, 1, largura - 2);
        y = limitar(y, 1, altura - 2);
    }

    // Calcula a direção do tiro até onde o jogador deve estar
    private Bullet mirar(int previsaoX, int previsaoY) {
        int dx = Integer.compare(previsaoX, x);
        int dy = Integer.compare(previsaoY, y);

        if (dx == 0 && dy == 0) {
            dy = 1; // jogador em cima do inimigo: atira para baixo mesmo assim
        }

        int yTiro = y + (boss ? 3 : 0);
        return new Bullet(x, yTiro, dx, dy, false);
    }

    // Verifica se a casa (nx, ny) está livre de outro inimigo
    private boolean livre(int nx, int ny, ArrayList<Enemy> todos) {
        for (Enemy outro : todos) {
            if (outro != this && outro.getX() == nx && outro.getY() == ny) {
                return false;
            }
        }

        return true;
    }

    // Verifica se um tiro acertou este inimigo
    public boolean contem(int px, int py) {
        if (!boss) {
            return px == x && py == y;
        }

        return px >= x - 2 && px <= x + 2 && py >= y && py <= y + 2;
    }

    // Empurra o inimigo para cima quando ele esbarra no jogador
    public void recuar() {
        y = Math.max(1, y - 3);
    }

    public void receberDano() {
        if (hp > 0) {
            hp--;
        }
    }

    public boolean estaVivo() {
        return hp > 0;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public boolean isBoss() {
        return boss;
    }

    public String getNome() {
        return nome;
    }

    public String getPersonalidade() {
        return personalidade;
    }

    public String getPensamento() {
        return pensamento;
    }

    public int getHp() {
        return hp;
    }

    public int getHpMax() {
        return hpMax;
    }

    public int getPontos() {
        return pontos;
    }

    private static int limitar(int valor, int minimo, int maximo) {
        return Math.max(minimo, Math.min(maximo, valor));
    }

    // Desenha uma parte do chefe (dx e dy são relativos ao centro)
    public char simboloBoss(int dx, int dy) {
        if (dy == 0) {
            if (dx == -1) return '/';
            if (dx == 0) return 'o';
            if (dx == 1) return '\\';
        } else if (dy == 1) {
            if (dx == -2) return '<';
            if (dx == 2) return '>';
            if (dx >= -1 && dx <= 1) return '=';
        } else if (dy == 2) {
            if (dx == -1) return '\\';
            if (dx == 0) return '_';
            if (dx == 1) return '/';
        }

        return ' ';
    }
}
