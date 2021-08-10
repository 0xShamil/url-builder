package dev.shamil;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author 0xShamil
 */
class UrlTest {

    final Set<String> SCOPE_IDS = new LinkedHashSet<>(Arrays.asList("eno1", "en1", "eth0", "X", "1", "2", "14", "20"));

    @Test
    void testSimpleUrlBuilding() {
        final String url = "api.github.com/";
        String buildUrl = Url.builder().host("api.github.com").toString();
        assertEquals(url, buildUrl);
    }

    @Test
    void fromJavaNetUrl() throws Exception {
        URL javaNetUrl = new URL("http://username:password@host/path?query#fragment");
        Url.Builder urlBuilder = Url.builder()
                .scheme("http")
                .encodedUsername("username")
                .encodedPassword("password")
                .host("host")
                .addPathSegment("path")
                .addQueryParameter("query", null)
                .fragment("fragment");
        assertEquals(javaNetUrl, new URL(urlBuilder.toString()));
    }

    @Test
    void testHost() {
        final String url = "https://api.github.com/";
        String buildUrl = Url.builder()
                .scheme("https")
                .host("api.github.com")
                .toString();

        assertEquals(url, buildUrl);
    }

    @Test
    void testHostByPort() {
        String buildUrl = Url.builder()
                .scheme("https")
                .host("api.github.com")
                .port(8088)
                .toString();
        final String url = "https://api.github.com:8088/";

        assertEquals(url, buildUrl);
    }

    @Test
    void testQuery() {
        String buildUrl = Url.builder()
                .scheme("https")
                .host("api.github.com")
                .addEncodedPathSegment("list")
                .addEncodedQueryParameter("max-keys", "100")
                .addEncodedQueryParameter("cache", "true")
                .toString();
        final String url = "https://api.github.com/list?max-keys=100&cache=true";

        assertEquals(url, buildUrl);
    }

    @Test
    void testCompositeQueryWithEncodedComponents() {
        String url = Url.builder()
                .scheme("http")
                .host("github.com")
                .addQueryParameter("a+=& b", "c+=& d")
                .toString();
        assertEquals("http://github.com/?a%2B%3D%26%20b=c%2B%3D%26%20d", url);

    }

    @Test
    void testForStringIPv6Input() throws UnknownHostException {
        String ipStr = "3ffe::1";
        // Shouldn't hit DNS, because it's an IP string literal.
        InetAddress ipv6Addr = InetAddress.getByName(ipStr);
        assertEquals(ipv6Addr, InetAddress.getByName(Url.canonicalizeHost(ipStr)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"::7:6:5:4:3:2:1", "::7:6:5:4:3:2:0", "7:6:5:4:3:2:1::", "0:6:5:4:3:2:1::"})
    void testForStringIPv6EightColons(String ipString) throws UnknownHostException {
        // Shouldn't hit DNS, because it's an IP string literal.
        InetAddress ipv6Addr = InetAddress.getByName(ipString);
        assertEquals(ipv6Addr, InetAddress.getByName(Url.canonicalizeHost(ipString)));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "0:0:0:0:0:0:0:1",
            "fe80::a",
            "fe80::1",
            "fe80::2",
            "fe80::42",
            "fe80::3dd0:7f8e:57b7:34d5",
            "fe80::71a3:2b00:ddd3:753f",
            "fe80::8b2:d61e:e5c:b333",
            "fe80::b059:65f4:e877:c40"
    })
    void testIPv6AddressWithScopeId(String ipString) {
        for (String scopeId : SCOPE_IDS) {
            String withScopeId = "[" + ipString + "%" + scopeId + "]";
            assertEquals(Url.canonicalizeHost(ipString), Url.canonicalizeHost(withScopeId));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"[2001:db8:0:0:1:0:0:1]", "[2001:0db8:0:0:1:0:0:1]", "[2001:db8::1:0:0:1]", "[2001:db8::0:1:0:0:1]",
            "[2001:0db8::1:0:0:1]", "[2001:db8:0:0:1::1]", "[2001:db8:0000:0:1::1]", "[2001:DB8:0:0:1::1]"})
    void testIPv6DifferentFormats(String address) {
        // Multiple representations of the same address; see http://tools.ietf.org/html/rfc5952.
        String expected = "2001:db8::1:0:0:1";
        assertEquals(expected, Url.canonicalizeHost(address));
    }


    // parser

    @Test
    void parseTrimsAsciiWhitespace() {
        String expected = Url.of("http://host/").toString();
        // Leading.
        assertEquals(expected, Url.of("\r\n\f \thttp://host/").toString());
        // Trailing.
        assertEquals(expected, Url.of("http://host/\f\n\t \r").toString());
        // Both.
        assertEquals(expected, Url.of("\t http://host/ \r").toString());
        // Both.
        assertEquals(expected, Url.of("    http://host/    ").toString());
        assertEquals(expected, Url.of("http://host/").resolve("   ").toString());
        assertEquals(expected, Url.of("http://host/").resolve("  .  ").toString());
    }

    @Test
    void parseDoesNotTrimOtherWhitespaceCharacters() {
        // Whitespace characters list from Google's Guava team: http://goo.gl/IcR9RD
        // line tabulation
        assertEquals("/%0B", Url.of("http://h/\u000b").encodedPath());
        // information separator 4
        assertEquals("/%1C", Url.of("http://h/\u001c").encodedPath());
        // information separator 3
        assertEquals("/%1D", Url.of("http://h/\u001d").encodedPath());
        // information separator 2
        assertEquals("/%1E", Url.of("http://h/\u001e").encodedPath());
        // information separator 1
        assertEquals("/%1F", Url.of("http://h/\u001f").encodedPath());
        // next line
        assertEquals("/%C2%85", Url.of("http://h/\u0085").encodedPath());
        // non-breaking space
        assertEquals("/%C2%A0", Url.of("http://h/\u00a0").encodedPath());
        // ogham space mark
        assertEquals("/%E1%9A%80", Url.of("http://h/\u1680").encodedPath());
        // mongolian vowel separator
        assertEquals("/%E1%A0%8E", Url.of("http://h/\u180e").encodedPath());
        // en quad
        assertEquals("/%E2%80%80", Url.of("http://h/\u2000").encodedPath());
        // em quad
        assertEquals("/%E2%80%81", Url.of("http://h/\u2001").encodedPath());
        // en space
        assertEquals("/%E2%80%82", Url.of("http://h/\u2002").encodedPath());
        // em space
        assertEquals("/%E2%80%83", Url.of("http://h/\u2003").encodedPath());
        // three-per-em space
        assertEquals("/%E2%80%84", Url.of("http://h/\u2004").encodedPath());
        // four-per-em space
        assertEquals("/%E2%80%85", Url.of("http://h/\u2005").encodedPath());
        // six-per-em space
        assertEquals("/%E2%80%86", Url.of("http://h/\u2006").encodedPath());
        // figure space
        assertEquals("/%E2%80%87", Url.of("http://h/\u2007").encodedPath());
        // punctuation space
        assertEquals("/%E2%80%88", Url.of("http://h/\u2008").encodedPath());
        // thin space
        assertEquals("/%E2%80%89", Url.of("http://h/\u2009").encodedPath());
        // hair space
        assertEquals("/%E2%80%8A", Url.of("http://h/\u200a").encodedPath());
        // zero-width space
        assertEquals("/%E2%80%8B", Url.of("http://h/\u200b").encodedPath());
        // zero-width non-joiner
        assertEquals("/%E2%80%8C", Url.of("http://h/\u200c").encodedPath());
        // zero-width joiner
        assertEquals("/%E2%80%8D", Url.of("http://h/\u200d").encodedPath());
        // left-to-right mark
        assertEquals("/%E2%80%8E", Url.of("http://h/\u200e").encodedPath());
        // right-to-left mark
        assertEquals("/%E2%80%8F", Url.of("http://h/\u200f").encodedPath());
        // line separator
        assertEquals("/%E2%80%A8", Url.of("http://h/\u2028").encodedPath());
        // paragraph separator
        assertEquals("/%E2%80%A9", Url.of("http://h/\u2029").encodedPath());
        // narrow non-breaking space
        assertEquals("/%E2%80%AF", Url.of("http://h/\u202f").encodedPath());
        // medium mathematical space
        assertEquals("/%E2%81%9F", Url.of("http://h/\u205f").encodedPath());
        // ideographic space
        assertEquals("/%E3%80%80", Url.of("http://h/\u3000").encodedPath());
    }

