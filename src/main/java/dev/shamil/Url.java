/*
 * Copyright (C) 2015 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * The MIT License
 *
 * Copyright (C) 2021 Shamil
 *
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
 * LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 * OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package dev.shamil;

import java.net.IDN;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * @author 0xShamil
 */
public final class Url {
    public static final String HTTP = "http";
    public static final String HTTPS = "https";
    private static final int SIZE = 8192;
    private static final String EMPTY = "";

    private static final char[] HEX_DIGITS = {
            '0', '1', '2', '3', '4', '5', '6', '7',
            '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'
    };


    /**
     * Either "http" or "https".
     */
    final String scheme;
    /**
     * Canonical hostname.
     */
    final String host;
    /**
     * Either 80, 443 or a user-specified port. In range [1..65535].
     */
    final int port;
    /**
     * Decoded username.
     */
    private final String username;
    /**
     * Decoded password.
     */
    private final String password;
    /**
     * A list of canonical path segments. This list always contains at least one element, which may be
     * the empty string. Each segment is formatted with a leading '/', so if path segments were ["a",
     * "b", ""], then the encoded path would be "/a/b/".
     */
    private final List<String> pathSegments;

    /**
     * Alternating, decoded query names and values, or null for no query. Names may be empty or
     * non-empty, but never null. Values are null if the name has no corresponding '=' separator, or
     * empty, or non-empty.
     */
    private final List<String> queryNamesAndValues;

    /**
     * Decoded fragment.
     */
    private final String fragment;

    /**
     * Canonical URL.
     */
    private final String url;

    Url(Builder builder) {
        this.scheme = builder.scheme;
        this.username = percentDecode(builder.encodedUsername, false);
        this.password = percentDecode(builder.encodedPassword, false);
        this.host = builder.host;
        this.port = builder.effectivePort();
        this.pathSegments = percentDecode(builder.encodedPathSegments, false);
        this.queryNamesAndValues = builder.encodedQueryNamesAndValues != null
                ? percentDecode(builder.encodedQueryNamesAndValues, true)
                : null;
        this.fragment = builder.encodedFragment != null
                ? percentDecode(builder.encodedFragment, false)
                : null;
        this.url = builder.toString();
    }

    /**
     * Returns either "http" or "https".
     */
    public String scheme() {
        return scheme;
    }

    public boolean isHttps() {
        return scheme.equals(HTTPS);
    }

    /**
     * Returns the username, or an empty string if none is set.
     */
    public String encodedUsername() {
        if (username.isEmpty()) {
            return "";
        }
        int usernameStart = scheme.length() + 3; // "://".length() == 3.
        int usernameEnd = delimiterOffset(url, usernameStart, url.length(), ":@");
        return url.substring(usernameStart, usernameEnd);
    }

    /**
     * Returns the decoded username, or an empty string if none is present.
     */
    public String username() {
        return username;
    }

    /**
     * Returns the password, or an empty string if none is set.
     */
    public String encodedPassword() {
        if (password.isEmpty()) {
            return "";
        }
        int passwordStart = url.indexOf(':', scheme.length() + 3) + 1;
        int passwordEnd = url.indexOf('@');
        return url.substring(passwordStart, passwordEnd);
    }

    /**
     * Returns the decoded password, or an empty string if none is present.
     */
    public String password() {
        return password;
    }

    /**
     * Returns the host address suitable for use with {@link InetAddress#getAllByName(String)}. May
     * be:
     *
     * <ul>
     *   <li>A regular host name, like {@code android.com}.
     *   <li>An IPv4 address, like {@code 127.0.0.1}.
     *   <li>An IPv6 address, like {@code ::1}. Note that there are no square braces.
     *   <li>An encoded IDN, like {@code xn--n3h.net}.
     * </ul>
     */
    public String host() {
        return host;
    }

    /**
     * Returns the explicitly-specified port if one was provided, or the default port for this URL's
     * scheme.
     */
    public int port() {
        return port;
    }

    /**
     * Returns the number of segments in this URL's path. This is also the number of slashes in the
     * URL's path, like 3 in {@code http://host/a/b/c}. This is always at least 1.
     */
    public int pathSize() {
        return pathSegments.size();
    }

    /**
     * Returns the entire path of this URL encoded for use in HTTP resource resolution. The returned
     * path will start with {@code "/"}.
     */
    public String encodedPath() {
        int pathStart = url.indexOf('/', scheme.length() + 3); // "://".length() == 3.
        int pathEnd = pathDelimiterOffset(url, pathStart, url.length());
        return url.substring(pathStart, pathEnd);
    }

    /**
     * Returns a list of encoded path segments like {@code ["a", "b", "c"]} for the URL {@code
     * http://host/a/b/c}. This list is never empty though it may contain a single empty string.
     */
    public List<String> encodedPathSegments() {
        int pathStart = url.indexOf('/', scheme.length() + 3);
        int pathEnd = pathDelimiterOffset(url, pathStart, url.length());
        List<String> result = new ArrayList<>();
        for (int i = pathStart; i < pathEnd; ) {
            i++; // Skip the '/'.
            int segmentEnd = delimiterOffset(url, i, pathEnd, '/');
            result.add(url.substring(i, segmentEnd));
            i = segmentEnd;
        }
        return result;
    }

    /**
     * Returns a list of path segments like {@code ["a", "b", "c"]} for the URL {@code
     * http://host/a/b/c}. This list is never empty though it may contain a single empty string.
     */
    public List<String> pathSegments() {
        return pathSegments;
    }

    /**
     * Returns the query of this URL, encoded for use in HTTP resource resolution. The returned string
     * may be null (for URLs with no query), empty (for URLs with an empty query) or non-empty (all
     * other URLs).
     */
    public String encodedQuery() {
        if (queryNamesAndValues == null) {
            return null; // No query.
        }
        int queryStart = url.indexOf('?') + 1;
        int queryEnd = delimiterOffset(url, queryStart, url.length(), '#');
        return url.substring(queryStart, queryEnd);
    }

    /**
     * Returns this URL's query, like {@code "abc"} for {@code http://host/?abc}. Most callers should
     * prefer {@link #queryParameterName} and {@link #queryParameterValue} because these methods offer
     * direct access to individual query parameters.
     */
    public String query() {
        if (queryNamesAndValues == null) {
            return null; // No query.
        }
        StringBuilder result = new StringBuilder();
        namesAndValuesToQueryString(result, queryNamesAndValues);
        return result.toString();
    }

    /**
     * Returns the number of query parameters in this URL, like 2 for {@code
     * http://host/?a=apple&b=banana}. If this URL has no query this returns 0. Otherwise it returns
     * one more than the number of {@code "&"} separators in the query.
     */
    public int querySize() {
        return queryNamesAndValues != null ? queryNamesAndValues.size() / 2 : 0;
    }

    /**
     * Returns the first query parameter named {@code name} decoded using UTF-8, or null if there is
     * no such query parameter.
     */
    public String queryParameter(String name) {
        if (queryNamesAndValues == null) {
            return null;
        }
        for (int i = 0, size = queryNamesAndValues.size(); i < size; i += 2) {
            if (name.equals(queryNamesAndValues.get(i))) {
                return queryNamesAndValues.get(i + 1);
            }
        }
        return null;
    }

    /**
     * Returns the distinct query parameter names in this URL, like {@code ["a", "b"]} for {@code
     * http://host/?a=apple&b=banana}. If this URL has no query this returns the empty set.
     */
    public Set<String> queryParameterNames() {
        if (queryNamesAndValues == null) {
            return Collections.emptySet();
        }
        Set<String> result = new LinkedHashSet<>();
        for (int i = 0, size = queryNamesAndValues.size(); i < size; i += 2) {
            result.add(queryNamesAndValues.get(i));
        }
        return Collections.unmodifiableSet(result);
    }

