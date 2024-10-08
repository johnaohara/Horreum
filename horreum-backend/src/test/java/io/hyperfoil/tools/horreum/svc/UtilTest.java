package io.hyperfoil.tools.horreum.svc;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

import org.jboss.logmanager.Level;
import org.jboss.logmanager.LogContext;
import org.jboss.logmanager.formatters.PatternFormatter;
import org.jboss.logmanager.handlers.OutputStreamHandler;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.LongNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import io.hyperfoil.tools.horreum.test.HorreumTestProfile;
import io.hyperfoil.tools.horreum.test.PostgresResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.oidc.server.OidcWiremockTestResource;

@QuarkusTest
@QuarkusTestResource(PostgresResource.class)
@QuarkusTestResource(OidcWiremockTestResource.class)
@TestProfile(HorreumTestProfile.class)
public class UtilTest {

    public static class StringHandler extends OutputStreamHandler {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        public StringHandler() {
            setOutputStream(baos);
            this.setFormatter(new PatternFormatter("%m"));
            this.setLevel(Level.ALL);
            this.setAutoFlush(true);
            LogContext.getLogContext().getLogger("").addHandler(this);
        }

        public String getLog() {
            return baos.toString();
        }

        @Override
        public void close() throws SecurityException {
            LogContext.getLogContext().getLogger("").removeHandler(this);
            super.close();
            try {
                baos.close();
            } catch (IOException e) {
                /* meh */}
        }
    }

    private static class CloseableServer implements Closeable {
        private HttpServer httpServer;
        private AtomicInteger callCount = new AtomicInteger(0);

        public int getCallCount() {
            return callCount.get();
        }

        public String getUrl() {
            if (httpServer != null) {
                return "http://" + getHostname() + ":" + getPort();
            }
            return "";
        }

        public String getHostname() {
            if (httpServer != null) {
                return httpServer.getAddress().getHostName();
            } else {
                return "";
            }
        }

        public int getPort() {
            if (httpServer != null) {
                return httpServer.getAddress().getPort();
            } else {
                return 0;
            }
        }

        public CloseableServer(String endpoint, String response) throws IOException {
            httpServer = HttpServer.create(new InetSocketAddress(InetAddress.getLocalHost().getHostName(), 0), 0); // or use InetSocketAddress(0) for ephemeral port
            httpServer.createContext(endpoint, new HttpHandler() {
                public void handle(HttpExchange exchange) throws IOException {
                    callCount.incrementAndGet();
                    exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, response.getBytes().length);
                    exchange.getResponseBody().write(response.getBytes());
                    exchange.close();
                }
            });
            httpServer.start();
        }

        @Override
        public void close() throws IOException {
            if (httpServer != null) {
                httpServer.stop(0);
            }
        }
    }

    @Test
    public void toInstant_nulls() throws UnsupportedEncodingException {
        try (StringHandler handler = new StringHandler()) {
            assertNull(Util.toInstant(null), "null input should return null");
            assertNull(Util.toInstant(""), "empty string should return null");
            assertNull(Util.toInstant(" "), "blank string should return null");
            assertFalse(handler.getLog().contains("DateTimeParseException"), handler.getLog());
        }
    }

    @Test
    public void toInstant_valid() throws IOException {
        //already an instant
        assertNotNull(Util.toInstant(Instant.now()), "failed to handle instant input");
        //number
        assertNotNull(Util.toInstant(System.currentTimeMillis()), "failed to parse current millis");
        //strings
        assertNotNull(Util.toInstant("" + System.currentTimeMillis()), "failed to parse millis as string");
        assertNotNull(Util.toInstant("2020-01-01"), "failed to parse yyyy-mm-dd");
        assertNotNull(Util.toInstant("2020-01-01T01:01:01"), "failed to parse YYYY-MM-DDTHH:mm:ss");
        assertNotNull(Util.toInstant("2020-01-01T01:01:01Z"), "failed to parse YYYY-MM-DDThh:mm:ssZ");
        assertNotNull(Util.toInstant("2020-01-01T01:01:01+00:00"), "failed to parse YYYY-MM-DDThh:mm:ss[+-]zz:zz");
        assertNotNull(Util.toInstant("2024-03-11T22:16:24.302655-04:00"), "failed to parse ISO with microseconds and zzzz");
        //json
        assertNotNull(Util.toInstant(new TextNode("2020-01-01")), "failed to parse json text YYYY-MM-DD");
        assertNotNull(Util.toInstant(new LongNode(System.currentTimeMillis())), "failed to parse current millis as json node");
    }

    @org.junit.jupiter.api.Test
    public void evaluateOnceJsonKeyAccess() throws JsonProcessingException {
        Object rtrn = Util.evaluateOnce(
                """
                        (input)=>{
                            return input.foo;
                        }
                        """,
                new ObjectMapper().readTree("{\"foo\":\"bar\"}"),
                Util::convert,
                (s, t) -> {
                    Assertions.fail(t.getMessage());
                },
                (s) -> {
                    //do nothing for this test
                });
        Assertions.assertNotNull(rtrn, "method should return a value");
        Assertions.assertInstanceOf(String.class, rtrn,
                "return should be a string but was " + (rtrn.getClass().getSimpleName()));
        String s = (String) rtrn;
        Assertions.assertEquals("bar", s, "rtrn should be 'bar'");
    }

    @org.junit.jupiter.api.Test
    public void evaluateOnceAsync() {
        Object rtrn = Util.evaluateOnce(
                """
                        async ()=>{
                            return "foo";
                        }
                        """,
                null, //no input needed
                Util::convert,
                (s, t) -> {
                    Assertions.fail(t.getMessage());
                },
                (s) -> {
                    //do nothing for this test
                });
        Assertions.assertNotNull(rtrn, "async method should return a value");
        Assertions.assertInstanceOf(String.class, rtrn,
                "return should be a string but was " + (rtrn.getClass().getSimpleName()));
        String s = (String) rtrn;
        Assertions.assertEquals("foo", s, "rtrn should be 'foo'");
    }

    @org.junit.jupiter.api.Test
    public void evaluateOnceAsyncAwaitFetchJson() {
        try (CloseableServer server = new CloseableServer("/", "{\"key\":42}")) {
            Object rtrn = Util.evaluateOnce(
                    """
                            async ()=>{
                                let rtrn = await fetch("SERVER_URL");
                                let data = await rtrn.json();
                                return data.key;
                            }
                            """.replace("SERVER_URL", server.getUrl()),
                    null, //no input needed
                    Util::convert,
                    (s, t) -> {
                        Assertions.fail(t.getMessage());
                    },
                    (s) -> {
                        //do nothing for this test
                    });
            Assertions.assertEquals(0, server.getCallCount(), "request should not call service due to context sandboxing");
            //these tests will be used once we add support for JsFetch to the sanbdox
            //            Assertions.assertNotNull(rtrn, "async method should return a value");
            //            Assertions.assertInstanceOf(Number.class,rtrn, "return should be a number but was " + (rtrn.getClass().getSimpleName()));
            //            Assertions.assertInstanceOf(Long.class, rtrn, "rtrn should be a long but was " + (rtrn.getClass().getSimpleName()));
            //            Long l = (Long)rtrn;
            //            Assertions.assertEquals(42l, l.longValue(), "rtrn should be 42");
        } catch (IOException e) {
            Assertions.fail(e.getMessage());
        }
    }
}
