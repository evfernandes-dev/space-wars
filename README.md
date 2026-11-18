# Space Wars

Terminal space shooter written in Java, inspired by classic Atari games. Enemies use a local Ollama model to decide how to attack.

## Requirements

- Java 21+
- Ollama with the `llama3.2:1b` model

## Run

```bash
ollama pull llama3.2:1b
ollama serve
javac -encoding UTF-8 -cp "lib\*" -d out src\*.java
java -cp "out;lib/*" Main
```

On Linux or macOS, use `:` instead of `;` in the classpath.

## Controls

- `WASD` or arrow keys: move
- `Space` or `F`: shoot
- `Q`: quit
- `R`: restart after game over

If Ollama is offline, the game still works in instinct mode.
