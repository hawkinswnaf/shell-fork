package net.commotionwireless.shell;

import java.io.IOException;

public class ShellProcess {
	private String mTag, mCommand;
	private Shell mShell;

	public ShellProcess(String tag, String command, Shell shell) {
		mTag = tag;
		mCommand = command;
		mShell = shell;
	}

	public void sendInput(String input) throws IOException {
		mShell.sendCommand("INPUT:" + mTag + ":" + input + ":");
	}

	public void sendOutput(String output) {
		System.out.println(this + ": " + output);
	}

	public void stopped() {
		System.out.println(this + ": stopped.\n");
	}

	public String toString() {
		return mTag;
	}

	public boolean start() {
		boolean didStart = true;
		if (mShell.startProcess(this)) {
			try {
				mShell.sendCommand("START:" + mTag + ":" + mCommand + ":");
			} catch (IOException ioEx) {
				didStart = false;
			}
		}
		else 
			didStart = false;

		return didStart;
	}

	public boolean stop() {
		boolean didStop = true;
		try {
			mShell.sendCommand("STOP:" + mTag + ":" + mCommand + ":");
		} catch (IOException ioEx) {
			didStop = false;
		}
		if (didStop) {
			mShell.stopProcess(this);
		}
		return didStop;
	}

	public String getTag() {
		return mTag;
	}

	public boolean equals(Object obj) {
		if (obj instanceof ShellProcess) {
			ShellProcess otherProcess = (ShellProcess)obj;
			return (otherProcess.getTag().equals(mTag));
		}
		return false;
	}

	public int hashCode() {
		int hash = 0;
		for (byte c : mTag.getBytes()) {
			hash += (int)c;
		}
		return hash;
	}

	public static void main(String args[]) {
		ShellProcess p = new ShellProcess("P", "test", null);
		ShellProcess q = new ShellProcess("Q", "test", null);
		ShellProcess r = new ShellProcess("R", "test", null);
		ShellProcess s = new ShellProcess("S", "test", null);
		System.out.println("hash: " + p.hashCode());
		System.out.println("hash: " + q.hashCode());
		System.out.println("hash: " + r.hashCode());
		System.out.println("hash: " + s.hashCode());
	}
}
