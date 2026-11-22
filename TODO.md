# TODO — Barra lateral estilo VS Code

Mapa de cada requisito para a solução implementada. Status: ✅ resolvido · 🚧 em andamento · ⏳ pendente.

## Build e qualidade

| # | Problema / requisito | Solução | Status |
|---|---|---|---|
| 1 | `WorkspaceSearchService` não compilava (`searchFile` com assinatura antiga) | Removido o parâmetro `root` de `searchFile(Path, String, boolean, int, List)` em `io.simpleide.search.WorkspaceSearchService` | ✅ |
| 2 | 5 arquivos violando o `spring-javaformat` | `mvn -pl simpleide spring-javaformat:apply` em todo o módulo | ✅ |
| 3 | Build e testes não verificados | `mvn -pl simpleide -am -DskipTests compile` e `mvn -pl simpleide test` | ✅ |

## Sidebar / views

| # | Requisito | Solução | Status |
|---|---|---|---|
| 4 | Barra de itens à esquerda (Explorer, Search, Settings embaixo) | `ui/sidebar/ActivityBar` + `SidebarView` + `SidebarPanel` | ✅ |
| 5 | Explorer começa com “No folder opened” | `ui/sidebar/ExplorerPanel` (CardLayout vazio/tree); `MainFrame` não carrega `user.dir` sozinho | ✅ |
| 6 | Search com interface usando o serviço de busca | `ui/sidebar/WorkspaceSearchPanel` (campo, match case, resultados agrupados por arquivo, duplo clique abre a linha) + `search/WorkspaceSearchService` | ✅ |
| 7 | Settings alterando tema, fontes, editor e atalhos | `ui/sidebar/SettingsPanel` + `settings/SettingsService`, `IdeSettings`, `IdeAction`, `KeybindingManager` | ✅ |
| 8 | Debug Console mostrando erros/warnings LSP com linha | `ui/sidebar/DebugConsolePanel` + `EditorTab.getDiagnostics()` + `EditorManager.addDiagnosticsListener` (despachado no EDT via `ThreadMaster`) | ✅ |
| 9 | Ctrl+B fecha qualquer view aberta à esquerda | `MainFrame.toggleSidebar()` + `IdeAction.TOGGLE_SIDEBAR` | ✅ |
| 10 | Explorer aberto por padrão; Search/Settings ocupam o lugar | `SidebarPanel.showView(EXPLORER)` + `ActivityBar` ligado a `MainFrame.showSidebarView` | ✅ |

## Menus e atalhos

| # | Requisito | Solução | Status |
|---|---|---|---|
| 11 | Renomear “Set directory” para “Open Folder” | `FileMenu`/`IdeMenuBar`/`MenuBarFactory` com callback `openFolder` | ✅ |
| 12 | Adicionar “Close Folder” | Item `Close Folder` no `FileMenu` + `MainFrame.closeFolder()` (fecha editores e watcher) | ✅ |
| 13 | Ordem alfabética no File menu | Close Folder, New File, New Folder, Open Folder, Save | ✅ |
| 14 | Atalhos para Open/Close Folder, New File, Close Editor, Close All, Save All | `IdeAction` + `KeybindingManager` instalado no root pane pelo `MainFrame` | ✅ |
| 15 | Atalhos customizáveis nas settings | `SettingsPanel` → `SettingsService` → `KeybindingManager.reload/install` + labels de acelerador atualizados via `FileMenu.applySettings` | ✅ |

## Fluxo de pastas e arquivos

| # | Requisito | Solução | Status |
|---|---|---|---|
| 16 | Sem pasta: New File usa `JFileChooser`, cria arquivo, abre a pasta no Explorer e o arquivo no editor | `MainFrame.createNewFile()` + `createFileAndOpen()` + `loadFolder()` + `editorManager.openFile()` | ✅ |
| 17 | Close Folder também fecha os editores | `MainFrame.closeFolder()` → `EditorManager.closeAllEditors()` | ✅ |
| 18 | Open Folder via `JFileChooser` | `MainFrame.openFolder()` | ✅ |

## Layout e painel inferior

| # | Requisito | Solução | Status |
|---|---|---|---|
| 19 | Painel inferior à direita da sidebar e abaixo do editor | Split horizontal `[activity bar + sidebar]` × split vertical `[editor]` / `[painel inferior]` no `MainFrame` | ✅ |
| 20 | Renomear “Debug Console” para “Output” | Aba `Output` com `LogPanel` | ✅ |
| 21 | Nova aba “Debug Console” com diagnóstico LSP | `DebugConsolePanel` ligado a `addSelectedTabListener`/`addDiagnosticsListener`; duplo clique navega até o range | ✅ |
| 22 | Ordem Output, Debug Console, Terminal | `JTabbedPane` do painel inferior nessa ordem | ✅ |

## ThreadMaster / listeners

| # | Requisito | Solução | Status |
|---|---|---|---|
| 23 | Nenhuma `Thread`/`Executor`/`Timer`/`invokeLater` fora do ThreadMaster | `LspClient`, `LspEditorSupport`, `BusyBoxTerminalSession`, `LinuxStatusNotifierTray`, `ThreadMaster.registerShutdownAction` | ✅ |
| 24 | Todos os listeners passando pelo ThreadMaster | `MouseNode`, `WindowNode`, `TreeSelectionNode`, `TreeWillExpandNode`, `PropertyChangeNode`, `SwingActionNode` + migração dos usos | ✅ |
| 25 | MainFrame sem listeners crus | `WindowNode`, `ActionNode`, `TreeSelectionNode`, `SwingActionNode` no `MainFrame` | ✅ |

## Testes

| # | Requisito | Solução | Status |
|---|---|---|---|
| 26 | Testes das novas classes | `SettingsServiceTest`, `IdeSettingsTest`, `WorkspaceSearchServiceTest` (persistência, normalização, atalhos, busca e limite de resultados) | ✅ |
| 27 | `FileMenuTest` atualizado para os novos itens/ordem | 5 callbacks e ordem alfabética verificada | ✅ |
| 28 | Testes de listeners assíncronos ajustados | `LspAutoCompletionTest` e `TreeFileTest` aguardam a fila do EDT após a migração para o ThreadMaster | ✅ |

## Verificação executada

```sh
./mvnw -q -pl simpleide spring-javaformat:apply
./mvnw -q -pl simpleide -am -DskipTests compile
./mvnw -q -pl simpleide test
```

Resultado: compilação e suíte completa passando. A interface não foi aberta visualmente neste ambiente (sem display); a validação foi por compilação, testes e inspeção do código.
