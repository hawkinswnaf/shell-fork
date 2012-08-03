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

	synchronized public static Shell getInstance() {
		if (mShell == null) {
			mShell = new Shell();
		}
		return mShell;
	}
	
	private Shell() {
		mProcesses = new Vector<ShellProcess>();
		mRunning = false;
	}

	public boolean isRunning() {
		return mRunning;
	}

	public boolean startProcess(ShellProcess process) {
		mProcesses.addElement(process);
		return true;
	}

	public boolean stopProcess(ShellProcess process) {
		mProcesses.removeElement(process);
		return true;
	}

	public void startShell() {
		mShellThread = new Thread(this);
		mShellThread.start();
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
				do {
					line = mBr.readLine();
					System.out.println("Terminal: " + line);
				} while (line != null);
			} catch (Exception e) {
				/* error.
				 */
				System.err.println("e: " + e.toString());
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
				do {
					String lineParts[];
					String type = null, tag = null, output = null;
					line = socketInputStreamReader.readLine();

					lineParts = line.split(":", 3);
					if (lineParts.length > 0 && lineParts[0] != null) 
						type = lineParts[0];
					if (lineParts.length > 1 && lineParts[1] != null)
						tag = lineParts[1];
					if (lineParts.length > 2 && lineParts[2] != null)
						output = lineParts[2];

					if (type.equalsIgnoreCase("output") && tag != null) {
						for (ShellProcess p : mProcesses) {
							if (p.getTag().equals(tag)) {
								p.sendOutput(output);
								break;
							}
						}
					}
				} while (line != null);
			} catch (Exception e) {
				System.err.println("ShellIo.run(): " + e.toString());
			}
			System.err.println("ShellIo.run(): ending");
			mRunning = false;
		}
	}

	public void sendCommand(String command) throws IOException {
		if (mShellIo != null && mRunning) {
			mShellIo.sendCommand(command);
		}
		else {
			System.out.println("mShellIo or mRunning are not appropriate (sendCommand()).");
		}
	}

	public void run() {
		ShellTerminalMonitor mOutputMonitor, mErrorMonitor;
		Thread mOutputMonitorThread, mErrorMonitorThread;
		Thread mShellIoThread;

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

		(mOutputMonitorThread = new Thread(mOutputMonitor = new ShellTerminalMonitor(mInputStream))).start();
		(mErrorMonitorThread = new Thread(mErrorMonitor = new ShellTerminalMonitor(mErrorStream))).start();

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
			return;
		}

		(mShellIoThread = new Thread(mShellIo)).start();

		mRunning = true;
	
		try {
			mProcess.waitFor();
		} catch (InterruptedException interruptedEx) {
			System.err.println("Shell.run: " + interruptedEx.toString());
		}
		mRunning = false;
	}
}
