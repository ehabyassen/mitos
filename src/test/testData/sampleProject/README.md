# Mitos sample fixture

Minimal Spring-MVC + JSP + JS app used by the integration tests and the
SRS §7.3 acceptance walkthrough.

```
HomeController#home (Java, @GetMapping)
       │   FORWARD (Java → JSP, view name "home")
       ▼
home.jsp                      ─── JSP_INCLUDE ──▶ _header.jsp
   │                          ─── EL_REFERENCE ─▶ GreetingService#hello
   │                          ─── SCRIPTLET_CALL ▶ home.js
   │                          ─── JS_INVOCATION ─▶ submitGreeting (onsubmit)
   ▼
home.js
   │   AJAX_REQUEST  (heuristic, dashed)
   ▼
HomeController#greet (Java, @PostMapping("/api/greet"))
   │
   ▼
GreetingService#hello → GreetingService#formatGreeting   (DIRECT_CALL)
```

Place the caret on `HomeController#home` and press `Ctrl+Alt+Shift+G` to
reproduce the SRS §7.3 acceptance scenario.
