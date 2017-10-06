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

package reactor.netty.tcp;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import io.netty.bootstrap.AbstractBootstrap;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.SslHandshakeCompletionEvent;
import io.netty.resolver.DefaultAddressResolverGroup;
import io.netty.resolver.NoopAddressResolverGroup;
import io.netty.util.NetUtil;
import reactor.netty.NettyPipeline;
import reactor.netty.channel.BootstrapHandlers;
import reactor.netty.channel.ChannelOperations;
import reactor.util.Logger;
import reactor.util.Loggers;

/**
 * @author Stephane Maldini
 */
final class TcpUtils {

	static Bootstrap updateProxySupport(Bootstrap b, ProxyProvider proxyOptions) {
		BootstrapHandlers.updateConfiguration(b, NettyPipeline.ProxyHandler, channel -> {
			if (proxyOptions.shouldProxy(b.config()
			                              .remoteAddress())) {

				channel.pipeline()
				       .addFirst(NettyPipeline.ProxyHandler,
						       proxyOptions.newProxyHandler());

				if (log.isDebugEnabled()) {
					channel.pipeline()
					       .addFirst(new LoggingHandler("reactor.ipc.netty.proxy"));
				}
			}
		});

		if (b.config()
		     .resolver() == DefaultAddressResolverGroup.INSTANCE) {
			return b.resolver(NoopAddressResolverGroup.INSTANCE);
		}
		return b;
	}

	static ServerBootstrap updateSslSupport(ServerBootstrap b,
			SslProvider sslProvider) {

		BootstrapHandlers.updateConfiguration(b,
				NettyPipeline.SslHandler,
				new SslSupportConsumer(sslProvider, b));

		return b;
	}

	static Bootstrap updateSslSupport(Bootstrap b,
			SslProvider sslProvider) {

		BootstrapHandlers.updateConfiguration(b,
				NettyPipeline.SslHandler,
				new SslSupportConsumer(sslProvider, b));

		return b;
	}

	@Nullable
	static SslContext findSslContext(Bootstrap b) {
		SslSupportConsumer c =
				BootstrapHandlers.findConfiguration(SslSupportConsumer.class,
						b.config()
						 .handler());

		return c != null ? c.sslProvider.getSslContext() : null;
	}

	@Nullable
	static SslContext findSslContext(ServerBootstrap b) {
		SslSupportConsumer c =
				BootstrapHandlers.findConfiguration(SslSupportConsumer.class,
						b.config()
						 .childHandler());

		return c != null ? c.sslProvider.getSslContext() : null;
	}

	@SuppressWarnings("unchecked")
	static String getHost(ServerBootstrap b) {
		if (b.config()
		     .localAddress() instanceof InetSocketAddress) {
			return ((InetSocketAddress) b.config()
			                             .localAddress()).getHostName();
		}
		return NetUtil.LOCALHOST.getHostName();
	}

	@SuppressWarnings("unchecked")
	static String getHost(Bootstrap b) {
		if (b.config()
		     .remoteAddress() instanceof InetSocketAddress) {
			return ((InetSocketAddress) b.config()
			                             .remoteAddress()).getHostName();
		}
		return NetUtil.LOCALHOST.getHostName();
	}

	@SuppressWarnings("unchecked")
	static int getPort(Bootstrap b) {
		if (b.config()
		     .remoteAddress() instanceof InetSocketAddress) {
			return ((InetSocketAddress) b.config()
			                             .remoteAddress()).getPort();
		}
		return DEFAULT_PORT;
	}

	static Bootstrap removeProxySupport(Bootstrap b) {
		BootstrapHandlers.removeConfiguration(b, NettyPipeline.ProxyHandler);
		return b;
	}

	static Bootstrap removeSslSupport(Bootstrap b) {
		BootstrapHandlers.removeConfiguration(b, NettyPipeline.SslHandler);
		return b;
	}

