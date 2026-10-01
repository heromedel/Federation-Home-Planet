package homeplanet.parser;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import homeplanet.core.SafeFiles;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A custom ship's papers for Long Range Comm.: the blueprint the station drew up for her (a remodel, or a design's built
 * copy) and the pictures it uses from the art folder. They travel in her package, so another station can fly her.
 *
 * <p>Blueprint numbers are each station's own (two stations can both have a DESIGN_2, different ships), so arriving
 * papers are fitted in: a blueprint with the same content as one here is used as it is (a ship coming home finds her
 * own); otherwise she gets the next free number, her pictures are filed under it, and the names her save gives (her
 * blueprint, twice, and her picture set) are renamed in the save's bytes before it's ever read. A received blueprint
 * isn't commissionable: she flies on it, and it stays in the mod while ships use it.
 */
public final class ShipPapers {
	private static final Logger log = LoggerFactory.getLogger(ShipPapers.class);
	private ShipPapers() { }

	public static final String BLUEPRINT = "blueprint.xml", ART = "art/";
	private static final int MAX_PICTURES = 40, MAX_SIDE = 4096;
	private static final String CRLF = "\r\n";

	/** Is this a blueprint a station drew up (a remodel or a design), rather than one FTL or the bundled copies have? */
	public static boolean custom(String bpId) {
		return bpId != null && (CompanionMod.isRemodelId(bpId) || bpId.matches("PLAYER_SHIP_DESIGN_\\d+(_V\\d+)?" + Retrofit.SUFFIX));
	}

	// ---- packing (the sender) ----

	/** Her papers as package entries: the blueprint and her pictures. Empty for a ship on a stock blueprint. Throws if they can't be found. */
	public static Map<String, byte[]> papersOf(String bpId) throws IOException {
		Map<String, byte[]> out = new LinkedHashMap<String, byte[]>();
		if (!custom(bpId)) return out;
		ShipDesign art;
		String xml;
		if (CompanionMod.isRemodelId(bpId)) {
			CompanionMod.Remodel r = CompanionMod.find(CompanionMod.load(), bpId);
			if (r == null) throw new IOException("her blueprint (" + bpId + ") isn't on file at this station");
			xml = "<remodels>" + CRLF + CompanionMod.remodelXml(r) + "</remodels>" + CRLF;
			art = r.geometry;
		} else {
			ShipDesign d = builtCopy(ShipDesign.load(), bpId);
			if (d == null) throw new IOException("her blueprint (" + bpId + ") isn't on file at this station");
			xml = "<designs>" + CRLF + ShipDesign.xmlOf(d) + "</designs>" + CRLF;
			art = d;
		}
		out.put(BLUEPRINT, xml.getBytes(StandardCharsets.UTF_8));
		for (String src : ShipArt.filesOf(art)) {
			File f = ShipArt.file(src);
			if (!f.isFile()) throw new IOException("a picture of hers is missing: " + f);
			out.put(ART + f.getName(), SafeFiles.read(f));
		}
		return out;
	}
	/** The built copy (or kept old version) of a design that this blueprint is, or null. */
	static ShipDesign builtCopy(List<ShipDesign> designs, String bpId) {
		for (ShipDesign d : designs) if (d.built && !d.isWorking() && DesignExport.bpId(d).equals(bpId)) return d;
		return null;
	}

	// ---- reading what arrives ----

	/** Papers as they arrived: the blueprint (a remodel or a design) and her pictures by file name. */
	static final class Incoming {
		CompanionMod.Remodel remodel;
		ShipDesign design;
		final Map<String, BufferedImage> pictures = new LinkedHashMap<String, BufferedImage>();
		/** The pictures her blueprint uses, as they arrived. */
		ShipDesign art() { return remodel != null ? remodel.geometry : design; }
	}

