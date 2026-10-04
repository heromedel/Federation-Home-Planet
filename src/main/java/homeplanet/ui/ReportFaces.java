package homeplanet.ui;

import java.awt.Component;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import javax.swing.GrayFilter;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

import net.blerf.ftl.parser.SavedGameParser.CrewState;

import homeplanet.parser.Assignments;

/**
 * An expedition report with its crew's faces: each crew member's line starts with their icon, a health bar under it as
 * the Cargo Bay draws one (green and red for a hurt, purple and full for the infirmary), greyed for the dead and the
 * taken. The same in the pop-up and the inbox's letter.
 */
final class ReportFaces {
	private ReportFaces() {}

	/** The report's words into this document, a face before each crew member's line. */
	static void insert(StyledDocument doc, String body, List<Assignments.Face> faces, AttributeSet base) throws BadLocationException {
		List<Assignments.Face> byName = new ArrayList<Assignments.Face>(faces);
		byName.sort(new Comparator<Assignments.Face>() { // the longest name first: "Ann" must not take "Anna"'s line
			public int compare(Assignments.Face a, Assignments.Face b) { return b.crew.getName().length() - a.crew.getName().length(); }
		});
		String[] lines = body.split("\n", -1);
		for (int i = 0; i < lines.length; i++) {
			String line = lines[i];
			int start = doc.getLength();
			Icon icon = null;
			for (Assignments.Face f : byName) {
				if (!line.startsWith(f.crew.getName() + " ")) continue;
				icon = icon(f);
				if (icon != null) {
					SimpleAttributeSet a = new SimpleAttributeSet();
					StyleConstants.setIcon(a, icon);
					doc.insertString(doc.getLength(), " ", a);
				}
				byName.remove(f);
				break;
			}
			doc.insertString(doc.getLength(), line + (i < lines.length - 1 ? "\n" : ""), base);
			if (icon != null) { // a hanging indent: a line that wraps lines up with the words, not under the face
				SimpleAttributeSet hang = new SimpleAttributeSet();
				StyleConstants.setLeftIndent(hang, icon.getIconWidth());
				StyleConstants.setFirstLineIndent(hang, -icon.getIconWidth());
				doc.setParagraphAttributes(start, doc.getLength() - start, hang, false);
			}
		}
	}

	/** A face: the crew icon with its health bar (purple and full in the infirmary), greyed for the dead and the taken. */
	static Icon icon(Assignments.Face f) {
		Icon face = IconFactory.crewIcon(f.crew);
		if (face == null) return null;
		if ("dead".equals(f.state) || "taken".equals(f.state)) return grey(face);
		if ("infirmary".equals(f.state)) return withBar(face, 1f, CrewReport.INFIRMARY);
		CrewState c = f.crew;
		int max = c.getRace() == null ? 100 : c.getRace().getMaxHealth();
		return withBar(face, c.getHealth() >= max ? -1f : c.getHealth() / (float) max, CrewReport.HEALTH);
	}

	/** Room between a face and its words, part of the face so the hanging indent knows it. */
	private static final int GAP = 8;
	/** The icon with a thin bar under it (fill below 0: no bar, but the same height, so the lines stay even). */
	private static Icon withBar(final Icon icon, final float fill, final java.awt.Color c) {
		return new Icon() {
			public int getIconWidth() { return Math.max(icon.getIconWidth(), 24) + GAP; }
			public int getIconHeight() { return icon.getIconHeight() + 4; }
			public void paintIcon(Component cmp, Graphics g, int x, int y) {
				int w = getIconWidth() - GAP;
				icon.paintIcon(cmp, g, x + (w - icon.getIconWidth()) / 2, y);
				if (fill < 0) return;
				int bx = x + (w - 24) / 2, by = y + icon.getIconHeight() + 1;
				if (c == CrewReport.HEALTH) { g.setColor(CrewReport.HURT); g.fillRect(bx, by, 24, 2); }
				g.setColor(c);
				g.fillRect(bx, by, Math.max(1, Math.round(24 * fill)), 2);
			}
		};
	}

	private static Icon grey(Icon icon) {
		BufferedImage img = new BufferedImage(Math.max(1, icon.getIconWidth()), Math.max(1, icon.getIconHeight()), BufferedImage.TYPE_INT_ARGB);
		Graphics g = img.createGraphics();
		icon.paintIcon(null, g, 0, 0);
		g.dispose();
		return withBar(new ImageIcon(GrayFilter.createDisabledImage(img)), -1f, CrewReport.HEALTH);
	}
}
