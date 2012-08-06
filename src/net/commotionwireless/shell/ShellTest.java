package net.commotionwireless.shell;

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
	}
	@Before public void shellProcessTestBefore() {
		grep = new ShellProcess("G", "grep --line-buffered asdf", shell);
		assertTrue(grep.start());
	}

	@After public void shellProcessTestAfter() {
		assertTrue(grep.stop());
	}

	@Test public void shellProcessTestSameTag() {
		ShellProcess grep2 = null;
		grep2 = new ShellProcess("G", "grep --line-buffered asdf", shell);
		assertFalse("grep2.start() did not fail.", grep2.start());
	}

	@AfterClass public static void shellProcessTestStop() {
		shell.stopShell();	
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
