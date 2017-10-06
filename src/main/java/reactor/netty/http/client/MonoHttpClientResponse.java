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

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Predicate;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.AsciiString;
import org.reactivestreams.Publisher;
import reactor.core.CoreSubscriber;
import reactor.core.Exceptions;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.ByteBufFlux;
import reactor.netty.ByteBufMono;
import reactor.netty.NettyInbound;
import reactor.netty.NettyOutbound;
import reactor.netty.channel.AbortedException;

/**
 * @author Stephane Maldini
 */
final class MonoHttpClientResponse extends Mono<HttpClientResponse> implements
                                                                    HttpClient.RequestSender {

	final HttpClient                                                     parent;
	final URI                                                            startURI;
	final HttpMethod                                                     method;

	static final AsciiString ALL = new AsciiString("*/*");

	MonoHttpClientResponse(HttpClient parent, HttpMethod method) {
		this.parent = parent;

		try {
			this.startURI = new URI(formatSchemeAndHost());
		}
		catch (URISyntaxException e) {
			throw Exceptions.bubble(e);
		}
		this.method = method == HttpClient.WS ? HttpMethod.GET : method;

	}

	@Override
	public HttpClient.RequestSender uri(String uri) {
		return null;
	}

	@Override
	public HttpClient.RequestSender uri(Mono<String> uri) {
		return null;
	}

	@Override
	public Mono<HttpClientResponse> response() {
		return null;
	}

	@Override
	public <V> Flux<V> response(BiFunction<? super HttpClientResponse, ? super ByteBufFlux, ? extends Publisher<? extends V>> receiver) {
		return null;
	}

	@Override
	public ByteBufFlux responseContent() {
		return null;
	}

	@Override
	public <V> Mono<V> responseSingle(BiFunction<? super HttpClientResponse, ? super ByteBufMono, ? extends Mono<? extends V>> receiver) {
		return null;
	}

	@Override
	public HttpClient.ResponseReceiver<?> send(Publisher<? extends ByteBuf> body) {
		return null;
	}

	@Override
	public HttpClient.ResponseReceiver<?> send(BiFunction<? super HttpClientRequest, ? super NettyOutbound, ? extends NettyOutbound> sender) {
		return null;
	}

	@Override
	public HttpClient.ResponseReceiver<?> sendForm(Consumer<HttpClientForm> formCallback) {
		return null;
	}

	@Override
	@SuppressWarnings("unchecked")
	public void subscribe(final CoreSubscriber<? super HttpClientResponse> subscriber) {
		ReconnectableBridge bridge = new ReconnectableBridge();
		bridge.activeURI = startURI;

		Mono.defer(() -> client.newHandler(new HttpClientHandler(this,
						bridge),
				parent.options.getRemoteAddress(bridge.activeURI),
				HttpClientBaseUrl.isSecure(bridge.activeURI),
				bridge))
		    .retry(bridge)
		    .cast(HttpClientResponse.class)
		    .subscribe(subscriber);
	}

	String formatSchemeAndHost() {
		String uri = parent.uri();

		if (!uri.startsWith(HttpClient.HTTP_SCHEME) && !uri.startsWith(HttpClient.WS_SCHEME)) {
			StringBuilder schemeBuilder = new StringBuilder();

			boolean isSecure = parent.tcpConfiguration()
			                         .isSecure();

			if (method == HttpClient.WS) {
				schemeBuilder.append(
						isSecure ? HttpClient.WSS_SCHEME : HttpClient.WS_SCHEME);
			}
			else {
				schemeBuilder.append(
						isSecure ? HttpClient.HTTPS_SCHEME : HttpClient.HTTP_SCHEME);
			}

			final String scheme = schemeBuilder.append("://").toString();
			if (uri.startsWith("/")) {
				//consider relative URL, use the base hostname/port or fallback to localhost
				SocketAddress remote = parent.tcpConfiguration().ad;

				if (remote instanceof InetSocketAddress) {
					InetSocketAddress inet = (InetSocketAddress) remote;

					return scheme + inet.getHostName() + ":" + inet.getPort() + uri;
				}
				else {
					return scheme + "localhost" + uri;
				}
			}
			else {
				//consider absolute URL
				return scheme + uri;
			}
		}
		else {
			return uri;
		}
	}

	static final class HttpClientHandler
			implements BiFunction<NettyInbound, NettyOutbound, Publisher<Void>> {

		final MonoHttpClientResponse parent;
		final ReconnectableBridge    bridge;

		HttpClientHandler(MonoHttpClientResponse parent, ReconnectableBridge bridge) {
			this.bridge = bridge;
			this.parent = parent;
		}

		@Override
		public Publisher<Void> apply(NettyInbound in, NettyOutbound out) {
			try {
				URI uri = bridge.activeURI;
				HttpClientOperations ch = (HttpClientOperations) in;
				String host = uri.getHost();
				int port = uri.getPort();
				if (port != -1 && port != 80 && port != 443) {
					host = host + ':' + port;
				}
				ch.getNettyRequest()
				  .setUri(uri.getRawPath() + (uri.getQuery() == null ? "" :
						  "?" + uri.getRawQuery()))
				  .setMethod(parent.method)
				  .setProtocolVersion(HttpVersion.HTTP_1_1)
				  .headers()
				  .add(HttpHeaderNames.HOST, host)
				  .add(HttpHeaderNames.ACCEPT, ALL);

				if (parent.method == HttpMethod.GET || parent.method == HttpMethod.HEAD) {
					ch.chunkedTransfer(false);
				}

				if (parent.handler != null) {
					return parent.handler.apply(ch);
				}
				else {
					return ch.send();
				}
			}
			catch (Throwable t) {
				return Mono.error(t);
			}
		}

		@Override
		public String toString() {
			return "HttpClientHandler{" + "startURI=" + bridge.activeURI + ", method=" + parent.method + ", handler=" + parent.handler + '}';
		}

	}

	static final class ReconnectableBridge
			implements Predicate<Throwable>, Consumer<Channel> {

		volatile URI      activeURI;
		volatile String[] redirectedFrom;

		ReconnectableBridge() {
		}

		void redirect(String to) {
			String[] redirectedFrom = this.redirectedFrom;
			URI from = activeURI;
			try {
				activeURI = new URI(to);
			}
			catch (URISyntaxException e) {
				throw Exceptions.propagate(e);
			}
			if (redirectedFrom == null) {
				this.redirectedFrom = new String[]{from.toString()};
			}
			else {
				String[] newRedirectedFrom = new String[redirectedFrom.length + 1];
				System.arraycopy(redirectedFrom,
						0,
						newRedirectedFrom,
						0,
						redirectedFrom.length);
				newRedirectedFrom[redirectedFrom.length] = from.toString();
				this.redirectedFrom = newRedirectedFrom;
			}
		}

		@Override
		public void accept(Channel channel) {
			String[] redirectedFrom = this.redirectedFrom;
			if (redirectedFrom != null) {
				channel.attr(HttpClientOperations.REDIRECT_ATTR_KEY)
				       .set(redirectedFrom);
			}
		}

		@Override
		public boolean test(Throwable throwable) {
			if (throwable instanceof RedirectClientException) {
				RedirectClientException re = (RedirectClientException) throwable;
				redirect(re.location);
				return true;
			}
			if (AbortedException.isConnectionReset(throwable)) {
				redirect(activeURI.toString());
				return true;
			}
			return false;
		}
	}


}