	/**
	 * Reads and checks a custom ship's papers without installing anything: one blueprint, the one her save names, that
	 * the station's own readers accept; pictures that are pictures, and all the ones it uses. Throws with the reason.
	 */
	static Incoming read(Map<String, byte[]> files, String bpId) throws IOException {
		byte[] xml = files.get(BLUEPRINT);
		if (xml == null) throw new IOException("her blueprint didn't come with her");
		Incoming in = new Incoming();
		int pictures = 0;
		for (Map.Entry<String, byte[]> e : files.entrySet()) {
			if (!e.getKey().startsWith(ART)) continue;
			String name = e.getKey().substring(ART.length());
			if (!name.matches("[A-Za-z0-9_.-]{1,80}\\.png") || ++pictures > MAX_PICTURES) throw new IOException("her pictures aren't as a blueprint's are (" + shown(name) + ")");
			BufferedImage img;
			try { img = ImageIO.read(new ByteArrayInputStream(e.getValue())); } catch (Exception x) { img = null; }
			if (img == null || img.getWidth() > MAX_SIDE || img.getHeight() > MAX_SIDE) throw new IOException(shown(name) + " isn't a picture The Home Planet Station can use");
			in.pictures.put(name, img);
		}
		File tmp = File.createTempFile("papers-", ".xml");
		try {
			SafeFiles.write(tmp, xml);
			String text = new String(xml, StandardCharsets.UTF_8);
			if (text.contains("<!DOCTYPE") || text.contains("<!ENTITY")) throw new IOException("her blueprint isn't a plain blueprint");
			if (CompanionMod.isRemodelId(bpId)) {
				List<CompanionMod.Remodel> rs = new ArrayList<CompanionMod.Remodel>();
				try { CompanionMod.readInto(tmp, rs); } catch (Exception x) { throw new IOException("her blueprint couldn't be read: " + x.getMessage()); }
				if (rs.size() != 1 || !rs.get(0).id.equals(bpId)) throw new IOException("her blueprint isn't the one her save names");
				in.remodel = rs.get(0);
				if (!in.remodel.base.matches("PLAYER_SHIP_[A-Z0-9_]+") || !in.remodel.id.startsWith(in.remodel.base + "_R")) throw new IOException("her blueprint names an unknown model");
				if (net.blerf.ftl.parser.DataManager.get().getShip(in.remodel.base + Retrofit.SUFFIX) == null) throw new IOException("her model (" + in.remodel.base + ") isn't in this station's game data");
				if (!java.util.Arrays.asList(CompanionMod.FILES).contains(in.remodel.file)) throw new IOException("her blueprint names an unknown blueprint file");
				if (in.remodel.geometry != null) in.remodel.geometry.id = in.remodel.id;
			} else {
				List<ShipDesign> ds = new ArrayList<ShipDesign>();
				try { ShipDesign.readInto(tmp, ds); } catch (Exception x) { throw new IOException("her blueprint couldn't be read: " + x.getMessage()); }
				if (ds.size() != 1 || !ds.get(0).built || !DesignExport.bpId(ds.get(0)).equals(bpId)) throw new IOException("her blueprint isn't the one her save names");
				in.design = ds.get(0);
				if (!in.design.id.matches("DESIGN_\\d+") || in.design.version < 1) throw new IOException("her blueprint's number isn't a design's");
			}
		} finally {
			tmp.delete();
		}
		ShipDesign art = in.art();
		if (art != null) for (String src : ShipArt.filesOf(art)) {
			String name = new File(src.substring(5)).getName();
			if (!src.startsWith("file:art/") || !in.pictures.containsKey(name)) throw new IOException("a picture of hers didn't come with her (" + shown(name) + ")");
		}
		if (art != null) for (String src : new String[] {art.art, art.floor}) {
			if (src.startsWith("game:") && !src.substring(5).matches("[A-Za-z0-9_]{1,64}")) throw new IOException("her blueprint names game art that isn't there");
		}
		return in;
	}
	/** A file name from another station, as safe to show. */
	private static String shown(String s) { return s.replaceAll("[^A-Za-z0-9_. -]", "?").substring(0, Math.min(s.length(), 60)); }

	/** Checks a custom ship's papers (as {@link #read}), installing nothing. */
	public static void check(Map<String, byte[]> files, String bpId) throws IOException { read(files, bpId); }

	// ---- fitting them in (the receiver) ----

	/** What installing a ship's papers did: her save, renamed onto the blueprint here; and whether the mod must be rebuilt. */
	public static final class Installed {
		public final byte[] save;
		public final String bpId;
		public final boolean newBlueprint;
		Installed(byte[] save, String bpId, boolean newBlueprint) { this.save = save; this.bpId = bpId; this.newBlueprint = newBlueprint; }
	}

