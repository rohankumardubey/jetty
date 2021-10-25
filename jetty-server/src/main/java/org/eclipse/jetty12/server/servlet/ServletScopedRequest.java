//
// ========================================================================
// Copyright (c) 1995-2021 Mort Bay Consulting Pty Ltd and others.
//
// This program and the accompanying materials are made available under the
// terms of the Eclipse Public License v. 2.0 which is available at
// https://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
// which is available at https://www.apache.org/licenses/LICENSE-2.0.
//
// SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
// ========================================================================
//

package org.eclipse.jetty12.server.servlet;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.security.Principal;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Map;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.ReadListener;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletConnection;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpUpgradeHandler;
import jakarta.servlet.http.Part;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.http.MetaData;
import org.eclipse.jetty.util.SharedBlockingCallback;
import org.eclipse.jetty.util.SharedBlockingCallback.Blocker;
import org.eclipse.jetty12.server.Content;
import org.eclipse.jetty12.server.MetaConnection;
import org.eclipse.jetty12.server.Request;
import org.eclipse.jetty12.server.Response;
import org.eclipse.jetty12.server.handler.ScopedRequest;

public class ServletScopedRequest extends ScopedRequest implements Runnable
{
    final ServletRequestState _servletRequestState;
    final MutableHttpServletRequest _httpServletRequest;
    final HttpServletResponse _httpServletResponse;
    final ServletHandler.MappedServlet _mappedServlet;
    ReadListener _readListener;

    protected ServletScopedRequest(
        ServletRequestState servletRequestState,
        Request request,
        Response response,
        String pathInContext,
        ServletHandler.MappedServlet mappedServlet)
    {
        super(servletRequestState.getServletContextContext().getContext(), request, pathInContext);
        _servletRequestState = servletRequestState;
        _httpServletRequest = new MutableHttpServletRequest();
        _httpServletResponse = new MutableHttpServletResponse(response);
        _mappedServlet = mappedServlet;
    }

    ServletRequestState getServletRequestState()
    {
        return _servletRequestState;
    }

    public HttpServletRequest getHttpServletRequest()
    {
        return _httpServletRequest;
    }

    public MutableHttpServletRequest getMutableHttpServletRequest()
    {
        return _httpServletRequest;
    }

    public HttpServletResponse getHttpServletResponse()
    {
        return _httpServletResponse;
    }

    public ServletHandler.MappedServlet getMappedServlet()
    {
        return _mappedServlet;
    }

    public static ServletScopedRequest getRequest(HttpServletRequest httpServletRequest)
    {
        while (httpServletRequest != null)
        {
            if (httpServletRequest instanceof ServletRequestState)
                return ((ServletRequestState)httpServletRequest).getServletScopedRequest();
            if (httpServletRequest instanceof HttpServletRequestWrapper)
                httpServletRequest = (HttpServletRequest)((HttpServletRequestWrapper)httpServletRequest).getRequest();
            else
                break;
        }
        return null;
    }

    @Override
    public void run()
    {
        _servletRequestState.handle();
    }

    private Runnable onContentAvailable()
    {
        // TODO not sure onReadReady is right method or at least could be renamed.
        return _servletRequestState.onReadReady() ? this : null;
    }

    public class MutableHttpServletRequest implements HttpServletRequest
    {
        public void setSessionManager(SessionHandler sessionHandler)
        {
            // TODO
        }

        public void setSession(Object o)
        {
            // TODO
        }

        @Override
        public String getRequestId()
        {
            return ServletScopedRequest.this.getId();
        }

        @Override
        public String getProtocolRequestId()
        {
            return ServletScopedRequest.this.getChannel().getStream().getId();
        }