    /**
     * Returns all values for the query parameter {@code name} ordered by their appearance in this
     * URL. For example this returns {@code ["banana"]} for {@code queryParameterValue("b")} on {@code
     * http://host/?a=apple&b=banana}.
     */
    public List<String> queryParameterValues(String name) {
        if (queryNamesAndValues == null) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>();
        for (int i = 0, size = queryNamesAndValues.size(); i < size; i += 2) {
            if (name.equals(queryNamesAndValues.get(i))) {
                result.add(queryNamesAndValues.get(i + 1));
            }
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * Returns the name of the query parameter at {@code index}. For example this returns {@code "a"}
     * for {@code queryParameterName(0)} on {@code http://host/?a=apple&b=banana}. This throws if
     * {@code index} is not less than the {@linkplain #querySize query size}.
     */
    public String queryParameterName(int index) {
        if (queryNamesAndValues == null) {
            throw new IndexOutOfBoundsException();
        }
        return queryNamesAndValues.get(index * 2);
    }

    /**
     * Returns the value of the query parameter at {@code index}. For example this returns {@code
     * "apple"} for {@code queryParameterName(0)} on {@code http://host/?a=apple&b=banana}. This
     * throws if {@code index} is not less than the {@linkplain #querySize query size}.
     */
    public String queryParameterValue(int index) {
        if (queryNamesAndValues == null) {
            throw new IndexOutOfBoundsException();
        }
        return queryNamesAndValues.get(index * 2 + 1);
    }

    /**
     * Returns this URL's encoded fragment, like {@code "abc"} for {@code http://host/#abc}. This
     * returns null if the URL has no fragment.
     */
    public String encodedFragment() {
        if (fragment == null) {
            return null;
        }
        int fragmentStart = url.indexOf('#') + 1;
        return url.substring(fragmentStart);
    }

    /**
     * Returns this URL's fragment, like {@code "abc"} for {@code http://host/#abc}. This returns null
     * if the URL has no fragment.
     */
    public String fragment() {
        return fragment;
    }

    /**
     * Returns this URL as a {@link URL java.net.URL}.
     */
    public URL url() {
        try {
            return new URL(url);
        } catch (MalformedURLException e) {
            throw new RuntimeException(e); // Unexpected!
        }
    }

    /**
     * Returns this URL as a {@link URI java.net.URI}. Because {@code URI} is more strict than this
     * class, the returned URI may be semantically different from this URL:
     *
     * <ul>
     *     <li>Characters forbidden by URI like {@code [} and {@code |} will be escaped.
     *     <li>Invalid percent-encoded sequences like {@code %xx} will be encoded like {@code %25xx}.
     *     <li>Whitespace and control characters in the fragment will be stripped.
     * </ul>
     *
     * <p>These differences may have a significant consequence when the URI is interpreted by a
     * webserver. For this reason the {@linkplain URI URI class} and this method should be avoided.
     */
    public URI uri() {
        String uri = newBuilder().reEncodeForUri().toString();
        try {
            return new URI(uri);
        } catch (URISyntaxException e) {
            // Unlikely edge case: the URI has a forbidden character in the fragment. Strip it & retry.
            try {
                String stripped = uri.replaceAll("[\\u0000-\\u001F\\u007F-\\u009F\\p{javaWhitespace}]", "");
                return URI.create(stripped);
            } catch (Exception e1) {
                throw new RuntimeException(e); // Unexpected!
            }
        }
    }

    /**
     * Returns a string with containing this URL with its username, password, query, and fragment
     * stripped, and its path replaced with {@code /...}. For example, redacting {@code
     * http://username:password@example.com/path} returns {@code http://example.com/...}.
     */
    public String redact() {
        return newBuilder("/...")
                .username("")
                .password("")
                .build()
                .toString();
    }

    /**
     * Returns the URL that would be retrieved by following {@code link} from this URL, or null if
     * the resulting URL is not well-formed.
     */
    public Url resolve(String link) {
        Url.Builder builder = newBuilder(link);
        return builder != null ? builder.build() : null;
    }

    public Builder newBuilder() {
        Builder result = new Builder();
        result.scheme = scheme;
        result.encodedUsername = encodedUsername();
        result.encodedPassword = encodedPassword();
        result.host = host;
        // If we're set to a default port, unset it in case of a scheme change.
        result.port = port != defaultPort(scheme) ? port : -1;
        result.encodedPathSegments.clear();
        result.encodedPathSegments.addAll(encodedPathSegments());
        result.encodedQuery(encodedQuery());
        result.encodedFragment = encodedFragment();
        return result;
    }

    /**
     * Returns a builder for the URL that would be retrieved by following {@code link} from this URL,
     * or null if the resulting URL is not well-formed.
     */
    public Builder newBuilder(String link) {
        try {
            return new Builder().parse(this, link);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Url && ((Url) other).url.equals(url);
    }

    @Override
    public int hashCode() {
        return url.hashCode();
    }

    @Override
    public String toString() {
        return url;
    }

    private List<String> percentDecode(List<String> list, boolean plusIsSpace) {
        int size = list.size();
        List<String> result = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            String s = list.get(i);
            result.add(s != null ? percentDecode(s, plusIsSpace) : null);
        }
        return Collections.unmodifiableList(result);
    }


    //-------------------//
    //  FACTORY METHODS  //
    //-------------------//

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns a new {@code HttpUrl} representing {@code url} if it is a well-formed HTTP or HTTPS
     * URL, or null if it isn't.
     */
    public static Url of(String url) {
        try {
            return (url == null) ? null : new Builder().parse(null, url).build();
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    /**
     * Returns an {@link Url} for {@code url} if its protocol is {@code http} or {@code https}, or
     * null if it has any other protocol.
     */
    public static Url of(URL url) {
        return (url == null) ? null : of(url.toString());
    }

    public static Url of(URI uri) {
        return (uri == null) ? null : of(uri.toString());
    }


    public static final class Builder {
        private final List<String> encodedPathSegments = new ArrayList<>();
        private String scheme;
        private String host;
        private String encodedUsername = EMPTY;
        private String encodedPassword = EMPTY;
        private int port = -1;
        private List<String> encodedQueryNamesAndValues; //Lazily instantiated
        private String encodedFragment;

        private String url;

        Builder() {
            encodedPathSegments.add(EMPTY); // The default path is '/' which needs a trailing space.
        }

        public Builder scheme(String scheme) {
            this.scheme = paramNotBlank(scheme, "scheme");
            return this;
        }

        /**
         * @param providedHost either a regular hostname, International Domain Name, IPv4 address, or IPv6 address.
         */
        public Builder host(String providedHost) {
            paramNotBlank(providedHost, "host");
            String encoded = canonicalizeHost(providedHost);
            if (encoded == null) {
                throw new IllegalArgumentException("unexpected host: " + host);
            }
            this.host = encoded;
            return this;
        }

        public Builder port(int port) {
            if (port <= 0 || port > 65535) {
                throw new IllegalArgumentException("unexpected port: " + port);
            }
            this.port = port;
            return this;
        }

        public Builder username(String username) {
            paramNotBlank(username, "username");
            this.encodedUsername = encode(username, UrlToken.USERNAME, 0, username.length(), false, false, false, true);
            return this;
        }

        public Builder encodedUsername(String encodedUsername) {
            paramNotBlank(encodedUsername, "username");
            this.encodedUsername = encode(encodedUsername, UrlToken.USERNAME, 0, encodedUsername.length(), true, false, false, true);
            return this;
        }

        public Builder password(String password) {
            paramNotBlank(password, "password");
            this.encodedPassword = encode(password, UrlToken.PASSWORD, 0, password.length(), false, false, false, true);
            return this;
        }

        public Builder encodedPassword(String encodedPassword) {
            paramNotBlank(encodedPassword, "encodedPassword");
            this.encodedPassword = encode(encodedPassword, UrlToken.PASSWORD, 0, encodedPassword.length(), true, false, false, true);
            return this;
        }

        public Builder addPathSegment(String pathSegment) {
            push(paramNotNull(pathSegment, "pathSegment"), 0, pathSegment.length(), false, false);
            return this;
        }

        /**
         * Adds a set of path segments separated by a slash {@code /}.
         */
        public Builder addPathSegments(String pathSegments) {
            return addPathSegments(paramNotNull(pathSegments, "pathSegments"), false);
        }

        public Builder addEncodedPathSegment(String encodedPathSegment) {
            push(paramNotNull(encodedPathSegment, "encodedPathSegment"), 0, encodedPathSegment.length(), false, true);
            return this;
        }

        public Builder addEncodedPathSegments(String encodedPathSegments) {
            return addPathSegments(paramNotNull(encodedPathSegments, "encodedPathSegments"), true);
        }

        private Builder addPathSegments(String pathSegments,
                                        boolean alreadyEncoded) {
            paramNotNull(pathSegments, "pathSegments");
            int offset = 0;
            do {
                int segmentEnd = pathSegmentDelimiterOffset(pathSegments, offset, pathSegments.length());
                boolean addTrailingSlash = segmentEnd < pathSegments.length();
                push(pathSegments, offset, segmentEnd, addTrailingSlash, alreadyEncoded);
                offset = segmentEnd + 1;
            } while (offset <= pathSegments.length());

            return this;
        }

        public Builder setPathSegment(int index, String pathSegment) {
            paramNotNull(pathSegment, "pathSegment");
            String canonicalPathSegment = encode(pathSegment, UrlToken.PATH, 0,
                    pathSegment.length(), false, false, false, true);
            if (isDot(canonicalPathSegment) || isDotDot(canonicalPathSegment)) {
                throw new IllegalArgumentException("unexpected path segment: " + pathSegment);
            }
            encodedPathSegments.set(index, canonicalPathSegment);
            return this;
        }

        public Builder setEncodedPathSegment(int index, String encodedPathSegment) {
            paramNotNull(encodedPathSegment, "encodedPathSegment");
            String canonicalPathSegment = encode(encodedPathSegment, UrlToken.PATH, 0,
                    encodedPathSegment.length(), true, false, false, true);
            encodedPathSegments.set(index, canonicalPathSegment);
            if (isDot(canonicalPathSegment) || isDotDot(canonicalPathSegment)) {
                throw new IllegalArgumentException("unexpected path segment: " + encodedPathSegment);
            }
            return this;
        }

        public Builder removePathSegment(int index) {
            encodedPathSegments.remove(index);
            if (encodedPathSegments.isEmpty()) {
                encodedPathSegments.add(""); // Always leave at least one '/'.
            }
            return this;
        }

        public Builder encodedPath(String encodedPath) {
            paramNotNull(encodedPath, "encoded path");
            if (!encodedPath.startsWith("/")) {
                throw new IllegalArgumentException("unexpected encodedPath: " + encodedPath);
            }
            resolvePath(encodedPath, 0, encodedPath.length());
            return this;
        }

        public Builder query(String query) {
            this.encodedQueryNamesAndValues = query != null
                    ? queryStringToNamesAndValues(encode(
                    query, UrlToken.QUERY_ENCODED, 0, query.length(), false, false, true, true))
                    : null;
            return this;
        }

        public Builder encodedQuery(String encodedQuery) {
            this.encodedQueryNamesAndValues = (encodedQuery != null && encodedQuery.length() > 0)
                    ? queryStringToNamesAndValues(encode(
                    encodedQuery, UrlToken.QUERY_ENCODED, 0, encodedQuery.length(), true, false, true, true))
                    : null;
            return this;
        }

        /**
         * Encodes the query parameter using UTF-8 and adds it to this URL's query string.
         */
        public Builder addQueryParameter(String name, String value) {
            ensureQueryParamsState();
            encodedQueryNamesAndValues.add(encode(paramNotBlank(name, "queryParameterName"), UrlToken.QUERY, 0, name.length(), false, false, true, true));
            encodedQueryNamesAndValues.add(value != null && value.length() > 0 ? encode(value, UrlToken.QUERY, 0, value.length(), false, false, true, true) : null);
            return this;
        }

        public Builder setQueryParameter(String name, String value) {
            removeAllQueryParameters(name);
            addQueryParameter(name, value);
            return this;
        }

        /**
         * Adds the pre-encoded query parameter to this URL's query string.
         */
        public Builder addEncodedQueryParameter(String encodedName,
                                                String encodedValue) {
            ensureQueryParamsState();
            encodedQueryNamesAndValues.add(encode(
                    paramNotBlank(encodedName, "queryParameterName"), UrlToken.QUERY_RE_ENCODE, 0, encodedName.length(), true, false, true, true)
            );
            encodedQueryNamesAndValues.add(encodedValue != null && encodedValue.length() > 0 ? encode(encodedValue, UrlToken.QUERY_RE_ENCODE, 0, encodedValue.length(), true, false, true, true) : null);
            return this;
        }

        public Builder setEncodedQueryParameter(String encodedName, String encodedValue) {
            removeAllEncodedQueryParameters(encodedName);
            addEncodedQueryParameter(encodedName, encodedValue);
            return this;
        }

        public Builder removeAllQueryParameters(String name) {
            paramNotBlank(name, "queryParameterName");
            if (encodedQueryNamesAndValues == null) {
                return this;
            }
            String nameToRemove = encode(name, UrlToken.QUERY, 0, name.length(), false, false, true, true);
            removeAllCanonicalQueryParameters(nameToRemove);
            return this;
        }

        public Builder removeAllEncodedQueryParameters(String encodedName) {
            paramNotBlank(encodedName, "queryParameterName");
            if (encodedQueryNamesAndValues == null) {
                return this;
            }
            removeAllCanonicalQueryParameters(encode(encodedName, UrlToken.QUERY_RE_ENCODE, 0, encodedName.length(), true, false, true, true));
            return this;
        }

        private void removeAllCanonicalQueryParameters(String canonicalName) {
            for (int i = encodedQueryNamesAndValues.size() - 2; i >= 0; i -= 2) {
                if (canonicalName.equals(encodedQueryNamesAndValues.get(i))) {
                    encodedQueryNamesAndValues.remove(i + 1);
                    encodedQueryNamesAndValues.remove(i);
                    if (encodedQueryNamesAndValues.isEmpty()) {
                        encodedQueryNamesAndValues = null;
                        return;
                    }
                }
            }
        }

        private void ensureQueryParamsState() {
            if (encodedQueryNamesAndValues == null) {
                encodedQueryNamesAndValues = new ArrayList<>();
            }
        }

        public Builder fragment(String fragment) {
            this.encodedFragment = fragment != null
                    ? encode(fragment, UrlToken.FRAGMENT, 0, fragment.length(), false, false, false, false)
                    : null;
            return this;
        }

        public Builder encodedFragment(String encodedFragment) {
            this.encodedFragment = encodedFragment != null
                    ? encode(encodedFragment, UrlToken.FRAGMENT, 0, encodedFragment.length(), true, false, false, false)
                    : null;
            return this;
        }

        public Url build() {
            if (scheme == null) {
                throw new IllegalStateException("scheme == null");
            }
            if (host == null) {
                throw new IllegalStateException("host == null");
            }
            return new Url(this);
        }

        @Override
        public String toString() {
            if (url == null) {
                url = buildInternal();
            }
            return url;
        }

        private String buildInternal() {
            StringBuilder result = new StringBuilder();
            if (scheme != null) {
                result.append(scheme);
                result.append("://");
            }

            if (!encodedUsername.isEmpty() || !encodedPassword.isEmpty()) {
                result.append(encodedUsername);
                if (!encodedPassword.isEmpty()) {
                    result.append(':');
                    result.append(encodedPassword);
                }
                result.append('@');
            }

            if (host != null) {
                if (host.indexOf(':') != -1) {
                    // Host is an IPv6 address.
                    result.append('[');
                    result.append(host);
                    result.append(']');
                } else {
                    result.append(host);
                }
            }

            if (port != -1) {
                if (scheme == null || port != defaultPort(scheme)) {
                    result.append(':');
                    result.append(port);
                }
            }

            for (int i = -1, size = encodedPathSegments.size(); ++i < size; ) {
                result.append('/');
                result.append(encodedPathSegments.get(i));
            }

            if (encodedQueryNamesAndValues != null) {
                result.append('?');
                namesAndValuesToQueryString(result, encodedQueryNamesAndValues);
            }

            if (encodedFragment != null) {
                result.append('#');
                result.append(encodedFragment);
            }

            return result.toString();
        }

        public Builder parse(Url base, String input) {
            int pos = skipUntilFirstLeadingNonWhitespace(input);
            int limit = skipUntilFirstTrailingNonWhitespace(input, pos, input.length());

            int schemeDelimiterOffset = schemeDelimiterOffset(input, pos, limit);
            if (schemeDelimiterOffset != -1) {
                if (input.regionMatches(true, pos, "https:", 0, 6)) {
                    this.scheme = "https";
                    pos += 6; //"https:".length();
                } else if (input.regionMatches(true, pos, "http:", 0, 5)) {
                    this.scheme = "http";
                    pos += 5; //"http:".length()
                } else {
                    throw new IllegalArgumentException("Expected URL scheme 'http' or 'https' but was '"
                            + input.substring(0, schemeDelimiterOffset) + "'");
                }
            } else if (base != null) {
                this.scheme = base.scheme;
            } else {
                throw new IllegalArgumentException("Expected URL scheme 'http' or 'https' but no colon was found");
            }

            // Authority.
            boolean hasUsername = false;
            boolean hasPassword = false;
            int slashCount = slashCount(input, pos, limit);
            if (slashCount >= 2 || base == null || !base.scheme.equals(this.scheme)) {
                // Read an authority if either:
                //  * The input starts with 2 or more slashes. These follow the scheme if it exists.
                //  * The input scheme exists and is different from the base URL's scheme.
                //
                // The structure of an authority is:
                //   username:password@host:port
                //
                // Username, password and port are optional.
                //   [username[:password]@]host[:port]
                pos += slashCount;
                authority:
                while (true) {
                    int componentDelimiterOffset = authorityDelimiterOffset(input, pos, limit);
                    int c = componentDelimiterOffset != limit ? input.charAt(componentDelimiterOffset) : -1;
                    switch (c) {
                        case '@':
                            // User info precedes.
                            if (!hasPassword) {
                                int passwordColonOffset = delimiterOffset(input, pos, componentDelimiterOffset, ':');
                                String canonicalUsername = encode(input, UrlToken.USERNAME, pos, passwordColonOffset, true, false, false, true);
                                encodedUsername = hasUsername
                                        ? encodedUsername + "%40" + canonicalUsername
                                        : canonicalUsername;
                                if (passwordColonOffset != componentDelimiterOffset) {
                                    hasPassword = true;
                                    encodedPassword = encode(input, UrlToken.PASSWORD, passwordColonOffset + 1, componentDelimiterOffset, true, false, false, true);
                                }
                                hasUsername = true;
                            } else {
                                encodedPassword = new StringBuilder()
                                        .append(encodedPassword)
                                        .append("%40")
                                        .append(encode(input, UrlToken.PASSWORD, pos, componentDelimiterOffset, true, false, false, true))
                                        .toString();
                            }
                            pos = componentDelimiterOffset + 1;
                            break;

                        case -1:
                        case '/':
                        case '\\':
                        case '?':
                        case '#':
                            // Host info precedes.
                            int portColonOffset = portColonOffset(input, pos, componentDelimiterOffset);
                            if (portColonOffset + 1 < componentDelimiterOffset) {
                                host = input.substring(pos, portColonOffset);
                                port = parsePort(input, portColonOffset + 1, componentDelimiterOffset);
                                if (port == -1) {
                                    throw new IllegalArgumentException("Invalid URL port: \""
                                            + input.substring(portColonOffset + 1, componentDelimiterOffset) + '"');
                                }
                            } else {
                                host = input.substring(pos, portColonOffset);
                                port = defaultPort(scheme);
                            }
                            if (host == null) {
                                throw new IllegalArgumentException("Invalid URL host: \"" + input.substring(pos, portColonOffset) + '"');
                            }
                            pos = componentDelimiterOffset;
                            break authority;
                    }
                }
            } else {
                // This is a relative link. Copy over all authority components. Also maybe the path & query.
                this.encodedUsername = base.encodedUsername();
                this.encodedPassword = base.encodedPassword();
                this.host = base.host;
                this.port = base.port;
                this.encodedPathSegments.clear();
                this.encodedPathSegments.addAll(base.encodedPathSegments());
                if (pos == limit || input.charAt(pos) == '#') {
                    encodedQuery(base.encodedQuery());
                }
            }

            // Resolve the relative path.
            int pathDelimiterOffset = pathDelimiterOffset(input, pos, limit);
            resolvePath(input, pos, pathDelimiterOffset);
            pos = pathDelimiterOffset;

            // Query.
            if (pos < limit && input.charAt(pos) == '?') {
                int queryDelimiterOffset = delimiterOffset(input, pos, limit, '#');
                this.encodedQueryNamesAndValues = queryStringToNamesAndValues(encode(input, UrlToken.QUERY_ENCODED, pos + 1, queryDelimiterOffset, true, false, true, true));
                pos = queryDelimiterOffset;
            }

            // Fragment.
            if (pos < limit && input.charAt(pos) == '#') {
                this.encodedFragment = encode(input, UrlToken.FRAGMENT, pos + 1, limit, true, false, false, false);
            }

            return this;
        }

        private void resolvePath(String input, int pos, int limit) {
            // Read a delimiter.
            if (pos == limit) {
                // Empty path: keep the base path as-is.
                return;
            }
            char c = input.charAt(pos);
            if (c == '/' || c == '\\') {
                // Absolute path: reset to the default "/".
                encodedPathSegments.clear();
                encodedPathSegments.add("");
                pos++;
            } else {
                // Relative path: clear everything after the last '/'.
                encodedPathSegments.set(encodedPathSegments.size() - 1, "");
            }

            // Read path segments.
            for (int i = pos; i < limit; ) {
                int pathSegmentDelimiterOffset = pathSegmentDelimiterOffset(input, i, limit);
                boolean segmentHasTrailingSlash = pathSegmentDelimiterOffset < limit;
                push(input, i, pathSegmentDelimiterOffset, segmentHasTrailingSlash, true);
                i = pathSegmentDelimiterOffset;
                if (segmentHasTrailingSlash) {
                    i++;
                }
            }
        }

        /**
         * Adds a path segment. If the input is ".." or equivalent, this pops a path segment.
         */
        private void push(String input,
                          int pos,
                          int limit,
                          boolean addTrailingSlash,
                          boolean alreadyEncoded) {
            String segment = encode(input, UrlToken.PATH, pos, limit, alreadyEncoded, false, false, true);
            if (isDot(segment)) {
                return; // Skip '.' path segments.
            }
            if (isDotDot(segment)) {
                pop();
                return;
            }
            int currentSize = encodedPathSegments.size();
            if (encodedPathSegments.get(currentSize - 1).isEmpty()) {
                encodedPathSegments.set(currentSize - 1, segment);
            } else {
                encodedPathSegments.add(segment);
            }
            if (addTrailingSlash) {
                encodedPathSegments.add("");
            }
        }

        /**
         * Removes a path segment. When this method returns the last segment is always "", which means
         * the encoded path will have a trailing '/'.
         *
         * <p>Popping "/a/b/c/" yields "/a/b/". In this case the list of path segments goes from ["a",
         * "b", "c", ""] to ["a", "b", ""].
         *
         * <p>Popping "/a/b/c" also yields "/a/b/". The list of path segments goes from ["a", "b", "c"]
         * to ["a", "b", ""].
         */
        private void pop() {
            String removed = encodedPathSegments.remove(encodedPathSegments.size() - 1);

            // Make sure the path ends with a '/' by either adding an empty string or clearing a segment.
            if (removed.isEmpty() && !encodedPathSegments.isEmpty()) {
                encodedPathSegments.set(encodedPathSegments.size() - 1, "");
            } else {
                encodedPathSegments.add("");
            }
        }

        int effectivePort() {
            return port != -1 ? port : defaultPort(scheme);
        }

        /**
         * Re-encodes the components of this URL so that it satisfies (obsolete) RFC 2396, which is
         * particularly strict for certain components.
         */
        Builder reEncodeForUri() {
            for (int i = 0, size = encodedPathSegments.size(); i < size; i++) {
                String pathSegment = encodedPathSegments.get(i);
                encodedPathSegments.set(i,
                        encode(pathSegment, UrlToken.PATH_URI, 0, pathSegment.length(), true, true, false, true));
            }
            if (encodedQueryNamesAndValues != null) {
                for (int i = 0, size = encodedQueryNamesAndValues.size(); i < size; i++) {
                    String component = encodedQueryNamesAndValues.get(i);
                    if (component != null) {
                        encodedQueryNamesAndValues.set(i,
                                encode(component, UrlToken.QUERY_URI, 0, component.length(), true, true, true, true));
                    }
                }
            }
            if (encodedFragment != null) {
                encodedFragment = encode(encodedFragment, UrlToken.FRAGMENT_URI, 0, encodedFragment.length(), true, true, false, false);
            }
            return this;
        }
    }

    enum UrlToken {
        SCHEME,
        USERNAME,
        PASSWORD,
        HOST,
        PORT,
        PATH,
        PATH_URI, // java.net.URI specific tweaks
        QUERY,
        QUERY_RE_ENCODE,
        QUERY_ENCODED,
        QUERY_URI, // java.net.URI specific tweaks
        FRAGMENT,
        FRAGMENT_URI // java.net.URI specific tweaks
    }


    //-------------------//
    //      PARSING      //
    //-------------------//

    /**
     * Increments {@code pos} until {@code input[pos]} is not ASCII whitespace.
     */
    private static int skipUntilFirstLeadingNonWhitespace(String input) {
        for (int i = 0; i < input.length(); i++) {
            switch (input.charAt(i)) {
                case '\t':
                case '\n':
                case '\f':
                case '\r':
                case ' ':
                    continue;
                default:
                    return i;
            }
        }
        return input.length();
    }

    /**
     * Decrements {@code limit} until {@code input[limit - 1]} is not ASCII whitespace. Stops at
     * {@code pos}.
     */
    private static int skipUntilFirstTrailingNonWhitespace(String input, int pos, int limit) {
        for (int i = limit - 1; i >= pos; i--) {
            switch (input.charAt(i)) {
                case '\t':
                case '\n':
                case '\f':
                case '\r':
                case ' ':
                    continue;
                default:
                    return i + 1;
            }
        }
        return pos;
    }

    private static int slashCount(String input, int pos, int limit) {
        int slashCount = 0;
        while (pos < limit) {
            char c = input.charAt(pos);
            if (c == '\\' || c == '/') {
                slashCount++;
                pos++;
            } else {
                break;
            }
        }
        return slashCount;
    }

    private static int portColonOffset(String input, int pos, int limit) {
        for (int i = pos; i < limit; i++) {
            switch (input.charAt(i)) {
                case '[':
                    while (++i < limit) {
                        if (input.charAt(i) == ']') {
                            break;
                        }
                    }
                    break;
                case ':':
                    return i;
            }
        }
        return limit; // No colon.
    }

    private static int parsePort(String input, int pos, int limit) {
        try {
            // Canonicalize the port string to skip whitespace and non-digit characters.
            StringBuilder sb = new StringBuilder();
            while (pos < limit) {
                char c = input.charAt(pos);
                if (c >= '0' && c <= '9') {
                    sb.append(c);
                }
                pos++;
            }
            String portString = sb.toString();
            int i = Integer.parseInt(portString);
            if (i > 0 && i <= 65535) {
                return i;
            }
            return -1;
        } catch (NumberFormatException e) {
            return -1; // Invalid port.
        }
    }

    private static int defaultPort(String scheme) {
        if ("http".equalsIgnoreCase(scheme)) {
            return 80;
        } else if ("https".equalsIgnoreCase(scheme)) {
            return 443;
        } else {
            return -1;
        }
    }

    /**
     * Returns the index of the ':' in {@code input} that is after scheme characters. Returns -1 if
     * {@code input} does not have a scheme that starts at {@code pos}.
     */
    private static int schemeDelimiterOffset(String input, int pos, int limit) {
        if (limit - pos < 2) {
            return -1;
        }

        char c0 = input.charAt(pos);
        if ((c0 < 'a' || c0 > 'z') && (c0 < 'A' || c0 > 'Z')) {
            // Not a scheme start char.
            return -1;
        }

        for (int i = pos + 1; i < limit; i++) {
            char c = input.charAt(i);
            if ((c >= 'a' && c <= 'z')
                    || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9')
                    || c == '+'
                    || c == '-'
                    || c == '.') {
                continue; // valid scheme character. keep checking.
            }

            if (c == ':') {
                return i; // Scheme prefix!
            } else {
                return -1; // Non-scheme character before the first ':'.
            }
        }

        return -1; // No ':'; doesn't start with a scheme.
    }

    /**
     * Returns the index of the first character in {@code input} that contains a delimiter character
     * that might appear in authority component. Returns limit if there is no such character.
     */
    private static int authorityDelimiterOffset(String input, int pos, int limit) {
        for (int i = pos; i < limit; i++) {
            char ch = input.charAt(i);
            switch (ch) {
                case '@':
                case '/':
                case '\\':
                case '?':
                case '#':
                    return i;
            }
        }
        return limit;
    }

    /**
     * Returns the index of the first character in {@code input} that contains a delimiter character
     * that might appear in path component. Returns limit if there is no such character.
     */
    private static int pathDelimiterOffset(String input, int pos, int limit) {
        for (int i = pos; i < limit; i++) {
            char ch = input.charAt(i);
            switch (ch) {
                case '?':
                case '#':
                    return i;
            }
        }
        return limit;
    }

    private static int pathSegmentDelimiterOffset(String input, int pos, int limit) {
        for (int i = pos; i < limit; i++) {
            char ch = input.charAt(i);
            switch (ch) {
                case '/':
                case '\\':
                    return i;
            }
        }
        return limit;
    }

    /**
     * Returns the index of the first character in {@code input} that is {@code delimiter}. Returns
     * limit if there is no such character.
     */
    private static int delimiterOffset(String input, int pos, int limit, char delimiter) {
        for (int i = pos; i < limit; i++) {
            if (input.charAt(i) == delimiter) {
                return i;
            }
        }
        return limit;
    }

    /**
     * Returns the index of the first character in {@code input} that contains a character in {@code
     * delimiters}. Returns limit if there is no such character.
     */
    private static int delimiterOffset(String input, int pos, int limit, String delimiters) {
        for (int i = pos; i < limit; i++) {
            if (delimiters.indexOf(input.charAt(i)) != -1) {
                return i;
            }
        }
        return limit;
    }

    private static boolean isDot(String input) {
        return input.equals(".") || input.equalsIgnoreCase("%2e");
    }

    private static boolean isDotDot(String input) {
        switch (input) {
            case "..":
            case "%2e.":
            case "%2E.":
            case ".%2e":
            case ".%2E":
            case "%2e%2e":
            case "%2E%2E":
                return true;
            default:
                return false;
        }
    }

    /**
     * Cuts {@code encodedQuery} up into alternating parameter names and values. This divides a query
     * string like {@code subject=math&easy&problem=5-2=3} into the list {@code ["subject", "math",
     * "easy", null, "problem", "5-2=3"]}. Note that values may be null and may contain '='
     * characters.
     */
    private static List<String> queryStringToNamesAndValues(String encodedQuery) {
        List<String> result = new ArrayList<>();
        for (int pos = 0; pos <= encodedQuery.length(); ) {
            int querySeparatorOffset = encodedQuery.indexOf('&', pos);
            if (querySeparatorOffset == -1) {
                querySeparatorOffset = encodedQuery.length();
            }

            int equalsOffset = encodedQuery.indexOf('=', pos);
            if (equalsOffset == -1 || equalsOffset > querySeparatorOffset) {
                result.add(encodedQuery.substring(pos, querySeparatorOffset));
                result.add(null); // No value for this name.
            } else {
                result.add(encodedQuery.substring(pos, equalsOffset));
                result.add(encodedQuery.substring(equalsOffset + 1, querySeparatorOffset));
            }
            pos = querySeparatorOffset + 1;
        }
        return result;
    }

    private static void namesAndValuesToQueryString(StringBuilder out,
                                                    List<String> namesAndValues) {
        for (int i = 0, size = namesAndValues.size(); i < size; i += 2) {
            String name = namesAndValues.get(i);
            String value = namesAndValues.get(i + 1);
            if (i > 0) {
                out.append('&');
            }
            out.append(name);
            if (value != null) {
                out.append('=');
                out.append(value);
            }
        }
    }


    //-----------------------------------//
    //      ENCODING FUNCTIONALITIES     //
    //-----------------------------------//

    /**
     * Returns a substring of {@code input} on the range {@code [pos..limit)} with the following
     * transformations:
     * <ul>
     *   <li>Tabs, newlines, form feeds and carriage returns are skipped.
     *   <li>In queries, ' ' is encoded to '+' and '+' is encoded to "%2B".
     *   <li>Characters in {@code encodeSet} are percent-encoded.
     *   <li>Control characters and non-ASCII characters are percent-encoded.
     *   <li>All other characters are copied without transformation.
     * </ul>
     *
     * @param alreadyEncoded true to leave '%' as-is; false to convert it to '%25'.
     * @param strict         true to encode '%' if it is not the prefix of a valid percent encoding.
     * @param plusIsSpace    true to encode '+' as "%2B" if it is not already encoded.
     * @param asciiOnly      true to encode all non-ASCII codepoints.
     */
    public static String encode(String source,
                                UrlToken type,
                                int start,
                                int limit,
                                boolean alreadyEncoded,
                                boolean strict,
                                boolean plusIsSpace,
                                boolean asciiOnly) {
        if (source == null || source.length() == 0) {
            return source;
        }

        StringBuilder builder = null; // Lazily allocated.
        int codePoint;
        for (int i = start; i < limit; i += Character.charCount(codePoint)) {
            codePoint = source.codePointAt(i);
            if (codePoint < 0x20
                    || codePoint == 0x7f
                    || codePoint >= 0x80 && asciiOnly
                    || requiresEncoding(codePoint, type)
                    || codePoint == '%' && (!alreadyEncoded || strict && !percentEncoded(source, i, limit))
                    || codePoint == '+' && plusIsSpace) {
                // Uh-Oh! Slow path: the character at i requires encoding!
                builder = new StringBuilder();
                builder.append(source, start, i);
                encode(builder, source, type, i, limit, alreadyEncoded, strict, plusIsSpace, asciiOnly);
                return builder.toString();
            }
        }

        // Fast path: no characters in [pos..limit) required encoding.
        return source.substring(start, limit);
    }

    private static void encode(StringBuilder builder,
                               String input,
                               UrlToken urlTokenType,
                               int pos,
                               int limit,
                               boolean alreadyEncoded,
                               boolean strict,
                               boolean plusIsSpace,
                               boolean asciiOnly) {
        ByteBuffer encodedCharBuffer = null; // Lazily allocated.
        int codePoint;
        for (int i = pos; i < limit; i += Character.charCount(codePoint)) {
            codePoint = input.codePointAt(i);
            if (alreadyEncoded
                    && (codePoint == '\t' || codePoint == '\n' || codePoint == '\f' || codePoint == '\r')) {
                // Skip this character.
                continue;
            }

            if (codePoint == '+' && plusIsSpace) {
                // Encode '+' as '%2B' since we permit ' ' to be encoded as either '+' or '%20'.
                builder.append(alreadyEncoded ? "+" : "%2B");
            } else if (codePoint < 0x20
                    || codePoint == 0x7f
                    || codePoint >= 0x80 && asciiOnly
                    || requiresEncoding(codePoint, urlTokenType)
                    || codePoint == '%' && (!alreadyEncoded || strict && !percentEncoded(input, i, limit))) {

                // Percent encode this character.
                if (encodedCharBuffer == null) {
                    encodedCharBuffer = ByteBuffer.allocate(8);
                }

                writeUtf8CodePoint(encodedCharBuffer, codePoint);

                encodedCharBuffer.flip();

                while (encodedCharBuffer.hasRemaining()) {
                    int b = encodedCharBuffer.get() & 0xff;
                    builder.append('%');
                    builder.append(HEX_DIGITS[(b >> 4) & 0xf]);
                    builder.append(HEX_DIGITS[b & 0xf]);
                }

                encodedCharBuffer.clear();

            } else {
                // This character doesn't need encoding. Just copy it over.
                builder.append((char) codePoint);
            }
        }
    }

    private static boolean percentEncoded(String encoded, int pos, int limit) {
        return pos + 2 < limit
                && encoded.charAt(pos) == '%'
                && decodeHexDigit(encoded.charAt(pos + 1)) != -1
                && decodeHexDigit(encoded.charAt(pos + 2)) != -1;
    }

    private static String percentDecode(String encoded, boolean plusIsSpace) {
        return percentDecode(encoded, 0, encoded.length(), plusIsSpace);
    }

    private static String percentDecode(String encoded, int pos, int limit, boolean plusIsSpace) {
        for (int i = pos; i < limit; i++) {
            char c = encoded.charAt(i);
            if (c == '%' || (c == '+' && plusIsSpace)) {
                // Slow path: the character at i requires decoding!
                ByteBuffer buffer = ByteBuffer.allocate(SIZE);
                writeUtf8(encoded, pos, i, buffer);
                percentDecode(buffer, encoded, i, limit, plusIsSpace);
                return readUtf8(buffer);
            }
        }

        // Fast path: no characters in [pos..limit) required decoding.
        return encoded.substring(pos, limit);
    }

    private static void percentDecode(ByteBuffer buffer,
                                      String encoded,
                                      int pos,
                                      int limit,
                                      boolean plusIsSpace) {
        int codePoint;
        for (int i = pos; i < limit; i += Character.charCount(codePoint)) {
            codePoint = encoded.codePointAt(i);
            if (codePoint == '%' && i + 2 < limit) {
                int d1 = decodeHexDigit(encoded.charAt(i + 1));
                int d2 = decodeHexDigit(encoded.charAt(i + 2));
                if (d1 != -1 && d2 != -1) {
                    buffer.put((byte) ((d1 << 4) + d2));
                    i += 2;
                    continue;
                }
            } else if (codePoint == '+' && plusIsSpace) {
                buffer.put((byte) ' ');
                continue;
            }
            buffer.put((byte) codePoint);
        }
    }

    private static void writeUtf8(String string, int beginIndex, int endIndex, ByteBuffer buffer) {
        // Transcode a UTF-16 Java String to UTF-8 bytes.
        for (int i = beginIndex; i < endIndex; ) {
            int c = string.charAt(i);
            if (c < 0x80) {
                // Emit a 7-bit character with 1 byte.
                buffer.put((byte) c); // 0xxxxxxx
                i++;

                // Fast-path contiguous runs of ASCII characters. This is ugly, but yields a ~4x performance
                // improvement over independent character looping.
                while (i < endIndex) {
                    c = string.charAt(i);
                    if (c >= 0x80) {
                        break;
                    }
                    buffer.put((byte) c); // 0xxxxxxx
                    i++;
                }
            } else if (c < 0x800) {
                // Emit a 11-bit character with 2 bytes.
                buffer.put((byte) (c >> 6 | 0xc0)); // 110xxxxx
                buffer.put((byte) (c & 0x3f | 0x80)); // 10xxxxxx
                i++;

            } else if (c < 0xd800 || c > 0xdfff) {
                // Emit a 16-bit character with 3 bytes.
                buffer.put((byte) (c >> 12 | 0xe0)); // 1110xxxx
                buffer.put((byte) (c >> 6 & 0x3f | 0x80)); // 10xxxxxx
                buffer.put((byte) (c & 0x3f | 0x80)); // 10xxxxxx
                i++;

            } else {
                // c is a surrogate. Make sure it is a high surrogate & that its successor is a low
                // surrogate. If not, the UTF-16 is invalid, in which case we emit a replacement character.
                int low = i + 1 < endIndex ? string.charAt(i + 1) : 0;
                if (c > 0xdbff || low < 0xdc00 || low > 0xdfff) {
                    buffer.put((byte) '?');
                    i++;
                    continue;
                }

                // UTF-16 high surrogate: 110110xxxxxxxxxx (10 bits)
                // UTF-16 low surrogate:  110111yyyyyyyyyy (10 bits)
                // Unicode code point:    00010000000000000000 + xxxxxxxxxxyyyyyyyyyy (21 bits)
                int codePoint = 0x010000 + ((c & ~0xd800) << 10 | low & ~0xdc00);

                // Emit a 21-bit character with 4 bytes.
                buffer.put((byte) (codePoint >> 18 | 0xf0)); // 11110xxx
                buffer.put((byte) (codePoint >> 12 & 0x3f | 0x80)); // 10xxxxxx
                buffer.put((byte) (codePoint >> 6 & 0x3f | 0x80)); // 10xxyyyy
                buffer.put((byte) (codePoint & 0x3f | 0x80)); // 10yyyyyy
                i += 2;
            }
        }
    }

    private static void writeUtf8CodePoint(ByteBuffer buffer, int codePoint) {
        if (codePoint < 0x80) {
            // Emit a 7-bit code point with 1 byte.
            buffer.put(((byte) codePoint));

        } else if (codePoint < 0x800) {
            // Emit a 11-bit code point with 2 bytes.
            buffer.put((byte) (codePoint >> 6 | 0xc0)); // 110xxxxx
            buffer.put((byte) (codePoint & 0x3f | 0x80)); // 10xxxxxx

        } else if (codePoint < 0x10000) {
            if (codePoint >= 0xd800 && codePoint <= 0xdfff) {
                // Emit a replacement character for a partial surrogate.
                buffer.put((byte) '?');
            } else {
                // Emit a 16-bit code point with 3 bytes.
                buffer.put((byte) (codePoint >> 12 | 0xe0)); // 1110xxxx
                buffer.put((byte) (codePoint >> 6 & 0x3f | 0x80)); // 10xxxxxx
                buffer.put((byte) (codePoint & 0x3f | 0x80)); // 10xxxxxx
            }

        } else if (codePoint <= 0x10ffff) {
            // Emit a 21-bit code point with 4 bytes.
            buffer.put((byte) (codePoint >> 18 | 0xf0)); // 11110xxx
            buffer.put((byte) (codePoint >> 12 & 0x3f | 0x80)); // 10xxxxxx
            buffer.put((byte) (codePoint >> 6 & 0x3f | 0x80)); // 10xxxxxx
            buffer.put((byte) (codePoint & 0x3f | 0x80)); // 10xxxxxx

        } else {
            throw new IllegalArgumentException("Unexpected code point: " + Integer.toHexString(codePoint));
        }
    }

    private static String readUtf8(ByteBuffer buffer) {
        buffer.flip();
        return StandardCharsets.UTF_8.decode(buffer).toString();
    }

    private static int decodeHexDigit(char c) {
        if (c >= '0' && c <= '9') {
            return c - '0';
        }
        if (c >= 'a' && c <= 'f') {
            return c - 'a' + 10;
        }
        if (c >= 'A' && c <= 'F') {
            return c - 'A' + 10;
        }
        return -1;
    }

    private static int decodeHex(int b1, int b2) {
        int i1 = decodeHex(b1);
        int i2 = decodeHex(b2);
        return i1 > -1 && i2 > -1 ? i1 << 4 | i2 : -1;
    }

    private static int decodeHex(int b) {
        if (b >= '0' && b <= '9') {
            return b - '0';
        } else if (b >= 'A' && b <= 'F') {
            return b - 'A' + 0xA;
        } else if (b >= 'a' && b <= 'f') {
            return b - 'a' + 0xA;
        } else {
            return -1;
        }
    }

    /**
     * return a hex value for the supplied character.
     */
    private static int toHex(char c) {
        int hex;
        if ('0' <= c && c <= '9') {
            hex = c - '0';
        } else if ('a' <= c && c <= 'f') {
            hex = 10 + c - 'a';
        } else if ('A' <= c && c <= 'F') {
            hex = 10 + c - 'A';
        } else {
            hex = -1;
        }
        return hex;
    }

    private static boolean requiresEncoding(int c, UrlToken tokenType) {
        switch (tokenType) {
            case SCHEME:
                return !isAlpha(c) && !isDigit(c) && '+' != c && '-' != c && '.' != c;
            case USERNAME:
            case PASSWORD:
                return !isValidUsernameOrPasswordChar(c);
            case HOST:
                return !isUnreserved(c) && !isSubDelimiter(c);
            case PORT:
                return !isDigit(c);
            case PATH:
                return !isValidPathSegmentChar(c);
            case PATH_URI:
                return !isValidURIPathSegmentChar(c);
            case QUERY:
                return !isValidQueryComponentChar(c);
            case QUERY_RE_ENCODE:
                return !isValidQueryComponentReEncodeChar(c);
            case QUERY_ENCODED:
                return !isValidEncodedQueryChar(c);
            case QUERY_URI:
                return !isValidURIQueryComponentChar(c);
            case FRAGMENT:
                return false;
            case FRAGMENT_URI:
                return !isValidURIFragmentChar(c);
            default:
                // If this ever happens it is due to bug in
                return true;
        }
    }

    /**
     * whether the given character is in the {@code ALPHA} set.
     */
    protected static boolean isAlpha(int c) {
        return (c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z');
    }

    /**
     * whether the given character is in the {@code DIGIT} set.
     */
    protected static boolean isDigit(int c) {
        return (c >= '0' && c <= '9');
    }

    /**
     * whether the given character is in the {@code sub-delims} set.
     */
    protected static boolean isSubDelimiter(int c) {
        return ('!' == c || '$' == c || '&' == c || '\'' == c || '(' == c || ')' == c || '*' == c || '+' == c ||
                ',' == c || ';' == c || '=' == c);
    }

    /**
     * whether the given character is in the {@code unreserved} set.
     */
    protected static boolean isUnreserved(int c) {
        return (isAlpha(c) || isDigit(c) || '-' == c || '.' == c || '_' == c || '~' == c);
    }

    /**
     * Checks whether the provided char can be used in a username or password without encoding.
     */
    protected static boolean isValidUsernameOrPasswordChar(int c) {
        switch (c) {
            case ' ':
            case '"':
            case ':':
            case ';':
            case '<':
            case '=':
            case '>':
            case '@':
            case '[':
            case ']':
            case '^':
            case '`':
            case '{':
            case '}':
            case '|':
            case '/':
            case '\\':
            case '?':
            case '#':
                return false;
            default:
                return true;
        }
    }

    /**
     * Checks whether the provided char can be used in a pathSegment without encoding.
     */
    protected static boolean isValidPathSegmentChar(int c) {
        switch (c) {
            case ' ':
            case '"':
            case '<':
            case '>':
            case '^':
            case '`':
            case '{':
            case '}':
            case '|':
            case '/':
            case '\\':
            case '?':
            case '#':
                return false;
            default:
                return true;
        }
    }

    protected static boolean isValidURIPathSegmentChar(int c) {
        switch (c) {
            case '[':
            case ']':
                return false;
            default:
                return true;
        }
    }

    /**
     * Checks whether the provided char can be used in a pathSegment without encoding.
     */
    protected static boolean isValidEncodedQueryChar(int c) {
        switch (c) {
            case ' ':
            case '"':
            case '<':
            case '>':
            case '\'':
            case '#':
                return false;
            default:
                return true;
        }
    }

    protected static boolean isValidQueryComponentChar(int c) {
        switch (c) {
            case ' ':
            case '!':
            case '"':
            case '#':
            case '$':
            case '\'':
            case '&':
            case '(':
            case ')':
            case ',':
            case '/':
            case ':':
            case ';':
            case '<':
            case '=':
            case '>':
            case '?':
            case '@':
            case '[':
            case ']':
            case '\\':
            case '^':
            case '`':
            case '{':
            case '|':
            case '}':
            case '~':
                return false;
            default:
                return true;
        }
    }

    protected static boolean isValidQueryComponentReEncodeChar(int c) {
        switch (c) {
            case ' ':
            case '"':
            case '#':
            case '\'':
            case '&':
            case '<':
            case '=':
            case '>':
                return false;
            default:
                return true;
        }
    }

    protected static boolean isValidURIQueryComponentChar(int c) {
        switch (c) {
            case '\\':
            case '^':
            case '`':
            case '{':
            case '}':
            case '|':
                return false;
            default:
                return true;
        }
    }

    protected static boolean isValidURIFragmentChar(int c) {
        switch (c) {
            case ' ':
            case '"':
            case '#':
            case '<':
            case '>':
            case '\\':
            case '^':
            case '`':
            case '{':
            case '}':
            case '|':
                return false;
            default:
                return true;
        }
    }

    private static boolean requiresDecoding(String str) {
        for (int i = -1, limit = str.length() - 2; ++i < limit; ) {
            char c = str.charAt(i);
            if (c == '%') {
                char c1 = str.charAt(i + 1);
                char c2 = str.charAt(i + 2);
                int decoded = decodeHex(c1, c2);
                if (decoded != -1 && matchesUnreserved(decoded)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String decodeUnreserved(String str) {
        if (str.isEmpty()) {
            return str;
        }

        if (!requiresDecoding(str)) {
            // fast path
            return str;
        }

        // uh-oh! decoding is required

        byte[] bytes = str.getBytes(StandardCharsets.UTF_8);
        int p = 0;
        int i = 0;
        for (int end = bytes.length; i < end; i++) {
            byte b = bytes[i];
            if (b == '%' && i < end - 2) {
                byte b1 = bytes[i + 1];
                byte b2 = bytes[i + 2];
                int decoded = decodeHex(b1, b2);
                if (decoded > -1 && matchesUnreserved(decoded)) {
                    bytes[p++] = (byte) decoded;
                    i += 2;
                    continue;
                }
            }

            if (p != i) {
                bytes[p] = b;
            }
            p++;
        }
        return new String(bytes, 0, p, StandardCharsets.UTF_8);
    }

    private static boolean matchesUnreserved(int codePoint) {
        return codePoint >= 'a' && codePoint <= 'z'
                || codePoint >= 'A' && codePoint <= 'Z'
                || codePoint >= '0' && codePoint <= '9'
                || codePoint == '-'
                || codePoint == '.'
                || codePoint == '_'
                || codePoint == '~';
    }


    //-----------------------------------//
    //          HOST VALIDATION          //
    //-----------------------------------//

    static String canonicalizeHost(String hostName) {
        String ascii = validateAndConvertToAscii(hostName);

        if (hostName.contains(":")) {
            return hostName.startsWith("[") && hostName.endsWith("]")
                    ? checkIpv6(hostName, 1, hostName.length() - 1)
                    : checkIpv6(hostName, 0, hostName.length());
        } else {
            if (ascii.charAt(ascii.length() - 1) == '.') {
                ascii = ascii.substring(0, ascii.length() - 1);
            }
            int dot = ascii.lastIndexOf('.');
            if (dot != ascii.length() - 1
                    && (ascii.charAt(dot + 1) >= '0' && ascii.charAt(dot + 1) <= '9')) {
                hostName = checkIpv4(ascii);
            } else {
                hostName = checkDns(ascii);
            }
        }

        return hostName;
    }

    static String validateAndConvertToAscii(String hostname) {
        String ascii;
        try {
            ascii = IDN.toASCII(decodeUnreserved(hostname), IDN.ALLOW_UNASSIGNED);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("invalid hostname: " + hostname);
        }

        if (ascii.isEmpty() || ".".equals(ascii)) {
            throw new IllegalArgumentException("invalid hostname: name cannot be null or empty");
        }

        return ascii;
    }

    static String checkDns(String hostname) {
        int lastDot = -1;
        for (int i = 0; i < hostname.length(); i++) {
            char c = hostname.charAt(i);
            boolean allowable = (c >= 'a' && c <= 'z')
                    || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9')
                    || c == '-'
                    || c == '.';
            if (!allowable) {
                return null;
            } else if (c == '.') {
                if (lastDot == i - 1) {
                    return null;
                }
                lastDot = i;
            }
        }
        return hostname.toLowerCase(Locale.US);
    }

    static String checkIpv4(String hostname) {
        List<String> segments = tokenizeIpv4(hostname);
        if (segments.size() != 4) {
            return null;
        }
        byte[] address = new byte[4];
        for (int i = -1, size = segments.size(); ++i < size; ) {
            int val;
            String segment = segments.get(i);
            // Disallow leading zeroes, because no clear standard exists on
            // whether these should be interpreted as decimal or octal.
            if (segment.length() > 1 && segment.startsWith("0")) {
                return null;
            }
            try {
                val = Integer.parseInt(segment);
            } catch (NumberFormatException e) {
                return null;
            }
            if (val < 0 || val > 255) {
                return null;
            }
            address[i] = (byte) val;
        }
        return (address[0] & 0xff) + "." + (address[1] & 0xff) + "." + (address[2] & 0xff) + "." + (address[3] & 0xff);
    }

    static String checkIpv6(String ipString, int start, int end) {
        boolean hasDot = false;
        int percentIndex = -1;
        for (int i = start - 1; ++i < end; ) {
            char c = ipString.charAt(i);
            if (c == '.') {
                hasDot = true;
            } else if (c == ':') {
                if (hasDot) {
                    return null; // Colons must not appear after dots.
                }
            } else if (c == '%') {
                percentIndex = i;
                break; // everything after a '%' is ignored
            } else if (Character.digit(c, 16) == -1) {
                return null; // Everything else must be a decimal or hex digit.
            }
        }
        if (hasDot) {
            ipString = convertDottedQuadToHex(ipString);
            if (ipString == null) {
                return null;
            }
        }
        if (percentIndex != -1) {
            end = percentIndex;
        }

        // Short circuit for addresses that are empty or too small.
        switch (end - start) {
            case 0:
            case 1:
                return null;
            case 2:
                return "::".regionMatches(0, ipString, start, (end - start + 1))
                        ? fromAddress(new int[8])
                        : null;
        }

        int[] addr = new int[8];
        int addrPointer = 0;
        int compressionStarts = -1;

        // Ensure that a leading colon means there are exactly two colons,
        // in which case the address is compressed at the front.
        if (ipString.charAt(start) == ':') {
            if (ipString.charAt(start + 1) != ':') {
                return null;
            }
            if (ipString.charAt(start + 2) == ':') {
                return null;
            }
            compressionStarts = 0;
            start += 2;
        }


        // Split at each segment, interpreting the character hex values.
        for (int i = start; i < end; i++) {
            if (addrPointer == 8) {
                return null;
            }
            if (ipString.charAt(i) == ':') {
                if (compressionStarts != -1) {
                    return null;
                }
                compressionStarts = addrPointer;
                continue;
            }

            // Decode the hex segment.
            int segEnd = Math.min(i + 4, end);
            int segVal = 0;
            for (; i < segEnd; i++) {
                char c = ipString.charAt(i);
                if (c == ':') {
                    break;
                }
                int hex = toHex(c);
                if (hex == -1) {
                    return null;
                }
                segVal = segVal * 16 | hex;
            }
            addr[addrPointer++] = segVal;

            // Ensure that the ip address doesn't end in a colon.
            if (end == i) {
                break;
            } else if (ipString.charAt(i) == ':') {
                // Don't allow trailing colon.
                if (i == end - 1) {
                    return null;
                }
            } else {
                return null;
            }
        }

        // Insert the compressed zeroes.
        if (compressionStarts == -1 && addrPointer < 8 - 1) {
            return null;
        } else if (compressionStarts > -1) {
            if (addrPointer == 8) {
                return null;
            }
            for (int i = 0, limit = addrPointer - compressionStarts; ++i <= limit; ) {
                addr[8 - i] = addr[addrPointer - i];
                addr[addrPointer - i] = 0;
            }
        }

        return fromAddress(addr);
    }

    private static String fromAddress(int[] address) {
        // 1. Find where to compress. Prefer compressing on right side,
        // and only compress if more than one segment can be eliminated.
        byte[] zeroesStartingHere = {
                0, 0, 0, 0, 0, 0, 0, (byte) (address[7] == 0 ? 1 : 0)
        };
        int startCompress = 8;
        byte numCompress = 0;
        for (int i = address.length - 2; i >= 0; i--) {
            if (address[i] == 0) {
                zeroesStartingHere[i] = (byte) (zeroesStartingHere[i + 1] + 1);
                if (zeroesStartingHere[i] > 1 && zeroesStartingHere[i] >= numCompress) {
                    numCompress = zeroesStartingHere[i];
                    startCompress = i;
                }
            }
        }

        int endCompress = startCompress == 8
                ? 8
                : startCompress + zeroesStartingHere[startCompress];

        StringBuilder sb = new StringBuilder();
        if (startCompress == 0) {
            sb.append(':');
        }
        for (int i = 0; i < 8; i++) {
            if (i == startCompress) {
                sb.append(':');
                continue;
            } else if (i > startCompress && i < endCompress) {
                continue;
            }
            sb.append(Integer.toHexString(address[i]));
            if (i < address.length - 1) {
                sb.append(':');
            }
        }
        return sb.toString();
    }

    private static List<String> tokenizeIpv4(String string) {
        int off = 0;
        int next = 0;
        ArrayList<String> tokens = new ArrayList<>();
        while ((next = string.indexOf('.', off)) != -1) {
            tokens.add(string.substring(off, next));
            off = next + 1;
        }

        // If no match was found, return the same string
        if (off == 0) {
            tokens.add(string);
            return tokens;
        }

        // Add remaining segment
        tokens.add(string.substring(off));

        return tokens;
    }

    private static String convertDottedQuadToHex(String ipString) {
        int lastColon = ipString.lastIndexOf(':');
        String initialPart = ipString.substring(0, lastColon + 1);
        String dottedQuad = ipString.substring(lastColon + 1);
        byte[] quad = textToNumericFormatV4(dottedQuad);
        if (quad == null) {
            return null;
        }
        String penultimate = Integer.toHexString(((quad[0] & 0xff) << 8) | (quad[1] & 0xff));
        String ultimate = Integer.toHexString(((quad[2] & 0xff) << 8) | (quad[3] & 0xff));
        return initialPart + penultimate + ":" + ultimate;
    }

    private static byte[] textToNumericFormatV4(String ipString) {
        byte[] bytes = new byte[4];
        int i = 0;
        try {
            for (String octet : tokenizeIpv4(ipString)) {
                bytes[i++] = parseOctet(octet);
            }
        } catch (NumberFormatException ex) {
            return null;
        }

        return i == 4 ? bytes : null;
    }

    private static byte parseOctet(String ipPart) {
        // Note: we already verified that this string contains only hex digits.
        int octet = Integer.parseInt(ipPart);
        // Disallow leading zeroes, because no clear standard exists on
        // whether these should be interpreted as decimal or octal.
        if (octet > 255 || (ipPart.startsWith("0") && ipPart.length() > 1)) {
            throw new NumberFormatException();
        }
        return (byte) octet;
    }

    //-------------------//
    //     UTILITIES     //
    //-------------------//

    static <T extends CharSequence> T paramNotNull(final T cs,
                                                   final String paramName) {
        if (cs == null) {
            throw new IllegalArgumentException(String.format("%s must not be empty.", paramName));
        }
        return cs;
    }

    static <T extends CharSequence> T paramNotBlank(final T cs,
                                                    final String paramName) {
        if (cs == null || cs.length() == 0) {
            throw new IllegalArgumentException(String.format("%s must not be empty.", paramName));
        }
        return cs;
    }
}
