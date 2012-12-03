package net.commotionwireless.shell;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.Process;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Vector;

/**
 * Shell is the singleton class that 
 * provides a Java interface to the 
 * fork shell. Processes running in a 
 * fork shell are represented as
 * {@link ShellProcess}es. Process control
 * (creation, start, stop) is done 
 * through ShellProcess-es. 
 *
 * To start using a Shell, do the following:
 * <pre>
 * Shell shell = Shell.getInstance();
 * shell.setForkCmd("./fork"); //optional.
 * shell.startShell();
 * </pre>
*/
public class Shell implements Runnable {
	private static Shell mShell = null;
	private Process mProcess = null;
	private InputStream mErrorStream, mOutputFromForkStream;
	private OutputStream mInputToForkStream;
	private ShellIo mShellIo;
	private ShellRunningStatus mRunning;
	private Vector<ShellProcess> mProcesses;
	private Thread mShellThread;
	private ShellDebugMonitor mDebugMonitor;
	private String mForkCmd;
	private String[] mEnvp;
	private String mKey;
	private Object mKeySignal, mShellIoSignal;

	/**
	 * Indicates the status of the Shell
	 * and its I/O link.
	 */
	public enum ShellRunningStatus {
		/**
		 * Not running because of an error.
		 */
		ERROR, 
		/**
		 * Not running.
		 */
		NOTRUNNING, 
		
		/**
		 * Running.
		 */
		RUNNING
	}

	/**
	 * Returns the active Shell.
	 * @return	The active shell.
	 */
	synchronized public static Shell getInstance() {
		if (mShell == null) {
			mShell = new Shell();
		}
		return mShell;
	}

	public static void waitForShellToStart(Shell shell) {
		System.err.println("Waiting for shell to start.");
		while (true) {
			try {
				synchronized (shell) {
					if (shell.isRunning() != Shell.ShellRunningStatus.NOTRUNNING) break;
					shell.wait();
				}
			} catch (InterruptedException interruptedEx) {
			}
		}
		System.err.println("Shell started.");
	}
	
	private Shell() {
		mProcesses = new Vector<ShellProcess>();
		mDebugMonitor = null;
		mRunning = ShellRunningStatus.NOTRUNNING;
		mForkCmd = null;
		mEnvp = null;
		mKey = null;
		mKeySignal = new Object();
		mShellIoSignal = new Object();
	}

	/**
	 * Set the Shell Debug Monitor for this shell. Used by 
	 * the unit testing code.
	 *
	 * @param debugMonitor ShellDebugMonitor to use to monitor
	 * this shell.
	 */
	public void setDebugMonitor(ShellDebugMonitor debugMonitor) {
		mDebugMonitor = debugMonitor;
	}

	/**
	 * Get the active Debug Monitor.
	 *
	 * @return The active Debug Monitor.
	 */
	public ShellDebugMonitor getDebugMonitor() {
		return mDebugMonitor;
	}

	/**
	 * Is the Shell running?
	 *
	 * @return One of ShellRunningStatus indicating whether 
	 * the Shell is running.
	 */
	public ShellRunningStatus isRunning() {
		return mRunning;
	}

	final protected boolean startProcess(ShellProcess process) {
		if (mProcesses.contains(process)) {
			return false;
		}
		mProcesses.addElement(process);
		return true;
	}

	final protected boolean stopProcess(ShellProcess process) {
		synchronized (this) {
			mProcesses.removeElement(process);
			this.notifyAll(); 
		}
		return true;
	}

	/**
	 * Start the Shell running.
	 *
	 * @return Whether the Shell started or not.
	 */
	public boolean startShell() {
		mShellThread = new Thread(this);
		mShellThread.start();
		return true;
	}

	/**
	 * Stop the shell, if it is running.
	 *
	 * @return Whether the Shell stopped or not.
	 */
	public boolean stopShell() {
		System.out.println("Waiting for all processes to stop.");
		synchronized (this) {
			while (!mProcesses.isEmpty()) {
				try {
					this.wait();
				} catch (InterruptedException interruptedEx) {
					/* 
					 * who cares.
					 */
				}
			}
		}
		System.out.println("Sending KILL command.");
		try {
			sendCommand("KILL:::");
		} catch (IOException ioEx) {
			System.err.println("Could not send KILL command.");
			return false;
		}
		return true;
	}

	class ShellIo implements Runnable {
		InetAddress mHost;
		int mCommandPort, mOutputPort;
		ShellRunningStatus mRunning;
		/*
		 * these are named wrt to the fork process, 
		 * not this process.
		 */
		BufferedWriter mInputToForkWriter;
		BufferedReader mOutputFromForkReader;

		public ShellIo(OutputStream inputToFork, InputStream outputFromFork) {
				mOutputFromForkReader=new BufferedReader(new InputStreamReader(outputFromFork));
				mInputToForkWriter=new BufferedWriter(new OutputStreamWriter(inputToFork));
				mRunning = Shell.ShellRunningStatus.NOTRUNNING;
		}

		public Shell.ShellRunningStatus isRunning() {
			return mRunning;
		}

		public void sendCommand(String command) throws IOException {
			String actualCommand;

			actualCommand = "KEY" + ":" + command + "\n";

			mInputToForkWriter.write(actualCommand, 0, actualCommand.length());
			mInputToForkWriter.flush();
		}

