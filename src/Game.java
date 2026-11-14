import java.util.ArrayList;
import java.util.Random;
import java.util.Scanner;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.NonBlockingReader;

/**
 * Toda a lógica e o desenho do SPACE WARS ficam aqui.
 *
 * Novidades desta versão:
 * - a nave do jogador voa livre pelo mapa (WASD ou setas);
 * - cada inimigo tem nome e personalidade, e o Ollama decide o alvo e a rajada;
 * - se o Ollama estiver fora do ar, o inimigo entra no "instinto" e continua atacando.
 *
 * O JLine serve só para ler teclas sem Enter. Se não der, o jogo cai
 * para o modo por linha, digitando comandos.
 */
public class Game {

    // Tamanho do campo. A linha e a coluna das pontas são a borda.
    private static final int LARGURA = 64;
    private static final int ALTURA = 20;
    private static final int TICK_MS = 60;

    // Cores ANSI
    private static final String RESET = "\033[0m";
    private static final String VERDE = "\033[1;32m";
    private static final String VERMELHO = "\033[1;31m";
    private static final String AMARELO = "\033[1;33m";
    private static final String AZUL = "\033[1;34m";
    private static final String CIANO = "\033[1;36m";
    private static final String MAGENTA = "\033[1;35m";

    private static final String[] NOMES_BOSS = {"ZORK", "NEXUS", "OMEGA", "VORTEX"};

    private static final String[] NOMES_INIMIGOS = {
            "VIPER", "GHOST", "RAZOR", "NOVA", "HAVOC",
            "SABLE", "ONYX", "FURY", "ECHO", "JINX"
    };

    // Cada inimigo ganha uma personalidade. É isso que faz o Ollama
    // tomar decisões diferentes mesmo com o mesmo cenário.
    private static final String[] PERSONALIDADES = {
            "caçador frio, gosta de encurralar",
            "psicopata imprevisível, muda de plano toda hora",
            "atirador de elite, prefere matar de longe",
            "vingativo, recua e volta quando você erra",
            "imitador, copia o seu movimento e provoca",
            "kamikaze, avança sem medo",
            "estrategista paciente, flanqueia e espera",
            "nervoso, atira em rajada e nunca para"
    };

    private final OllamaAI ollama;
    private final Random random;
    private final Scanner scanner;

    // Respostas do Ollama entram aqui e são aplicadas pelo jogo no próximo tick.
    // Isso evita mexer no inimigo direto da thread da IA (mais seguro e simples).
    private final ConcurrentLinkedQueue<Resposta> respostas = new ConcurrentLinkedQueue<>();

    private Terminal terminal;
    private NonBlockingReader leitor;
    private boolean modoLinha;
    private boolean fechado;

    private Player player;
    private ArrayList<Enemy> inimigos;
    private ArrayList<Bullet> tiros;
    private boolean[][] estrelas;

    private int fase;
    private int pontos;
    private boolean gameOver;
    private boolean sair;

    private String mensagem;
    private volatile String mensagemIA;

    // Guardam a última posição do jogador para calcular a velocidade dele
    private int ultimoX;
    private int ultimoY;
    private int velX;
    private int velY;

    // Controle do modo instinto (quando o Ollama está fora do ar)
    private boolean avisouOffline;
    private long proximaTentativaOffline;

    // De quem é a vez de pensar. Sem isso, o primeiro inimigo da lista
    // puxa todas as decisões para si.
    private int vezDaIA;

    // Uma resposta da IA esperando para ser aplicada
    private static class Resposta {
        final Enemy inimigo;
        final String texto;

        Resposta(Enemy inimigo, String texto) {
            this.inimigo = inimigo;
            this.texto = texto;
        }
    }

    public Game() {
        this.ollama = new OllamaAI();
        this.random = new Random();
        this.scanner = new Scanner(System.in);
        this.mensagem = "";
        this.mensagemIA = "IA: acordando os inimigos...";
    }

