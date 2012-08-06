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

public class Shell implements Runnable {
	private static Shell mShell = null;
	private Process mProcess = null;
	private InputStream mErrorStream, mInputStream;
	private OutputStream mOutputStream;
	private ShellIo mShellIo;
	private boolean mRunning;
	private Vector<ShellProcess> mProcesses;
	private Thread mShellThread;
	private ShellDebugMonitor mDebugMonitor;

	synchronized public static Shell getInstance() {
		if (mShell == null) {
			mShell = new Shell();
		}
		return mShell;
	}
	
	private Shell() {
		mProcesses = new Vector<ShellProcess>();
		mDebugMonitor = null;
		mRunning = false;
	}

	public void setDebugMonitor(ShellDebugMonitor debugMonitor) {
		mDebugMonitor = debugMonitor;
	}

	public ShellDebugMonitor getDebugMonitor() {
		return mDebugMonitor;
	}

	public boolean isRunning() {
		return mRunning;
	}

	public boolean startProcess(ShellProcess process) {
		if (mProcesses.contains(process)) {
			return false;
		}
		mProcesses.addElement(process);
		return true;
	}

	public boolean stopProcess(ShellProcess process) {
		mProcesses.removeElement(process);
		return true;
	}

	public boolean startShell() {
		mShellThread = new Thread(this);
		mShellThread.start();
		return true;
	}

	public boolean stopShell() {
		mProcess.destroy();
		return true;
	}

	class ShellTerminalMonitor implements Runnable {
		private InputStream mOutput;
		private BufferedReader mBr;
		public ShellTerminalMonitor(InputStream output) {
			mOutput = output;
			mBr = new BufferedReader(new InputStreamReader(mOutput), 8192);
		}

		public void run() {
			try {
				String line;
				line = mBr.readLine();
				while (line != null) {
					System.out.println("Terminal: " + line);
					line = mBr.readLine();
				}
			} catch (IOException ioEx) {
				/* error.
				 */
				System.err.println("ShellTerminalMonitor.run(): " + ioEx.toString());
			}
			System.err.println("ShellTerminalMonitor stopped.\n");
		}
	}

	class ShellIo implements Runnable {
		InetAddress mHost;
		int mCommandPort, mOutputPort;
		boolean mRunning;

		public ShellIo(String host, int commandPort, int outputPort) 
			throws UnknownHostException {
			mHost = InetAddress.getByName(host);
			mCommandPort = commandPort;
			mOutputPort = outputPort;
			mRunning = false;
		}

		public ShellIo()
			throws UnknownHostException {
			this("127.0.0.1", 5000, 5001);
		}

		public ShellIo(String host, int commandPort) 
			throws UnknownHostException {
			this(host, commandPort, commandPort);
		}

		public boolean isRunning() {
			return mRunning;
		}

		public void sendCommand(String command) throws IOException {
			Socket commandSocket = null;
			OutputStream socketOutputStream = null;
			OutputStreamWriter socketOutputStreamWriter = null;

			try {
				commandSocket = new Socket(mHost, mCommandPort);
			} catch (SecurityException securityEx) {
				throw new IOException(securityEx.toString());
			}

			socketOutputStream = commandSocket.getOutputStream();
			socketOutputStreamWriter = new OutputStreamWriter(socketOutputStream);
			socketOutputStreamWriter.write(command, 0, command.length());
			
			socketOutputStreamWriter.flush();
			socketOutputStreamWriter.close();
			socketOutputStream.flush();
			socketOutputStream.close();
			commandSocket.close();
		}

		public void run() {
			Socket outputSocket = null;
			InputStream socketInputStream = null;
			BufferedReader socketInputStreamReader = null;

			try {
				outputSocket = new Socket(mHost, mOutputPort);
			} catch (Exception exc) {
				return;
			}

			try {
				socketInputStream = outputSocket.getInputStream();
				socketInputStreamReader = new BufferedReader(new InputStreamReader(socketInputStream));
				mRunning = true;
			} catch (IOException ioex) {
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
			mRunning = false;
		}
	}

	public void sendCommand(String command) throws IOException {
		if (mShellIo != null && mShellIo.isRunning() && mRunning) {
			mShellIo.sendCommand(command);
		}
		else {
			throw new IOException("mShellIo and/or Shell are/is not running (Shell.sendCommand()).");
		}
	}

	public void run() {
		ShellTerminalMonitor outputMonitor, errorMonitor;
		Thread outputMonitorThread, errorMonitorThread;
		Thread shellIoThread;

		try {
			mProcess = Runtime.getRuntime().exec("./fork");
		} catch (IOException ioex) {
			System.err.println("Shell.run: " + ioex.toString());
			return;
		}
		if (mProcess == null) {
			System.err.println("Shell.run: ./fork failed!");
			return;
		}

		/*
		 * Start ./fork terminal io monitoring.
		 */
		mInputStream = mProcess.getInputStream();
		mOutputStream = mProcess.getOutputStream();
		mErrorStream = mProcess.getErrorStream();

		(outputMonitorThread = new Thread(outputMonitor = new ShellTerminalMonitor(mInputStream))).start();
		(errorMonitorThread = new Thread(errorMonitor = new ShellTerminalMonitor(mErrorStream))).start();

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
			return;
		}

		(shellIoThread = new Thread(mShellIo)).start();

		mRunning = true;
	
		try {
			mProcess.waitFor();
		} catch (InterruptedException interruptedEx) {
			System.err.println("Shell.run: " + interruptedEx.toString());
		}
		mRunning = false;
		mProcess = null;
	}
}
