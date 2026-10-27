package com.example.erp.frontend.base;

import jakarta.servlet.ServletContext;
import org.springframework.session.web.context.AbstractHttpSessionApplicationInitializer;

public class SessionInitializer extends AbstractHttpSessionApplicationInitializer {
    @Override
    public void onStartup(ServletContext servletContext) {
        String sessionStore = System.getenv().getOrDefault("HTTP_SESSION_STORE", "tomcat");
        if ("tomcat".equals(sessionStore)) {
            return;
        }
        if (!"postgresql".equals(sessionStore)) {
            throw new IllegalStateException("Unsupported HTTP_SESSION_STORE: " + sessionStore);
        }
        super.onStartup(servletContext);
    }
}