    public void iniciar() {

        configurarEntrada();

        try {
            reiniciarJogo();

            while (!sair) {

                if (modoLinha) {
                    desenhar();
                    System.out.print("Comando (w/a/s/d/f, r, q): ");
                    System.out.flush();

                    String comando;

                    try {
                        comando = scanner.nextLine().trim().toLowerCase();
                    } catch (Exception e) {
                        sair = true;
                        break;
                    }

                    aplicarComando(comando);

                    if (!gameOver) {
                        atualizarTick();
                    }

                } else {
                    lerTeclasRaw();

                    if (!gameOver) {
                        atualizarTick();
                    }

                    desenhar();
                    dormir(TICK_MS);
                }
            }

        } finally {
            fechar();
        }
    }

    // Tenta ativar o modo raw do terminal com JLine
    private void configurarEntrada() {

        if (System.console() != null) {
            try {
                terminal = TerminalBuilder.builder().system(true).build();

                if (!"dumb".equals(terminal.getType())) {
                    terminal.enterRawMode();
                    terminal.resume();
                    leitor = terminal.reader();
                    modoLinha = false;
                } else {
                    terminal.close();
                    terminal = null;
                    modoLinha = true;
                }
            } catch (Exception e) {
                terminal = null;
                modoLinha = true;
            }
        } else {
            modoLinha = true;
        }

        System.out.print("\033[?25l");
    }

    // Lê teclas sem Enter no modo raw
    private void lerTeclasRaw() {
        try {
            int b = leitor.read(5);

            if (b == -1) {
                sair = true;
                return;
            }

            while (b >= 0) {

                if (b == 27) {
                    // Setas: ESC [ A/B/C/D ou ESC O A/B/C/D
                    int segundo = leitor.read(5);
                    int terceiro = leitor.read(5);

                    if (segundo == '[' || segundo == 'O') {
                        if (terceiro == 'A') {
                            aplicarComando("w");
                        } else if (terceiro == 'B') {
                            aplicarComando("s");
                        } else if (terceiro == 'C') {
                            aplicarComando("d");
                        } else if (terceiro == 'D') {
                            aplicarComando("a");
                        }
                    }
                } else if (b == 'w' || b == 'W') {
                    aplicarComando("w");
                } else if (b == 'a' || b == 'A') {
                    aplicarComando("a");
                } else if (b == 's' || b == 'S') {
                    aplicarComando("s");
                } else if (b == 'd' || b == 'D') {
                    aplicarComando("d");
                } else if (b == 'f' || b == 'F' || b == ' ') {
                    aplicarComando("f");
                } else if (b == 'r' || b == 'R') {
                    if (gameOver) {
                        reiniciarJogo();
                    }
                } else if (b == 'q' || b == 'Q' || b == 3) {
                    sair = true;
                }

                if (sair) {
                    break;
                }

                b = leitor.read(5);
            }

        } catch (Exception e) {
            sair = true;
        }
    }

    // Aplica um comando do jogador (tanto faz se veio do teclado ou da linha)
    private void aplicarComando(String comando) {

        if (comando == null || comando.isEmpty()) {
            return;
        }

        if (comando.equals("w")) {
            player.mover(0, -1, LARGURA, ALTURA);
        } else if (comando.equals("s")) {
            player.mover(0, 1, LARGURA, ALTURA);
        } else if (comando.equals("a")) {
            player.mover(-1, 0, LARGURA, ALTURA);
        } else if (comando.equals("d")) {
            player.mover(1, 0, LARGURA, ALTURA);
        } else if (comando.equals("f") || comando.equals(" ")) {
            atirar();
        } else if (comando.equals("r")) {
            if (gameOver) {
                reiniciarJogo();
            }
        } else if (comando.equals("q")) {
            sair = true;
        }
    }

    private void atirar() {
        if (gameOver) {
            return;
        }

        Bullet tiro = player.atirar();

        if (tiro != null) {
            tiros.add(tiro);
        }
    }

