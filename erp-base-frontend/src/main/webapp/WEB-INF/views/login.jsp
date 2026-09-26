<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<!doctype html>
<html lang="cs">
<head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>ERP | Přihlášení</title><link rel="stylesheet" href="assets/base.css"><link rel="stylesheet" href="assets/workflows.css"></head>
<body><main class="login-page">
    <a href="eshop">E-shop</a><h1>Retail ERP</h1>
    <% if (request.getAttribute("error") != null) { %><p role="alert">Přihlášení se nezdařilo nebo služba není dostupná.</p><% } %>
    <form method="post" action="login" class="workflow-fields">
        <label>Uživatelské jméno<input name="username" required maxlength="120" autocomplete="username"></label>
        <label>Heslo<input name="password" type="password" required autocomplete="current-password"></label>
        <button type="submit" class="primary">Přihlásit se</button>
    </form>
    <% if (session.getAttribute("backendToken") != null) { %><form method="post" action="logout"><button type="submit">Odhlásit se</button></form><% } %>
</main></body></html>