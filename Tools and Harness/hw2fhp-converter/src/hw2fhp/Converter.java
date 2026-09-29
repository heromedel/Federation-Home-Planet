package hw2fhp;

import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;

import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

/**
 * HW to FHP Converter: a one-off tool that brings an FTL Homeworld saves folder (and the designs and remodels beside
 * the old program) into Federation Home Planet's vault. It borrows Home Planet's own classes from the jar beside it,
 * so it must sit in the same folder as "Federation Home Planet.jar". Run it once, then delete it.
 */
public final class Converter {
	private final JFrame frame = new JFrame("HW to FHP Converter");
	private final JTextField saves = new JTextField(44), oldApp = new JTextField(44), game = new JTextField(44), slip = new JTextField(44);
	private final JTextArea report = new JTextArea(18, 80);
	private final JButton convert = new JButton("Convert");

	public static void main(String[] args) {
		System.setProperty("homeplanet.noGameCheck", "true");
		SwingUtilities.invokeLater(new Runnable() { public void run() { new Converter().show(); } });
	}

	private void show() {
		// what the two programs' cfg files say, if they're beside this converter
		File here = here();
		Properties fhp = load(new File(here, "federation-home-planet.cfg")), hw = load(new File(here, Legacy.OLD_CFG));
		saves.setText(first(fhp.getProperty("ftlSavePath"), hw.getProperty("ftlSavePath")));
		game.setText(first(fhp.getProperty("ftlDatsPath"), hw.getProperty("ftlDatsPath")));
		slip.setText(first(fhp.getProperty("slipstream_dir"), hw.getProperty("slipstream_dir")));
		boolean oldHere = new File(here, "homeworld-designs.xml").isFile() || new File(here, "homeworld-remodels.xml").isFile() || new File(here, Legacy.OLD_CFG).isFile();
		oldApp.setText(oldHere ? here.getPath() : "");

		JPanel form = new JPanel(new GridBagLayout());
		GridBagConstraints c = new GridBagConstraints();
		c.insets = new Insets(3, 6, 3, 6);
		c.anchor = GridBagConstraints.WEST;
		c.gridy = 0;
		c.gridx = 0; c.gridwidth = 3;
		form.add(new JLabel("<html>Brings your FTL Homeworld ships, storage, designs and remodels into Federation Home Planet's vault.<br>"
				+ "Everything is zipped first; the old files end up in a \"" + Convert.BACKUP + "\" folder inside the saves folder.<br>"
				+ "Quit FTL and Home Planet before converting.</html>"), c);
		c.gridwidth = 1;
		row(form, c, "FTL saves folder (with continue.sav, Homeworld.sav...)", saves, true);
		row(form, c, "Old FTL Homeworld program folder (homeworld-designs.xml, homeworld-art), or blank", oldApp, true);
		row(form, c, "FTL's folder (ftl.dat)", game, true);
		row(form, c, "Slipstream folder (to swap the companion mod), or blank", slip, true);
		c.gridy++; c.gridx = 0; c.gridwidth = 3; c.anchor = GridBagConstraints.EAST;
		convert.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { start(); } });
		form.add(convert, c);

		report.setEditable(false);
		report.setFont(new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 12));
		frame.getContentPane().add(form, BorderLayout.NORTH);
		frame.getContentPane().add(new JScrollPane(report), BorderLayout.CENTER);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.pack();
		frame.setLocationRelativeTo(null);
		frame.setVisible(true);
	}
	private void row(JPanel form, GridBagConstraints c, String label, final JTextField field, boolean folder) {
		c.gridy++;
		c.gridx = 0; form.add(new JLabel(label), c);
		c.gridx = 1; form.add(field, c);
		JButton b = new JButton("Browse...");
		b.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				JFileChooser fc = new JFileChooser(field.getText().isEmpty() ? null : field.getText());
				fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
				if (fc.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) field.setText(fc.getSelectedFile().getPath());
			}
		});
		c.gridx = 2; form.add(b, c);
	}

	private void start() {
		final File s = dir(saves), o = dir(oldApp), g = dir(game), sl = dir(slip);
		if (s == null) { JOptionPane.showMessageDialog(frame, "The saves folder isn't there.", "Convert", JOptionPane.WARNING_MESSAGE); return; }
		if (g == null || !new File(g, "ftl.dat").isFile()) { JOptionPane.showMessageDialog(frame, "FTL's folder must hold ftl.dat.", "Convert", JOptionPane.WARNING_MESSAGE); return; }
		if (!Convert.anythingToConvert(s, o)) { JOptionPane.showMessageDialog(frame, "Nothing to convert: no FTL Homeworld files in " + s.getPath() + (o != null ? " or " + o.getPath() : "") + ".", "Convert", JOptionPane.INFORMATION_MESSAGE); return; }
		if (JOptionPane.showConfirmDialog(frame, "Convert now? FTL and Home Planet must be closed.", "Convert", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
		convert.setEnabled(false);
		report.setText("");
		new Thread(new Runnable() {
			public void run() {
				try {
					Convert.run(s, o, g, sl, new Convert.Log() {
						public void line(final String t) { SwingUtilities.invokeLater(new Runnable() { public void run() { report.append(t + "\n"); } }); }
					});
					done(null);
				} catch (final Exception e) {
					done(e);
				}
			}
		}, "convert").start();
	}
	private void done(final Exception e) {
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				if (e != null) {
					java.io.StringWriter sw = new java.io.StringWriter();
					e.printStackTrace(new java.io.PrintWriter(sw));
					report.append("\nFAILED: " + e + "\n" + sw + "\nNothing is lost: the zip in " + Convert.BACKUP + " has everything as it was.\n");
					convert.setEnabled(true);
				} else {
					report.append("\nFinished. Start Federation Home Planet, then Settings > Patch mods.\n");
				}
				try {
					File s = dir(saves);
					if (s != null) homeplanet.core.SafeFiles.writeText(new File(new File(s, Convert.BACKUP), "conversion-report.txt"), report.getText(), false);
				} catch (Exception x) { }
			}
		});
	}

	private static File dir(JTextField f) {
		String t = f.getText().trim();
		if (t.isEmpty()) return null;
		File d = new File(t);
		return d.isDirectory() ? d : null;
	}
	private static String first(String a, String b) { return a != null && !a.isEmpty() ? a : b != null ? b : ""; }
	private static Properties load(File f) {
		Properties p = new Properties();
		if (!f.isFile()) return p;
		InputStream in = null;
		try { in = new FileInputStream(f); p.load(in); } catch (Exception e) { } finally { try { if (in != null) in.close(); } catch (Exception e) { } }
		return p;
	}
	/** The converter's own folder (the jar's), which is also Home Planet's. */
	private static File here() {
		try {
			File jar = new File(Converter.class.getProtectionDomain().getCodeSource().getLocation().toURI());
			if (jar.isFile()) return jar.getAbsoluteFile().getParentFile();
		} catch (Exception e) { }
		return new File(".").getAbsoluteFile().getParentFile();
	}
}
