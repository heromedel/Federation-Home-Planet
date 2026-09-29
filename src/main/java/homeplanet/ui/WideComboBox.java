package homeplanet.ui;

import java.awt.Component;
import java.awt.Dimension;

import javax.swing.ComboBoxModel;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JList;
import javax.swing.ListCellRenderer;

/**
 * A dropdown whose open list is as wide as its longest line, so long entries aren't cut off.
 * Swing sizes the open list from the dropdown's own width, so this reports the wider width except while laying itself out.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
class WideComboBox extends JComboBox {
	private boolean layingOut = false;
	WideComboBox() { super(new DefaultComboBoxModel()); }
	private int cachedWidth = -1; // the list's width is measured once per model change, not on every paint
	WideComboBox(ComboBoxModel m) {
		super(m);
		m.addListDataListener(new javax.swing.event.ListDataListener() {
			public void intervalAdded(javax.swing.event.ListDataEvent e) { cachedWidth = -1; }
			public void intervalRemoved(javax.swing.event.ListDataEvent e) { cachedWidth = -1; }
			public void contentsChanged(javax.swing.event.ListDataEvent e) { cachedWidth = -1; }
		});
	}
	@Override
	public void doLayout() {
		try { layingOut = true; super.doLayout(); } finally { layingOut = false; }
	}
	@Override
	public Dimension getSize() {
		Dimension d = super.getSize();
		if (!layingOut) d.width = Math.max(d.width, listWidth());
		return d;
	}
	private int listWidth() {
		if (cachedWidth < 0) cachedWidth = measure();
		return cachedWidth;
	}
	private int measure() {
		ListCellRenderer r = getRenderer();
		JList probe = new JList();
		int w = 0;
		for (int i = 0; i < getItemCount(); i++) {
			Component c = r.getListCellRendererComponent(probe, getItemAt(i), i, false, false);
			w = Math.max(w, c.getPreferredSize().width);
		}
		return w + 24; // room for the scrollbar
	}
}
