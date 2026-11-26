# Run I/O and debugging

The editor's I/O tab displays a running program's standard output and error,
accepts standard input, and provides a stop control. The **Execute** menu exposes
run and debug actions for supported source files. Debug sessions use the Debug
Adapter Protocol (DAP); adapters are installed on demand where required.

The I/O tab is shared by normal execution and debugging. Stopping a process closes
its input/output streams and returns the editor to the ready state. See the
[language runner](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/API-Languages), [DAP API](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/API-Dap), and
[thread manager](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/Reference-ThreadMaster) guides for integration details.
