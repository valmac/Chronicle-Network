package net.openhft.chronicle.network.ssl;

import net.openhft.chronicle.bytes.Bytes;
import net.openhft.chronicle.core.io.Closeable;
import net.openhft.chronicle.network.NetworkContextManager;
import net.openhft.chronicle.network.api.TcpHandler;
import net.openhft.chronicle.network.api.session.SessionDetailsProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SslDelegatingTcpHandler<N extends SslNetworkContext>
        implements TcpHandler<N>, NetworkContextManager<N> {
    private static final Logger LOGGER = LoggerFactory.getLogger(SslDelegatingTcpHandler.class);

    private final TcpHandler<N> delegate;
    private final BytesBufferHandler<N> bufferHandler = new BytesBufferHandler<>();
    private SslEngineStateMachine stateMachine;
    private boolean handshakeComplete;

    SslDelegatingTcpHandler(final TcpHandler<N> delegate) {
        this.delegate = delegate;
    }

    @Override
    public void process(@NotNull final Bytes in, @NotNull final Bytes out, final N nc) {
        if (!handshakeComplete) {
            try {
                doHandshake(nc);
            } catch (Throwable t) {
                LOGGER.error("Failed to complete SSL handshake", t);
                throw new IllegalStateException("Unable to perform handshake", t);
            }
            handshakeComplete = true;
        }

        bufferHandler.set(delegate, in, out, nc);
        stateMachine.action();
    }

    private void doHandshake(final N nc) {
        stateMachine = new SslEngineStateMachine(bufferHandler, nc.isAcceptor());
        stateMachine.initialise(nc.sslContext(), nc.socketChannel());
    }

    @Override
    public void sendHeartBeat(final Bytes out, final SessionDetailsProvider sessionDetails) {
        delegate.sendHeartBeat(out, sessionDetails);
    }

    @Override
    public void onEndOfConnection(final boolean heartbeatTimeOut) {
        delegate.onEndOfConnection(heartbeatTimeOut);
    }

    @Override
    public void close() {
        if (stateMachine != null) {
            stateMachine.close();
        }
        delegate.close();
    }

    @Override
    public void onReadTime(final long readTimeNS) {
        delegate.onReadTime(readTimeNS);
    }

    @Override
    public void onWriteTime(final long writeTimeNS) {
        delegate.onWriteTime(writeTimeNS);
    }

    @Override
    public void onReadComplete() {
        delegate.onReadComplete();
    }

    @Override
    public boolean hasClientClosed() {
        return delegate.hasClientClosed();
    }

    public static void closeQuietly(@NotNull final Object... closables) {
        Closeable.closeQuietly(closables);
    }

    public static void closeQuietly(@Nullable final Object o) {
        Closeable.closeQuietly(o);
    }

    @Override
    public void notifyClosing() {
        delegate.notifyClosing();
    }

    @Override
    public boolean isClosed() {
        return delegate.isClosed();
    }

    @Override
    public N nc() {
        return (delegate instanceof NetworkContextManager) ? ((NetworkContextManager<N>) delegate).nc() : null;
    }

    @Override
    public void nc(final N nc) {
        if (delegate instanceof NetworkContextManager) {
            ((NetworkContextManager<N>) delegate).nc(nc);
        }
    }
}