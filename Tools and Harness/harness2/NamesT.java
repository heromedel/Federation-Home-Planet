import java.io.*; import java.util.*; import java.util.regex.*; import javax.xml.parsers.*; import org.w3c.dom.*; import homeplanet.core.*;
/** Crew names by race (6.29, docs/NAMES.md): every list in resource/lore/names reads, every name and every female form the station would make keeps the guide (the hard and voice rules, the length, no rank in front, capitals, both sexes, the Slugs' six rules, the Engi's four letters, Rock and Crystal apart), and a sample crew of each race is printed to read. The reading here is the reference for the station's own (the handoff to Prime). args: gamedir, world saves, work (unused: the lists are in the jar) */
public class NamesT {
 static final String[] RACES = {"human", "zoltan", "engi", "mantis", "rock", "crystal", "lanius", "slug"};
 static final String[] RANKS = {"sgt", "lt", "maj", "col", "cmd", "cpt"};
 /** One list: a race's file, or the human file's first or last names. */
 static class List_ { String race; LinkedHashMap<String, Integer> levels = new LinkedHashMap<String, Integer>(); List<String> suffix = new ArrayList<String>(), drop = new ArrayList<String>(); String replFrom, replTo; boolean hex; List<Name> names = new ArrayList<Name>(); }
 static class Name { String text, sex, rarity, from; Set<String> ex = new HashSet<String>(); }

 public static void main(String[] a) throws Exception {
  Map<String, List_> lists = new LinkedHashMap<String, List_>();
  for (String r : RACES) {
   InputStream in = NamesT.class.getResourceAsStream("/homeplanet/resource/lore/names/" + r + ".xml");
   Setup.chk("A: " + r + ".xml is in the jar", in != null); if (in == null) continue;
   Element root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(in).getDocumentElement();
   Setup.chk("A: " + r + ".xml names its race", r.equals(root.getAttribute("race")));
   if (r.equals("human")) { lists.put("human first", read(r, child(root, "first"))); lists.put("human last", read(r, child(root, "last"))); }
   else lists.put(r, read(r, root));
  }
  for (Map.Entry<String, List_> en : lists.entrySet()) {
   String k = en.getKey(); List_ l = en.getValue(); boolean human = l.race.equals("human");
   List<String> bad = new ArrayList<String>(); Set<String> seen = new HashSet<String>(); int men = 0, women = 0;
   for (Name n : l.names) {
    if (!n.sex.matches("[MFB]")) bad.add(n.text + ": sex " + n.sex);
    if (n.rarity != null && !l.levels.containsKey(n.rarity)) bad.add(n.text + ": no level " + n.rarity);
    if (!seen.add(shown(l, n))) bad.add(n.text + ": twice");
    if (!n.sex.equals("F")) men++; if (!n.sex.equals("M")) women++;
    List<String> all = new ArrayList<String>(); all.add(shown(l, n)); if (!n.sex.equals("M")) all.addAll(female(l, n));
    for (String s : all) {
     String why = Lore.rule(s); if (why != null) bad.add(s + ": " + why);
     if (s.length() > (s.equals(shown(l, n)) ? 14 : 16)) bad.add(s + ": too long (" + s.length() + ")");
     String first = s.split(" ")[0].replace(".", "").toLowerCase(); for (String rk : RANKS) if (first.equals(rk)) bad.add(s + ": starts with a rank");
     for (String w : s.split(" ")) if (!(l.hex ? w.matches("[0-9A-F]{4}[-~][0-9A-F]{4}") : w.matches("[A-Z][a-z]+|Mc[A-Z][a-z]+"))) bad.add(s + ": capitals");
    }
    if (n.sex.equals("B") && !n.ex.isEmpty() && female(l, n).isEmpty()) bad.add(n.text + ": every ending left out (tag it M)");
    if (l.hex && !n.text.matches("[A-Z][a-z]{3}")) bad.add(n.text + ": not a four-letter word");
    if (l.race.equals("slug") && (n.from == null || !slug(n.from).equals(n.text))) bad.add(n.text + ": the six rules make " + (n.from == null ? "(no from)" : slug(n.from)));
   }
   Setup.chk("B: " + k + ": " + l.names.size() + " names keep the guide" + (bad.isEmpty() ? "" : " " + bad), bad.isEmpty() && l.names.size() >= (k.equals("human first") ? 900 : k.equals("human last") ? 450 : 40) && !l.levels.isEmpty());
   if (!k.equals("human last")) Setup.chk("B: " + k + ": names for men and women (" + men + ", " + women + ")", men >= (human ? 300 : 15) && women >= (human ? 300 : 15));
  }
  // C: Rock and Crystal share no names, female forms included
  Set<String> rock = every(lists.get("rock")), crystal = every(lists.get("crystal")); Set<String> both = new TreeSet<String>(rock); both.retainAll(crystal);
  Setup.chk("C: Rock and Crystal share no names" + (both.isEmpty() ? "" : ": " + both), both.isEmpty());
  // D: the guide's own examples come out as the guide says
  String[][] slugs = {{"Slime", "Ssllimme"}, {"Slither", "Ssllither"}, {"Crawls", "Crawwlls"}, {"Snail", "Ssnnaill"}, {"Flowers", "Fllowwers"}, {"Glim", "Gllimm"}, {"Mirrow", "Mmirrow"}, {"Shimmer", "Shiimmer"}, {"Glib", "Gllib"}, {"Squill", "Squill"}};
  List<String> off = new ArrayList<String>(); for (String[] s : slugs) if (!slug(s[0]).equals(s[1])) off.add(s[0] + " makes " + slug(s[0]));
  Setup.chk("D: the Slugs' six rules make the guide's table" + off, off.isEmpty());
  Setup.chk("D: Ohm is never Ohmy; Ix is Ixa or Ixi; Pumice, Pumicite; Titanium, Titania; Byte, 4279-7465 and 4279~7465; Resi is her own",
    !female(lists.get("zoltan"), find(lists.get("zoltan"), "Ohm")).contains("Ohmy") && female(lists.get("zoltan"), find(lists.get("zoltan"), "Ohm")).contains("Ohmie")
    && new HashSet<String>(female(lists.get("mantis"), find(lists.get("mantis"), "Ix"))).equals(new HashSet<String>(Arrays.asList("Ixa", "Ixi")))
    && female(lists.get("rock"), find(lists.get("rock"), "Pumice")).contains("Pumicite") && female(lists.get("lanius"), find(lists.get("lanius"), "Titanium")).contains("Titania")
    && shown(lists.get("engi"), find(lists.get("engi"), "Byte")).equals("4279-7465") && female(lists.get("engi"), find(lists.get("engi"), "Byte")).equals(Arrays.asList("4279~7465"))
    && female(lists.get("zoltan"), find(lists.get("zoltan"), "Resi")).equals(Arrays.asList("Resi")));
  // a sample crew of each race, to read
  Random rng = new Random(629);
  for (Map.Entry<String, List_> en : lists.entrySet()) {
   if (en.getKey().equals("human last")) continue;
   StringBuilder sb = new StringBuilder("SAMPLE " + en.getKey().replace(" first", "") + ":");
   for (int i = 0; i < 6; i++) { boolean male = i % 2 == 0; String n = pick(en.getValue(), male, rng); if (en.getValue().race.equals("human")) n += " " + pick(lists.get("human last"), male, rng); sb.append(i == 0 ? " " : ", ").append(n).append(male ? " (M)" : " (F)"); }
   System.out.println(sb);
  }
  Setup.done();
 }

