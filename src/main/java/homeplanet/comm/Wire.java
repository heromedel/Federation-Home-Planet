package homeplanet.comm;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Long Range Comm.'s messages on the wire: a length, then a type, text fields and binary blobs (a ship's files).
 * Everything that comes in is from another computer: sizes are checked before anything is read into memory, and a
 * message that breaks a limit closes the channel ({@link Garbled}).
 */
public final class Wire {
	private Wire() { }

	/** Raised for anything malformed or too big: the channel closes. */
	public static final class Garbled extends IOException {
		public Garbled(String why) { super(why); }
	}

	/** The biggest message: a ship with art of her own and her history. */
	public static final int MAX_FRAME = 48 * 1024 * 1024;
	static final int MAX_FIELDS = 65535, MAX_BLOBS = 64;

	/** One message: its type, text fields in order, and any blobs. */
	public static final class Msg {
		public final String type;
		final Map<String, String> fields = new LinkedHashMap<String, String>();
		final List<byte[]> blobs = new ArrayList<byte[]>();
		public Msg(String type) { this.type = type; }

		public Msg put(String key, String value) { fields.put(key, value == null ? "" : value); return this; }
		public Msg put(String key, int value) { return put(key, Integer.toString(value)); }
		public Msg put(String key, long value) { return put(key, Long.toString(value)); }
		public Msg put(String key, boolean value) { return put(key, Boolean.toString(value)); }
		public Msg blob(byte[] b) { blobs.add(b); return this; }

		/** A field, or "" if it's missing. */
		public String get(String key) { String v = fields.get(key); return v == null ? "" : v; }
		public boolean has(String key) { return fields.containsKey(key); }
		public boolean flag(String key) { return "true".equals(fields.get(key)); }
		/** A whole number field within [min, max], or Garbled. */
		public int num(String key, int min, int max) throws Garbled {
			try {
				int v = Integer.parseInt(get(key).trim());
				if (v < min || v > max) throw new Garbled(type + "." + key + " out of range: " + v);
				return v;
			} catch (NumberFormatException e) {
				throw new Garbled(type + "." + key + " is not a number");
			}
		}
		public long longNum(String key) throws Garbled {
			try { return Long.parseLong(get(key).trim()); } catch (NumberFormatException e) { throw new Garbled(type + "." + key + " is not a number"); }
		}
		public Map<String, String> fields() { return fields; }
		public List<byte[]> blobs() { return blobs; }
		@Override public String toString() { return type + fields.keySet(); }
	}

	public static void write(OutputStream out, Msg m) throws IOException {
		ByteArrayOutputStream buf = new ByteArrayOutputStream();
		DataOutputStream d = new DataOutputStream(buf);
		d.writeUTF(m.type);
		if (m.fields.size() > MAX_FIELDS || m.blobs.size() > MAX_BLOBS) throw new IOException("The transmission has too many parts to send");
		d.writeShort(m.fields.size());
		for (Map.Entry<String, String> e : m.fields.entrySet()) {
			d.writeUTF(e.getKey());
			writeLong(d, e.getValue());
		}
		d.writeShort(m.blobs.size());
		for (byte[] b : m.blobs) { d.writeInt(b.length); d.write(b); }
		d.flush();
		if (buf.size() > MAX_FRAME) throw new IOException("The transmission is too large to send (" + buf.size() / (1024 * 1024) + " MB)");
		DataOutputStream o = new DataOutputStream(out);
		o.writeInt(buf.size());
		buf.writeTo(o);
		o.flush();
	}
	/** Text of any length (writeUTF stops at 64 KB). */
	private static void writeLong(DataOutputStream d, String s) throws IOException {
		byte[] b = s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
		d.writeInt(b.length);
		d.write(b);
	}

	/** Reads one message; Garbled if it's malformed or too big, EOFException at the end of the stream. */
	public static Msg read(InputStream in) throws IOException {
		DataInputStream d = new DataInputStream(in);
		int len = d.readInt();
		if (len <= 0 || len > MAX_FRAME) throw new Garbled("frame length " + len);
		byte[] frame = new byte[len];
		d.readFully(frame);
		DataInputStream f = new DataInputStream(new java.io.ByteArrayInputStream(frame));
		try {
			String type = f.readUTF();
			if (!type.matches("[A-Z]{1,16}")) throw new Garbled("message type");
			Msg m = new Msg(type);
			int n = f.readUnsignedShort();
			if (n > MAX_FIELDS) throw new Garbled("too many fields");
			for (int i = 0; i < n; i++) {
				String k = f.readUTF();
				int vl = f.readInt();
				if (vl < 0 || vl > f.available()) throw new Garbled("field length");
				byte[] v = new byte[vl];
				f.readFully(v);
				m.fields.put(k, new String(v, java.nio.charset.StandardCharsets.UTF_8));
			}
			int nb = f.readUnsignedShort();
			if (nb > MAX_BLOBS) throw new Garbled("too many blobs");
			for (int i = 0; i < nb; i++) {
				int bl = f.readInt();
				if (bl < 0 || bl > f.available()) throw new Garbled("blob length");
				byte[] b = new byte[bl];
				f.readFully(b);
				m.blobs.add(b);
			}
			if (f.available() != 0) throw new Garbled("trailing bytes");
			return m;
		} catch (java.io.EOFException e) {
			throw new Garbled("message cut short");
		} catch (java.io.UTFDataFormatException e) {
			throw new Garbled("bad text");
		}
	}
}