	static ServerBootstrap removeSslSupport(ServerBootstrap b) {
		BootstrapHandlers.removeConfiguration(b, NettyPipeline.SslHandler);
		return b;
	}

	static final class SslSupportConsumer implements Consumer<Channel> {

		final SslProvider sslProvider;
		final InetSocketAddress sniInfo;

		SslSupportConsumer(SslProvider sslProvider,
				AbstractBootstrap b) {
			this.sslProvider = sslProvider;

			if (b instanceof Bootstrap) {
				SocketAddress sniInfo = ((Bootstrap) b).config()
				                                       .remoteAddress();

				if (sniInfo instanceof InetSocketAddress) {
					this.sniInfo = (InetSocketAddress) sniInfo;
				}
				else {
					this.sniInfo = null;
				}

			}
			else {
				sniInfo = null;
			}
		}

		@Override
		public void accept(Channel channel) {
			SslHandler sslHandler;

			if (sniInfo != null) {
				sslHandler = sslProvider.getSslContext().newHandler(channel.alloc(),
						sniInfo.getHostName(),
						sniInfo.getPort());

				if (log.isDebugEnabled()) {
					log.debug("SSL enabled using engine {} and SNI {}",
							sslHandler.engine()
							          .getClass()
							          .getSimpleName(),
							sniInfo);
				}
			}
			else {
				sslHandler = sslProvider.getSslContext().newHandler(channel.alloc());

				if (log.isDebugEnabled()) {
					log.debug("SSL enabled using engine {}",
							sslHandler.engine()
							          .getClass()
							          .getSimpleName());
				}
			}

			sslProvider.configure(sslHandler);

			if (channel.pipeline()
			           .get(NettyPipeline.ProxyHandler) != null) {
				channel.pipeline()
				       .addAfter(NettyPipeline.ProxyHandler,
						       NettyPipeline.SslHandler,
						       sslHandler);
			}
			else {
				channel.pipeline()
				       .addFirst(NettyPipeline.SslHandler, sslHandler);
			}

			channel.pipeline()
			       .addAfter(NettyPipeline.SslHandler,
					       NettyPipeline.SslReader,
					       new SslReadHandler());
		}
	}

	static final class SslReadHandler extends ChannelInboundHandlerAdapter {

		boolean handshakeDone;

		@Override
		public void channelActive(ChannelHandlerContext ctx) throws Exception {
			ctx.read(); //consume handshake
		}

		@Override
		public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
			if (!handshakeDone) {
				ctx.read(); /* continue consuming. */
			}
			super.channelReadComplete(ctx);
		}

		@Override
		public void userEventTriggered(ChannelHandlerContext ctx, Object evt)
				throws Exception {
			if (evt instanceof SslHandshakeCompletionEvent) {
				handshakeDone = true;
				if (ctx.pipeline()
				       .context(this) != null) {
					ctx.pipeline()
					   .remove(this);
				}
				SslHandshakeCompletionEvent handshake = (SslHandshakeCompletionEvent) evt;
				if (handshake.isSuccess()) {
					ctx.fireChannelActive();
				}
				else {
					ctx.fireExceptionCaught(handshake.cause());
				}
			}
			super.userEventTriggered(ctx, evt);
		}
	}

	/**
	 * The default port for reactor-netty servers. Defaults to 12012 but can be tuned via
	 * the {@code PORT} <b>environment variable</b>.
	 */
	static final int                            DEFAULT_PORT =
			System.getenv("PORT") != null ? Integer.parseInt(System.getenv("PORT")) :
					12012;

	static final Logger                                         log           =
			Loggers.getLogger(TcpUtils.class);

	static final ChannelOperations.OnSetup TCP_OPS =
			(connection, events, msg) -> ChannelOperations.bind(connection, events);

	static final Consumer<SslProvider.SslContextSpec> SSL_DEFAULT_SPEC =
			sslProviderBuilder -> sslProviderBuilder.sslContext(TcpServerSecure.DEFAULT_SSL_CONTEXT);
}