    @Test
    void scheme() {
        assertEquals(Url.of("Http://host/"), Url.of("http://host/"));
        assertEquals(Url.of("HTTP://host/"), Url.of("http://host/"));
        assertEquals(Url.of("HTTPS://host/"), Url.of("https://host/"));

        assertInvalidBuild("image640://480.png", "Expected URL scheme 'http' or 'https' but was 'image640'");
        assertInvalidBuild("httpp://host/", "Expected URL scheme 'http' or 'https' but was 'httpp'");
        assertInvalidBuild("0ttp://host/", "Expected URL scheme 'http' or 'https' but no colon was found");
        assertInvalidBuild("ht+tp://host/", "Expected URL scheme 'http' or 'https' but was 'ht+tp'");
        assertInvalidBuild("ht.tp://host/", "Expected URL scheme 'http' or 'https' but was 'ht.tp'");
        assertInvalidBuild("ht-tp://host/", "Expected URL scheme 'http' or 'https' but was 'ht-tp'");
        assertInvalidBuild("ht1tp://host/", "Expected URL scheme 'http' or 'https' but was 'ht1tp'");
        assertInvalidBuild("httpss://host/", "Expected URL scheme 'http' or 'https' but was 'httpss'");
    }

    @Test
    void parseNoScheme() {
        assertInvalidBuild("//host", "Expected URL scheme 'http' or 'https' but no colon was found");
        assertInvalidBuild("/path", "Expected URL scheme 'http' or 'https' but no colon was found");
        assertInvalidBuild("path", "Expected URL scheme 'http' or 'https' but no colon was found");
        assertInvalidBuild("?query", "Expected URL scheme 'http' or 'https' but no colon was found");
        assertInvalidBuild("#fragment", "Expected URL scheme 'http' or 'https' but no colon was found");
    }

    @Test
    void newBuilderResolve() {
        // Non-exhaustive tests because implementation is the same as resolve.
        Url base = Url.of("http://host/a/b");
        assertEquals(base.newBuilder("https://host2").build(), Url.of("https://host2/"));
        assertEquals(base.newBuilder("//host2").build(), Url.of("http://host2/"));
        assertEquals(base.newBuilder("/path").build(), Url.of("http://host/path"));
        assertEquals(base.newBuilder("path").build(), Url.of("http://host/a/path"));
        assertEquals(base.newBuilder("?query").build(), Url.of("http://host/a/b?query"));
        assertEquals(base.newBuilder("#fragment").build(), Url.of("http://host/a/b#fragment"));
        assertEquals(base.newBuilder("").build(), Url.of("http://host/a/b"));
        assertNull(base.newBuilder("ftp://b"));
        assertNull(base.newBuilder("ht+tp://b"));
        assertNull(base.newBuilder("ht-tp://b"));
        assertNull(base.newBuilder("ht.tp://b"));
    }

    @Test
    void username() {
        assertEquals(Url.of("http://@host/path"), Url.of("http://host/path"));
        assertEquals(Url.of("http://user@host/path"), Url.of("http://user@host/path"));
    }

    /**
     * Given multiple '@' characters, the last one is the delimiter.
     */
    @Test
    void authorityWithMultipleAtSigns() {
        Url urlBuilder = Url.of("http://foo@bar@baz/path");
        assertEquals(urlBuilder, Url.of("http://foo%40bar@baz/path"));
    }

    /**
     * Given multiple ':' characters, the first one is the delimiter.
     */
    @Test
    void authorityWithMultipleColons() {
        assertEquals(Url.of("http://foo:pass1@bar:pass2@baz/path"), Url.of("http://foo:pass1%40bar%3Apass2@baz/path"));
    }

    @Test
    void parseWithNullString() {
        final Url builder = Url.of((String) null);
        assertNull(builder);
    }

    @Test
    void parseWithEmpty() {
        final Url builder = Url.of("");
        assertNull(builder);
    }

    @Test
    void parseWithProtocolAndHost() {
        final Url builder = Url.of("https://www.duckduckgo.com");
        assertEquals("https://www.duckduckgo.com/", builder.toString());
    }

    @Test
    void parseRemovesDefaultPort() {
        assertEquals("https://www.duckduckgo.com/", Url.of("https://www.duckduckgo.com:443").toString());
        assertEquals("http://www.duckduckgo.com/", Url.of("http://www.duckduckgo.com:80").toString());
    }

    @Test
    void parseWithProtocolAndHostAndPort() {
        final Url builder = Url.of("http://www.duckduckgo.com:8080");
        assertEquals("http://www.duckduckgo.com:8080/", builder.toString());
    }

    @Test
    void parseWithProtocolAndHostAndPath() {
        final Url builder = Url.of("http://www.duckduckgo.com/my/path");
        assertEquals("http://www.duckduckgo.com/my/path", builder.toString());
    }

    @Test
    void parseWithProtocolAndHostAndPortAndPath() {
        final Url builder = Url.of("http://www.duckduckgo.com:7060/my/path");
        assertEquals("http://www.duckduckgo.com:7060/my/path", builder.toString());
    }

    @Test
    void parseWithProtocolAndHostAndOneQueryParameter() {
        final Url builder = Url.of("https://www.duckduckgo.com?a=1");
        assertEquals("https://www.duckduckgo.com/?a=1", builder.toString());
    }

