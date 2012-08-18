package net.commotionwireless.shell;

import java.io.IOException;
import android.os.Handler;

public class ShellProcess {
	private String mStag, mCommand;
	private Shell mShell;
	private int mTag;
	private Handler mHandler;
//	private Object mHandler;
	private boolean mShouldStop;

	public ShellProcess(String sTag, String command, Shell shell) {
		mStag = sTag;
		mCommand = command;
		mShell = shell;
		mTag = -1;
		mShouldStop = false;
	}

	public void setHandler(Handler handler, int tag) {
//	public void setHandler(Object handler, int tag) {
		mTag = tag;
		mHandler = handler;
	}

	public void sendInput(String input) throws IOException {
		mShell.sendCommand("INPUT:" + mStag + ":" + input + ":");
	}

	public void sendOutput(String output) {
		System.out.println(this + ": " + output);
		if (mHandler != null) 
			mHandler.obtainMessage(mTag, output).sendToTarget();
	}

	synchronized public void stopped() {
		System.out.println(this + ": stopped.\n");
		mShouldStop = true;
	}

	public String toString() {
		return mStag;
	}

	public boolean runSynchronous() {
		if (!start()) {
			System.out.println("ShellProcess.runSynchronous: Error starting");
			return false;
		}
		while (true) {
			synchronized (this) { if (mShouldStop) break; }
			try {
				Thread.sleep(1000);
			} catch (InterruptedException interruptedEx) {
				/*
				 * meh.
				 */
			}
		}
		mShouldStop = false;
		return true;
	}

	public boolean runAsynchronous() {
		return start();
	}

	private boolean start() {
		boolean didStart = true;
		if (mShell.startProcess(this)) {
			try {
				mShell.sendCommand("START:" + mStag + ":" + mCommand + ":");
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
			mShell.sendCommand("STOP:" + mStag + ":" + mCommand + ":");
		} catch (IOException ioEx) {
			didStop = false;
		}
		if (didStop) {
			mShell.stopProcess(this);
		}
		return didStop;
	}

	public String getTag() {
		return mStag;
	}

	public boolean equals(Object obj) {
		if (obj instanceof ShellProcess) {
			ShellProcess otherProcess = (ShellProcess)obj;
			return (otherProcess.getTag().equals(mStag));
		}
		return false;
	}

	public int hashCode() {
		int hash = 0;
		for (byte c : mStag.getBytes()) {
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
