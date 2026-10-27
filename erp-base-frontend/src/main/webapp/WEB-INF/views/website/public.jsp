<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="com.example.erp.frontend.website.PublicPageView" %>
<% PublicPageView pageView = (PublicPageView) request.getAttribute("page"); %>
<!doctype html>
<html lang="cs">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title><%= pageView.title() %></title>
  <link rel="stylesheet" href="/assets/base.css?v=20261001-1">
</head>
<body>
  <main class="website-page">
    <header class="website-header">
      <div><span class="eyebrow">WEB</span><h1><%= pageView.title() %></h1></div>
    </header>
    <section class="website-section">
      <p><%= pageView.content() == null ? "" : pageView.content() %></p>
    </section>
  </main>
</body>
</html>