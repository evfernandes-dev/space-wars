import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Conversa com o Ollama de um jeito bem simples.
 *
 * Regras que o jogo precisa:
 * - só uma chamada por vez, porque o modelo é pequeno;
 * - tudo em segundo plano, para a tela não travar;
 * - se demorar ou der erro, devolve null e o jogo decide o que fazer;
 * - temperatura e seed mudam a cada chamada para o inimigo não repetir sempre a mesma ideia.
 */
public class OllamaAI {

    // Troque o modelo aqui se quiser outro (ex.: "qwen2.5:3b")
    public static final String MODELO = "llama3.2:1b";

    private static final String URL = "http://localhost:11434/api/generate";
    private static final int TIMEOUT_SEGUNDOS = 12;

    private final HttpClient cliente;
    private final ExecutorService executor;
    private final Random sorteio;

    private volatile boolean ocupado;
    private volatile Boolean online;

    public OllamaAI() {
        this.cliente = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();

        // Uma única thread trabalhando em segundo plano
        this.executor = Executors.newSingleThreadExecutor(tarefa -> {
            Thread thread = new Thread(tarefa, "ollama");
            thread.setDaemon(true);
            return thread;
        });

        this.sorteio = new Random();
    }

    public boolean estaOcupado() {
        return ocupado;
    }

    // null = ainda não sabemos, true = respondeu, false = falhou
    public Boolean estaOnline() {
        return online;
    }

    /**
     * Pede uma resposta curta ao modelo.
     * Retorna null (dentro do future) se já tiver chamada em andamento,
     * se estourar o tempo ou se der erro.
     */
    public CompletableFuture<String> gerarAsync(String prompt, double temperatura, int maxTokens) {

        synchronized (this) {
            if (ocupado) {
                return CompletableFuture.completedFuture(null);
            }

            ocupado = true;
        }

        CompletableFuture<String> futuro = CompletableFuture
                .supplyAsync(() -> chamar(prompt, temperatura, maxTokens), executor)
                .orTimeout(TIMEOUT_SEGUNDOS, TimeUnit.SECONDS)
                .exceptionally(erro -> "")
                .thenApply(texto -> texto == null || texto.isBlank() ? null : texto.trim());

        // Quando terminar (bem ou mal), libera para a próxima pergunta
        futuro.whenComplete((texto, erro) -> {
            synchronized (this) {
                ocupado = false;
            }
        });

        return futuro;
    }

    // Faz o POST de verdade. Roda na thread de segundo plano.
    private String chamar(String prompt, double temperatura, int maxTokens) {
        try {
            String corpo = "{"
                    + "\"model\":\"" + escapar(MODELO) + "\","
                    + "\"prompt\":\"" + escapar(prompt) + "\","
                    + "\"stream\":false,"
                    + "\"keep_alive\":\"15m\","
                    + "\"options\":{"
                    + "\"temperature\":" + temperatura + ","
                    + "\"top_p\":0.95,"
                    + "\"num_predict\":" + maxTokens + ","
                    + "\"seed\":" + sorteio.nextInt(1000000)
                    + "}}";

            HttpRequest requisicao = HttpRequest.newBuilder()
                    .uri(URI.create(URL))
                    .timeout(Duration.ofSeconds(TIMEOUT_SEGUNDOS))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(corpo))
                    .build();

            HttpResponse<String> resposta = cliente.send(
                    requisicao,
                    HttpResponse.BodyHandlers.ofString()
            );

            if (resposta.statusCode() != 200) {
                online = false;
                return "";
            }

            online = true;
            return pegarResponse(resposta.body());

        } catch (Exception e) {
            online = false;
            return "";
        }
    }

    // Escapa o texto para montar o JSON na mão
    private String escapar(String texto) {
        return texto
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    // Pega o campo "response" do JSON sem biblioteca externa
    private String pegarResponse(String json) {
        int chave = json.indexOf("\"response\"");

        if (chave < 0) {
            return "";
        }

        int doisPontos = json.indexOf(':', chave);

        if (doisPontos < 0) {
            return "";
        }

        int inicio = doisPontos + 1;

        while (inicio < json.length() && Character.isWhitespace(json.charAt(inicio))) {
            inicio++;
        }

        if (inicio >= json.length() || json.charAt(inicio) != '"') {
            return "";
        }

        inicio++;

        StringBuilder resultado = new StringBuilder();
        boolean escapado = false;

        for (int i = inicio; i < json.length(); i++) {
            char c = json.charAt(i);

            if (escapado) {
                if (c == 'n') {
                    resultado.append('\n');
                } else if (c == 'r') {
                    resultado.append('\r');
                } else if (c == 't') {
                    resultado.append('\t');
                } else if (c == '"') {
                    resultado.append('"');
                } else if (c == '\\') {
                    resultado.append('\\');
                } else {
                    resultado.append(c);
                }

                escapado = false;
            } else if (c == '\\') {
                escapado = true;
            } else if (c == '"') {
                return resultado.toString();
            } else {
                resultado.append(c);
            }
        }

        return resultado.toString();
    }
}
