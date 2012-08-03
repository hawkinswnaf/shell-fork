package net.commotionwireless.shell;

public class ShellTest {
	public static void main(String args[]) {
		ShellProcess grep, tee;
		Shell shell = Shell.getInstance();

		shell.startShell();

		grep = new ShellProcess("G", "grep --line-buffered *", shell);
		tee = new ShellProcess("T", "tee", shell);

		try {
			Thread.sleep(5000);
		} catch (InterruptedException interruptedEx) {
			/* ignore */
		}

		grep.start();
		tee.start();
	}
}