	/**
	 * Fits a custom ship's papers in at this station and returns her save renamed to match. Uses a blueprint here with
	 * the same content if there is one; otherwise files hers under the next free number (with her pictures), saves the
	 * list and registers it. Nothing is left half-installed if it fails. The caller rebuilds the mod when it's new.
	 */
	public static Installed install(Map<String, byte[]> files, byte[] save, String bpId) throws IOException {
		Incoming in = read(files, bpId);
		Map<String, String> renames = new LinkedHashMap<String, String>();
		if (in.remodel != null) {
			List<CompanionMod.Remodel> all = CompanionMod.load();
			String fp = fingerprint(in.remodel, in.pictures);
			for (CompanionMod.Remodel r : all) {
				if (!r.base.equals(in.remodel.base) || !fingerprint(r, null).equals(fp)) continue;
				rename(renames, in.remodel, r);
				return new Installed(renamed(save, renames), r.id, false);
			}
			CompanionMod.Remodel r = in.remodel.copy();
			r.id = CompanionMod.nextId(r.base, all);
			r.starter = false; // she came as a ship, not as plans to build more
			List<File> made = new ArrayList<File>();
			if (r.geometry != null) { r.geometry.id = r.id; refile(r.geometry, in.pictures, made); }
			rename(renames, in.remodel, r);
			all.add(r);
			try {
				CompanionMod.save(all);
				CompanionMod.register(CompanionMod.load());
				return new Installed(renamed(save, renames), r.id, true);
			} catch (IOException e) {
				undo(made);
				throw e;
			}
		}
		List<ShipDesign> all = ShipDesign.load();
		String fp = fingerprint(in.design, in.pictures);
		for (ShipDesign d : all) {
			if (!d.built || d.isWorking() || !fingerprint(d, null).equals(fp)) continue;
			rename(renames, in.design, d);
			return new Installed(renamed(save, renames), DesignExport.bpId(d), false);
		}
		ShipDesign d = ShipDesign.copy(in.design);
		d.id = ShipDesign.create(all).id;
		d.snapshotOf = d.id; // a built copy, with no working copy: like a retired design, she only flies
		d.frozenOf = null;
		d.built = true;
		d.starter = false;
		d.retired = true;
		List<File> made = new ArrayList<File>();
		refile(d, in.pictures, made);
		if (!DesignExport.problems(d).isEmpty()) { undo(made); throw new IOException("her blueprint has problems here: " + DesignExport.problems(d)); }
		rename(renames, in.design, d);
		all.add(d);
		boolean saved = false;
		try {
			ShipDesign.save(all);
			saved = true;
			CompanionMod.register(CompanionMod.load());
			return new Installed(renamed(save, renames), DesignExport.bpId(d), true);
		} catch (IOException e) {
			if (saved) { all.remove(d); try { ShipDesign.save(all); } catch (IOException again) { log.error("Could not take back the design " + d.id, again); } }
			undo(made);
			throw e;
		}
	}
	/** Takes a blueprint back out (her save couldn't be read on it after all). */
	public static void uninstall(String bpId) {
		try {
			if (CompanionMod.isRemodelId(bpId)) {
				List<CompanionMod.Remodel> all = CompanionMod.load();
				CompanionMod.Remodel r = CompanionMod.find(all, bpId);
				if (r == null) return;
				all.remove(r);
				CompanionMod.save(all);
			} else {
				List<ShipDesign> all = ShipDesign.load();
				ShipDesign d = builtCopy(all, bpId);
				if (d == null) return;
				all.remove(d);
				ShipDesign.save(all);
			}
			CompanionMod.register(CompanionMod.load());
			ShipArt.sweep();
		} catch (IOException e) {
			log.error("Could not take back the blueprint " + bpId, e);
		}
	}

	/** Her pictures filed in this station's art folder under her (new) number; the blueprint's sources follow. */
	private static void refile(ShipDesign d, Map<String, BufferedImage> pictures, List<File> made) throws IOException {
		try {
			if (d.art.startsWith("file:")) d.art = keep(pictures, d.art, d.id, "base", made);
			if (d.floor.startsWith("file:")) d.floor = keep(pictures, d.floor, d.id, "floor", made);
			for (int i = 0; i < d.gibFiles.size(); i++) if (d.gibFiles.get(i).startsWith("file:")) d.gibFiles.set(i, keep(pictures, d.gibFiles.get(i), d.id, "gib" + (i + 1), made));
		} catch (IOException e) {
			undo(made);
			throw e;
		}
	}
	private static String keep(Map<String, BufferedImage> pictures, String src, String id, String kind, List<File> made) throws IOException {
		BufferedImage img = pictures.get(new File(src.substring(5)).getName());
		String now = ShipArt.importImage(img, id, kind);
		made.add(ShipArt.file(now));
		return now;
	}
	private static void undo(List<File> made) { for (File f : made) f.delete(); }