 static Element child(Element e, String tag) { return (Element) e.getElementsByTagName(tag).item(0); }
 static List_ read(String race, Element root) {
  List_ l = new List_(); l.race = race;
  NodeList lv = root.getElementsByTagName("level"); for (int i = 0; i < lv.getLength(); i++) { Element e = (Element) lv.item(i); l.levels.put(e.getAttribute("name"), Integer.parseInt(e.getAttribute("weight"))); }
  Element f = child(root, "female");
  if (f != null) { l.suffix = split(f.getAttribute("suffix")); l.drop = split(f.getAttribute("drop")); if (f.hasAttribute("replace")) { l.replFrom = f.getAttribute("replace"); l.replTo = f.getAttribute("with"); } }
  Element w = child(root, "write"); l.hex = w != null && "hex".equals(w.getAttribute("as"));
  NodeList ns = root.getElementsByTagName("name");
  for (int i = 0; i < ns.getLength(); i++) {
   Element e = (Element) ns.item(i); Name n = new Name(); n.text = e.getTextContent().trim(); n.sex = e.hasAttribute("sex") ? e.getAttribute("sex") : "B";
   n.rarity = e.hasAttribute("rarity") ? e.getAttribute("rarity") : null; n.from = e.hasAttribute("from") ? e.getAttribute("from") : null; n.ex.addAll(split(e.getAttribute("ex"))); l.names.add(n);
  }
  return l;
 }
 static List<String> split(String s) { List<String> o = new ArrayList<String>(); for (String p : s.split(",")) if (!p.trim().isEmpty()) o.add(p.trim()); return o; }
 static Name find(List_ l, String text) { for (Name n : l.names) if (n.text.equals(text)) return n; throw new IllegalArgumentException(text); }
 static Set<String> every(List_ l) { Set<String> s = new HashSet<String>(); for (Name n : l.names) { s.add(shown(l, n)); if (!n.sex.equals("M")) s.addAll(female(l, n)); } return s; }

