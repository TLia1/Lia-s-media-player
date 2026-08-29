package com.lia.mediaplayer.video;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.ServerSocket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the address the audio handshake is told to use.
 *
 * <p>This is the shape of a bug that already happened: the listener was bound to
 * {@link InetAddress#getLoopbackAddress()} while ffmpeg was handed a hardcoded
 * {@code 127.0.0.1}. On a JVM that prefers IPv6 those are different addresses, so the
 * socket listened on {@code [::1]}, ffmpeg knocked on {@code 127.0.0.1}, and the
 * connection was refused against a port that was demonstrably open — which then took the
 * whole session down with it.</p>
 */
class FFmpegSessionHostTest {

    @Test
    void ipv4LoopbackIsWrittenBare() throws Exception {
        assertEquals("127.0.0.1", FFmpegSession.urlHost(InetAddress.getByName("127.0.0.1")));
    }

    /**
     * Bracketed, and in whatever literal form Java produces — {@code getHostAddress}
     * writes IPv6 out in full ({@code 0:0:0:0:0:0:0:1}) rather than compressed. That is a
     * valid literal and ffmpeg accepts it; what matters is the brackets, without which
     * the colons are read as a port separator.
     */
    @Test
    void ipv6LoopbackIsBracketedForAUrl() throws Exception {
        String host = FFmpegSession.urlHost(InetAddress.getByName("::1"));
        assertTrue(host.startsWith("[") && host.endsWith("]"), "IPv6 must be bracketed, got " + host);
        assertEquals(InetAddress.getByName("::1"),
                InetAddress.getByName(host.substring(1, host.length() - 1)));
    }

    /**
     * The property that actually matters: whatever the JVM's preferred loopback family
     * is, the host handed to ffmpeg names the address the socket is really listening on.
     */
    @Test
    void hostMatchesWhateverTheSocketBoundTo() throws Exception {
        try (ServerSocket listener = new ServerSocket()) {
            listener.bind(new java.net.InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 1);
            String host = FFmpegSession.urlHost(listener.getInetAddress());
            String bare = host.startsWith("[") ? host.substring(1, host.length() - 1) : host;
            assertEquals(listener.getInetAddress(), InetAddress.getByName(bare),
                    "the URL host must resolve back to the bound address");
            assertTrue(InetAddress.getByName(bare).isLoopbackAddress(), "and it must stay on loopback");
        }
    }
}
