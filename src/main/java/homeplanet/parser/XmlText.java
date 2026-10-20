package homeplanet.parser;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Element;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

/** The one place text is made safe for XML and HTML (element text, attribute values, comments), and the one XML reader (7.01). */
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

	/** The root element of a file of the station's own, read safely (no doctype, no outside entities) and quietly (a broken file
	 *  is the exception's message, never a line on the console). */
	public static Element parse(byte[] bytes) throws IOException {
		try {
			return builder().parse(new ByteArrayInputStream(bytes)).getDocumentElement();
		} catch (Exception e) {
			throw new IOException("broken XML: " + e.getMessage(), e);
		}
	}
	/** As {@link #parse(byte[])}, for a file on disk. */
	public static Element parse(File f) throws IOException {
		return parse(homeplanet.core.SafeFiles.read(f));
	}
	/** A safe, quiet builder, for a reader that needs more than the root. */
	public static DocumentBuilder builder() throws IOException {
		try {
			DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
			f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
			f.setExpandEntityReferences(false);
			DocumentBuilder b = f.newDocumentBuilder();
			b.setErrorHandler(new DefaultHandler() {
				@Override public void fatalError(SAXParseException e) throws SAXException { throw e; }
			});
			return b;
		} catch (Exception e) {
			throw new IOException("no XML reader: " + e.getMessage(), e);
		}
	}
}
