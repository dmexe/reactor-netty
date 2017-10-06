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

package reactor.netty.http;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import reactor.core.publisher.Mono;
import reactor.netty.FutureMono;
import reactor.netty.Connection;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.client.HttpClientResponse;
import reactor.netty.http.server.HttpServer;

/**
 * @author tokuhirom
 */
public class HttpErrorTests {

	@Test
	public void test() {
		Connection server = HttpServer.create(0)
		                              .newRouter(httpServerRoutes -> httpServerRoutes.get(
				                                "/",
				                                (httpServerRequest, httpServerResponse) -> {
					                                return httpServerResponse.sendString(
							                                Mono.error(new IllegalArgumentException()));
				                                }))
		                              .block(Duration.ofSeconds(30));

		HttpClient client = HttpClient.create(opt -> opt.host("localhost")
		                                                .port(server.address().getPort())
		                                                .disablePool());

		HttpClientResponse r = client.get("/")
		                             .block(Duration.ofSeconds(30));

		List<String> result = r.receive()
		                    .asString(StandardCharsets.UTF_8)
		                    .collectList()
		                    .block(Duration.ofSeconds(30));

		System.out.println("END");

		FutureMono.from(r.context().channel().closeFuture()).block(Duration.ofSeconds(30));

		Assert.assertTrue(result.isEmpty());
		Assert.assertTrue(r.isDisposed());
		server.dispose();
	}
}