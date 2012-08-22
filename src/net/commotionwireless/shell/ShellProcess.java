package net.commotionwireless.shell;

import java.io.IOException;
import android.os.Handler;

/**
 * ShellProcess represents a process
 * running in a fork shell (which 
 * itself is represented as a {@link Shell}.)
 */
public class ShellProcess {
	private String mStag, mCommand;
	private Shell mShell;
	private int mTag;
//	private Handler mHandler;
	private Object mHandler;
	private boolean mShouldStop;

	/**
	 * Create a new fork Shell process.
	 *
	 * @param sTag The tag for the process.
	 * @param command The command to start the process.
	 * @param shell The Shell under which this process will run.
	 */
	public ShellProcess(String sTag, String command, Shell shell) {
		mStag = sTag;
		mCommand = command;
		mShell = shell;
		mTag = -1;
		mShouldStop = false;
	}

	/**
	 * Set an Android Handler to take receipt 
	 * of output from this process.
	 *
	 * @param handler An Android Handler to notify about output.
	 * @param tag A tag used by the handler to identify this process.
	 */
//	public void setHandler(Handler handler, int tag) {
	public void setHandler(Object handler, int tag) {
		mTag = tag;
		mHandler = handler;
	}

	/**
	 * Give input to this process.
	 *
	 * @param input Input to this process.
	 * @throws IOException Throws an exception if
	 * the input cannot be given to the process.
	 */
	public void sendInput(String input) throws IOException {
		mShell.sendCommand("INPUT:" + mStag + ":" + input + ":");
	}

	final protected void sendOutput(String output) {
		System.out.println(this + ": " + output);
//		if (mHandler != null) 
//			mHandler.obtainMessage(mTag, output).sendToTarget();
	}

	synchronized protected void stopped() {
		System.out.println(this + ": stopped.\n");
		mShouldStop = true;
		this.notifyAll();
	}

	public String toString() {
		return mStag;
	}

	/**
	 * Run the process to completion.
	 *
	 * @return Whether the process ran successfully. This is NOT
	 * the return value of the underlying process.
	 */
	public boolean runSynchronous() {
		if (!start()) {
			System.out.println("ShellProcess.runSynchronous: Error starting");
			return false;
		}
		while (true) {
			synchronized (this) { if (mShouldStop) break; }
			try {
				synchronized (this) {
					this.wait(1000);
				}	
			} catch (InterruptedException interruptedEx) {
				/*
				 * meh.
				 */
			}
		}
		mShouldStop = false;
		mShell.stopProcess(this);
		return true;
	}

	/**
	 * Run the process asynchronously.
	 *
	 * @return Whether the process started successfully. 
	 */
	public boolean runAsynchronous() {
		return start();
	}

	private boolean start() {
		boolean didStart = true;
		if (mShell.startProcess(this)) {
			try {
				mShell.sendCommand("START:" + mStag + ":" + mCommand + ":");
			} catch (IOException ioEx) {
				System.err.println("Error: " + ioEx.toString());
				didStart = false;
			}
		}
		else 
			didStart = false;

		return didStart;
	}

	/**
	 * Stop a running process.
	 *
	 * @return Whether the process was stopped.
	 */
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

	/**
	 * Get the String tag of this process.
	 *
	 * @return The String tag of this process.
	 */
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
