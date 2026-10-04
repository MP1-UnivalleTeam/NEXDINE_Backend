package com.restaurant.app.controller;

import com.restaurant.app.model.Usuario;
import com.restaurant.app.services.UserService;
import com.restaurant.app.services.UserService.SuspendedMarker;

import jakarta.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@Controller
public class AuthController {

    @Autowired
    private UserService userService;

    @GetMapping({"/", "/login"})
    public String mostrarInicioSesion(HttpSession session) {

        if (session.getAttribute("currentUser") != null) {
            return "redirect:/dashboard";
        }

        return "login";
    }

    @PostMapping("/login")
    public String iniciarSesion(@RequestParam String nombre,
                                @RequestParam String contraseña,
                                HttpSession session,
                                Model model) {

        userService.initAdmin();

        Optional<Usuario> usuarioOpt =
                userService.login(nombre, contraseña);

        if (usuarioOpt.isPresent()) {

            Usuario usuario = usuarioOpt.get();

            if (usuario instanceof SuspendedMarker) {

                model.addAttribute(
                        "error",
                        "Usuario suspendido"
                );

                return "login";
            }

            session.setAttribute(
                    "currentUser",
                    usuario
            );

            return "redirect:/dashboard";
        }

        model.addAttribute(
                "error",
                "Credenciales incorrectas"
        );

        return "login";
    }

    @GetMapping("/logout")
    public String cerrarSesion(HttpSession session) {

        session.invalidate();

        return "redirect:/login";
    }
}