		public void run() {
			int connectionTries = 0;
			int maxConnectionTries = 2;
			Socket outputSocket = null;
			InputStream socketInputStream = null;
			BufferedReader socketInputStreamReader = null;

			mRunning = Shell.ShellRunningStatus.RUNNING;
			synchronized (mShellIoSignal) {
				mShellIoSignal.notifyAll();
			}
			try {
				String line;
				System.out.println("Waiting for readLine()");
				do {
					String lineParts[];
					String type = null, tag = null, output = null;
					ShellProcess p = null;
					
					line = mOutputFromForkReader.readLine();
					System.out.println("Done waiting for readLine(): " + line);
					if (line == null)
						continue;

					lineParts = line.split(":", 3);
					if (lineParts.length > 0 && lineParts[0] != null) 
						type = lineParts[0];
					if (lineParts.length > 1 && lineParts[1] != null)
						tag = lineParts[1];
					if (lineParts.length > 2 && lineParts[2] != null)
						output = lineParts[2];
	
					if (tag != null) {
							for (ShellProcess ip : mProcesses) {
								if (ip.getTag().equals(tag)) {
									p = ip;	
									break;
							}
						}
					}
					if (p == null) {
						/* no matching process found! continue.
						 */
						continue;
					}

					if (mDebugMonitor != null)
						mDebugMonitor.sendOutput(type, p, output);

					if (type.equalsIgnoreCase("output")) {
						p.sendOutput(output);
					} else if (type.equalsIgnoreCase("stopped")) {
						p.stopped();
					}
				} while (line != null);
			} catch (IOException ioEx) {
				System.err.println("ShellIo.run(): " + ioEx.toString());
			}
			System.err.println("ShellIo.run(): ending");
			mRunning = Shell.ShellRunningStatus.NOTRUNNING;
		}
	}

	final protected void sendCommand(String command) throws IOException {
		System.out.println("Sending command: " + command);
		if (mShellIo != null && 
			mShellIo.isRunning() == ShellRunningStatus.RUNNING && 
			mRunning == ShellRunningStatus.RUNNING) {
			mShellIo.sendCommand(command);
		}
		else {
			System.err.println("mShellIo: " + mShellIo);
			System.err.println("mShellIo.isRunning(): " + mShellIo.isRunning());
			System.err.println("mRunning: " + mRunning);
			throw new IOException("mShellIo and/or Shell are/is not running (Shell.sendCommand()).");
		}
		System.out.println("Done sending command.");
	}

	/**
	 * Override the default fork command that implements
	 * the shell.
	 *
	 * @param forkCmd The shell command to execute that will start 
	 * a fork shell.
	 */
	public void setForkCommand(String forkCmd) {
		mForkCmd = forkCmd;
	}

	/**
	 * Override the default fork command and its environment.
	 *
	 * @param forkCmd The shell command to execute that will start
	 * a fork shell.
	 * @param envp An array of name=value environment variables.
	 */
	public void setForkCommand(String forkCmd, String[] envp) {
		setForkCommand(forkCmd);
		mEnvp = envp;
	}

	public void run() {
		Thread outputMonitorThread, errorMonitorThread;
		Thread shellIoThread;

		try {
			if (mForkCmd == null)
				mProcess = Runtime.getRuntime().exec("./fork", mEnvp);
			else
				mProcess = Runtime.getRuntime().exec(mForkCmd, mEnvp);
		} catch (IOException ioex) {
			System.err.println("Shell.run: " + ioex.toString());
			mRunning = ShellRunningStatus.ERROR;
			synchronized (this) {
				this.notifyAll();
			}
			return;
		}
		if (mProcess == null) {
			System.err.println("Shell.run: ./fork failed!");
			mRunning = ShellRunningStatus.ERROR;
			synchronized (this) {
				this.notifyAll();
			}
			return;
		}

		System.err.println("Shell.run: started");

		/*
		 * Start ./fork terminal io monitoring.
		 */
		mOutputFromForkStream = mProcess.getInputStream();
		mInputToForkStream = mProcess.getOutputStream();
		mErrorStream = mProcess.getErrorStream();

		/*
		 * Start a connection to ./fork
		 * to actually gather subprocess
		 * io.
		 */
		mShellIo = new ShellIo(mInputToForkStream, mOutputFromForkStream);
		(shellIoThread = new Thread(mShellIo)).start();

		System.err.println("Waiting for ShellIo to start!");
		while (true) {
			try {
				synchronized (mShellIoSignal) {
					mShellIoSignal.wait();
					if (mShellIo.isRunning() != ShellRunningStatus.NOTRUNNING) break;
				}
			} catch (InterruptedException interruptedEx) {
				/*
				 */
			}
		}
		System.err.println("ShellIo started!");

		mRunning = ShellRunningStatus.RUNNING;

		synchronized (this) {
			this.notifyAll();
		}
	
		try {
			mProcess.waitFor();
		} catch (InterruptedException interruptedEx) {
			System.err.println("Shell.run: " + interruptedEx.toString());
		}
		mRunning = ShellRunningStatus.NOTRUNNING;
		mProcess = null;
		System.err.println("Shell.run: Shell stopping.");
	}
}
