package homeplanet.parser;

/** The one place text is made safe for XML and HTML: element text, attribute values, and CDATA-free comments. */
public final class XmlText {
	private XmlText() { }

	/** Text inside an element (or in HTML): & < > escaped. Null becomes empty. */
	public static String text(String s) {
		return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}
	/** An attribute value in double quotes: as {@link #text} plus the quote. */
	public static String attr(String s) {
		return text(s).replace("\"", "&quot;");
	}
	/** Text inside an XML comment, where "--" isn't allowed. */
	public static String comment(String s) {
		return text(s).replace("--", "-");
	}
}
