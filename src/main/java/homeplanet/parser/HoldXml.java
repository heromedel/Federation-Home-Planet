package homeplanet.parser;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import homeplanet.core.SafeFiles;
import net.blerf.ftl.parser.SavedGameParser;
import net.blerf.ftl.parser.SavedGameParser.CrewState;
import net.blerf.ftl.parser.SavedGameParser.CrewType;
import net.blerf.ftl.parser.SavedGameParser.DroneState;
import net.blerf.ftl.parser.SavedGameParser.SavedGameState;
import net.blerf.ftl.parser.SavedGameParser.ShipState;
import net.blerf.ftl.parser.SavedGameParser.WeaponState;

/**
 * The Cargo Hold's contents as xml (5.84): cargohold/cargohold.xml, in place of the pretend ship's save it was kept in
 * since 4B. In memory the hold is still that save ({@link SaveHelper#createStorageSave}, filled from the file), so
 * every reader of the hold works as it did; only the file is new. Supplies, weapons, drones, augments and cargo are
 * written by name; each crew member by name, race, skills and record, with FTL's own bytes for her beside them (her
 * looks and the rest), which the named values overrule when someone has edited them by hand.
 */
public final class HoldXml {
	private HoldXml() {}

	public static final String ROOT = "cargohold";
	static final int VERSION = 1;

	/** Is this file the hold's xml (not a save, and not the 5.72 to 5.83 record that had its name)? */
	public static boolean isHold(byte[] b) {
		String head = new String(b, 0, Math.min(b.length, 400), StandardCharsets.UTF_8);
		return head.startsWith("<?xml") && head.contains("<" + ROOT);
	}
	public static boolean isHold(File f) {
		try { return f.isFile() && isHold(SafeFiles.read(f)); } catch (IOException e) { return false; }
	}

	/** A hold's file read, whichever it is: the xml, or a save (a 5.x fleet's storage.sav or cargohold.sav). */
	public static SavedGameState read(File f) throws IOException {
		byte[] b = SafeFiles.read(f);
		if (isHold(b)) return read(b);
		try { return new SavedGameParser().readSavedGame(f); }
		catch (IOException e) { throw e; }
		catch (Exception e) { throw new IOException(f.getName() + " could not be read: " + e, e); }
	}

	public static byte[] toBytes(SavedGameState gs) throws IOException {
		ShipState s = gs.getPlayerShip();
		int format = gs.getFileFormat();
		StringBuilder b = new StringBuilder();
		b.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n");
		b.append("<!-- What the Cargo Hold holds. Federation Home Planet rewrites this file; edit it by hand only when the station is closed. -->\r\n");
		b.append('<').append(ROOT).append(" version=\"").append(VERSION).append("\" name=\"").append(XmlText.attr(gs.getPlayerShipName()))
			.append("\" dlc=\"").append(gs.isDLCEnabled()).append("\" format=\"").append(format).append("\">\r\n");
		b.append("\t<scrap>").append(s.getScrapAmt()).append("</scrap>\r\n");
		b.append("\t<fuel>").append(s.getFuelAmt()).append("</fuel>\r\n");
		b.append("\t<missiles>").append(s.getMissilesAmt()).append("</missiles>\r\n");
		b.append("\t<droneParts>").append(s.getDronePartsAmt()).append("</droneParts>\r\n");
		for (WeaponState w : s.getWeaponList()) b.append("\t<weapon id=\"").append(XmlText.attr(w.getWeaponId())).append("\"/>\r\n");
		for (DroneState d : s.getDroneList()) b.append("\t<drone id=\"").append(XmlText.attr(d.getDroneId())).append("\" health=\"").append(d.getHealth()).append("\"/>\r\n");
		for (String a : s.getAugmentIdList()) b.append("\t<augment id=\"").append(XmlText.attr(a)).append("\"/>\r\n");
		if (gs.getCargoIdList() != null) for (String c : gs.getCargoIdList()) b.append("\t<cargo id=\"").append(XmlText.attr(c)).append("\"/>\r\n");
		SavedGameParser p = new SavedGameParser();
		for (CrewState c : s.getCrewList()) {
			ByteArrayOutputStream raw = new ByteArrayOutputStream();
			p.writeCrewMember(raw, c, format);
			b.append("\t<crew name=\"").append(XmlText.attr(c.getName())).append("\" race=\"").append(XmlText.attr(c.getRace().getId()))
				.append("\" sex=\"").append(c.isMale() ? "male" : "female").append("\" health=\"").append(c.getHealth())
				.append("\" pilot=\"").append(c.getPilotSkill()).append("\" engines=\"").append(c.getEngineSkill()).append("\" shields=\"").append(c.getShieldSkill())
				.append("\" weapons=\"").append(c.getWeaponSkill()).append("\" repair=\"").append(c.getRepairSkill()).append("\" combat=\"").append(c.getCombatSkill())
				.append("\" repairs=\"").append(c.getRepairs()).append("\" kills=\"").append(c.getCombatKills()).append("\" evasions=\"").append(c.getPilotedEvasions())
				.append("\" jumps=\"").append(c.getJumpsSurvived()).append("\" masteries=\"").append(c.getSkillMasteriesEarned()).append("\">\r\n");
			b.append("\t\t<ftl>").append(Base64.getEncoder().encodeToString(raw.toByteArray())).append("</ftl>\r\n");
			b.append("\t</crew>\r\n");
		}
		b.append("</").append(ROOT).append(">\r\n");
		return b.toString().getBytes(StandardCharsets.UTF_8);
	}

