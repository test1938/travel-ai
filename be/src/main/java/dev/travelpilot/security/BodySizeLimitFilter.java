package dev.travelpilot.security;

import dev.travelpilot.common.error.ErrorCode;
import dev.travelpilot.common.error.GlobalExceptionHandler;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

/**
 * Rejects request bodies over the configured size (32 KB) with 400 INPUT_TOO_LONG. A declared Content-Length is
 * checked up front; chunked bodies are counted while read and fail with {@link BodyTooLargeException}, which the
 * exception handler maps to the same error.
 */
public class BodySizeLimitFilter extends OncePerRequestFilter {

    /** Thrown while reading a body that turned out to be too large. */
    public static class BodyTooLargeException extends IOException {
        public BodyTooLargeException(long limit) {
            super("Request body larger than " + limit + " bytes");
        }
    }

    private final long maxBytes;
    private final JsonMapper json = JsonMapper.builder().build();

    public BodySizeLimitFilter(long maxBytes) {
        this.maxBytes = maxBytes;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getContentLengthLong() > maxBytes) {
            response.setStatus(ErrorCode.INPUT_TOO_LONG.status().value());
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            // Outside Spring MVC: write the same ProblemDetail fields the exception handler produces.
            ProblemDetail pd = GlobalExceptionHandler.problem(ErrorCode.INPUT_TOO_LONG,
                    ErrorCode.INPUT_TOO_LONG.defaultMessage());
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("title", pd.getTitle());
            body.put("status", pd.getStatus());
            body.put("detail", pd.getDetail());
            if (pd.getProperties() != null) {
                body.putAll(pd.getProperties());
            }
            json.writeValue(response.getOutputStream(), body);
            return;
        }
        chain.doFilter(new LimitedRequest(request, maxBytes), response);
    }

    private static final class LimitedRequest extends HttpServletRequestWrapper {
        private final long limit;
        private ServletInputStream stream;

        LimitedRequest(HttpServletRequest request, long limit) {
            super(request);
            this.limit = limit;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            if (stream == null) {
                stream = new LimitedStream(super.getInputStream(), limit);
            }
            return stream;
        }
    }

    private static final class LimitedStream extends ServletInputStream {
        private final ServletInputStream delegate;
        private final long limit;
        private long read;

        LimitedStream(ServletInputStream delegate, long limit) {
            this.delegate = delegate;
            this.limit = limit;
        }

        @Override
        public int read() throws IOException {
            int b = delegate.read();
            if (b != -1) {
                count(1);
            }
            return b;
        }

        @Override
        public int read(byte[] buf, int off, int len) throws IOException {
            int n = delegate.read(buf, off, len);
            if (n > 0) {
                count(n);
            }
            return n;
        }

        private void count(int n) throws BodyTooLargeException {
            read += n;
            if (read > limit) {
                throw new BodyTooLargeException(limit);
            }
        }

        @Override
        public boolean isFinished() {
            return delegate.isFinished();
        }

        @Override
        public boolean isReady() {
            return delegate.isReady();
        }

        @Override
        public void setReadListener(ReadListener listener) {
            delegate.setReadListener(listener);
        }
    }
}
