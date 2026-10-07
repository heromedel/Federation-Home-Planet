package homeplanet.vault;

import java.io.File;
import java.io.IOException;
import java.util.Properties;

import homeplanet.core.Store;

/**
 * A ship flying in the fleet who belongs to someone else, kept in her history folder beside her {@link TradeMark}: her
 * owner, and what kind of loan she's on ("repair-job": the Nightjar, returned from aboard her). The Cargo Bay's
 * Return button asks this, not which ship she is, so a ship on loan over Long Range Comm. could use it too
 * (docs/IDEAS.md, Idea D).
 */
public final class Borrowed {
	public static final String FILE = "borrowed.txt";
	public final String owner, kind;
	private Borrowed(String owner, String kind) { this.owner = owner; this.kind = kind; }

	/** Her mark, or null if she's the fleet's own. */
	public static Borrowed of(Vault v, String id) {
		File f = new File(v.folderOfId(id), FILE);
		if (!f.isFile()) return null;
		try {
			Properties p = Store.load(f);
			return new Borrowed(p.getProperty("owner", ""), p.getProperty("kind", ""));
		} catch (IOException e) {
			return null;
		}
	}
	/** Marks her as borrowed from this owner. */
	public static void mark(Vault v, String id, String owner, String kind) throws IOException {
		Properties p = new Properties();
		p.setProperty("owner", owner);
		p.setProperty("kind", kind);
		Store.write(new File(v.folderOfId(id), FILE), p, "She belongs to someone else: Federation Home Planet reads this for the Return button");
	}
}