 /** The name as the station shows it: the Engi's word in hex, split in half; anyone else's as written. */
 static String shown(List_ l, Name n) {
  if (!l.hex) return n.text;
  StringBuilder h = new StringBuilder(); for (char c : n.text.toCharArray()) h.append(String.format("%02X", (int) c));
  return h.substring(0, h.length() / 2) + "-" + h.substring(h.length() / 2);
 }
 /** Every female form of a name: itself for an F name; for a B name, the file's replace, or one per ending not left out (a name ending in X takes only the rest of an ending with an X; otherwise a name already ending in one is its own; a drop ending goes first; a final E drops before a vowel). */
 static List<String> female(List_ l, Name n) {
  String s = shown(l, n); List<String> o = new ArrayList<String>();
  if (n.sex.equals("M")) return o;
  if (n.sex.equals("F")) { o.add(s); return o; }
  if (l.replFrom != null) { o.add(s.replace(l.replFrom, l.replTo)); return o; }
  if (l.suffix.isEmpty()) { o.add(s); return o; }
  String low = s.toLowerCase(); boolean xRule = false;
  for (String e : l.suffix) if (e.contains("x") && low.endsWith("x")) xRule = true; // the X rule comes first: Ix is Ixa or Ixi, not her own
  if (!xRule) for (String e : l.suffix) if (low.endsWith(e)) { o.add(s); return o; }
  String base = s; for (String d : l.drop) if (low.endsWith(d)) { base = s.substring(0, s.length() - d.length()); break; }
  for (String e : l.suffix) {
   if (n.ex.contains(e)) continue;
   String b = base, end = e;
   if (b.toLowerCase().endsWith("x") && end.contains("x")) { end = end.substring(end.indexOf('x') + 1); if (end.isEmpty()) continue; }
   if (b.toLowerCase().endsWith("e") && "aeiou".indexOf(end.charAt(0)) >= 0) b = b.substring(0, b.length() - 1);
   if (!o.contains(b + end)) o.add(b + end);
  }
  return o;
 }
 /** A crew member's name by the station's rule: by weight of rarity, a man an M or B name, a woman an F or B one, a B name in a female form. */
 static String pick(List_ l, boolean male, Random rng) {
  List<Name> ok = new ArrayList<Name>(); int total = 0; String first = l.levels.keySet().iterator().next();
  for (Name n : l.names) if (n.sex.equals("B") || n.sex.equals(male ? "M" : "F")) { ok.add(n); total += l.levels.get(n.rarity == null ? first : n.rarity); }
  int r = rng.nextInt(total); Name got = ok.get(0);
  for (Name n : ok) { r -= l.levels.get(n.rarity == null ? first : n.rarity); if (r < 0) { got = n; break; } }
  if (male) return shown(l, got);
  List<String> f = female(l, got); return f.get(rng.nextInt(f.size()));
 }

 /** The Slugs' six rules (docs/NAMES.md), drawn out word by word. */
 static String slug(String text) {
  StringBuilder out = new StringBuilder();
  for (String w : text.split(" ")) { if (out.length() > 0) out.append(' '); String d = slugWord(w.toLowerCase()); out.append(Character.toUpperCase(d.charAt(0))).append(d.substring(1)); }
  return out.toString();
 }
 static boolean vowel(char c) { return "aeiouy".indexOf(c) >= 0; }
 static String slugWord(String w) {
  // units: a two-letter sound (sh, th, ch, ph), a double the word already has, or one letter; double vowels go single
  List<String> u = new ArrayList<String>();
  for (int i = 0; i < w.length(); ) {
   char c = w.charAt(i), nx = i + 1 < w.length() ? w.charAt(i + 1) : 0;
   if (nx == 'h' && "stcp".indexOf(c) >= 0) { u.add("" + c + nx); i += 2; }
   else if (nx == c && vowel(c)) { u.add("" + c); i += 2; }
   else if (nx == c) { u.add("" + c + c); i += 2; }
   else { u.add("" + c); i++; }
  }
  int n = u.size(); boolean[] want = new boolean[n];
  for (int i = 0; i < n; i++) {
   String x = u.get(i); char c = x.charAt(0);
   if (x.length() == 2 && x.charAt(0) == x.charAt(1)) want[i] = true; // her own double stays
   else if (x.length() == 2) want[i] = false; // sh, th, ch, ph
   else if (vowel(c)) want[i] = i > 0 && u.get(i - 1).equals("sh"); // the vowel after SH draws out
   else if ("lmnwzvh".indexOf(c) >= 0) want[i] = !(c == 'w' && i == n - 1); // a final W stays single
   else want[i] = false; // R, the hard stops, and S and soft C (below)
  }
  StringBuilder o = new StringBuilder(); int run = 0;
  for (int i = 0; i < n; i++) {
   String x = u.get(i); char c = x.charAt(0);
   boolean soft = c == 's' && x.length() == 1 || c == 'c' && x.length() == 1 && i + 1 < n && "eiy".indexOf(u.get(i + 1).charAt(0)) >= 0;
   boolean own = x.length() == 2 && x.charAt(0) == x.charAt(1);
   boolean dbl = own || (soft ? run == 0 && i + 1 < n && want[i + 1] : want[i] && run < 2);
   if (own) o.append(x); else if (dbl) o.append(x).append(x); else o.append(x);
   run = dbl ? run + 1 : 0;
  }
  return o.toString();
 }
}
