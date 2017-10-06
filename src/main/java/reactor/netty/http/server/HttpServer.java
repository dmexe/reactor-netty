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

package reactor.netty.http.server;

import java.time.Duration;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.annotation.Nullable;

import io.netty.bootstrap.ServerBootstrap;
import org.reactivestreams.Publisher;
import reactor.core.Exceptions;
import reactor.core.publisher.Mono;
import reactor.netty.Connection;
import reactor.netty.ConnectionEvents;
import reactor.netty.DisposableServer;
import reactor.netty.channel.BootstrapHandlers;
import reactor.netty.channel.ChannelOperations;
import reactor.netty.tcp.TcpServer;

/**
 * An HttpServer allows to build in a safe immutable way an http server that is
 * materialized and connecting when {@link #bind(ServerBootstrap)} is ultimately called.
 * <p> Internally, materialization happens in three phases, first {@link
 * #tcpConfiguration()} is called to retrieve a ready to use {@link TcpServer}, then
 * {@link TcpServer#configure()} retrieve a usable {@link ServerBootstrap} for the final
 * {@link #bind(ServerBootstrap)} is called. <p> Examples:
 * <pre>
 * {@code
 * HttpServer.create()
 * .host("0.0.0.0")
 * .tcpConfiguration(TcpServer::secure)
 * .handler((req, res) -> res.sendString(Flux.just("hello"))
 * .bind()
 * .block();
 * }
 * }
 *
 * @author Stephane Maldini
 */
public abstract class HttpServer {

	/**
	 * Prepare a pooled {@link HttpServer}
	 *
	 * @return a {@link HttpServer}
	 */
	public static HttpServer create() {
		return HttpServerBind.INSTANCE;
	}

	/**
	 * Prepare a pooled {@link HttpServer}
	 *
	 * @return a {@link HttpServer}
	 */
	public static HttpServer from(TcpServer tcpServer) {
		return new HttpServerBind(tcpServer);
	}

	/**
	 * Bind the {@link HttpServer} and return a {@link Mono} of {@link Connection}. If
	 * {@link Mono} is cancelled, the underlying binding will be aborted. Once the {@link
	 * Connection} has been emitted and is not necessary anymore, disposing main server
	 * loop must be done by the user via {@link Connection#dispose()}.
	 *
	 * If updateConfiguration phase fails, a {@link Mono#error(Throwable)} will be returned;
	 *
	 * @return a {@link Mono} of {@link Connection}
	 */
	public final Mono<? extends DisposableServer> bind() {
		ServerBootstrap b;
		try{
			b = tcpConfiguration().configure();
		}
		catch (Throwable t){
			Exceptions.throwIfFatal(t);
			return Mono.error(t);
		}
		return bind(b);
	}

	/**
	 * Start a Server in a blocking fashion, and wait for it to finish initializing. The
	 * returned {@link DisposableServer} offers simple server API, including to {@link
	 * DisposableServer#disposeNow()} shut it down in a blocking fashion.
	 *
	 * @return a {@link DisposableServer}
	 */
	public final DisposableServer bindNow() {
		return bindNow(Duration.ofSeconds(45));
	}


	/**
	 * Start a Server in a blocking fashion, and wait for it to finish initializing. The
	 * returned {@link DisposableServer} offers simple server API, including to {@link
	 * DisposableServer#disposeNow()} shut it down in a blocking fashion.
	 *
	 * @param timeout max startup timeout
	 *
	 * @return a {@link DisposableServer}
	 */
	public final DisposableServer bindNow(Duration timeout) {
		Objects.requireNonNull(timeout, "timeout");
		return Objects.requireNonNull(bind().block(timeout), "aborted");
	}

	/**
	 * Start a Server in a fully blocking fashion, not only waiting for it to initialize
	 * but also blocking during the full lifecycle of the client/server. Since most
	 * servers will be long-lived, this is more adapted to running a server out of a main
	 * method, only allowing shutdown of the servers through sigkill.
	 * <p>
	 * Note that a {@link Runtime#addShutdownHook(Thread) JVM shutdown hook} is added by
	 * this method in order to properly disconnect the client/server upon receiving a
	 * sigkill signal.
	 *
	 * @param timeout a timeout for server shutdown
	 * @param onStart an optional callback on server start
	 */
	public final void bindUntilJavaShutdown(Duration timeout,
			@Nullable Consumer<DisposableServer> onStart) {

		Objects.requireNonNull(timeout, "timeout");
		DisposableServer facade = bindNow();

		Objects.requireNonNull(facade, "facade");

		if (onStart != null) {
			onStart.accept(facade);
		}
		Runtime.getRuntime()
		       .addShutdownHook(new Thread(() -> facade.disposeNow(timeout)));

		facade.onDispose()
		      .block();
	}

