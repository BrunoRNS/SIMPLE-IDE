# TODO — Debug/Breakpoints + DAP (branch `feature-runio-debug`)

## Contexto
- Repositório: `/workspaces/SIMPLE-IDE` · branch `feature-runio-debug`.
- Objetivo: corrigir breakpoints (UI editável) e a comunicação DAP para
  **Go, Python, JS** (stdio + tcp) e **Java** (Eclipse JDT LS / jdtls integrado ao LSP).
- **O usuário NÃO quer novos testes.** Focar em código funcionando, docs e build verde.

## Já feito (alterações não commitadas)
- `simpleide/.../ui/editor/Editor.java`: estado de hover + ícone translúcido
  (não selecionado) e vermelho preenchido (selecionado) via `BreakpointIcon(selected)`.
- `simpleide/.../ui/editor/EditorTab.java`: clique no gutter mapeia a linha com x
  clampado no `viewToModel2D`; hover por `mouseMoved`/`mouseExited`; `lineAtGutterPoint()`.
- `simpleide/.../lsp/client/LspClient.java`: `executeCommand(String, List<Object>)`
  usando `workspaceService.executeCommand(ExecuteCommandParams)`.
- `simpleide/.../dap/JavaDebugBridge.java` (novo): interface `JavaDebugClient`,
  `startDebugSession` (`vscode.java.startDebugSession`), `launchArguments`
  (`vscode.java.resolveMainClass` / `vscode.java.resolveClasspath`) e `start()`.
- `simpleide/.../dap/DapManager.java`: `startJava(...)`, `isLanguageServerDebug(...)`,
  `catch RuntimeException` em `start()`.
- `simpleide/.../ui/editor/EditorTab.java`: campo `languageServer` + `getLanguageServer()`
  + limpeza no `close()`.
- `simpleide/.../ui/editor/EditorManager.java`: `connectLanguageServer(Document)` público.
- `simpleide/.../frame/MainFrame.java`: `startDebugSession()` separa Java (jdtls via LSP)
  de adapter standalone; imports `LspClient`/`JavaDebugBridge`.

## Pendente (nesta ordem)
1. **Remover/ignorar os testes criados** para não quebrarem o build:
   - `simpleide/src/test/java/io/simpleide/dap/FakeDapServer.java`
   - `simpleide/src/test/java/io/simpleide/dap/DapSessionTransportTest.java`
   - `simpleide/src/test/java/io/simpleide/dap/DapLanguageRegistryTest.java`
2. **Verificar o fix do flake Windows x86** em
   `simpleide/src/test/java/io/simpleide/ui/editor/EditorManagerTest.java`
   (editado: import `AtomicBoolean` + cast `Callable<Boolean>`; ainda não validado).
3. **Compilar**: `./mvnw -B -DskipTests compile` e corrigir erros
   (confirmar campo `editorManager` no `MainFrame` e import `ExecuteCommandParams`).
4. **Formatação**: `make check-format` (spring-javaformat + `JavaImportFormatter --check`);
   remover imports não usados.
5. **Docs**: `docs/api/dap.md` e `docs/reference/DAP.md`
   (transporte `jdtls` + integração LSP, args de launch Java, UI de breakpoint
   hover/selecionado, transportes por linguagem).
6. **Validação real do jdtls** exige JDT LS + plugin java-debug — documentar o requisito;
   não afirmar verificado sem isso.
7. **Build final verde**: `make test` / `make verify` no Linux.

## Observações
- `EditorManagerTest.opensLargeTextInThePagedReadOnlyViewer` falhava rápido no Windows:
  a asserção dentro de `invokeAndWait` não é retentada pelo Awaitility. A correção usa
  polling booleano (`AtomicBoolean`) com timeout de 20s.
- Protocolos DAP: `stdio` e `tcp` já funcionam com servidor fake; `jdtls` usa porta
  externa retornada pelo LSP (`vscode.java.startDebugSession`).