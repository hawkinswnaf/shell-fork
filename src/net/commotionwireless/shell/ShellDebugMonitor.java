package net.commotionwireless.shell;

import net.commotionwireless.shell.ShellProcess;

interface ShellDebugMonitor {
	public void sendOutput(String type, ShellProcess p, String output);
}