	/** The names her save gives that change with her blueprint: the blueprint itself, and her picture set when it's her own. */
	private static void rename(Map<String, String> renames, Object from, Object to) {
		String fb = from instanceof ShipDesign ? DesignExport.bpId((ShipDesign) from) : ((CompanionMod.Remodel) from).id;
		String tb = to instanceof ShipDesign ? DesignExport.bpId((ShipDesign) to) : ((CompanionMod.Remodel) to).id;
		if (!fb.equals(tb)) renames.put(fb, tb);
		ShipDesign fa = from instanceof ShipDesign ? (ShipDesign) from : ((CompanionMod.Remodel) from).geometry;
		ShipDesign ta = to instanceof ShipDesign ? (ShipDesign) to : ((CompanionMod.Remodel) to).geometry;
		if (fa != null && ta != null) {
			String fg = DesignExport.gfx(fa), tg = DesignExport.gfx(ta);
			if (!fg.equals(tg)) renames.put(fg, tg);
		}
	}

	/**
	 * The save with these names changed. FTL writes a name as its length (four bytes, low first) and its letters; only
	 * whole names are changed, so a longer name that happens to contain one is left alone.
	 */
	static byte[] renamed(byte[] save, Map<String, String> renames) {
		byte[] out = save;
		for (Map.Entry<String, String> e : renames.entrySet()) out = replaceName(out, e.getKey(), e.getValue());
		return out;
	}
	private static byte[] replaceName(byte[] in, String from, String to) {
		byte[] f = framed(from), t = framed(to);
		java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream(in.length + 64);
		int i = 0;
		while (i < in.length) {
			if (i + f.length <= in.length && matches(in, i, f)) { out.write(t, 0, t.length); i += f.length; }
			else out.write(in[i++]);
		}
		return out.toByteArray();
	}
	private static byte[] framed(String s) {
		byte[] b = s.getBytes(StandardCharsets.US_ASCII);
		byte[] out = new byte[b.length + 4];
		for (int k = 0; k < 4; k++) out[k] = (byte) (b.length >>> (8 * k));
		System.arraycopy(b, 0, out, 4, b.length);
		return out;
	}
	private static boolean matches(byte[] in, int at, byte[] f) {
		for (int k = 0; k < f.length; k++) if (in[at + k] != f[k]) return false;
		return true;
	}

	// ---- telling the same blueprint ----

	/**
	 * What the game is given for a blueprint, numbered alike: two blueprints with the same fingerprint are the same ship
	 * whatever their numbers, names or dates. Her pictures count by what they show, not by their file names.
	 */
	private static String fingerprint(ShipDesign d, Map<String, BufferedImage> pictures) {
		ShipDesign c = ShipDesign.copy(d);
		c.id = "DESIGN_0"; c.name = ""; c.made = ""; c.version = 1; c.frozenOf = null; c.snapshotOf = null; c.built = true; c.starter = false; c.retired = false;
		c.art = pictureKey(c.art, pictures);
		c.floor = pictureKey(c.floor, pictures);
		for (int i = 0; i < c.gibFiles.size(); i++) c.gibFiles.set(i, pictureKey(c.gibFiles.get(i), pictures));
		return ShipDesign.xmlOf(c);
	}
	private static String fingerprint(CompanionMod.Remodel r, Map<String, BufferedImage> pictures) {
		CompanionMod.Remodel c = r.copy();
		c.id = c.base + "_R0" + Retrofit.SUFFIX; c.ship = ""; c.made = ""; c.starter = false;
		c.geometry = null;
		return CompanionMod.remodelXml(c) + (r.geometry == null ? "" : fingerprint(r.geometry, pictures));
	}
	/** A picture source as what it shows: the game's by name, a file by a digest of its pixels. */
	private static String pictureKey(String src, Map<String, BufferedImage> pictures) {
		if (!src.startsWith("file:")) return src;
		BufferedImage img = pictures != null ? pictures.get(new File(src.substring(5)).getName()) : ShipArt.load(src, "");
		if (img == null) return "missing";
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-1");
			int w = img.getWidth(), h = img.getHeight();
			int[] px = img.getRGB(0, 0, w, h, null, 0, w);
			java.nio.ByteBuffer b = java.nio.ByteBuffer.allocate(8 + px.length * 4);
			b.putInt(w).putInt(h);
			for (int p : px) b.putInt(p);
			StringBuilder sb = new StringBuilder("img:");
			for (byte x : md.digest(b.array())) sb.append(String.format("%02x", x));
			return sb.toString();
		} catch (Exception e) {
			return "unreadable";
		}
	}
}