        @Override
        public ServletConnection getServletConnection()
        {
            // TODO cache the results
            final MetaConnection metaConnection = ServletScopedRequest.this.getMetaConnection();
            return new ServletConnection()
            {
                @Override
                public String getConnectionId()
                {
                    return metaConnection.getId();
                }

                @Override
                public String getProtocol()
                {
                    return metaConnection.getProtocol();
                }

                @Override
                public String getProtocolConnectionId()
                {
                    return metaConnection.getConnection().toString(); // TODO getId
                }

                @Override
                public boolean isSecure()
                {
                    return metaConnection.isSecure();
                }
            };
        }

        @Override
        public String getAuthType()
        {
            return null;
        }

        @Override
        public Cookie[] getCookies()
        {
            return new Cookie[0];
        }

        @Override
        public long getDateHeader(String name)
        {
            return 0;
        }

        @Override
        public String getHeader(String name)
        {
            return ServletScopedRequest.this.getHeaders().get(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name)
        {
            return ServletScopedRequest.this.getHeaders().getValues(name);
        }

        @Override
        public Enumeration<String> getHeaderNames()
        {
            return ServletScopedRequest.this.getHeaders().getFieldNames();
        }

        @Override
        public int getIntHeader(String name)
        {
            return (int)ServletScopedRequest.this.getHeaders().getLongField(name);
        }

        @Override
        public String getMethod()
        {
            return getMethod();
        }

        @Override
        public String getPathInfo()
        {
            return ServletScopedRequest.this._mappedServlet.getServletPathMapping().getPathInfo();
        }

        @Override
        public String getPathTranslated()
        {
            return null;
        }

        @Override
        public String getContextPath()
        {
            return ServletScopedRequest.this.getContext().getContextPath();
        }

        @Override
        public String getQueryString()
        {
            return ServletScopedRequest.this.getURI().getQuery();
        }

        @Override
        public String getRemoteUser()
        {
            return null;
        }

        @Override
        public boolean isUserInRole(String role)
        {
            return false;
        }

        @Override
        public Principal getUserPrincipal()
        {
            return null;
        }

        @Override
        public String getRequestedSessionId()
        {
            return null;
        }

        @Override
        public String getRequestURI()
        {
            return ServletScopedRequest.this.getURI().toString();
        }

        @Override
        public StringBuffer getRequestURL()
        {
            return null;
        }

        @Override
        public String getServletPath()
        {
            return ServletScopedRequest.this._mappedServlet.getServletPathMapping().getServletPath();
        }

        @Override
        public HttpSession getSession(boolean create)
        {
            return null;
        }

        @Override
        public HttpSession getSession()
        {
            return null;
        }

        @Override
        public String changeSessionId()
        {
            return null;
        }

        @Override
        public boolean isRequestedSessionIdValid()
        {
            return false;
        }

        @Override
        public boolean isRequestedSessionIdFromCookie()
        {
            return false;
        }

        @Override
        public boolean isRequestedSessionIdFromURL()
        {
            return false;
        }

        @Override
        public boolean authenticate(HttpServletResponse response) throws IOException, ServletException
        {
            return false;
        }

        @Override
        public void login(String username, String password) throws ServletException
        {

        }

        @Override
        public void logout() throws ServletException
        {

        }

        @Override
        public Collection<Part> getParts() throws IOException, ServletException
        {
            return null;
        }

        @Override
        public Part getPart(String name) throws IOException, ServletException
        {
            return null;
        }

        @Override
        public <T extends HttpUpgradeHandler> T upgrade(Class<T> handlerClass) throws IOException, ServletException
        {
            return null;
        }

        @Override
        public Object getAttribute(String name)
        {
            return ServletScopedRequest.this.getAttribute(name);
        }

        @Override
        public Enumeration<String> getAttributeNames()
        {
            return ServletScopedRequest.this.getAttributeNames();
        }

        @Override
        public String getCharacterEncoding()
        {
            return null;
        }

        @Override
        public void setCharacterEncoding(String env) throws UnsupportedEncodingException
        {
        }

        @Override
        public int getContentLength()
        {
            return 0;
        }

        @Override
        public long getContentLengthLong()
        {
            return 0;
        }

        @Override
        public String getContentType()
        {
            return null;
        }

        private Content _content;

        @Override
        public ServletInputStream getInputStream() throws IOException
        {
            // TODO the stateful saving rather than create each call!
            //      in reality this will be the HttpInput class
            return new ServletInputStream()
            {
                @Override
                public boolean isFinished()
                {
                    Content content = _content;
                    return content != null && content.isLast();
                }

                @Override
                public boolean isReady()
                {
                    if (_content == null)
                    {
                        _content = readContent();
                        if (_content == null)
                        {
                            if (_readListener != null)
                                demandContent(ServletScopedRequest.this::onContentAvailable);
                            return false;
                        }
                    }

                    return true;
                }

                @Override
                public void setReadListener(ReadListener readListener)
                {
                    _readListener = readListener;
                }

                @Override
                public int read() throws IOException
                {
                    // TODO this is just the async version
                    if (_content == null)
                        _content = readContent();
                    if (_content != null & _content.hasRemaining())
                        return _content.getByteBuffer().get();
                    throw new IOException();
                }
            };
        }

        @Override
        public String getParameter(String name)
        {
            return null;
        }

        @Override
        public Enumeration<String> getParameterNames()
        {
            return null;
        }

        @Override
        public String[] getParameterValues(String name)
        {
            return new String[0];
        }

        @Override
        public Map<String, String[]> getParameterMap()
        {
            return null;
        }

        @Override
        public String getProtocol()
        {
            return null;
        }

        @Override
        public String getScheme()
        {
            return null;
        }

        @Override
        public String getServerName()
        {
            return null;
        }

        @Override
        public int getServerPort()
        {
            return 0;
        }

        @Override
        public BufferedReader getReader() throws IOException
        {
            return null;
        }

        @Override
        public String getRemoteAddr()
        {
            return null;
        }

        @Override
        public String getRemoteHost()
        {
            return null;
        }

        @Override
        public void setAttribute(String name, Object o)
        {

        }

        @Override
        public void removeAttribute(String name)
        {

        }

        @Override
        public Locale getLocale()
        {
            return null;
        }

        @Override
        public Enumeration<Locale> getLocales()
        {
            return null;
        }

        @Override
        public boolean isSecure()
        {
            return false;
        }

        @Override
        public RequestDispatcher getRequestDispatcher(String path)
        {
            return null;
        }

        @Override
        public int getRemotePort()
        {
            return 0;
        }

        @Override
        public String getLocalName()
        {
            return null;
        }

        @Override
        public String getLocalAddr()
        {
            return null;
        }

        @Override
        public int getLocalPort()
        {
            return 0;
        }

        @Override
        public ServletContext getServletContext()
        {
            return _servletRequestState.getServletContext();
        }

        @Override
        public AsyncContext startAsync() throws IllegalStateException
        {
            return null;
        }

        @Override
        public AsyncContext startAsync(ServletRequest servletRequest, ServletResponse servletResponse) throws IllegalStateException
        {
            return null;
        }

        @Override
        public boolean isAsyncStarted()
        {
            return false;
        }

        @Override
        public boolean isAsyncSupported()
        {
            return false;
        }

        @Override
        public AsyncContext getAsyncContext()
        {
            return null;
        }

        @Override
        public DispatcherType getDispatcherType()
        {
            return null;
        }
    }