    // Reinicia tudo para uma nova partida
    private void reiniciarJogo() {

        fase = 1;
        pontos = 0;
        gameOver = false;
        sair = false;

        player = new Player(LARGURA / 2, ALTURA - 2);
        inimigos = new ArrayList<>();
        tiros = new ArrayList<>();
        estrelas = new boolean[ALTURA][LARGURA];

        respostas.clear();
        ultimoX = player.getX();
        ultimoY = player.getY();
        velX = 0;
        velY = 0;

        avisouOffline = false;
        proximaTentativaOffline = 0;
        mensagemIA = "IA: acordando os inimigos...";

        gerarEstrelas();
        iniciarFase();
    }

    private void gerarEstrelas() {
        for (int y = 0; y < ALTURA; y++) {
            for (int x = 0; x < LARGURA; x++) {
                estrelas[y][x] = random.nextInt(100) < 12;
            }
        }
    }

    // Cria os inimigos da fase atual
    private void iniciarFase() {

        inimigos.clear();
        tiros.clear();
        respostas.clear();
        vezDaIA = 0;

        long agora = System.currentTimeMillis();

        if (fase % 3 == 0) {
            String nome = NOMES_BOSS[(fase / 3 - 1) % NOMES_BOSS.length];
            Enemy chefe = new Enemy(LARGURA / 2, 2, fase, true, nome,
                    "chefe arrogante e sádico, quer humilhar o jogador");
            chefe.proximaDecisaoEm = agora + 200;

            inimigos.add(chefe);
            mensagem = "ALERTA: BOSS " + nome + " APARECEU!";

            pedirDialogoBoss(nome);

        } else {
            int quantidade = Math.min(3 + fase, 7);

            for (int i = 0; i < quantidade; i++) {
                int x = 4 + i * ((LARGURA - 8) / Math.max(1, quantidade - 1));
                String nome = NOMES_INIMIGOS[(i + fase + random.nextInt(NOMES_INIMIGOS.length)) % NOMES_INIMIGOS.length];
                String personalidade = PERSONALIDADES[(i * 3 + fase) % PERSONALIDADES.length];

                Enemy inimigo = new Enemy(x, 2 + random.nextInt(2), fase, false, nome, personalidade);

                // Espalha as primeiras decisões para não perguntar tudo de uma vez
                inimigo.proximaDecisaoEm = agora + 250 + i * 400;

                inimigos.add(inimigo);
            }

            // Mostra os nomes para você já saber com quem está lidando
            StringBuilder nomes = new StringBuilder();

            for (Enemy inimigo : inimigos) {
                if (nomes.length() > 0) {
                    nomes.append(", ");
                }

                nomes.append(inimigo.getNome());
            }

            mensagem = "FASE " + fase + " - " + nomes;
        }
    }

    // Um tick do jogo: IA, tiros, inimigos e colisões
    private void atualizarTick() {

        long agora = System.currentTimeMillis();

        // Velocidade do jogador = diferença para o último tick.
        // A IA usa isso para prever para onde você está indo.
        velX = limitar(player.getX() - ultimoX, -1, 1);
        velY = limitar(player.getY() - ultimoY, -1, 1);
        ultimoX = player.getX();
        ultimoY = player.getY();

        atualizarIA(agora);
        aplicarRespostas();

        int previsaoX = limitar(player.getX() + velX * 5, 1, LARGURA - 2);
        int previsaoY = limitar(player.getY() + velY * 5, 1, ALTURA - 2);

        player.atualizarTick();
        moverTiros();
        moverInimigos(previsaoX, previsaoY);
        colisoes();
        verificarFase();
    }