    @Test
    void parseWithProtocolAndHostAndQueryParameter() {
        final Url builder = Url.of("https://www.duckduckgo.com?a=1&xx");
        assertEquals("https://www.duckduckgo.com/?a=1&xx", builder.toString());
    }

    @Test
    void parseWithProtocolAndHostAndPortAndOneQueryParameter() {
        final Url builder = Url.of("https://www.duckduckgo.com:8088?a=1");
        assertEquals("https://www.duckduckgo.com:8088/?a=1", builder.toString());
    }

    @Test
    void parseWithProtocolAndHostAndPathAndOneQueryParameter() {
        final Url builder = Url.of("https://www.duckduckgo.com/image.gif?a=1");
        assertEquals("https://www.duckduckgo.com/image.gif?a=1", builder.toString());
    }

    @Test
    void parseWithProtocolAndHostAndPortAndPathAndOneQueryParameter() {
        final Url builder = Url.of("https://www.duckduckgo.com:3021/my/path/again?version=123&encrypt");
        assertEquals("https://www.duckduckgo.com:3021/my/path/again?version=123&encrypt", builder.toString());
    }

    @Test
    void parseWithProtocolAndHostAndTwoQueryParameters() {
        final Url builder = Url.of("https://www.duckduckgo.com?a=1&b=2");
        assertEquals("https://www.duckduckgo.com/?a=1&b=2", builder.toString());
    }

    @Test
    void parseWithProtocolAndHostAndPortAndTwoQueryParameters() {
        final Url builder = Url.of("https://www.duckduckgo.com:7077?a=1&b=2");
        assertEquals("https://www.duckduckgo.com:7077/?a=1&b=2", builder.toString());
    }

    @Test
    void parseWithProtocolAndHostAndPathAndTwoQueryParameters() {
        final Url builder = Url.of("https://www.duckduckgo.com/image.gif?a=1&b=2");
        assertEquals("https://www.duckduckgo.com/image.gif?a=1&b=2", builder.toString());
    }

    @Test
    void parseWithProtocolAndHostAndPortAndPathAndTwoQueryParameters() {
        final Url builder = Url.of("https://www.duckduckgo.com:8090/my/path/again?a=1&b=2");
        assertEquals("https://www.duckduckgo.com:8090/my/path/again?a=1&b=2", builder.toString());
    }

    @Test
    void parseWithColonInPath() {
        final Url builder = Url.of("https://www.duckduckgo.com/my:/path");
        assertEquals("https://www.duckduckgo.com/my:/path", builder.toString());
    }

    @Test
    void parseURLWithNull() {
        assertNull(Url.of((URL) null));
    }

    @Test
    void parseURLSchemeAndHost() throws MalformedURLException {
        final Url builder = Url.of(new URL("http://www.duckduckgo.com"));
        assertEquals("http://www.duckduckgo.com/", builder.toString());
    }

    // compatibility

    @Test
    void toUriWithControlCharacters() throws Exception {
        // Percent-encoded in the path.
        assertEquals(new URI("http://host/a%00b"), Url.of("http://host/a\u0000b").uri());
        assertEquals(new URI("http://host/a%C2%80b"), Url.of("http://host/a\u0080b").uri());
        assertEquals(new URI("http://host/a%C2%9Fb"), Url.of("http://host/a\u009fb").uri());
        // Percent-encoded in the query.
        assertEquals(new URI("http://host/?a%00b"), Url.of("http://host/?a\u0000b").uri());
        assertEquals(new URI("http://host/?a%C2%80b"), Url.of("http://host/?a\u0080b").uri());
        assertEquals(new URI("http://host/?a%C2%9Fb"), Url.of("http://host/?a\u009fb").uri());
        // Stripped from the fragment.
        assertEquals(new URI("http://host/#a%00b"), Url.of("http://host/#a\u0000b").uri());
        assertEquals(new URI("http://host/#ab"), Url.of("http://host/#a\u0080b").uri());
        assertEquals(new URI("http://host/#ab"), Url.of("http://host/#a\u009fb").uri());
    }

    @Test
    void toUriWithSpaceCharacters() throws Exception {
        // Percent-encoded in the path.
        assertEquals(new URI("http://host/a%0Bb"), Url.of("http://host/a\u000bb").uri());
        assertEquals(new URI("http://host/a%20b"), Url.of("http://host/a b").uri());
        assertEquals(new URI("http://host/a%E2%80%89b"), Url.of("http://host/a\u2009b").uri());
        assertEquals(new URI("http://host/a%E3%80%80b"), Url.of("http://host/a\u3000b").uri());
        // Percent-encoded in the query.
        assertEquals(new URI("http://host/?a%0Bb"), Url.of("http://host/?a\u000bb").uri());
        assertEquals(new URI("http://host/?a%20b"), Url.of("http://host/?a b").uri());
        assertEquals(new URI("http://host/?a%E2%80%89b"), Url.of("http://host/?a\u2009b").uri());
        assertEquals(new URI("http://host/?a%E3%80%80b"), Url.of("http://host/?a\u3000b").uri());
        // Stripped from the fragment.
        assertEquals(new URI("http://host/#a%0Bb"), Url.of("http://host/#a\u000bb").uri());
        assertEquals(new URI("http://host/#a%20b"), Url.of("http://host/#a b").uri());
        assertEquals(new URI("http://host/#ab"), Url.of("http://host/#a\u2009b").uri());
        assertEquals(new URI("http://host/#ab"), Url.of("http://host/#a\u3000b").uri());
    }

    @Test
    void toUriWithNonHexPercentEscape() throws Exception {
        assertEquals(new URI("http://host/%25xx"), Url.of("http://host/%xx").uri());
    }

    @Test
    void toUriWithTruncatedPercentEscape() throws Exception {
        assertEquals(new URI("http://host/%25a"), Url.of("http://host/%a").uri());
        assertEquals(new URI("http://host/%25"), Url.of("http://host/%").uri());
    }

    @Test
    void fromJavaNetUrlUnsupportedScheme() throws Exception {
        URL javaNetUrl = new URL("mailto:user@example.com");
        assertNull(Url.of(javaNetUrl));
    }

    @Test
    void fromUri() throws Exception {
        URI uri = new URI("http://username:password@host/path?query#fragment");
        Url httpUrl = Url.of(uri);
        assertEquals("http://username:password@host/path?query#fragment", httpUrl.toString());
    }

    @Test
    void fromUriUnsupportedScheme() throws Exception {
        URI uri = new URI("mailto:user@example.com");
        assertNull(Url.of(uri));
    }

    @Test
    void fromUriPartial() throws Exception {
        URI uri = new URI("/path");
        assertNull(Url.of(uri));
    }


    // path

    @Test
    void parseUriPathWithSpecialCharacters() {
        Url.Builder builder = Url.builder()
                .scheme("http")
                .host("host")
                .addPathSegment("=[]:;\"~|?#@^/$%*");
        assertEquals("http://host/=[]:;%22~%7C%3F%23@%5E%2F$%25*", builder.toString());
    }