	public static SavedGameState read(byte[] bytes) throws IOException {
		Element root;
		try {
			DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
			f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
			f.setExpandEntityReferences(false);
			Document doc = f.newDocumentBuilder().parse(new ByteArrayInputStream(bytes));
			root = doc.getDocumentElement();
		} catch (Exception e) {
			throw new IOException("The Cargo Hold's file could not be read (" + e.getMessage() + "): put back a copy from its versions folder, or send it with a bug report", e);
		}
		if (!ROOT.equals(root.getTagName())) throw new IOException("The Cargo Hold's file holds <" + root.getTagName() + ">, not <" + ROOT + ">");
		String name = root.getAttribute("name");
		SavedGameState gs = SaveHelper.createStorageSave(name.isEmpty() ? "Spacedock Storage" : name, !"false".equals(root.getAttribute("dlc")));
		int format = num(root.getAttribute("format"), gs.getFileFormat());
		ShipState s = gs.getPlayerShip();
		SavedGameParser p = new SavedGameParser();
		for (Node n = root.getFirstChild(); n != null; n = n.getNextSibling()) {
			if (!(n instanceof Element)) continue;
			Element e = (Element) n;
			String tag = e.getTagName(), id = e.getAttribute("id");
			if (tag.equals("scrap")) s.setScrapAmt(num(e.getTextContent(), 0));
			else if (tag.equals("fuel")) s.setFuelAmt(num(e.getTextContent(), 0));
			else if (tag.equals("missiles")) s.setMissilesAmt(num(e.getTextContent(), 0));
			else if (tag.equals("droneParts")) s.setDronePartsAmt(num(e.getTextContent(), 0));
			else if (tag.equals("weapon") && !id.isEmpty()) s.getWeaponList().add(SaveHelper.newIdleWeapon(id));
			else if (tag.equals("drone") && !id.isEmpty()) {
				DroneState d = SaveHelper.newIdleDrone(id);
				if (e.hasAttribute("health")) d.setHealth(num(e.getAttribute("health"), d.getHealth()));
				s.getDroneList().add(d);
			}
			else if (tag.equals("augment") && !id.isEmpty()) s.getAugmentIdList().add(id);
			else if (tag.equals("cargo") && !id.isEmpty() && gs.getCargoIdList() != null) gs.getCargoIdList().add(id);
			else if (tag.equals("crew")) s.getCrewList().add(crew(p, e, format));
		}
		return gs;
	}

	/** A crew member: FTL's bytes for her if they are there, then what the file names, which wins. */
	private static CrewState crew(SavedGameParser p, Element e, int format) throws IOException {
		CrewState c = null;
		for (Node n = e.getFirstChild(); n != null; n = n.getNextSibling()) {
			if (n instanceof Element && ((Element) n).getTagName().equals("ftl")) {
				try { c = p.readCrewMember(new ByteArrayInputStream(Base64.getDecoder().decode(n.getTextContent().trim())), format); }
				catch (Exception x) { throw new IOException("The Cargo Hold's crew member " + e.getAttribute("name") + " could not be read: " + x.getMessage(), x); }
			}
		}
		if (c == null) { c = new CrewState(); c.setPlayerControlled(true); c.setHealth(100); }
		if (e.hasAttribute("name")) c.setName(e.getAttribute("name"));
		CrewType race = CrewType.findById(e.getAttribute("race"));
		if (race != null) c.setRace(race);
		if (e.hasAttribute("sex")) c.setMale(!"female".equals(e.getAttribute("sex")));
		c.setHealth(num(e.getAttribute("health"), c.getHealth()));
		c.setPilotSkill(num(e.getAttribute("pilot"), c.getPilotSkill()));
		c.setEngineSkill(num(e.getAttribute("engines"), c.getEngineSkill()));
		c.setShieldSkill(num(e.getAttribute("shields"), c.getShieldSkill()));
		c.setWeaponSkill(num(e.getAttribute("weapons"), c.getWeaponSkill()));
		c.setRepairSkill(num(e.getAttribute("repair"), c.getRepairSkill()));
		c.setCombatSkill(num(e.getAttribute("combat"), c.getCombatSkill()));
		c.setRepairs(num(e.getAttribute("repairs"), c.getRepairs()));
		c.setCombatKills(num(e.getAttribute("kills"), c.getCombatKills()));
		c.setPilotedEvasions(num(e.getAttribute("evasions"), c.getPilotedEvasions()));
		c.setJumpsSurvived(num(e.getAttribute("jumps"), c.getJumpsSurvived()));
		c.setSkillMasteriesEarned(num(e.getAttribute("masteries"), c.getSkillMasteriesEarned()));
		return c;
	}
	private static int num(String s, int otherwise) {
		try { return s == null || s.trim().isEmpty() ? otherwise : Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return otherwise; }
	}
}
