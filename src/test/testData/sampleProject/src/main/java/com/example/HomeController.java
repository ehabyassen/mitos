package com.example;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/home")
public class HomeController {

    private final GreetingService service;

    @Autowired
    public HomeController(GreetingService service) {
        this.service = service;
    }

    /**
     * Render the home page. Mitos should resolve the returned view name
     * "home" → /WEB-INF/views/home.jsp.
     */
    @GetMapping
    public String home(java.util.Map<String, Object> model) {
        model.put("greeting", service.hello("world"));
        return "home";
    }

    @PostMapping("/api/greet")
    public String greet(String name) {
        return service.hello(name);
    }
}
