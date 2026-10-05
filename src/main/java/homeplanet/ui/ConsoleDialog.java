package homeplanet.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingWorker;

import homeplanet.core.StationConsole;
import homeplanet.vault.Vault;

/**
 * The console (heromedel, 5.22): ~ opens it over the station. Modal, so nothing else takes input while it's up; while
 * /passtime runs, its own input and close are locked too, for half a second at least, then the Space Dock is rebuilt
 * and what the days brought is told. Up and Down bring back earlier lines; Esc closes.
 */
final class ConsoleDialog extends JDialog {
	/** At least this long locked for a /passtime, however fast the days go (heromedel: fast hands can't cut in). */
	static final int HOLD_MS = 500;
	private static final List<String> history = new ArrayList<String>(); // for the session, across openings
	private final MainFrame frame;
	private final JTextArea out = new JTextArea(14, 60);
	private final JTextField in = new JTextField();
	private final JLabel status = new JLabel(" ");
	private int recall;
	private boolean busy;

	static void open(MainFrame frame) { new ConsoleDialog(frame).setVisible(true); }

	private ConsoleDialog(MainFrame frame) {
		super(frame, "Console", ModalityType.APPLICATION_MODAL);
		this.frame = frame;
		out.setEditable(false);
		out.setLineWrap(true); out.setWrapStyleWord(true);
		out.setFont(MenuTheme.TEXT_FONT);
		out.setBackground(RecordsLog.BG);
		out.setForeground(new java.awt.Color(0xdc, 0xe4, 0xeb));
		out.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
		out.setText("Type a command and press Enter.");
		JScrollPane scroll = new JScrollPane(out);
		scroll.setBorder(BorderFactory.createMatteBorder(0, 0, 2, 0, MenuTheme.GOLD));
		in.setFont(MenuTheme.TEXT_FONT);
		in.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { enter(); } });
		in.addKeyListener(new KeyAdapter() {
			@Override public void keyPressed(KeyEvent e) {
				if (history.isEmpty()) return;
				if (e.getKeyCode() == KeyEvent.VK_UP) recall = Math.max(0, recall - 1);
				else if (e.getKeyCode() == KeyEvent.VK_DOWN) recall = Math.min(history.size(), recall + 1);
				else return;
				in.setText(recall < history.size() ? history.get(recall) : "");
			}
		});
		recall = history.size();
		status.setFont(MenuTheme.TEXT_FONT);
		JPanel south = new JPanel(new BorderLayout(0, 4));
		south.add(in, BorderLayout.CENTER);
		south.add(status, BorderLayout.SOUTH);
		JPanel body = new JPanel(new BorderLayout(0, 6));
		body.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		body.add(scroll, BorderLayout.CENTER);
		body.add(south, BorderLayout.SOUTH);
		getContentPane().add(body);
		setDefaultCloseOperation(DO_NOTHING_ON_CLOSE); // never mid-run
		addWindowListener(new java.awt.event.WindowAdapter() { @Override public void windowClosing(java.awt.event.WindowEvent e) { close(); } });
		getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "close");
		getRootPane().getActionMap().put("close", new javax.swing.AbstractAction() { public void actionPerformed(ActionEvent e) { close(); } });
		pack();
		setMinimumSize(new Dimension(480, 260));
		setLocationRelativeTo(frame);
		ScreenFit.keepOnScreen(this);
	}
	private void close() { if (!busy) dispose(); }
	private void say(String text) {
		if (text.isEmpty()) return;
		out.append("\n" + text);
		out.setCaretPosition(out.getDocument().getLength());
	}

	private void enter() {
		if (busy) return;
		String line = in.getText();
		in.setText("");
		if (line.trim().isEmpty()) return;
		history.add(line.trim());
		recall = history.size();
		say("> " + line.trim());
		StationConsole.Reply r = StationConsole.answer(line);
		say(r.text);
		if (r.days > 0) pass(r.days);
	}

	/** Days passed in the background, locked until done (and half a second at least), then told once. */
	private void pass(final int days) {
		if (!frame.atSpaceDock()) { say("Go back to the Space Dock first: time can't pass with another screen open."); return; }
		busy = true;
		in.setEnabled(false);
		final long start = System.currentTimeMillis();
		StationConsole.noted(days);
		new SwingWorker<StationConsole.Round, Integer>() {
			@Override protected StationConsole.Round doInBackground() {
				StationConsole.Round round = new StationConsole.Round();
				Vault v = Vault.get();
				for (int i = 1; i <= days; i++) { StationConsole.passDay(v, round); publish(i); }
				return round;
			}
			@Override protected void process(List<Integer> done) { status.setText("Day " + done.get(done.size() - 1) + " of " + days + "..."); }
			@Override protected void done() {
				StationConsole.Round got = null;
				try { got = get(); }
				catch (Exception e) { say("The Home Planet Station could not pass time: " + e.getMessage()); }
				final StationConsole.Round round = got;
				int wait = (int) Math.max(1, HOLD_MS - (System.currentTimeMillis() - start));
				javax.swing.Timer t = new javax.swing.Timer(wait, new ActionListener() { public void actionPerformed(ActionEvent e) {
					if (round != null) say("Passed " + days + (days == 1 ? " day." : " days."));
					status.setText(" ");
					frame.spaceDock.init(); // the Space Dock as the days left it, once
					busy = false;
					in.setEnabled(true);
					in.requestFocusInWindow();
					if (round != null) frame.spaceDock.tell(round.back, round.ransomNews, round.upAgain, true); // then what they brought
				} });
				t.setRepeats(false);
				t.start();
			}
		}.execute();
	}
}
