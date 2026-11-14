/**
 * Ponto de entrada do SPACE WARS.
 *
 * O Main só cria o jogo, registra uma limpeza de segurança para o terminal
 * e chama iniciar(). Toda a lógica fica dentro de Game.
 */
public class Main {

    public static void main(String[] args) {

        Game jogo = new Game();

        // Se o programa for interrompido, restaura o terminal
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            public void run() {
                jogo.fechar();
            }
        }));

        jogo.iniciar();
    }
}
