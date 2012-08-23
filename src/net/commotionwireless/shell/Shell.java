package net.commotionwireless.shell;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.BufferedReader;
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
	private InputStream mErrorStream, mInputStream;
	private OutputStream mOutputStream;
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

	class ShellTerminalMonitor implements Runnable {
		private InputStream mOutput;
		private BufferedReader mBr;
		public ShellTerminalMonitor(InputStream output) {
			mOutput = output;
			mBr = new BufferedReader(new InputStreamReader(mOutput), 50);
		}

		public void run() {
			System.err.println("ShellTerminalMonitor started.");
			try {
				String line;
				line = mBr.readLine();
				while (line != null) {
					String lineContents[] = line.split(":");
					if (mKey == null &&
						line.startsWith("KEY:") &&
						lineContents.length == 2) {
						mKey = lineContents[1];
						System.out.println("Setting KEY: " + mKey);
						synchronized (mKeySignal) {
							mKeySignal.notifyAll();
						}
					}
					System.out.println("Terminal: " + line);
					line = mBr.readLine();
				}
			} catch (IOException ioEx) {
				/* error.
				 */
				System.err.println("ShellTerminalMonitor.run(): " + ioEx.toString());
			}
			System.err.println("ShellTerminalMonitor stopped.");
		}
	}

	class ShellIo implements Runnable {
		InetAddress mHost;
		int mCommandPort, mOutputPort;
		ShellRunningStatus mRunning;

		public ShellIo(String host, int commandPort, int outputPort) 
			throws UnknownHostException {
			mHost = InetAddress.getByName(host);
			mCommandPort = commandPort;
			mOutputPort = outputPort;
			mRunning = Shell.ShellRunningStatus.NOTRUNNING;
		}

		public ShellIo()
			throws UnknownHostException {
			this("127.0.0.1", 5000, 5001);
		}

		public ShellIo(String host, int commandPort) 
			throws UnknownHostException {
			this(host, commandPort, commandPort);
		}

		public Shell.ShellRunningStatus isRunning() {
			return mRunning;
		}

		public void sendCommand(String command) throws IOException {
			Socket commandSocket = null;
			OutputStream socketOutputStream = null;
			OutputStreamWriter socketOutputStreamWriter = null;
			String actualCommand;

			try {
				commandSocket = new Socket(mHost, mCommandPort);
			} catch (SecurityException securityEx) {
				throw new IOException(securityEx.toString());
			}

			actualCommand = mKey + ":" + command;

			socketOutputStream = commandSocket.getOutputStream();
			socketOutputStreamWriter = new OutputStreamWriter(socketOutputStream);
			socketOutputStreamWriter.write(actualCommand, 0, actualCommand.length());
			
			socketOutputStreamWriter.flush();
			socketOutputStreamWriter.close();
			socketOutputStream.flush();
			socketOutputStream.close();
			commandSocket.close();
		}

		public void run() {
			int connectionTries = 0;
			int maxConnectionTries = 2;
			Socket outputSocket = null;
			InputStream socketInputStream = null;
			BufferedReader socketInputStreamReader = null;

			mRunning = Shell.ShellRunningStatus.ERROR;

		
			while (connectionTries < maxConnectionTries) {
				try {
					outputSocket = new Socket(mHost, mOutputPort);
					break;
				} catch (Exception exc) {
					System.err.println("ShellIo.run:" + exc.toString());
				}
				connectionTries++;
				System.out.println("ShellIo will try again.");
				try {
					Thread.sleep(500);
				} catch (InterruptedException interruptedEx) {
					/* 
					 * who cares!
					 */
				}
			}

			if (outputSocket == null) {
				System.out.println("ShellIo officially failed.");
				synchronized (mShellIoSignal) {
					mShellIoSignal.notifyAll();
				}
				return;
			}

			try {
				socketInputStream = outputSocket.getInputStream();
				socketInputStreamReader = new BufferedReader(new InputStreamReader(socketInputStream));
				mRunning = Shell.ShellRunningStatus.RUNNING;
				synchronized (mShellIoSignal) {
					mShellIoSignal.notifyAll();
				}
			} catch (IOException ioex) {
				System.err.println("ShellIo.run:" + ioex.toString());
				synchronized (mShellIoSignal) {
					mShellIoSignal.notifyAll();
				}
				return;
			}

			try {
				String line;
				line = socketInputStreamReader.readLine();
				while (line != null) {
					String lineParts[];
					String type = null, tag = null, output = null;
					ShellProcess p = null;

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
					line = socketInputStreamReader.readLine();
				}
			} catch (IOException ioEx) {
				System.err.println("ShellIo.run(): " + ioEx.toString());
			}
			System.err.println("ShellIo.run(): ending");
			mRunning = Shell.ShellRunningStatus.NOTRUNNING;
		}
	}

	final protected void sendCommand(String command) throws IOException {
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
		ShellTerminalMonitor outputMonitor, errorMonitor;
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
		mInputStream = mProcess.getInputStream();
		mOutputStream = mProcess.getOutputStream();
		mErrorStream = mProcess.getErrorStream();

		(outputMonitorThread = new Thread(outputMonitor = new ShellTerminalMonitor(mInputStream))).start();
		(errorMonitorThread = new Thread(errorMonitor = new ShellTerminalMonitor(mErrorStream))).start();

		/*
		 * wait until we get a key.
		 */
		while (true) {
			try {
				synchronized (mKeySignal) {
					mKeySignal.wait();
					if (mKey != null) break; 
				}
			} catch (InterruptedException interruptedEx) {
				/*
				 */
			}
		}

		/*
		 * Start a connection to ./fork
		 * to actually gather subprocess
		 * io.
		 */
		try {
			mShellIo = new ShellIo();
		} catch (UnknownHostException unknownHostEx) {
			System.err.println("Shell.run: " + unknownHostEx.toString());
			mProcess.destroy();
			mProcess = null;
			mRunning = ShellRunningStatus.ERROR;
			synchronized (this) {
				this.notifyAll();
			}
			return;
		}

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