    @Test
    void relativePath() {
        Url base = Url.of("http://host/a/b/c");
        assertEquals(Url.of("http://host/a/b/d/e/f"), base.resolve("d/e/f"));
        assertEquals(Url.of("http://host/d/e/f"), base.resolve("../../d/e/f"));
        assertEquals(Url.of("http://host/a/"), base.resolve(".."));
        assertEquals(Url.of("http://host/"), base.resolve("../.."));
        assertEquals(Url.of("http://host/"), base.resolve("../../.."));
        assertEquals(Url.of("http://host/a/b/"), base.resolve("."));
        assertEquals(Url.of("http://host/a/"), base.resolve("././.."));
        assertEquals(Url.of("http://host/a/b/c/"), base.resolve("c/d/../e/../"));
        assertEquals(Url.of("http://host/a/b/..e/"), base.resolve("..e/"));
        assertEquals(Url.of("http://host/a/b/e/f../"), base.resolve("e/f../"));
        assertEquals(Url.of("http://host/a/"), base.resolve("%2E."));
        assertEquals(Url.of("http://host/a/"), base.resolve(".%2E"));
        assertEquals(Url.of("http://host/a/"), base.resolve("%2E%2E"));
        assertEquals(Url.of("http://host/a/"), base.resolve("%2e."));
        assertEquals(Url.of("http://host/a/"), base.resolve(".%2e"));
        assertEquals(Url.of("http://host/a/"), base.resolve("%2e%2e"));
        assertEquals(Url.of("http://host/a/b/"), base.resolve("%2E"));
        assertEquals(Url.of("http://host/a/b/"), base.resolve("%2e"));
    }

    @Test
    void relativePathWithTrailingSlash() {
        Url base = Url.of("http://host/a/b/c/");
        assertEquals(Url.of("http://host/a/b/"), base.resolve(".."));
        assertEquals(Url.of("http://host/a/b/"), base.resolve("../"));
        assertEquals(Url.of("http://host/a/"), base.resolve("../.."));
        assertEquals(Url.of("http://host/a/"), base.resolve("../../"));
        assertEquals(Url.of("http://host/"), base.resolve("../../.."));
        assertEquals(Url.of("http://host/"), base.resolve("../../../"));
        assertEquals(Url.of("http://host/"), base.resolve("../../../.."));
        assertEquals(Url.of("http://host/"), base.resolve("../../../../"));
        assertEquals(Url.of("http://host/a"), base.resolve("../../../../a"));
        assertEquals(Url.of("http://host/"), base.resolve("../../../../a/.."));
        assertEquals(Url.of("http://host/a/"), base.resolve("../../../../a/b/.."));
    }

    @Test
    void pathWithBackslash() {
        Url base = Url.of("http://host/a/b/c");
        assertEquals(Url.of("http://host/a/b/d/e/f"), base.resolve("d\\e\\f"));
        assertEquals(Url.of("http://host/d/e/f"), base.resolve("../..\\d\\e\\f"));
        assertEquals(Url.of("http://host/"), base.resolve("..\\.."));
    }

    @Test
    void relativePathWithSameScheme() {
        Url base = Url.of("http://host/a/b/c");
        assertEquals(Url.of("http://host/a/b/d/e/f"), base.resolve("http:d/e/f"));
        assertEquals(Url.of("http://host/d/e/f"), base.resolve("http:../../d/e/f"));
    }

    @Test
    void decodeSlashCharacterInDecodedPathSegment() {
        assertEquals(Arrays.asList("a/b/c"), Url.of("http://host/a%2Fb%2Fc").pathSegments());
    }

    @Test
    void decodeEmptyPathSegments() {
        assertEquals(Arrays.asList(""), Url.of("http://host/").pathSegments());
    }

    @Test
    public void composeEncodesWhitespace() throws Exception {
        Url url = new Url.Builder()
                .scheme("http")
                .username("a\r\n\f\t b")
                .password("c\r\n\f\t d")
                .host("host")
                .addPathSegment("e\r\n\f\t f")
                .query("g\r\n\f\t h")
                .fragment("i\r\n\f\t j")
                .build();
        assertEquals(("http://a%0D%0A%0C%09%20b:c%0D%0A%0C%09%20d@host/e%0D%0A%0C%09%20f?g%0D%0A%0C%09%20h#i%0D%0A%0C%09 j"), url.toString());
        assertEquals("a\r\n\f\t b", url.username());
        assertEquals("c\r\n\f\t d", url.password());
        assertEquals("e\r\n\f\t f", url.pathSegments().get(0));
        assertEquals("g\r\n\f\t h", url.query());
        assertEquals("i\r\n\f\t j", url.fragment());
    }

    @Test
    public void composeFromUnencodedComponents() throws Exception {
        Url url = new Url.Builder()
                .scheme("http")
                .username("a:\u0001@/\\?#%b")
                .password("c:\u0001@/\\?#%d")
                .host("ef")
                .port(8080)
                .addPathSegment("g:\u0001@/\\?#%h")
                .query("i:\u0001@/\\?#%j")
                .fragment("k:\u0001@/\\?#%l")
                .build();
        assertEquals("http://a%3A%01%40%2F%5C%3F%23%25b:c%3A%01%40%2F%5C%3F%23%25d@ef:8080/g:%01@%2F%5C%3F%23%25h?i:%01@/\\?%23%25j#k:%01@/\\?#%25l", url.toString());
        assertEquals("http", url.scheme());
        assertEquals("a:\u0001@/\\?#%b", url.username());
        assertEquals("c:\u0001@/\\?#%d", url.password());
        assertEquals(Arrays.asList("g:\u0001@/\\?#%h"), url.pathSegments());
        assertEquals("i:\u0001@/\\?#%j", url.query());
        assertEquals("k:\u0001@/\\?#%l", url.fragment());
        assertEquals("a%3A%01%40%2F%5C%3F%23%25b", url.encodedUsername());
        assertEquals("c%3A%01%40%2F%5C%3F%23%25d", url.encodedPassword());
        assertEquals("/g:%01@%2F%5C%3F%23%25h", url.encodedPath());
        assertEquals("i:%01@/\\?%23%25j", url.encodedQuery());
        assertEquals("k:%01@/\\?#%25l", url.encodedFragment());
    }

