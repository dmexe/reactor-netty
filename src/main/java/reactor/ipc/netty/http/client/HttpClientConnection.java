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

package reactor.ipc.netty.http.client;

import java.util.Objects;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.EventLoopGroup;
import io.netty.handler.ssl.JdkSslContext;
import reactor.core.publisher.Mono;
import reactor.ipc.netty.Connection;
import reactor.ipc.netty.http.HttpResources;
import reactor.ipc.netty.resources.LoopResources;
import reactor.ipc.netty.tcp.TcpClient;

/**
 * @author Stephane Maldini
 */
final class HttpClientConnection extends HttpClient {

	static final HttpClientConnection INSTANCE = new HttpClientConnection();

	final TcpClient tcpClient;

	HttpClientConnection() {
		this(DEFAULT_TCP_CLIENT);
	}

	HttpClientConnection(TcpClient tcpClient) {
		this.tcpClient = Objects.requireNonNull(tcpClient, "tcpClient");
	}

	@Override
	protected Mono<? extends Connection> connect(Bootstrap b) {
		if (b.config()
		     .group() == null) {
			LoopResources loops = HttpResources.get();

			boolean useNative = LoopResources.DEFAULT_NATIVE && !(tcpClient.sslContext() instanceof JdkSslContext);

			EventLoopGroup elg = loops.onClient(useNative);

			b.group(elg)
			 .channel(loops.onChannel(elg));
		}
		return tcpClient.connect(b);
	}

	@Override
	protected TcpClient configureTcp() {
		return tcpClient;
	}
}