package homeplanet.comm;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;

import javax.swing.SwingUtilities;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One open link to another station: a TCP socket, a thread reading it, and messages handed over on the event thread.
 * Quiet links are kept up with a ping, and a link silent for too long counts as lost. {@link Post} is the listening
 * end, open only while the Long Range Comm. screen is.
 */
public final class Channel {
	private static final Logger log = LoggerFactory.getLogger(Channel.class);

	/** The ports the stations use: the first free one of these, so two stations can share a computer. */
	public static final int PORT0 = 47610, PORTS = 10;
	static final int PING_MS = 15000, SILENT_MS = 60000, CONNECT_MS = 8000;

	/** What the channel tells its owner, always on the event thread. */
	public interface Listener {
		void received(Wire.Msg m);
		/** The link is gone: why, in words (never called twice). */
		void closed(String why);
	}

	private final Socket socket;
	private final InputStream in;
	private final OutputStream out;
	private volatile Listener listener;
	private volatile boolean closed = false;
	private volatile long lastSent = System.currentTimeMillis();
	public final String address;

	private Channel(Socket s) throws IOException {
		socket = s;
		s.setTcpNoDelay(true);
		s.setSoTimeout(SILENT_MS);
		in = new BufferedInputStream(s.getInputStream());
		out = new BufferedOutputStream(s.getOutputStream());
		address = s.getInetAddress().getHostAddress() + ":" + s.getPort();
	}

	/** Connects to a station (off the event thread: it can take a few seconds). */
	public static Channel connect(String host, int port) throws IOException {
		Socket s = new Socket();
		try {
			s.connect(new InetSocketAddress(host, port), CONNECT_MS);
			return new Channel(s);
		} catch (IOException e) {
			try { s.close(); } catch (IOException x) { }
			throw e;
		}
	}

	/** Reads the first message before anything else runs (the hello), waiting at most this long. */
	public Wire.Msg readFirst(int ms) throws IOException {
		int was = socket.getSoTimeout();
		socket.setSoTimeout(ms);
		try { return Wire.read(in); } finally { socket.setSoTimeout(was); }
	}

	/** Starts handing messages to the listener (and pinging). */
	public void start(Listener l) {
		listener = l;
		Thread reader = new Thread(new Runnable() { public void run() { readLoop(); } }, "Long Range Comm. reader");
		reader.setDaemon(true);
		reader.start();
		Thread pinger = new Thread(new Runnable() {
			public void run() {
				while (!closed) {
					try { Thread.sleep(2000); } catch (InterruptedException e) { return; }
					if (!closed && System.currentTimeMillis() - lastSent > PING_MS) {
						try { send(new Wire.Msg("PING")); } catch (IOException e) { fail("The link to the other station was lost."); }
					}
				}
			}
		}, "Long Range Comm. ping");
		pinger.setDaemon(true);
		pinger.start();
	}
	private void readLoop() {
		while (!closed) {
			final Wire.Msg m;
			try {
				m = Wire.read(in);
			} catch (Wire.Garbled e) {
				log.warn("Garbled transmission from {}: {}", address, e.getMessage());
				fail("The Home Planet Station received a garbled transmission and closed the channel.");
				return;
			} catch (SocketTimeoutException e) {
				fail("The other station went silent: the link was lost.");
				return;
			} catch (IOException e) {
				if (!closed) fail("The link to the other station was lost.");
				return;
			}
			if (m.type.equals("PING")) continue;
			if (m.type.equals("BYE")) { fail(Line.text(m.get("why"), 200)); return; }
			SwingUtilities.invokeLater(new Runnable() { public void run() { if (listener != null) listener.received(m); } });
		}
	}

	public void send(Wire.Msg m) throws IOException {
		if (closed) throw new IOException("The channel is closed");
		synchronized (out) {
			Wire.write(out, m);
			lastSent = System.currentTimeMillis();
		}
	}
	/** Sends, and on a failure closes the link (the listener hears why). */
	public boolean trySend(Wire.Msg m) {
		try { send(m); return true; }
		catch (IOException e) { fail("The link to the other station was lost."); return false; }
	}

	/** Closes, telling the other station why (shown to its commander). The listener isn't called. */
	public void close(String why) {
		if (closed) return;
		try { send(new Wire.Msg("BYE").put("why", why)); } catch (IOException e) { }
		closed = true;
		listener = null;
		try { socket.close(); } catch (IOException e) { }
	}
	public boolean isClosed() { return closed; }
	/** Cuts the link with no goodbye and no word to the listener (the harness's crash). */
	public void kill() {
		closed = true;
		listener = null;
		try { socket.close(); } catch (IOException e) { }
	}

	private void fail(final String why) {
		if (closed) return;
		closed = true;
		try { socket.close(); } catch (IOException e) { }
		final Listener l = listener;
		listener = null;
		if (l != null) SwingUtilities.invokeLater(new Runnable() { public void run() { l.closed(why); } });
	}

	// ---- the listening end ----

	/** Takes incoming hails while it's open; each new channel goes to the handler (off the event thread, hello unread). */
	public static final class Post {
		public interface Handler { void hailed(Channel c); }
		private final ServerSocket server;
		public final int port;
		private volatile boolean open = true;

		public Post(final Handler h) throws IOException {
			ServerSocket s = null;
			int p = -1;
			for (int i = 0; i < PORTS && s == null; i++) {
				try {
					s = new ServerSocket();
					s.setReuseAddress(false);
					s.bind(new InetSocketAddress(PORT0 + i));
					p = PORT0 + i;
				} catch (IOException e) {
					try { if (s != null) s.close(); } catch (IOException x) { }
					s = null;
				}
			}
			if (s == null) throw new IOException("ports " + PORT0 + " to " + (PORT0 + PORTS - 1) + " are all in use");
			server = s;
			port = p;
			Thread t = new Thread(new Runnable() {
				public void run() {
					while (open) {
						try {
							Socket sock = server.accept();
							if (!open) { sock.close(); return; }
							h.hailed(new Channel(sock));
						} catch (IOException e) {
							if (open) log.warn("Long Range Comm. post: {}", e.toString());
						}
					}
				}
			}, "Long Range Comm. post");
			t.setDaemon(true);
			t.start();
		}
		public void close() {
			open = false;
			try { server.close(); } catch (IOException e) { }
		}
	}
}
