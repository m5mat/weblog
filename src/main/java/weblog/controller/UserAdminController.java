package weblog.controller;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javax.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import weblog.exception.UsernameAlreadyExistsException;
import weblog.model.Logbook;
import weblog.model.User;
import weblog.service.RoleService;
import weblog.service.UserService;

@Controller
@RequestMapping(path="/admin/users")
public class UserAdminController {
	
	Logger logger = LoggerFactory.getLogger(this.getClass());
	
	@Autowired private UserService userService;
	@Autowired private RoleService roleService;
	
    @GetMapping("")
    public String home(Model model, User user, @RequestParam("page") Optional<Integer> page, 
    		@ModelAttribute("activelogbook") Logbook activeLogbook,
    	    @RequestParam("size") Optional<Integer> size, @RequestParam("edit") Optional<Long> editId) {
    	
    	int currentPage = page.orElse(1);
        int pageSize = size.orElse(20);
        
        PageRequest pageable = PageRequest.of(currentPage - 1, pageSize);
        Page<User> userPage = userService.getPaginatedArticles(pageable);
        
        int totalPages = userPage.getTotalPages();
        if(totalPages > 0) {
            List<Integer> pageNumbers = IntStream.rangeClosed(1,totalPages).boxed().collect(Collectors.toList());
            model.addAttribute("pageNumbers", pageNumbers);
        }
        model.addAttribute("currentPage", currentPage);
        model.addAttribute("totalPages", totalPages);
        
        if ( editId.isPresent() ) {
        	user = userService.getById(editId.get())
                .orElseThrow(() -> new IllegalArgumentException("Invalid contact Id:" + editId.get()));
        	model.addAttribute("user", user);
        }
        
        model.addAttribute("userList", userPage.getContent());
        
        User myUser = userService.getUser(SecurityContextHolder.getContext().getAuthentication().getName());
        model.addAttribute("user", myUser);
              
        return "user";
    }
    
    @PostMapping("/adduser")
    public String addUser(@Valid User user, BindingResult result, Model model) {
    	
    	String generatedPassword = null;
    	
    	try {
    		generatedPassword = userService.addUser(user);
    	} catch (UsernameAlreadyExistsException e) {
    		result.reject("Username " + user.getUsername() + " already exists");
    	}
    	
        if (result.hasErrors()) {
        	return "redirect:/admin/users";
        }
        
        model.addAttribute("generatedPassword", generatedPassword);
        
        return "redirect:/admin/users";
    }
    
    @GetMapping("/edit/{id}")
    public String showUpdateUserForm(@PathVariable("id") long id, Model model) {
        User user = userService.getById(id)
          .orElseThrow(() -> new IllegalArgumentException("Invalid User Id:" + id));
         
        model.addAttribute("user", user);
        model.addAttribute("submitUrl", "/admin/user/update/" + id);
        return "redirect:/admin/users";
    }
    
    @PostMapping("/update/{id}")
    public String updateUser(@PathVariable("id") long id, @Valid User user, 
      BindingResult result, Model model) {
        if (result.hasErrors()) {
            user.setId(id);
            return "index";
        }
             
        userService.save(user);
        //model.addAttribute("contacts", contactRepository.findAll());
        return "redirect:/admin/users";
    }
         
    @GetMapping("/delete/{id}")
    public String deleteUser(@PathVariable("id") long id, Model model) {
        User user = userService.getById(id)
          .orElseThrow(() -> new IllegalArgumentException("Invalid User Id:" + id));
        userService.delete(user);
        //model.addAttribute("contacts", contactRepository.findAll());
        return "redirect:/admin/users";
    }

    @GetMapping("/disable/{id}")
    public String disableUser(@PathVariable("id") long id, Model model) {
        User user = userService.getById(id)
          .orElseThrow(() -> new IllegalArgumentException("Invalid User Id:" + id));
        userService.disable(user);
        //model.addAttribute("contacts", contactRepository.findAll());
        return "redirect:/admin/users";
    }

    @GetMapping("/enable/{id}")
    public String enableUser(@PathVariable("id") long id, Model model) {
        User user = userService.getById(id)
          .orElseThrow(() -> new IllegalArgumentException("Invalid User Id:" + id));
        userService.enable(user);
        //model.addAttribute("contacts", contactRepository.findAll());
        return "redirect:/admin/users";
    }

    @GetMapping("/make-admin/{id}")
    public String adminifyUser(@PathVariable("id") long id, Model model) {
        User user = userService.getById(id)
          .orElseThrow(() -> new IllegalArgumentException("Invalid User Id:" + id));
        userService.makeAdmin(user);
        //model.addAttribute("contacts", contactRepository.findAll());
        return "redirect:/admin/users";
    }

    @GetMapping("/make-non-admin/{id}")
    public String deAdminifyUser(@PathVariable("id") long id, Model model) {
        User user = userService.getById(id)
          .orElseThrow(() -> new IllegalArgumentException("Invalid User Id:" + id));
        userService.makeNonAdmin(user);
        //model.addAttribute("contacts", contactRepository.findAll());
        return "redirect:/admin/users";
    }

    @GetMapping("/resetpassword/{id}")
    public String resetPasswordForUser(@PathVariable("id") long id, Model model) {
        User user = userService.getById(id)
          .orElseThrow(() -> new IllegalArgumentException("Invalid User Id:" + id));
        
        String newPassword = userService.resetPassword(user);
        
        model.addAttribute("generatedPassword", newPassword);

        return "redirect:/admin/users";
    }
}