    @Test
    public void composeFromEncodedComponents() throws Exception {
        Url url = new Url.Builder()
                .scheme("http")
                .encodedUsername("a:\u0001@/\\?#%25b")
                .encodedPassword("c:\u0001@/\\?#%25d")
                .host("ef")
                .port(8080)
                .addEncodedPathSegment("g:\u0001@/\\?#%25h")
                .encodedQuery("i:\u0001@/\\?#%25j")
                .encodedFragment("k:\u0001@/\\?#%25l")
                .build();
        assertEquals("http://a%3A%01%40%2F%5C%3F%23%25b:c%3A%01%40%2F%5C%3F%23%25d@ef:8080/g:%01@%2F%5C%3F%23%25h?i:%01@/\\?%23%25j#k:%01@/\\?#%25l", url.toString());
        assertEquals("http", url.scheme());
        assertEquals("a:\u0001@/\\?#%b", url.username());
        assertEquals("c:\u0001@/\\?#%d", url.password());
        assertEquals(Arrays.asList("g:\u0001@/\\?#%h"), url.pathSegments());
        assertEquals("i:\u0001@/\\?#%j", url.query());
        assertEquals("k:\u0001@/\\?#%l", url.fragment());
        assertEquals("a%3A%01%40%2F%5C%3F%23%25b", url.encodedUsername());
        assertEquals("c%3A%01%40%2F%5C%3F%23%25d", url.encodedPassword());
        assertEquals("/g:%01@%2F%5C%3F%23%25h", url.encodedPath());
        assertEquals("i:%01@/\\?%23%25j", url.encodedQuery());
        assertEquals("k:%01@/\\?#%25l", url.encodedFragment());
    }

    @Test
    public void composeWithEncodedPath() throws Exception {
        Url url = new Url.Builder()
                .scheme("http")
                .host("host")
                .encodedPath("/a%2Fb/c")
                .build();
        assertEquals("http://host/a%2Fb/c", url.toString());
        assertEquals("/a%2Fb/c", url.encodedPath());
        assertEquals(Arrays.asList("a/b", "c"), url.pathSegments());
    }

    @Test
    public void composeMixingPathSegments() throws Exception {
        Url url = new Url.Builder()
                .scheme("http")
                .host("host")
                .encodedPath("/a%2fb/c")
                .addPathSegment("d%25e")
                .addEncodedPathSegment("f%25g")
                .build();
        assertEquals("http://host/a%2fb/c/d%2525e/f%25g", url.toString());
        assertEquals("/a%2fb/c/d%2525e/f%25g", url.encodedPath());
        assertEquals(Arrays.asList("a%2fb", "c", "d%2525e", "f%25g"), url.encodedPathSegments());
        assertEquals(Arrays.asList("a/b", "c", "d%25e", "f%g"), url.pathSegments());
    }

    @Test
    public void composeWithAddSegment() throws Exception {
        Url base = Url.of("http://host/a/b/c");
        assertEquals("/a/b/c/", base.newBuilder().addPathSegment("").build().encodedPath());
        assertEquals("/a/b/c/d", (Object) base.newBuilder().addPathSegment("").addPathSegment("d").build().encodedPath());
        assertEquals("/a/b/", base.newBuilder().addPathSegment("..").build().encodedPath());
        assertEquals("/a/b/", base.newBuilder().addPathSegment("").addPathSegment("..").build().encodedPath());
        assertEquals("/a/b/c/", base.newBuilder().addPathSegment("").addPathSegment("").build().encodedPath());
    }

    @Test
    public void pathSize() throws Exception {
        assertEquals(1, Url.of("http://host/").pathSize());
        assertEquals(3, Url.of("http://host/a/b/c").pathSize());
    }

    @Test
    public void addPathSegments() throws Exception {
        Url base = Url.of("http://host/a/b/c");

        // Add a string with zero slashes: resulting URL gains one slash.
        assertEquals("/a/b/c/", base.newBuilder().addPathSegments("").build().encodedPath());
        assertEquals("/a/b/c/d", base.newBuilder().addPathSegments("d").build().encodedPath());

        // Add a string with one slash: resulting URL gains two slashes.
        assertEquals("/a/b/c//", base.newBuilder().addPathSegments("/").build().encodedPath());
        assertEquals("/a/b/c/d/", base.newBuilder().addPathSegments("d/").build().encodedPath());
        assertEquals("/a/b/c//d", base.newBuilder().addPathSegments("/d").build().encodedPath());

        // Add a string with two slashes: resulting URL gains three slashes.
        assertEquals("/a/b/c///", base.newBuilder().addPathSegments("//").build().encodedPath());
        assertEquals("/a/b/c//d/", base.newBuilder().addPathSegments("/d/").build().encodedPath());
        assertEquals("/a/b/c/d//", base.newBuilder().addPathSegments("d//").build().encodedPath());
        assertEquals("/a/b/c///d", base.newBuilder().addPathSegments("//d").build().encodedPath());
        assertEquals("/a/b/c/d/e/f", base.newBuilder().addPathSegments("d/e/f").build().encodedPath());
    }

    @Test
    void addPathSegmentsOntoTrailingSlash() throws Exception {
        Url base = Url.of("http://host/a/b/c/");

        // Add a string with zero slashes: resulting URL gains zero slashes.
        assertEquals("/a/b/c/", base.newBuilder().addPathSegments("").build().encodedPath());
        assertEquals("/a/b/c/d", base.newBuilder().addPathSegments("d").build().encodedPath());

        // Add a string with one slash: resulting URL gains one slash.
        assertEquals("/a/b/c//", base.newBuilder().addPathSegments("/").build().encodedPath());
        assertEquals("/a/b/c/d/", base.newBuilder().addPathSegments("d/").build().encodedPath());
        assertEquals("/a/b/c//d", base.newBuilder().addPathSegments("/d").build().encodedPath());

        // Add a string with two slashes: resulting URL gains two slashes.
        assertEquals("/a/b/c///", base.newBuilder().addPathSegments("//").build().encodedPath());
        assertEquals("/a/b/c//d/", base.newBuilder().addPathSegments("/d/").build().encodedPath());
        assertEquals("/a/b/c/d//", base.newBuilder().addPathSegments("d//").build().encodedPath());
        assertEquals("/a/b/c///d", base.newBuilder().addPathSegments("//d").build().encodedPath());
        assertEquals("/a/b/c/d/e/f", base.newBuilder().addPathSegments("d/e/f").build().encodedPath());
    }

    @Test
    void addPathSegmentsWithBackslash() throws Exception {
        Url base = Url.of("http://host/");
        assertEquals("/d/e", base.newBuilder().addPathSegments("d\\e").build().encodedPath());
        assertEquals("/d/e", base.newBuilder().addEncodedPathSegments("d\\e").build().encodedPath());
    }

    @Test
    void addPathSegmentsWithEmptyPaths() throws Exception {
        Url base = Url.of("http://host/a/b/c");
        assertEquals("/a/b/c//d/e///f", base.newBuilder().addPathSegments("/d/e///f").build().encodedPath());
    }

    @Test
    void addEncodedPathSegments() throws Exception {
        Url base = Url.of("http://host/a/b/c");
        assertEquals("/a/b/c/d/e/%20/", (Object) base.newBuilder().addEncodedPathSegments("d/e/%20/\n").build().encodedPath());
    }

    @Test
    void addPathSegmentDotDoesNothing() throws Exception {
        Url base = Url.of("http://host/a/b/c");
        assertEquals("/a/b/c", base.newBuilder().addPathSegment(".").build().encodedPath());
    }

