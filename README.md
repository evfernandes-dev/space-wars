# Space Wars

Jogo de nave no terminal feito em Java, no estilo dos jogos do Atari.

A ideia é simples e divertida: você voa livremente pelo mapa e atira nos
inimigos, e eles decidem sozinhos como te atacar usando o
[Ollama](https://ollama.com) (IA local) para pensar. Cada inimigo tem nome e
personalidade, então o comportamento muda de partida para partida.

## Como rodar

Precisa de Java 21+ e do Ollama com o modelo `llama3.2:1b`:

```bash
ollama pull llama3.2:1b
ollama serve
```

Depois, na pasta do projeto:

```bash
javac -encoding UTF-8 -cp "lib\*" -d out src\*.java
java -cp "out;lib/*" Main
```

No Linux ou macOS, troque `;` por `:` no classpath.

Se o Ollama estiver desligado o jogo não trava: os inimigos entram no modo
instinto e continuam atacando.

## Controles

- `WASD` ou setas: mover a nave
- `ESPAÇO` ou `F`: atirar
- `Q`: sair
- `R`: reiniciar depois do game over

## Estrutura

```text
src/Main.java      ponto de entrada
src/Game.java      loop, desenho e regras
src/Player.java    nave do jogador
src/Enemy.java     inimigos (só executam o plano do Ollama)
src/Bullet.java    tiros
src/OllamaAI.java  conversa com o Ollama
lib/               biblioteca JLine (teclado sem Enter)
```

## Observações

- O modelo usado é configurado em `OllamaAI.java`.
- Projeto simples, feito para aprender e se divertir.
