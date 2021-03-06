package com.github.kristofa.brave.jersey;

import com.github.kristofa.brave.ClientTracer;
import com.github.kristofa.brave.EndPointSubmitter;
import com.github.kristofa.brave.ServerTracer;
import com.github.kristofa.brave.SpanId;
import com.github.kristofa.brave.client.ClientRequestHeaders;
import com.google.common.base.Optional;
import com.sun.jersey.api.client.ClientRequest;
import com.sun.jersey.core.util.StringKeyObjectValueIgnoreCaseMultivaluedMap;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.runners.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

import javax.servlet.FilterChain;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.MultivaluedMap;
import java.net.URI;

import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ClientServletCompatibilityTest {

    @Mock
    ClientTracer clientTracer;
    @Mock
    ServerTracer serverTracer;
    @Mock
    EndPointSubmitter endPointSubmitter;
    @Mock
    FilterChain filterChain;
    @Mock
    ClientRequest clientRequest;
    @Mock
    HttpServletRequest servletRequest;
    @Mock
    ServletResponse servletResponse;

    JerseyClientTraceFilter clientFilter;
    ServletTraceFilter servletFilter;

    @Before
    public void setUp() {
        clientFilter = new JerseyClientTraceFilter(clientTracer, Optional.<String>absent());
        servletFilter = new ServletTraceFilter(serverTracer, endPointSubmitter);
    }

    @Test
    public void shouldHandleAllProvidedIds() throws Exception {
        validateUsingSpan(mockSpan(-123L, 456L, 789L));
    }

    @Test
    public void shouldHandleMissingParentId() throws Exception {
        validateUsingSpan(mockSpan(-123L, 456L, null));
    }

    private SpanId mockSpan(long traceId, long spanId, Long parentSpanId) {
        SpanId mockedSpan = mock(SpanId.class);
        when(mockedSpan.getTraceId()).thenReturn(traceId);
        when(mockedSpan.getSpanId()).thenReturn(spanId);
        when(mockedSpan.getParentSpanId()).thenReturn(parentSpanId);
        return mockedSpan;
    }

    private void validateUsingSpan(SpanId span) throws Exception {
        when(clientRequest.getURI()).thenReturn(URI.create("http://testuri.com/path"));
        when(clientRequest.getHeaders()).thenReturn(new StringKeyObjectValueIgnoreCaseMultivaluedMap());
        when(clientTracer.startNewSpan(anyString())).thenReturn(span);

        ClientRequestHeaders.addTracingHeaders(new JerseyClientRequestAdapter(clientRequest), span);

        final MultivaluedMap<String, Object> headers = clientRequest.getHeaders();
        when(servletRequest.getHeader(anyString())).thenAnswer(new Answer<Object>() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                Object[] arguments = invocation.getArguments();
                return headers.getFirst((String) arguments[0]);
            }
        });
        servletFilter.doFilter(servletRequest, servletResponse, filterChain);
        long traceId = span.getTraceId();
        long spanId = span.getSpanId();
        Long parentSpanId = span.getParentSpanId();
        verify(serverTracer).setStateCurrentTrace(eq(traceId),
                eq(spanId),
                eq(parentSpanId),
                anyString());
    }
}
