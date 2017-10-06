/*
 * Copyright (c) 2011-2017 Pivotal Software Inc, All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package reactor.netty.http.client;

import java.util.function.Consumer;

import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.cookie.Cookie;
import reactor.netty.Connection;
import reactor.netty.http.HttpInfos;

/**
 * An Http Reactive client write contract for outgoing requests. It inherits several
 * accessor related to HTTP flow : headers, params, URI, method, websocket...
 *
 * @author Stephane Maldini
 * @author Simon Baslé
 */
public interface HttpClientRequest extends HttpInfos {

	/**
	 * Add an outbound cookie
	 *
	 * @return this outbound
	 */
	HttpClientRequest addCookie(Cookie cookie);

	/**
	 * Add an outbound http header, appending the value if the header is already set.
	 *
	 * @param name header name
	 * @param value header value
	 *
	 * @return this outbound
	 */
	HttpClientRequest addHeader(CharSequence name, CharSequence value);

	/**
	 * Set transfer-encoding header
	 *
	 * @param chunked true if transfer-encoding:chunked
	 *
	 * @return this outbound
	 */
	HttpClientRequest chunkedTransfer(boolean chunked);

	/**
	 * Enable http status 302 auto-redirect support
	 *
	 * @return {@literal this}
	 */
	HttpClientRequest followRedirect();

	/**
	 * Toggle the request to fail in case of a client-side error.
	 *
	 * @param shouldFail true if the request should fail in case of client errors.
	 * @return {@literal this}
	 */
	HttpClientRequest failOnClientError(boolean shouldFail);

	/**
	 * Toggle the request to fail in case of a server-side error.
	 *
	 * @param shouldFail true if the request should fail in case of server errors.
	 * @return {@literal this}
	 */
	HttpClientRequest failOnServerError(boolean shouldFail);

	/**
	 * Return  true if headers and status have been sent to the server
	 *
	 * @return true if headers and status have been sent to the server
	 */
	boolean isSent();

	/**
	 * Set an outbound header, replacing any pre-existing value.
	 *
	 * @param name headers key
	 * @param value header value
	 *
	 * @return this outbound
	 */
	HttpClientRequest header(CharSequence name, CharSequence value);

	/**
	 * Set outbound headers from the passed headers. It will however ignore {@code
	 * HOST} header key. Any pre-existing value for the passed headers will be replaced.
	 *
	 * @param headers a netty headers map
	 *
	 * @return this outbound
	 */
	HttpClientRequest headers(HttpHeaders headers);

	/**
	 * Return true  if redirected will be followed
	 *
	 * @return true if redirected will be followed
	 */
	boolean isFollowRedirect();

	/**
	 * set the request keepAlive if true otherwise removeConfiguration the existing connection keep alive header
	 *
	 * @return this outbound
	 */
	HttpClientRequest keepAlive(boolean keepAlive);

	/**
	 * Return the previous redirections or empty array
	 *
	 * @return the previous redirections or empty array
	 */
	String[] redirectedFrom();

	/**
	 * Return outbound headers to be sent
	 *
	 * @return outbound headers to be sent
	 */
	HttpHeaders requestHeaders();

	/**
	 * Call the given consumer when a connection is available
	 *
	 * @param connectionHandler callback on available connection
	 *
	 * @return this request
	 */
	HttpClientRequest withConnection(Consumer<? super Connection> connectionHandler);

}