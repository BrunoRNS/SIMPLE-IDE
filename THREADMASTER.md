# Usando o gerenciador de threads

O `ThreadMaster` controla as tarefas da aplicação e mantém uma árvore dos nós
registrados. Use sempre a instância compartilhada:

```java
ThreadMaster threads = ThreadMaster.getInstance();
```

O aplicativo inicializa o gerenciador ao abrir a janela. Se precisar iniciar
uma tarefa antes desse ponto, chame `initialize()` uma vez. Depois da
inicialização, cada `register(...)` também agenda o novo nó.

## Ação de botão

`ActionNode` implementa `ActionListener`. Seu callback é executado no EDT
(Event Dispatch Thread) e recebe o `ActionEvent`:

```java
JButton salvar = new JButton("Salvar");
salvar.addActionListener(new ActionNode(event -> {
    System.out.println("Ação: " + event.getActionCommand());
}));
```

Também é possível fornecer um callback de encerramento, executado pelo
gerenciador após a ação:

```java
salvar.addActionListener(new ActionNode(
    event -> salvarArquivo(),
    event -> System.out.println("Ação finalizada")
));
```

O evento cria uma tarefa EDT transitória na árvore. Ela é removida ao terminar;
não é necessário registrar o listener manualmente.

## Atualizar um componente no EDT

Código que roda em uma `RunnableNode` não deve alterar componentes Swing
diretamente. Agende a atualização com `executeOnEDT`:

```java
ThreadMaster threads = ThreadMaster.getInstance();

threads.register(new RunnableNode(ThreadMaster.ROOT, () -> {
    String resultado = carregarDados();
    threads.executeOnEDT(() -> statusLabel.setText(resultado));
}));
```

`executeOnEDT` executa a ação no EDT e aceita um encerramento opcional:

```java
threads.executeOnEDT(
    () -> statusLabel.setText("Pronto"),
    () -> registrarFimDaAtualizacao()
);
```

As tarefas agendadas assim também são registradas temporariamente na árvore e
removidas depois da execução.

## Mostrar um pop-up

Use `startManaged()` (ou `run()`) em qualquer thread; o pop-up é mostrado no EDT:

```java
new InfoPopUP("Salvo", "O arquivo foi salvo com sucesso").startManaged();
```

Outros tipos seguem o mesmo padrão:

```java
import io.simpleide.frame.popup.utils.ErrorTypePopUp;

new WarningPopUP("Atenção", "Confira os dados").startManaged();
new ErrorPopUP("Não foi possível salvar", ErrorTypePopUp.RUNTIME_ERROR).startManaged();
new SuccessPopUP("Concluído", "Operação finalizada").startManaged();
```

## Trabalho em segundo plano

Use `RunnableNode` para tarefas que não devem bloquear a interface. Seus
callbacks rodam no pool do gerenciador, fora do EDT:

```java
ThreadMaster threads = ThreadMaster.getInstance();

threads.register(new RunnableNode(
    ThreadMaster.ROOT,
    () -> {
        processarArquivo();
        threads.executeOnEDT(() -> statusLabel.setText("Processamento concluído"));
    },
    () -> liberarRecursos()
));
```

Mantenha o trabalho do callback de uma ação curta: operações demoradas, como
acesso a arquivos ou rede, devem ir para uma `RunnableNode`. Assim a janela
continua responsiva.

O pool usa até dois workers e uma fila limitada. Portanto, uma tarefa não deve
esperar que outra tarefa do mesmo pool seja iniciada para poder terminar. Use
`onClose` para liberar recursos ou reagir ao encerramento; a interrupção é
cooperativa, então tarefas longas devem respeitar `Thread.currentThread().isInterrupted()`
ou tratar `InterruptedException`.

## Listener de mudança

Para mudanças que implementam `ChangeListener`, use `ChangeNode`. O callback
recebe o `ChangeEvent` e também roda no EDT:

```java
import javax.swing.event.ChangeListener;

ChangeListener listener = new ChangeNode(event -> {
    sliderLabel.setText("Valor alterado");
});

slider.getModel().addChangeListener(listener);
```

## Encerrar a aplicação

Ao fechar a janela principal, o aplicativo chama `ThreadMaster.shutdown()`.
Isso interrompe tarefas em execução e executa seus callbacks `onClose`.
O gerenciador encerrado não pode ser inicializado novamente.