    @Test
    void addPathSegmentEncodes() throws Exception {
        Url base = Url.of("http://host/a/b/c");
        assertEquals("/a/b/c/%252e", base.newBuilder().addPathSegment("%2e").build().encodedPath());
        assertEquals("/a/b/c/%252e%252e", base.newBuilder().addPathSegment("%2e%2e").build().encodedPath());
    }

    @Test
    void addPathSegmentDotDotPopsDirectory() throws Exception {
        Url base = Url.of("http://host/a/b/c");
        assertEquals("/a/b/", base.newBuilder().addPathSegment("..").build().encodedPath());
    }

    @Test
    void addPathSegmentDotAndIgnoredCharacter() throws Exception {
        Url base = Url.of("http://host/a/b/c");
        assertEquals("/a/b/c/.%0A", base.newBuilder().addPathSegment(".\n").build().encodedPath());
    }

    @Test
    void addEncodedPathSegmentDotAndIgnoredCharacter() throws Exception {
        Url base = Url.of("http://host/a/b/c");
        assertEquals("/a/b/c", base.newBuilder().addEncodedPathSegment(".\n").build().encodedPath());
    }

    @Test
    void addEncodedPathSegmentDotDotAndIgnoredCharacter() throws Exception {
        Url base = Url.of("http://host/a/b/c");
        assertEquals("/a/b/", base.newBuilder().addEncodedPathSegment("..\n").build().encodedPath());
    }

    @Test
    void setPathSegment() throws Exception {
        Url base = Url.of("http://host/a/b/c");
        assertEquals("/d/b/c", base.newBuilder().setPathSegment(0, "d").build().encodedPath());
        assertEquals("/a/d/c", base.newBuilder().setPathSegment(1, "d").build().encodedPath());
        assertEquals("/a/b/d", base.newBuilder().setPathSegment(2, "d").build().encodedPath());
    }

    @Test
    void setPathSegmentEncodes() throws Exception {
        Url base = Url.of("http://host/a/b/c");
        assertEquals("/%2525/b/c", base.newBuilder().setPathSegment(0, "%25").build().encodedPath());
        assertEquals("/.%0A/b/c", base.newBuilder().setPathSegment(0, ".\n").build().encodedPath());
        assertEquals("/%252e/b/c", base.newBuilder().setPathSegment(0, "%2e").build().encodedPath());
    }

    @Test
    void setPathSegmentAcceptsEmpty() throws Exception {
        Url base = Url.of("http://host/a/b/c");
        assertEquals("//b/c", base.newBuilder().setPathSegment(0, "").build().encodedPath());
        assertEquals("/a/b/", base.newBuilder().setPathSegment(2, "").build().encodedPath());
    }

    @Test
    void setPathSegmentRejectsDot() throws Exception {
        Url base = Url.of("http://host/a/b/c");
        assertThrows(IllegalArgumentException.class, () -> base.newBuilder().setPathSegment(0, "."));
    }

    @Test
    void setPathSegmentRejectsDotDot() throws Exception {
        Url base = Url.of("http://host/a/b/c");
        assertThrows(IllegalArgumentException.class, () -> base.newBuilder().setPathSegment(0, ".."));
    }

    @Test
    void setPathSegmentWithSlash() throws Exception {
        Url base = Url.of("http://host/a/b/c");
        Url url = base.newBuilder().setPathSegment(1, "/").build();
        assertEquals("/a/%2F/c", url.encodedPath());
    }

    @Test
    void setPathSegmentOutOfBounds() throws Exception {
        assertThrows(IndexOutOfBoundsException.class, () -> new Url.Builder().setPathSegment(1, "a"));
    }

    @Test
    void setEncodedPathSegmentEncodes() throws Exception {
        Url base = Url.of("http://host/a/b/c");
        assertEquals("/%25/b/c", base.newBuilder().setEncodedPathSegment(0, "%25").build().encodedPath());
    }

    @Test
    void setEncodedPathSegmentRejectsDot() throws Exception {
        Url base = Url.of("http://host/a/b/c");
        assertThrows(IllegalArgumentException.class, () -> base.newBuilder().setEncodedPathSegment(0, "."));
    }

    @Test
    void setEncodedPathSegmentRejectsDotAndIgnoredCharacter() throws Exception {
        Url base = Url.of("http://host/a/b/c");
        assertThrows(IllegalArgumentException.class, () -> base.newBuilder().setEncodedPathSegment(0, ".\n"));
    }

    @Test
    void setEncodedPathSegmentRejectsDotDot() throws Exception {
        Url base = Url.of("http://host/a/b/c");
        assertThrows(IllegalArgumentException.class, () -> base.newBuilder().setEncodedPathSegment(0, ".."));
    }

    @Test
    void setEncodedPathSegmentRejectsDotDotAndIgnoredCharacter() throws Exception {
        Url base = Url.of("http://host/a/b/c");
        assertThrows(IllegalArgumentException.class, () -> base.newBuilder().setEncodedPathSegment(0, "..\n"));
    }

    @Test
    void setEncodedPathSegmentWithSlash() throws Exception {
        Url base = Url.of("http://host/a/b/c");
        Url url = base.newBuilder().setEncodedPathSegment(1, "/").build();
        assertEquals("/a/%2F/c", url.encodedPath());
    }

    @Test
    void setEncodedPathSegmentOutOfBounds() throws Exception {
        assertThrows(IndexOutOfBoundsException.class, () -> new Url.Builder().setEncodedPathSegment(1, "a"));
    }

    @Test
    void removePathSegment() throws Exception {
        Url base = Url.of("http://host/a/b/c");
        Url url = base.newBuilder()
                .removePathSegment(0)
                .build();
        assertEquals("/b/c", url.encodedPath());
    }

    @Test
    void removePathSegmentDoesntRemovePath() throws Exception {
        Url base = Url.of("http://host/a/b/c");
        Url url = base.newBuilder()
                .removePathSegment(0)
                .removePathSegment(0)
                .removePathSegment(0)
                .build();
        assertEquals(Arrays.asList(""), url.pathSegments());
        assertEquals("/", url.encodedPath());
    }

    @Test
    void removePathSegmentOutOfBounds() throws Exception {
        assertThrows(IndexOutOfBoundsException.class, () -> new Url.Builder().removePathSegment(1));
    }

    @Test
    void percentDecode() {
        assertEquals(Arrays.asList("\u0000"), Url.of("http://host/%00").pathSegments());
        assertEquals(Arrays.asList("a", "\u2603", "c"), Url.of("http://host/a/%E2%98%83/c").pathSegments());
        assertEquals(Arrays.asList("a", "\uD83C\uDF69", "c"), Url.of("http://host/a/%F0%9F%8D%A9/c").pathSegments());
        assertEquals(Arrays.asList("a", "b", "c"), Url.of("http://host/a/%62/c").pathSegments());
        assertEquals(Arrays.asList("a", "z", "c"), Url.of("http://host/a/%7A/c").pathSegments());
        assertEquals(Arrays.asList("a", "z", "c"), Url.of("http://host/a/%7a/c").pathSegments());
    }

