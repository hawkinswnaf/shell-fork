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

public class Shell implements Runnable {
	private static Shell mShell = null;
	private Process mProcess = null;
	private InputStream mErrorStream, mInputStream;
	private OutputStream mOutputStream;
	private ShellIo mShellIo;

	public static Shell getInstance() {
		if (mShell == null) {
			mShell = new Shell();
		}
		return mShell;
	}
	
	private Shell() {
	}


	public static void main(String args[]) {
		Thread shellThread = null;
		Shell shell = Shell.getInstance();

		System.out.println(shell);

		shellThread = new Thread(shell);

		shellThread.start();

		try {
			Thread.sleep(5000);
			shell.sendCommand("START:G:ps -ef:");
		} catch (IOException ioex) {
			System.err.println("shell ioex: " + ioex);
		} catch (InterruptedException interrupted) {
			System.err.println("shell interrupted: " + interrupted)
		}
		try {
			shellThread.join();
		} catch (InterruptedException interruptedEx) {
			System.err.println("interruptedEx: " + interruptedEx.toString());
		}
	}

	public String toString() {
		return "Shell toString()\n";
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
			} catch (SecurityException sex) {
				throw new IOException(sex.toString());
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
					line = socketInputStreamReader.readLine();
					System.out.println("output line: " + line);
				} while (line != null);
			} catch (Exception e) {
			}
		}
	}

	public void sendCommand(String command) throws IOException {
		if (mShellIo != null) {
			mShellIo.sendCommand(command);
		}
	}

	public void run() {
		ShellTerminalMonitor mOutputMonitor, mErrorMonitor;
		Thread mOutputMonitorThread, mErrorMonitorThread;
		Thread mShellIoThread;

		try {
			mProcess = Runtime.getRuntime().exec("./fork");
		} catch (IOException ioex) {
			System.err.println("ioex: " + ioex.toString());
			return;
		}
		if (mProcess == null) {
			System.out.println("./fork failed!");
			return;
		}
		mInputStream = mProcess.getInputStream();
		mOutputStream = mProcess.getOutputStream();
		mErrorStream = mProcess.getErrorStream();

		mOutputMonitorThread = new Thread(mOutputMonitor = new ShellTerminalMonitor(mInputStream));
		mErrorMonitorThread = new Thread(mErrorMonitor = new ShellTerminalMonitor(mErrorStream));

		mOutputMonitorThread.start();
		mErrorMonitorThread.start();

		try {
			mShellIo = new ShellIo();
		} catch (UnknownHostException unknownHost) {
			System.err.println("Unknown host error: " + unknownHost.toString());
			mProcess.destroy();
			return;
		}

		mShellIoThread = new Thread(mShellIo);
		mShellIoThread.start();
	
		try {
			mProcess.waitFor();
		} catch (InterruptedException interruptedEx) {
			System.err.println("Interrupted: " + interruptedEx.toString());
		}	
	}
}