    // Pede planos ao Ollama ou aciona o instinto local
    private void atualizarIA(long agora) {

        boolean online = Boolean.TRUE.equals(ollama.estaOnline());

        if (!online) {
            // Ollama fora do ar: instinto simples (perseguição + rajada)
            for (Enemy inimigo : inimigos) {
                int desvio = random.nextInt(9) - 4;
                int alvoX = limitar(player.getX() + desvio, 1, LARGURA - 2);
                int alvoY = limitar(player.getY() - 4, 1, ALTURA - 2);

                inimigo.pensar(alvoX, alvoY, random.nextInt(2), "instinto");
            }

            if (!avisouOffline) {
                avisouOffline = true;
                mensagemIA = "IA offline: inimigos no instinto. Tentando reconectar...";
            }

            // De vez em quando tentamos de novo, caso o Ollama volte
            if (agora >= proximaTentativaOffline) {
                proximaTentativaOffline = agora + 10000;

                if (!inimigos.isEmpty()) {
                    pedirDecisao(inimigos.get(0), agora);
                }
            }

            return;
        }

        // Com o Ollama online, cada inimigo pensa no seu ritmo.
        // O rodízio garante que todos tenham chance de decidir.
        for (int k = 0; k < inimigos.size(); k++) {
            Enemy inimigo = inimigos.get((vezDaIA + k) % inimigos.size());

            if (agora >= inimigo.proximaDecisaoEm && !ollama.estaOcupado()) {
                pedirDecisao(inimigo, agora);
                vezDaIA = (vezDaIA + k + 1) % inimigos.size();
                break;
            }
        }
    }

    // Manda o cenário para o Ollama. A resposta cai na fila.
    private void pedirDecisao(Enemy inimigo, long agora) {

        inimigo.proximaDecisaoEm = agora + intervaloDele(inimigo);

        String prompt = montarPrompt(inimigo);

        ollama.gerarAsync(prompt, 1.15, 90).thenAccept(texto -> {
            if (texto != null && !texto.isBlank()) {
                respostas.add(new Resposta(inimigo, texto));
            }
        });
    }

    private int intervaloDele(Enemy inimigo) {
        return inimigo.isBoss()
                ? 1700 + random.nextInt(900)
                : 2600 + random.nextInt(1500);
    }

    // Aplica as respostas que chegaram do Ollama
    private void aplicarRespostas() {

        Resposta resposta;

        while ((resposta = respostas.poll()) != null) {

            // Ignora resposta de inimigo que já morreu (ou de outra fase)
            if (!inimigos.contains(resposta.inimigo)) {
                continue;
            }

            Integer alvoX = lerNumero(resposta.texto, "alvo_x");
            Integer alvoY = lerNumero(resposta.texto, "alvo_y");

            // Resposta veio estranha? O inimigo segue o plano antigo
            if (alvoX == null || alvoY == null) {
                continue;
            }

            String pensamento = lerTexto(resposta.texto, "pensamento");
            int rajada = lerRajada(resposta.texto);

            resposta.inimigo.pensar(
                    limitar(alvoX, 1, LARGURA - 2),
                    limitar(alvoY, 1, ALTURA - 2),
                    rajada,
                    pensamento == null ? "..." : pensamento
            );

            mensagemIA = "MENTE " + resposta.inimigo.getNome() + ": " + resposta.inimigo.getPensamento();
        }
    }

    // Monta o texto que o modelo vai ler. Quanto mais curto, mais rápido ele responde.
    private String montarPrompt(Enemy inimigo) {

        String memoria = (inimigo.getPensamento() == null) ? "nenhuma" : inimigo.getPensamento();

        return "Você é o inimigo " + inimigo.getNome() + " de um jogo de nave no terminal. "
                + "Sua personalidade: " + inimigo.getPersonalidade() + ". "
                + "Sua última ideia: " + memoria + ". "
                + "Você está em x=" + inimigo.getX() + ", y=" + inimigo.getY()
                + " (o campo vai de x=1 a " + (LARGURA - 2) + " e y=1 a " + (ALTURA - 2) + "). "
                + "O jogador está em x=" + player.getX() + ", y=" + player.getY()
                + " e pode fugir para qualquer lado. Ele tem " + player.getVidas() + " vidas. "
                + "Fase " + fase + ". Seja imprevisível: persiga, flanqueie, finja, cerque, atire. "
                + "Responda SOMENTE com um JSON neste formato, trocando os valores por uma ideia sua: "
                + "{\"pensamento\":\"<ideia curta>\",\"alvo_x\":10,\"alvo_y\":5,\"atirar\":1}";
    }