    class MutableHttpServletResponse implements HttpServletResponse
    {
        private final SharedBlockingCallback _blocker = new SharedBlockingCallback();
        private final Response _response;

        MutableHttpServletResponse(Response response)
        {
            _response = response;
        }

        @Override
        public void addCookie(Cookie cookie)
        {
            // TODO
        }

        @Override
        public boolean containsHeader(String name)
        {
            return _response.getHttpFields().contains(name);
        }

        @Override
        public String encodeURL(String url)
        {
            return null;
        }

        @Override
        public String encodeRedirectURL(String url)
        {
            return null;
        }

        @Override
        public void sendError(int sc, String msg) throws IOException
        {
            switch (sc)
            {
                case -1:
                    _servletRequestState.getServletScopedRequest().getChannel().getStream().failed(new IOException(msg));
                    break;

                case HttpStatus.PROCESSING_102:
                    try (Blocker blocker = _blocker.acquire())
                    {
                        // TODO static MetaData
                        _servletRequestState.getServletScopedRequest().getChannel().getStream()
                            .send(new MetaData.Response(null, 102, null), false, blocker);
                    }
                    break;

                default:
                    // This is just a state change
                    _servletRequestState.sendError(sc, msg);
                    break;
            }
        }

        @Override
        public void sendError(int sc) throws IOException
        {
            sendError(sc, null);
        }