    @Test
    void malformedPercentEncoding() {
        assertEquals(Arrays.asList("a%f", "b"), Url.of("http://host/a%f/b").pathSegments());
        assertEquals(Arrays.asList("%", "b"), Url.of("http://host/%/b").pathSegments());
        assertEquals(Arrays.asList("%"), Url.of("http://host/%").pathSegments());
        assertEquals(Arrays.asList("%00"), Url.of("http://github.com/%%30%30").pathSegments());
    }

    @Test
    void malformedUtf8Encoding() {
        // Replace a partial UTF-8 sequence with the Unicode replacement character.
        assertEquals(Arrays.asList("a", "\ufffdx", "c"), Url.of("http://host/a/%E2%98x/c").pathSegments());
    }

    @Test
    void incompleteUrlComposition() {
        Throwable error = assertThrows(IllegalStateException.class, () -> new Url.Builder().scheme("http").build());
        assertEquals("host == null", error.getMessage());

        error = assertThrows(IllegalStateException.class, () -> new Url.Builder().host("host").build());
        assertEquals("scheme == null", error.getMessage());
    }


    // Query

    @Test
    void toUriQueryParameterNameSpecialCharacters() {
        Url.Builder urlBuilder = Url.builder()
                .scheme("http")
                .host("host")
                .addQueryParameter("=[]:;\"~|?#@^/$%*", "a");
        assertEquals("http://host/?%3D%5B%5D%3A%3B%22%7E%7C%3F%23%40%5E%2F%24%25*=a", urlBuilder.toString());
    }

    @Test
    void toUriQueryParameterValueSpecialCharacters() {
        Url.Builder urlBuilder = Url.builder()
                .scheme("http")
                .host("host")
                .addQueryParameter("a", "=[]:;\"~|?#@^/$%*");

        assertEquals("http://host/?a=%3D%5B%5D%3A%3B%22%7E%7C%3F%23%40%5E%2F%24%25*", urlBuilder.toString());
    }

    @Test
    void toUriQueryValueSpecialCharacters() {
        String url = Url.builder()
                .scheme("http")
                .host("host")
                .query("=[]:;\"~|?#@^/$%*")
                .toString();
        assertEquals("http://host/?=[]:;%22~|?%23@^/$%25*", url);
    }

    @Test
    void queryCharactersEncodedWhenComposed() {
        Url url = new Url.Builder()
                .scheme("http")
                .host("host")
                .addQueryParameter("a", "!$(),/:;?@[]\\^`{|}~")
                .build();
        assertEquals("http://host/?a=%21%24%28%29%2C%2F%3A%3B%3F%40%5B%5D%5C%5E%60%7B%7C%7D%7E", url.toString());
        assertEquals("!$(),/:;?@[]\\^`{|}~", url.queryParameter("a"));
    }

    /**
     * When callers use {@code addEncodedQueryParameter()} we only encode what's strictly required.
     * We retain the encoded (or non-encoded) state of the input.
     */
    @Test
    void queryCharactersNotReencodedWhenComposedWithAddEncoded() {
        Url url = new Url.Builder()
                .scheme("http")
                .host("host")
                .addEncodedQueryParameter("a", "!$(),/:;?@[]\\^`{|}~")
                .build();
        assertEquals("http://host/?a=!$(),/:;?@[]\\^`{|}~", url.toString());
        assertEquals("!$(),/:;?@[]\\^`{|}~", url.queryParameter("a"));
    }

    /**
     * When callers parse a URL with query components that aren't encoded, we shouldn't convert them
     * into a canonical form because doing so could be semantically different.
     */
    @Test
    void queryCharactersNotReencodedWhenParsed() {
        Url url = Url.of("http://host/?a=!$(),/:;?@[]\\^`{|}~");
        assertEquals("http://host/?a=!$(),/:;?@[]\\^`{|}~", url.toString());
        assertEquals("!$(),/:;?@[]\\^`{|}~", url.queryParameter("a"));
    }

    @Test
    void composeQueryWithComponents() {
        Url base = Url.of("http://host/");
        Url url = base.newBuilder().addQueryParameter("a+=& b", "c+=& d").build();
        assertEquals("http://host/?a%2B%3D%26%20b=c%2B%3D%26%20d", url.toString());
        assertEquals("c+=& d", url.queryParameterValue(0));
        assertEquals("a+=& b", url.queryParameterName(0));
        assertEquals("c+=& d", url.queryParameter("a+=& b"));
        assertEquals(Collections.singleton("a+=& b"), url.queryParameterNames());
        assertEquals(singletonList("c+=& d"), url.queryParameterValues("a+=& b"));
        assertEquals(1, url.querySize());
        // Ambiguous! (Though working as designed.)
        assertEquals("a+=& b=c+=& d", url.query());
        assertEquals("a%2B%3D%26%20b=c%2B%3D%26%20d", url.encodedQuery());
    }

    @Test
    void composeQueryWithEncodedComponents() {
        Url base = Url.of("http://host/");
        Url url = base.newBuilder().addEncodedQueryParameter("a+=& b", "c+=& d").build();
        assertEquals("http://host/?a+%3D%26%20b=c+%3D%26%20d", url.toString());
        assertEquals("c =& d", url.queryParameter("a =& b"));
    }

    @Test
    void composeQueryRemoveQueryParameter() throws Exception {
        Url url = Url.of("http://host/").newBuilder()
                .addQueryParameter("a+=& b", "c+=& d")
                .removeAllQueryParameters("a+=& b")
                .build();
        assertEquals("http://host/", url.toString());
        assertNull(url.queryParameter("a+=& b"));
    }

    @Test
    void composeQueryRemoveEncodedQueryParameter() throws Exception {
        Url url = Url.of("http://host/").newBuilder()
                .addEncodedQueryParameter("a+=& b", "c+=& d")
                .removeAllEncodedQueryParameters("a+=& b")
                .build();
        assertEquals("http://host/", url.toString());
        assertNull(url.queryParameter("a =& b"));
    }

    @Test
    void composeQuerySetQueryParameter() throws Exception {
        Url url = Url.of("http://host/").newBuilder()
                .addQueryParameter("a+=& b", "c+=& d")
                .setQueryParameter("a+=& b", "ef")
                .build();
        assertEquals("http://host/?a%2B%3D%26%20b=ef", url.toString());
        assertEquals("ef", url.queryParameter("a+=& b"));
    }

    @Test
    void composeQuerySetEncodedQueryParameter() throws Exception {
        Url url = Url.of("http://host/").newBuilder()
                .addEncodedQueryParameter("a+=& b", "c+=& d")
                .setEncodedQueryParameter("a+=& b", "ef")
                .build();
        assertEquals("http://host/?a+%3D%26%20b=ef", url.toString());
        assertEquals("ef", url.queryParameter("a =& b"));
    }

