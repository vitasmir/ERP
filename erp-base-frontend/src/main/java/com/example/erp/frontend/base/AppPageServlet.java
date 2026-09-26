package com.example.erp.frontend.base;

import java.io.IOException;
import java.util.Map;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@WebServlet({"", "/apps"})
public class AppPageServlet extends HttpServlet {
    private static final Map<String, Page> PAGES = Map.of(
            "", new Page("apps", "Přehled systému", "BASE / ADMINISTRATION", "apps/index.jsp"),
            "/apps", new Page("apps", "Přehled systému", "BASE / ADMINISTRATION", "apps/index.jsp"));

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        Page page = PAGES.get(request.getServletPath());
        if (page == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        request.setAttribute("activePage", page.activePage());
        request.setAttribute("pageTitle", page.title());
        request.setAttribute("breadcrumb", page.breadcrumb());
        request.getRequestDispatcher("/WEB-INF/views/" + page.view()).forward(request, response);
    }

    private record Page(String activePage, String title, String breadcrumb, String view) { }
}