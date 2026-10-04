package homeplanet.ui;

import java.awt.Component;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JOptionPane;

import homeplanet.vault.Reputation;

/**
 * A fee paid in scrap or reputation (heromedel, 5.13): a New Journey, taking a system off at Refit, stripping a ship's
 * systems. All in scrap, all in reputation, or the scrap there is and reputation for the rest. Reputation never goes
 * below zero for these (a plea, a promise of adventure and rest in quarters may). Only with Reputation on: without it,
 * the callers keep their scrap-only questions.
 */
final class RepPay {
	private RepPay() { }

	/**
	 * Asks how to pay {@code fee}: returns {scrap, reputation}, or null (cancelled, or neither can pay: then it says so).
	 * {@code what} starts the question ("Taking the Shields off costs"); {@code scrapFrom} names where the scrap is.
	 */
	static int[] choose(Component owner, String title, String what, int fee, int scrapHave, int repHave, String scrapFrom) {
		List<String> labels = new ArrayList<String>();
		List<int[]> pays = new ArrayList<int[]>();
		if (scrapHave >= fee) { labels.add(fee + " scrap"); pays.add(new int[] {fee, 0}); }
		if (repHave >= fee) { labels.add(fee + " reputation"); pays.add(new int[] {0, fee}); }
		if (scrapHave > 0 && scrapHave < fee && repHave >= fee - scrapHave) { labels.add(scrapHave + " scrap and " + (fee - scrapHave) + " reputation"); pays.add(new int[] {scrapHave, fee - scrapHave}); }
		String have = scrapFrom + " has " + Math.max(0, scrapHave) + " scrap.\nYour reputation is " + Reputation.signed(repHave) + ".";
		if (pays.isEmpty()) {
			JOptionPane.showMessageDialog(owner, what + " " + fee + " scrap or reputation.\n\n" + have + "\n\nReputation can't go below zero for this.", title, JOptionPane.INFORMATION_MESSAGE);
			return null;
		}
		labels.add("Cancel");
		Object[] options = labels.toArray();
		int pick = JOptionPane.showOptionDialog(owner, what + " " + fee + " scrap or reputation.\n\n" + have + "\n\nHow will you pay?", title,
				JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[options.length - 1]);
		return pick < 0 || pick >= pays.size() ? null : pays.get(pick);
	}
	/** A payment in words, for the history log: "25 scrap", "25 reputation", "10 scrap and 15 reputation". */
	static String words(int[] pay) {
		return pay[1] == 0 ? pay[0] + " scrap" : pay[0] == 0 ? pay[1] + " reputation" : pay[0] + " scrap and " + pay[1] + " reputation";
	}
}