    // Lê um número do JSON na mão: acha a chave e depois os dígitos
    private Integer lerNumero(String texto, String chave) {

        int pos = texto.indexOf("\"" + chave + "\"");

        if (pos < 0) {
            return null;
        }

        int doisPontos = texto.indexOf(':', pos);

        if (doisPontos < 0) {
            return null;
        }

        int i = doisPontos + 1;

        while (i < texto.length()
                && !Character.isDigit(texto.charAt(i))
                && texto.charAt(i) != '-') {
            i++;
        }

        int inicio = i;

        if (i < texto.length() && texto.charAt(i) == '-') {
            i++;
        }

        while (i < texto.length() && Character.isDigit(texto.charAt(i))) {
            i++;
        }

        if (i == inicio) {
            return null;
        }

        try {
            return Integer.parseInt(texto.substring(inicio, i));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // Lê um texto entre aspas depois da chave (usado para o pensamento)
    private String lerTexto(String texto, String chave) {

        int pos = texto.indexOf("\"" + chave + "\"");

        if (pos < 0) {
            return null;
        }

        int doisPontos = texto.indexOf(':', pos);

        if (doisPontos < 0) {
            return null;
        }

        int abre = texto.indexOf('"', doisPontos);

        if (abre < 0) {
            return null;
        }

        int fecha = texto.indexOf('"', abre + 1);

        if (fecha < 0) {
            return null;
        }

        String valor = texto.substring(abre + 1, fecha).trim();

        if (valor.length() > 80) {
            valor = valor.substring(0, 80) + "...";
        }

        return valor;
    }

    private int lerRajada(String texto) {
        Integer numero = lerNumero(texto, "atirar");

        if (numero != null) {
            return limitar(numero, 0, 3);
        }

        // Alguns modelos respondem true/false em vez de número
        if (texto.toLowerCase().contains("\"atirar\":true")) {
            return 1;
        }

        return 0;
    }

    // Move os tiros e tira da lista os que saíram da tela
    private void moverTiros() {
        for (int i = tiros.size() - 1; i >= 0; i--) {
            Bullet tiro = tiros.get(i);
            tiro.mover();

            if (tiro.foraDaTela(LARGURA, ALTURA)) {
                tiros.remove(i);
            }
        }
    }

    // Cada inimigo executa o plano atual
    private void moverInimigos(int previsaoX, int previsaoY) {
        for (Enemy inimigo : inimigos) {
            Bullet tiro = inimigo.atualizar(LARGURA, ALTURA, previsaoX, previsaoY, inimigos);

            if (tiro != null) {
                tiros.add(tiro);
            }
        }
    }

    // Colisões de corpos e de tiros
    private void colisoes() {

        // Inimigo em cima do jogador (o chefe conta com o corpo todo)
        for (Enemy inimigo : inimigos) {
            if (inimigo.contem(player.getX(), player.getY())) {
                inimigo.recuar();

                if (!player.estaInvulneravel()) {
                    player.perderVida();
                    mensagem = inimigo.getNome() + " colidiu com você! Vidas: " + player.getVidas();

                    pedirDicaCopiloto();

                    if (player.getVidas() <= 0) {
                        gameOver = true;
                        mensagem = "GAME OVER";
                        return;
                    }
                }
            }
        }

        for (int i = tiros.size() - 1; i >= 0; i--) {
            Bullet tiro = tiros.get(i);

            if (tiro.isDoJogador()) {
                acertouInimigo(tiro, i);
            } else if (tiro.getX() == player.getX() && tiro.getY() == player.getY()) {
                tiros.remove(i);

                if (!player.estaInvulneravel()) {
                    player.perderVida();
                    mensagem = "Você foi atingido! Vidas: " + player.getVidas();

                    pedirDicaCopiloto();

                    if (player.getVidas() <= 0) {
                        gameOver = true;
                        mensagem = "GAME OVER";
                        return;
                    }
                }
            }
        }
    }

    // Tiro do jogador acertou alguém?
    private void acertouInimigo(Bullet tiro, int indice) {

        for (int j = inimigos.size() - 1; j >= 0; j--) {
            Enemy inimigo = inimigos.get(j);

            if (inimigo.contem(tiro.getX(), tiro.getY())) {
                inimigo.receberDano();
                tiros.remove(indice);

                if (!inimigo.estaVivo()) {
                    pontos += inimigo.getPontos();

                    if (inimigo.isBoss()) {
                        mensagem = "BOSS " + inimigo.getNome() + " DERROTADO! +" + inimigo.getPontos();
                    } else {
                        mensagem = inimigo.getNome() + " destruído! +" + inimigo.getPontos();
                    }

                    inimigos.remove(j);
                }

                return;
            }
        }
    }

    // Se limpou a fase, começa a próxima
    private void verificarFase() {
        if (!gameOver && inimigos.isEmpty()) {
            fase++;
            iniciarFase();
        }
    }

    // Pede uma apresentação para o chefe
    private void pedirDialogoBoss(String nome) {

        String prompt = "Você é o chefe " + nome + " de um jogo de nave no terminal. "
                + "Fase " + fase + ". Ameace o jogador em português, com no máximo 12 palavras. "
                + "Responda apenas com a fala.";

        String[] reservas = {
                nome + " está aqui. Sua nave será poeira espacial.",
                "Nenhum herói escapa de " + nome + ".",
                nome + " já viu o seu fim."
        };

        ollama.gerarAsync(prompt, 1.0, 60).thenAccept(frase -> {
            mensagemIA = nome + ": "
                    + (frase != null ? frase : reservas[random.nextInt(reservas.length)]);
        });
    }

    // Pede uma dica ao copiloto com dados reais da partida
    private void pedirDicaCopiloto() {

        String prompt = "Você é o copiloto do jogador em um jogo de nave no terminal. "
                + "Dados reais: vidas " + player.getVidas()
                + ", fase " + fase
                + ", pontos " + pontos
                + ", inimigos " + inimigos.size() + ". "
                + "O jogador pode voar para qualquer lado do mapa. "
                + "Dê uma dica curta em português, com no máximo 12 palavras. "
                + "Não altere regras, vidas ou pontos.";

        String[] reservas = {
                "Cuidado! Sua nave está com pouca vida.",
                "Você pode voar para qualquer lado. Use isso!",
                "Não fique parado: os inimigos pensam rápido.",
                "Atire enquanto se move para não virar alvo fácil."
        };

        ollama.gerarAsync(prompt, 0.9, 60).thenAccept(frase -> {
            mensagemIA = "COPILOTO: "
                    + (frase != null ? frase : reservas[random.nextInt(reservas.length)]);
        });
    }

    // Desenha a tela inteira
    private void desenhar() {

        StringBuilder tela = new StringBuilder();
        tela.append("\033[H\033[2J");

        if (gameOver) {
            desenharGameOver(tela);
            System.out.print(tela);
            System.out.flush();
            return;
        }

        tela.append(CIANO).append("================================================================\n");
        tela.append(CIANO).append("                          SPACE WARS\n");
        tela.append(CIANO).append("================================================================\n");

        for (int y = 0; y < ALTURA; y++) {

            for (int x = 0; x < LARGURA; x++) {

                // Borda do mapa
                if (y == 0 || y == ALTURA - 1) {
                    char c = (x == 0 || x == LARGURA - 1) ? '+' : '-';
                    tela.append(CIANO).append(c).append(RESET);
                    continue;
                }

                if (x == 0 || x == LARGURA - 1) {
                    tela.append(CIANO).append('|').append(RESET);
                    continue;
                }

                boolean desenhou = false;

                // Jogador
                if (x == player.getX() && y == player.getY()) {

                    if (player.estaInvulneravel()) {
                        tela.append(AMARELO);
                    } else {
                        tela.append(VERDE);
                    }

                    tela.append('^').append(RESET);
                    desenhou = true;
                }

                // Inimigos
                if (!desenhou) {
                    for (Enemy inimigo : inimigos) {

                        if (inimigo.isBoss()) {
                            int dx = x - inimigo.getX();
                            int dy = y - inimigo.getY();

                            if (dx >= -2 && dx <= 2 && dy >= 0 && dy <= 2) {
                                char c = inimigo.simboloBoss(dx, dy);

                                if (c != ' ') {
                                    tela.append(MAGENTA).append(c).append(RESET);
                                    desenhou = true;
                                }
                            }
                        } else if (x == inimigo.getX() && y == inimigo.getY()) {
                            tela.append(VERMELHO).append('V').append(RESET);
                            desenhou = true;
                        }

                        if (desenhou) {
                            break;
                        }
                    }
                }

                // Tiros
                if (!desenhou) {
                    for (Bullet tiro : tiros) {

                        if (tiro.getX() == x && tiro.getY() == y) {

                            if (tiro.isDoJogador()) {
                                tela.append(AMARELO).append('|');
                            } else {
                                tela.append(VERMELHO).append('*');
                            }

                            tela.append(RESET);
                            desenhou = true;
                            break;
                        }
                    }
                }

                // Estrelas e espaço vazio
                if (!desenhou) {
                    if (estrelas[y][x]) {
                        tela.append(AZUL).append('*').append(RESET);
                    } else {
                        tela.append(' ');
                    }
                }
            }

            tela.append('\n');
        }

        tela.append(CIANO).append("----------------------------------------------------------------\n");

        tela.append("VIDAS: ").append(player.getVidas())
                .append("   PONTOS: ").append(pontos)
                .append("   FASE: ").append(fase);

        Enemy boss = acharBoss();

        if (boss != null) {
            tela.append("   BOSS ").append(boss.getNome())
                    .append(" ").append(barraVida(boss.getHp(), boss.getHpMax(), 10))
                    .append(" ").append(boss.getHp()).append("/").append(boss.getHpMax());
        }

        tela.append('\n');
        tela.append(mensagem).append('\n');
        tela.append(MAGENTA).append(mensagemIA).append(RESET).append('\n');

        if (modoLinha) {
            tela.append("w/a/s/d mover   f atirar   r reiniciar   q sair   (Enter)\n");
        } else {
            tela.append("WASD/setas: mover   ESPACO/f: atirar   Q: sair\n");
        }

        System.out.print(tela);
        System.out.flush();
    }

    private void desenharGameOver(StringBuilder tela) {

        tela.append(CIANO).append("================================================================\n");
        tela.append(CIANO).append("                          SPACE WARS\n");
        tela.append(CIANO).append("================================================================\n");
        tela.append('\n');
        tela.append(VERMELHO).append("                         GAME OVER\n").append(RESET);
        tela.append('\n');
        tela.append("PONTOS FINAIS: ").append(pontos).append('\n');
        tela.append("FASE ALCANÇADA: ").append(fase).append('\n');
        tela.append('\n');

        if (modoLinha) {
            tela.append("Digite r para reiniciar ou q para sair (Enter).\n");
        } else {
            tela.append("R - reiniciar   Q - sair\n");
        }

        tela.append('\n');
        tela.append(MAGENTA).append(mensagemIA).append(RESET).append('\n');
    }

    private Enemy acharBoss() {
        for (Enemy inimigo : inimigos) {
            if (inimigo.isBoss()) {
                return inimigo;
            }
        }

        return null;
    }

    private String barraVida(int atual, int maximo, int tamanho) {

        int cheios = 0;

        if (maximo > 0) {
            cheios = (atual * tamanho) / maximo;
        }

        StringBuilder barra = new StringBuilder();
        barra.append('[');

        for (int i = 0; i < tamanho; i++) {
            if (i < cheios) {
                barra.append('#');
            } else {
                barra.append('-');
            }
        }

        barra.append(']');
        return barra.toString();
    }

    private static int limitar(int valor, int minimo, int maximo) {
        return Math.max(minimo, Math.min(maximo, valor));
    }

    private void dormir(int milissegundos) {
        try {
            Thread.sleep(milissegundos);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // Fecha o JLine e devolve o terminal ao normal
    public synchronized void fechar() {

        if (fechado) {
            return;
        }

        fechado = true;
        System.out.print("\033[?25h");
        System.out.flush();

        if (terminal != null) {
            try {
                terminal.close();
            } catch (Exception e) {
                // Nada para fazer aqui
            }

            terminal = null;
        }
    }
}
