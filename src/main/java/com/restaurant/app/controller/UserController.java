package com.restaurant.app.controller;

import com.restaurant.app.model.Usuario;
import com.restaurant.app.services.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpSession;
import java.util.List;

@Controller
public class UserController {

    @Autowired
    private UserService userService;

    @GetMapping("/dashboard")
    public String mostrarPanel(HttpSession session, Model model) {

        Usuario usuarioActual =
                (Usuario) session.getAttribute("currentUser");

        if (usuarioActual == null)
            return "redirect:/login";

        List<Usuario> usuarios =
                userService.getAllUsers();

        model.addAttribute("currentUser", usuarioActual);
        model.addAttribute("users", usuarios);

        return "dashboard";
    }

    @GetMapping("/users")
    public String verUsuarios(HttpSession session, Model model) {

        Usuario usuarioActual =
                (Usuario) session.getAttribute("currentUser");

        if (usuarioActual == null)
            return "redirect:/login";

        if ("CLIENTE".equals(usuarioActual.getRol()))
            return "redirect:/dashboard";

        List<Usuario> usuarios =
                userService.getAllUsers();

        model.addAttribute("users", usuarios);
        model.addAttribute("currentUser", usuarioActual);

        return "dashboard";
    }

    @GetMapping("/users/search")
    public String consultarUsuario(@RequestParam @NonNull Long id,
                                   HttpSession session,
                                   Model model) {

        Usuario usuarioActual =
                (Usuario) session.getAttribute("currentUser");

        if (usuarioActual == null)
            return "redirect:/login";

        userService.getUserById(id)
                .ifPresent(usuario ->
                        model.addAttribute("searchedUser", usuario));

        model.addAttribute("currentUser", usuarioActual);
        model.addAttribute("users",
                userService.getAllUsers());

        return "dashboard";
    }

    @PostMapping("/users/create")
    public String agregarUsuario(@RequestParam String nombre,
                                 @RequestParam String contraseña,
                                 @RequestParam String rol,
                                 @RequestParam String celular,
                                 @RequestParam String direccion,
                                 HttpSession session) {

        Usuario usuarioActual =
                (Usuario) session.getAttribute("currentUser");

        if (usuarioActual == null)
            return "redirect:/login";

        if (!"ADMINISTRADOR".equals(usuarioActual.getRol()))
            return "redirect:/dashboard";

        if ("ADMINISTRADOR".equals(rol))
            return "redirect:/users";

        userService.createUser(
                nombre,
                contraseña,
                rol,
                celular,
                direccion
        );

        return "redirect:/users";
    }

    @PostMapping("/users/edit")
    public String editarUsuario(@RequestParam @NonNull Long id,
                                @RequestParam String nombre,
                                @RequestParam String rol,
                                @RequestParam String celular,
                                @RequestParam String direccion,
                                HttpSession session) {

        Usuario usuarioActual =
                (Usuario) session.getAttribute("currentUser");

        if (usuarioActual == null)
            return "redirect:/login";

        if (!"ADMINISTRADOR".equals(usuarioActual.getRol()))
            return "redirect:/dashboard";

        userService.updateUser(
                id,
                nombre,
                rol,
                celular,
                direccion
        );

        return "redirect:/users";
    }

    @PostMapping("/users/delete")
    public String eliminarUsuario(@RequestParam @NonNull Long id,
                                  HttpSession session) {

        Usuario usuarioActual =
                (Usuario) session.getAttribute("currentUser");

        if (usuarioActual == null)
            return "redirect:/login";

        if (!"ADMINISTRADOR".equals(usuarioActual.getRol()))
            return "redirect:/dashboard";

        userService.deleteUser(id);

        return "redirect:/users";
    }

    @PostMapping("/users/toggle")
    public String verificarUsuario(@RequestParam @NonNull Long id,
                                   HttpSession session) {

        Usuario usuarioActual =
                (Usuario) session.getAttribute("currentUser");

        if (usuarioActual == null)
            return "redirect:/login";

        if (!"ADMINISTRADOR".equals(usuarioActual.getRol()))
            return "redirect:/dashboard";

        userService.toggleUserStatus(id);

        return "redirect:/users";
    }
}