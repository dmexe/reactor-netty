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

package reactor.netty;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.Connection;
import reactor.netty.DisposableChannel;
import reactor.netty.DisposableServer;
import reactor.netty.NettyPipeline;
import reactor.netty.http.server.HttpServer;
import reactor.netty.tcp.TcpClient;
import reactor.netty.tcp.TcpServer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

public class DisposableChannelTest {

	@Test
	public void simpleServerFromAsyncServer() throws InterruptedException {
		DisposableServer simpleServer =
				TcpServer.create()
				         .handler((in, out) -> out
						         .options(NettyPipeline.SendOptions::flushOnEach)
						         .sendString(
								         in.receive()
								           .asString()
								           .takeUntil(s -> s.endsWith("CONTROL"))
								           .map(s -> "ECHO: " + s.replaceAll("CONTROL", ""))
								           .concatWith(Mono.just("DONE"))
						         )
						         .neverComplete()
				         )
				         .bindNow();

		System.out.println(simpleServer.host());
		System.out.println(simpleServer.port());

		AtomicReference<List<String>> data1 = new AtomicReference<>();
		AtomicReference<List<String>> data2 = new AtomicReference<>();

		Connection simpleClient1 =
				TcpClient.create()
				         .port(simpleServer.port())
				         .handler((in, out) -> out.options(NettyPipeline
						         .SendOptions::flushOnEach)
				                                .sendString(Flux.just("Hello", "World", "CONTROL"))
				                                .then(in.receive()
				                                        .asString()
				                                        .takeUntil(s -> s.endsWith("DONE"))
				                                        .map(s -> s.replaceAll("DONE", ""))
				                                        .filter(s -> !s.isEmpty())
				                                        .collectList()
				                                        .doOnNext(data1::set)
				                                        .doOnNext(System.err::println)
				                                        .then()))
				         .connectNow();

		Connection simpleClient2 =
				TcpClient.create()
				         .port(simpleServer.port())
				         .handler((in, out) -> out.options(NettyPipeline.SendOptions::flushOnEach)
				                                .sendString(Flux.just("How", "Are", "You?", "CONTROL"))
				                                .then(in.receive()
				                                        .asString()
				                                        .takeUntil(s -> s.endsWith("DONE"))
				                                        .map(s -> s.replaceAll("DONE", ""))
				                                        .filter(s -> !s.isEmpty())
				                                        .collectList()
				                                        .doOnNext(data2::set)
				                                        .doOnNext(System.err::println)
				                                        .then()))
				         .connectNow();

		Thread.sleep(100);
		System.err.println("STOPPING 1");
		simpleClient1.disposeNow();

		System.err.println("STOPPING 2");
		simpleClient2.disposeNow();

		System.err.println("STOPPING SERVER");
		simpleServer.disposeNow();

		assertThat(data1.get())
				.allSatisfy(s -> assertThat(s).startsWith("ECHO: "));
		assertThat(data2.get())
				.allSatisfy(s -> assertThat(s).startsWith("ECHO: "));

		assertThat(data1.get()
		                .toString()
		                .replaceAll("ECHO: ", "")
		                .replaceAll(", ", ""))
				.isEqualTo("[HelloWorld]");
		assertThat(data2.get()
		                .toString()
		                .replaceAll("ECHO: ", "")
		                .replaceAll(", ", ""))
		.isEqualTo("[HowAreYou?]");
	}

	@Test
	public void testTimeoutOnStart() {
		TcpServer server = new TcpServer() {
			@Override
			public Mono<? extends DisposableServer> bind(ServerBootstrap b) {
				return Mono.never();
			}
		};

		assertThatExceptionOfType(RuntimeException.class)
				.isThrownBy(() -> server.bindNow(Duration.ofMillis(100)))
				.withCauseExactlyInstanceOf(TimeoutException.class)
				.withMessage("java.util.concurrent.TimeoutException: couldn't be started within 100ms");
	}

	@Test
	public void testHttpTimeoutOnStart() {
		HttpServer server = new HttpServer() {
			@Override
			protected Mono<? extends DisposableServer> bind(ServerBootstrap b) {
				return Mono.never();
			}
		};

		assertThatExceptionOfType(RuntimeException.class)
				.isThrownBy(() -> server.bindNow(Duration.ofMillis(100)))
				.withCauseExactlyInstanceOf(TimeoutException.class)
				.withMessage("java.util.concurrent.TimeoutException: couldn't be started within 100ms");
	}

	@Test
	public void testTimeoutOnStop() {
		final DisposableChannel neverStop = EmbeddedChannel::new;

		assertThatExceptionOfType(RuntimeException.class)
				.isThrownBy(() -> neverStop.disposeNow(Duration.ofMillis(100)))
				.withCauseExactlyInstanceOf(TimeoutException.class)
				.withMessage("java.util.concurrent.TimeoutException: couldn't be stopped within 100ms");
	}

	@Test
	public void getContextAddressAndHost() {
		EmbeddedChannel c = new EmbeddedChannel();
		DisposableChannel facade = () -> c;

		assertThat(facade.channel()).isSameAs(c);
		assertThat(facade.address()).isEqualTo(c.remoteAddress());
	}
}