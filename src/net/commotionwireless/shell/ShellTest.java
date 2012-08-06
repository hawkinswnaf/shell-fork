package net.commotionwireless.shell;

import java.io.IOException;
import org.junit.*;
import static org.junit.Assert.*;

public class ShellTest {
	private static Shell shell = null;
	ShellProcess grep = null;

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
		grep = new ShellProcess("G", "grep --line-buffered asdfg", shell);
		assertTrue(grep.start());
	}

	@After public void shellProcessTestAfter() {
		assertTrue(grep.stop());
	}

	@Test public void shellProcessTestSameTag() {
		ShellProcess grep2 = null;
		grep2 = new ShellProcess("G", "grep --line-buffered asdfg", shell);
		assertFalse("grep2.start() did not fail.", grep2.start());
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