	/**
	 * Enable GZip response compression if the client request presents accept encoding
	 * headers.
	 *
	 * @return a new {@link HttpServer}
	 */
	public final HttpServer compress() {
		return tcpConfiguration(COMPRESS_ATTR_CONFIG);
	}

	/**
	 * Enable GZip response compression if the client request presents accept encoding
	 * headers
	 * AND the response reaches a minimum threshold
	 *
	 * @param minResponseSize compression is performed once response size exceeds given
	 * value in byte
	 *
	 * @return a new {@link HttpServer}
	 */
	public final HttpServer compress(int minResponseSize) {
		if (minResponseSize < 0) {
			throw new IllegalArgumentException("minResponseSize must be positive");
		}
		return tcpConfiguration(tcp -> tcp.attr(HttpServerOperations.PRODUCE_GZIP, minResponseSize));
	}

	/**
	 * Attach an IO handler to react on connected server
	 *
	 * @param handler an IO handler that can dispose underlying connection when {@link
	 * Publisher} terminates. Only the first registered handler will subscribe to the
	 * returned {@link Publisher} while other will immediately cancel given a same
	 * {@link Connection}
	 *
	 * @return a new {@link HttpServer}
	 */
	@SuppressWarnings("unchecked")
	public final HttpServer handler(BiFunction<? super HttpServerRequest, ? super HttpServerResponse, ? extends Publisher<Void>> handler) {
		Objects.requireNonNull(handler, "handler");
		return tcpConfiguration(tcp -> tcp.handler((inp, out) -> handler.apply((HttpServerRequest) inp,
				(HttpServerResponse) out)));
	}

	/**
	 * Disable gzip compression
	 *
	 * @return a new {@link HttpServer}
	 */
	public final HttpServer noCompression() {
		return tcpConfiguration(COMPRESS_ATTR_DISABLE);
	}

	/**
	 * Apply {@link ServerBootstrap} configuration given mapper taking currently
	 * configured one and returning a new one to be ultimately used for socket binding.
	 * <p> Configuration will apply during {@link #tcpConfiguration()} phase.
	 *
	 * @param tcpMapper A tcpServer mapping function to update tcp configuration and
	 * return an enriched tcp server to use.
	 *
	 * @return a new {@link HttpServer}
	 */
	public final HttpServer tcpConfiguration(Function<? super TcpServer, ? extends TcpServer> tcpMapper) {
		return new HttpServerTcpConfig(this, tcpMapper);
	}

	/**
	 * Define routes for the server through the provided {@link HttpServerRoutes} builder.
	 *
	 * @param routesBuilder provides a route builder to be mutated in order to define routes.
	 * @return a new {@link HttpServer} starting the router on subscribe
	 */
	public final HttpServer router(Consumer<? super HttpServerRoutes>
			routesBuilder) {
		Objects.requireNonNull(routesBuilder, "routeBuilder");
		HttpServerRoutes routes = HttpServerRoutes.newRoutes();
		routesBuilder.accept(routes);
		return handler(routes);
	}

	/**
	 * Bind the {@link HttpServer} and return a {@link Mono} of {@link Connection}
	 *
	 * @param b the {@link ServerBootstrap} to bind
	 *
	 * @return a {@link Mono} of {@link Connection}
	 */
	protected abstract Mono<? extends DisposableServer> bind(ServerBootstrap b);

	/**
	 * Materialize a TcpServer from the parent {@link HttpServer} chain to use with
	 * {@link #bind(ServerBootstrap)} or separately
	 *
	 * @return a configured {@link TcpServer}
	 */
	protected TcpServer tcpConfiguration() {
		return DEFAULT_TCP_SERVER;
	}

	static final ChannelOperations.OnSetup HTTP_OPS = new ChannelOperations.OnSetup() {
		@Override
		public ChannelOperations<?, ?> create(Connection c,
				ConnectionEvents listener,
				Object msg) {
			return HttpServerOperations.bindHttp(c, listener, msg);
		}

		@Override
		public boolean createOnConnected() {
			return false;
		}
	};

	static final Function<ServerBootstrap, ServerBootstrap> HTTP_OPS_CONF = b -> {
		BootstrapHandlers.channelOperationFactory(b, HTTP_OPS);
		return b;
	};

	static final int DEFAULT_PORT =
			System.getenv("PORT") != null ? Integer.parseInt(System.getenv("PORT")) : 8080;

	static final TcpServer DEFAULT_TCP_SERVER = TcpServer.create()
	                                                     .host("0.0.0.0")
	                                                     .port(DEFAULT_PORT)
	                                                     .bootstrap(HTTP_OPS_CONF);

	static final Function<TcpServer, TcpServer> COMPRESS_ATTR_CONFIG =
			tcp -> tcp.attr(HttpServerOperations.PRODUCE_GZIP, 0);

	static final Function<TcpServer, TcpServer> COMPRESS_ATTR_DISABLE =
			tcp -> tcp.attr(HttpServerOperations.PRODUCE_GZIP, null);
}