    @Test
    void composeQueryMultipleEncodedValuesForParameter() throws Exception {
        Url url = Url.of("http://host/").newBuilder()
                .addQueryParameter("a+=& b", "c+=& d")
                .addQueryParameter("a+=& b", "e+=& f")
                .build();
        assertEquals("http://host/?a%2B%3D%26%20b=c%2B%3D%26%20d&a%2B%3D%26%20b=e%2B%3D%26%20f", url.toString());
        assertEquals(2, url.querySize());
        assertEquals(Collections.singleton("a+=& b"), url.queryParameterNames());
        assertEquals(Arrays.asList("c+=& d", "e+=& f"), url.queryParameterValues("a+=& b"));
    }

    @Test
    void absentQueryIsZeroNameValuePairs() throws Exception {
        Url url = Url.of("http://host/").newBuilder()
                .query(null)
                .build();
        assertEquals(0, url.querySize());
    }

    @Test
    void emptyQueryIsSingleNameValuePairWithEmptyKey() throws Exception {
        Url url = Url.of("http://host/").newBuilder()
                .query("")
                .build();
        assertEquals(1, url.querySize());
        assertEquals("", url.queryParameterName(0));
        assertNull(url.queryParameterValue(0));
    }

    @Test
    void ampersandQueryIsTwoNameValuePairsWithEmptyKeys() throws Exception {
        Url url = Url.of("http://host/").newBuilder()
                .query("&")
                .build();
        assertEquals(2, url.querySize());
        assertEquals("", url.queryParameterName(0));
        assertNull(url.queryParameterValue(0));
        assertEquals("", url.queryParameterName(1));
        assertNull(url.queryParameterValue(1));
    }

    @Test
    void removeAllDoesNotRemoveQueryIfNoParametersWereRemoved() throws Exception {
        Url url = Url.of("http://host/").newBuilder()
                .query("")
                .removeAllQueryParameters("a")
                .build();
        assertEquals("http://host/?", url.toString());
    }

    @Test
    void queryParametersWithoutValues() throws Exception {
        Url url = Url.of("http://host/?foo&bar&baz");
        assertEquals(3, url.querySize());
        assertEquals(new LinkedHashSet<>(Arrays.asList("foo", "bar", "baz")), url.queryParameterNames());
        assertNull(url.queryParameterValue(0));
        assertNull(url.queryParameterValue(1));
        assertNull(url.queryParameterValue(2));
        assertEquals(singletonList((String) null), url.queryParameterValues("foo"));
        assertEquals(singletonList((String) null), url.queryParameterValues("bar"));
        assertEquals(singletonList((String) null), url.queryParameterValues("baz"));
    }

    @Test
    void queryParametersWithEmptyValues() throws Exception {
        Url url = Url.of("http://host/?foo=&bar=&baz=");
        assertEquals(3, url.querySize());
        assertEquals(new LinkedHashSet<>(Arrays.asList("foo", "bar", "baz")), url.queryParameterNames());
        assertEquals("", url.queryParameterValue(0));
        assertEquals("", url.queryParameterValue(1));
        assertEquals("", url.queryParameterValue(2));
        assertEquals(singletonList(""), url.queryParameterValues("foo"));
        assertEquals(singletonList(""), url.queryParameterValues("bar"));
        assertEquals(singletonList(""), url.queryParameterValues("baz"));
    }

    @Test
    void queryParametersWithRepeatedName() throws Exception {
        Url url = Url.of("http://host/?foo[]=1&foo[]=2&foo[]=3");
        assertEquals(3, url.querySize());
        assertEquals(Collections.singleton("foo[]"), url.queryParameterNames());
        assertEquals("1", url.queryParameterValue(0));
        assertEquals("2", url.queryParameterValue(1));
        assertEquals("3", url.queryParameterValue(2));
        assertEquals(Arrays.asList("1", "2", "3"), url.queryParameterValues("foo[]"));
    }

    @Test
    void queryParameterLookupWithNonCanonicalEncoding() throws Exception {
        Url url = Url.of("http://host/?%6d=m&+=%20");
        assertEquals("m", url.queryParameterName(0));
        assertEquals(" ", url.queryParameterName(1));
        assertEquals("m", url.queryParameter("m"));
        assertEquals(" ", url.queryParameter(" "));
    }

    @Test
    void parsedQueryDoesntIncludeFragment() {
        Url url = Url.of("http://host/?#fragment");
        assertEquals("fragment", url.fragment());
        assertEquals("", url.query());
        assertEquals("", url.encodedQuery());
    }

    // Fragment

    @Test
    void toUriFragmentSpecialCharacters() {
        Url url = new Url.Builder()
                .scheme("http")
                .host("host")
                .fragment("=[]:;\"~|?#@^/$%*")
                .build();
        assertEquals("http://host/#=[]:;\"~|?#@^/$%25*", url.toString());
    }


    @Test
    void fragmentNonAscii() {
        Url url = Url.of("http://host/#Σ");
        assertEquals("http://host/#Σ", url.toString());
        assertEquals("Σ", url.fragment());
        assertEquals("Σ", url.encodedFragment());
        assertEquals("http://host/#Σ", url.uri().toString());
    }

    @Test
    void fragmentNonAsciiThatOffendsJavaNetUri() throws Exception {
        Url url = Url.of("http://host/#\u0080");
        assertEquals("http://host/#\u0080", url.toString());
        assertEquals("\u0080", url.fragment());
        assertEquals("\u0080", url.encodedFragment());
        // Control characters may be stripped!
        assertEquals(new URI("http://host/#"), url.uri());
    }

    @Test
    void fragmentPercentEncodedNonAscii() {
        Url url = Url.of("http://host/#%C2%80");
        assertEquals("http://host/#%C2%80", url.toString());
        assertEquals("\u0080", url.fragment());
        assertEquals("%C2%80", url.encodedFragment());
        assertEquals("http://host/#%C2%80", url.uri().toString());
    }

    @Test
    void fragmentPercentEncodedPartialCodePoint() {
        Url url = Url.of("http://host/#%80");
        assertEquals("http://host/#%80", url.toString());
        // Unicode replacement character.
        assertEquals("\ufffd", url.fragment());
        assertEquals("%80", url.encodedFragment());
        assertEquals("http://host/#%80", url.uri().toString());
    }

    @Test
    void clearFragment() {
        Url url = Url.of("http://host/#fragment")
                .newBuilder()
                .fragment(null)
                .build();
        assertEquals("http://host/", url.toString());
        assertNull(url.fragment());
        assertNull(url.encodedFragment());
    }

    @Test
    void clearEncodedFragment() {
        Url url = Url.of("http://host/#fragment")
                .newBuilder()
                .encodedFragment(null)
                .build();
        assertEquals("http://host/", url.toString());
        assertNull(url.fragment());
        assertNull(url.encodedFragment());
    }


    private void assertInvalidBuild(String string, String exceptionMessage) {
        Throwable th = assertThrows(
                IllegalArgumentException.class,
                () -> new Url.Builder().parse(null, string)
        );

        assertEquals(exceptionMessage, th.getMessage());
    }

}