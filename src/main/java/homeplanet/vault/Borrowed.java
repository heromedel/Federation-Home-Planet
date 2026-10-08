package homeplanet.vault;

import java.io.IOException;
import java.util.Properties;


/**
 * A ship flying in the fleet who belongs to someone else, kept in her record beside her {@link TradeMark} (5.98; borrowed.txt before): her
 * owner, and what kind of loan she's on ("repair-job": the Nightjar, returned from aboard her). The Cargo Bay's
 * Return button asks this, not which ship she is, so a ship on loan over Long Range Comm. could use it too
 * (docs/IDEAS.md, Idea D).
 */
public final class Borrowed {
	public final String owner, kind;
	private Borrowed(String owner, String kind) { this.owner = owner; this.kind = kind; }

	/** Her mark, or null if she's the fleet's own. */
	public static Borrowed of(Vault v, String id) {
		Properties p = ShipStore.notes(v.folderOfId(id), ShipStore.BORROWED);
		return p.isEmpty() ? null : new Borrowed(p.getProperty("owner", ""), p.getProperty("kind", ""));
	}
	/** Marks her as borrowed from this owner. */
	public static void mark(Vault v, String id, String owner, String kind) throws IOException {
		Properties p = new Properties();
		p.setProperty("owner", owner);
		p.setProperty("kind", kind);
		v.setNotes(id, ShipStore.BORROWED, p);
	}
}
