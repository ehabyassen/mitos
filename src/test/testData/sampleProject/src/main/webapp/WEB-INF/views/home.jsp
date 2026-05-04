<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ include file="/WEB-INF/views/_header.jsp" %>

<jsp:useBean id="greetingService" class="com.example.GreetingService" scope="application"/>

<html>
<head>
    <title>Home</title>
    <script src="/static/js/home.js"></script>
</head>
<body>
    <h1>${greetingService.hello("world")}</h1>

    <form id="greet-form" onsubmit="submitGreeting(event)">
        <input type="text" name="name"/>
        <button type="submit" onclick="trackClick()">Greet</button>
    </form>

    <c:out value="${greeting}"/>
</body>
</html>