        @Override
        public void sendRedirect(String location) throws IOException
        {
            // TODO
        }

        @Override
        public void setDateHeader(String name, long date)
        {
            _response.getHttpFields().putDateField(name, date);
        }

        @Override
        public void addDateHeader(String name, long date)
        {

        }

        @Override
        public void setHeader(String name, String value)
        {
            _response.getHttpFields().put(name, value);
        }

        @Override
        public void addHeader(String name, String value)
        {
            _response.getHttpFields().add(name, value);
        }

        @Override
        public void setIntHeader(String name, int value)
        {
            // TODO do we need int versions?
            _response.getHttpFields().putLongField(name, value);
        }

        @Override
        public void addIntHeader(String name, int value)
        {
            // TODO do we need a native version?
            _response.getHttpFields().add(name, Integer.toString(value));
        }

        @Override
        public void setStatus(int sc)
        {
            _response.setStatus(sc);
        }

        @Override
        public int getStatus()
        {
            return 0;
        }

        @Override
        public String getHeader(String name)
        {
            return null;
        }

        @Override
        public Collection<String> getHeaders(String name)
        {
            return null;
        }

        @Override
        public Collection<String> getHeaderNames()
        {
            return null;
        }

        @Override
        public String getCharacterEncoding()
        {
            return null;
        }

        @Override
        public String getContentType()
        {
            return null;
        }

        @Override
        public ServletOutputStream getOutputStream() throws IOException
        {
            // TODO this will be done in HttpOutput
            return new ServletOutputStream()
            {
                @Override
                public boolean isReady()
                {
                    return false;
                }

                @Override
                public void setWriteListener(WriteListener writeListener)
                {
                    // TODO
                }

                @Override
                public void flush() throws IOException
                {
                    try (Blocker blocker = _blocker.acquire())
                    {
                        _response.write(false, blocker);
                    }
                }

                @Override
                public void close() throws IOException
                {
                    try (Blocker blocker = _blocker.acquire())
                    {
                        _response.write(true, blocker);
                    }
                }

                @Override
                public void write(int b) throws IOException
                {
                    // TODO
                }
            };
        }

        @Override
        public PrintWriter getWriter() throws IOException
        {
            return null;
        }

        @Override
        public void setCharacterEncoding(String charset)
        {

        }

        @Override
        public void setContentLength(int len)
        {

        }

        @Override
        public void setContentLengthLong(long len)
        {

        }

        @Override
        public void setContentType(String type)
        {

        }

        @Override
        public void setBufferSize(int size)
        {

        }

        @Override
        public int getBufferSize()
        {
            return 0;
        }

        @Override
        public void flushBuffer() throws IOException
        {
            try (Blocker blocker = _blocker.acquire())
            {
                _response.write(false, blocker);
            }
        }

        @Override
        public void resetBuffer()
        {
            // TODO I don't think this is right... maybe just a HttpWriter reset
            if (!_response.isCommitted())
                _response.reset();
        }

        @Override
        public boolean isCommitted()
        {
            return _response.isCommitted();
        }

        @Override
        public void reset()
        {
            if (!_response.isCommitted())
                _response.reset();
        }

        @Override
        public void setLocale(Locale loc)
        {
        }

        @Override
        public Locale getLocale()
        {
            return null;
        }
    }
}