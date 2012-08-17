package net.commotionwireless.shell;

import java.io.IOException;
import org.junit.*;
import static org.junit.Assert.*;

public class ShellTest {
	private static Shell shell = null;
	ShellProcess grep = null;
	ShellProcess sleep2 = null, sleep4 = null, sleep8 = null;

	@BeforeClass public static void shellProcessTestSetup() {
		shell = Shell.getInstance();
		shell.startShell();

		try {
			Thread.sleep(5000);
		} catch (InterruptedException interruptedEx) {
			/*
			 */
		}

		assertTrue(shell.isRunning());
	}
	
	@AfterClass public static void shellProcessTestStop() {
		shell.stopShell();	
	}

	@Before public void shellProcessTestBefore() {
		sleep2 = new ShellProcess("S2", "sleep 2", shell);
		sleep4 = new ShellProcess("S4", "sleep 4", shell);
		sleep8 = new ShellProcess("S8", "sleep 8", shell);
		grep = new ShellProcess("G", "grep --line-buffered asdfg", shell);
		assertTrue(grep.runAsynchronous());
	}

	@After public void shellProcessTestAfter() {
		assertTrue(grep.stop());
	}

	@Test public void shellProcessTestSleep2() {
		long startTime, endTime, sleepTime;

		startTime = System.nanoTime();
		sleep2.runSynchronous();
		endTime = System.nanoTime();

		sleepTime = (endTime-startTime)/1000000000L;

		System.out.println("sleep2 ran for " + sleepTime + " seconds");
	}

	@Test public void shellProcessTestSleep4() {
		long startTime, endTime, sleepTime;

		startTime = System.nanoTime();
		sleep4.runSynchronous();
		endTime = System.nanoTime();

		sleepTime = (endTime-startTime)/1000000000L;

		System.out.println("sleep4 ran for " + sleepTime + " seconds");
	}

	@Test public void shellProcessTestSleep8() {
		long startTime, endTime, sleepTime;

		startTime = System.nanoTime();
		sleep8.runSynchronous();
		endTime = System.nanoTime();

		sleepTime = (endTime-startTime)/1000000000L;

		System.out.println("sleep8 ran for " + sleepTime + " seconds");
	}

	@Test public void shellProcessTestSameTag() {
		ShellProcess grep2 = null;
		grep2 = new ShellProcess("G", "grep --line-buffered asdfg", shell);
		assertFalse("grep2.runAsynchronous() did not fail.", grep2.runAsynchronous());
	}

	class GrepShellDebugMonitor implements ShellDebugMonitor {
		private String mOutputComparison;
		private boolean mOutputMatches;

		GrepShellDebugMonitor(String outputComparison) {
			mOutputComparison = outputComparison;
			mOutputMatches = false;
		}
		public void sendOutput(String type, ShellProcess p, String output) {
			System.out.println("output: " + output);
			if (output.equalsIgnoreCase(mOutputComparison)) {
				mOutputMatches = true;
			}
		}
		public boolean outputMatches() {
			return mOutputMatches;
		}
	}
	@Test public void shellProcessTestGrepNoMatch() {
		GrepShellDebugMonitor grepDebug = new GrepShellDebugMonitor("asdf");
		shell.setDebugMonitor(grepDebug);
		try {
			grep.sendInput("asdf");
		} catch (IOException ioEx) {
			/*
			 */
		}
		try {
			Thread.sleep(4000);
		} catch (InterruptedException interruptedEx) {
			/*
			 */
		}	
		assertFalse("Grep output matched!", grepDebug.outputMatches());
		shell.setDebugMonitor(null);
	}

	@Test public void shellProcessTestGrepMatch() {
		GrepShellDebugMonitor grepDebug = new GrepShellDebugMonitor("asdfg");
		shell.setDebugMonitor(grepDebug);
		try {
			grep.sendInput("asdfg");
		} catch (IOException ioEx) {
			/*
			 */
		}
		try {
			Thread.sleep(4000);
		} catch (InterruptedException interruptedEx) {
			/*
			 */
		}	
		assertTrue("Grep output did not match!", grepDebug.outputMatches());
		shell.setDebugMonitor(null);
	}

	public static void main(String args[]) {
		ShellTest test = new ShellTest();
		shellProcessTestSetup();
		test.shellProcessTestBefore();
		test.shellProcessTestSameTag();
		test.shellProcessTestAfter();
		shellProcessTestStop();
	}
}
