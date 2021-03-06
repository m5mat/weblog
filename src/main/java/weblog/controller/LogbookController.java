package weblog.controller;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import weblog.model.Entitlement;
import weblog.EventStreamMessage;
import weblog.model.Contact;
import weblog.model.Logbook;
import weblog.model.User;
import weblog.service.ContactService;
import weblog.service.LogbookService;
import weblog.service.UserService;

/*
 * TODO: Add user/permissions check
 */

@Controller
@RequestMapping(path="/logbook")
public class LogbookController {

	Logger logger = LoggerFactory.getLogger(this.getClass());
	
	@Autowired
	private LogbookService logbookService;
	
	@Autowired
	private UserService userService;

	@Autowired
	private ContactService contactService;
	
	/*
	 * If we end up here then go to the first/default/any logbook
	 */
	@RequestMapping(value = "", method = RequestMethod.GET)
	public String anyLogbook(Model model, Contact contact, HttpServletResponse response) throws IOException {
		
		User user = userService.getUser(SecurityContextHolder.getContext().getAuthentication().getName());
        Logbook logbook = user.getAnyLogbook();
        
        return home(model, new Contact(), logbook, response, Optional.empty(), Optional.empty(), Optional.empty());
	}
	
	/*
	 * API - return a list of the user's logbooks
	 */
	@RequestMapping(value = "list.json", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<List<String>> getUsersLogbooks() {
		
		User user = userService.getUser(SecurityContextHolder.getContext().getAuthentication().getName());
		Collection<Logbook> logbooks = user.getLogbooks(Entitlement.VIEW);
		
		List<String> logbookList = new ArrayList<String>();
		
        for (Iterator<Logbook> iterator = logbooks.iterator(); iterator.hasNext();) {
        	logbookList.add("/logbook/" + iterator.next().getId());
        }
        return ResponseEntity.ok(logbookList);
	}
	
	/*
	 * TODO: Logbook security
	 */
	@RequestMapping(value = "/{logbook}.json", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Logbook> getLogbook(@PathVariable Logbook logbook, HttpServletResponse response) throws IOException {
		// Check that the currently logged in user has access to the requested logbook
		if ( !logbookService.getUserEntitlement(logbook, userService.getThisUser(), Entitlement.VIEW) ) {
        	response.sendError(response.SC_FORBIDDEN, "Sorry, you do not have VIEW access to the logbook " + logbook.getName());
        }
        
		return ResponseEntity.ok(logbook);
	}
	
    @GetMapping("/{logbook}")
    public String home(Model model, Contact contact, @PathVariable Logbook logbook, HttpServletResponse response, @RequestParam("page") Optional<Integer> page, 
    	      @RequestParam("size") Optional<Integer> size, @RequestParam("edit") Optional<Long> editId) throws IOException {
    	
    	int currentPage = page.orElse(1);
        int pageSize = size.orElse(20);
        
        User user = userService.getThisUser();
        model.addAttribute("user", user);
                
        // Check that the currently logged in user has access to the requested logbook
        if ( !logbookService.getUserEntitlement(logbook, user, Entitlement.VIEW) ) {
        	response.sendError(response.SC_FORBIDDEN, "Sorry, you do not have VIEW access to the logbook " + logbook.getName());
        }
        
        PageRequest pageable = PageRequest.of(currentPage - 1, pageSize);
        Page<Contact> contactPage = contactService.getPaginatedLogbookEntries(pageable, logbook);
        
        int totalPages = contactPage.getTotalPages();
        if(totalPages > 0) {
            List<Integer> pageNumbers = IntStream.rangeClosed(1,totalPages).boxed().collect(Collectors.toList());
            model.addAttribute("pageNumbers", pageNumbers);
        }
        model.addAttribute("currentPage", currentPage);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("activeContactList", true);
        model.addAttribute("contactList", contactPage.getContent());
        
        if ( editId.isPresent() ) {
        	contact = contactService.getById(editId.get())
                .orElseThrow(() -> new IllegalArgumentException("Invalid contact Id:" + editId.get()));
        }
        
        model.addAttribute("contact", contact);
        model.addAttribute("logbook", logbook);
        
        model.addAttribute("map_url", "/" + logbook.getId());
        
        model.addAttribute("submitUrl", "/logbook/" + logbook.getId() + "/addcontact");
              
        return "index";
    }
    
    @PostMapping("/{logbook}/addcontact")
    public String addContact(@PathVariable Logbook logbook, @Valid Contact contact, BindingResult result, Model model, HttpServletResponse response, @ModelAttribute("activelogbook") Logbook activeLogbook, RedirectAttributes attributes) throws IOException {
    	
    	// Check that the currently logged in user has access to the requested logbook
		if ( !logbookService.getUserEntitlement(logbook, userService.getThisUser(), Entitlement.ADD) ) {
        	response.sendError(response.SC_FORBIDDEN, "Sorry, you do not have ADD access to the logbook " + logbook.getName());
        }
		
		User user = userService.getUser(SecurityContextHolder.getContext().getAuthentication().getName());
        model.addAttribute("user", user);
        
    	
		if (result.hasErrors()) {
            return "index";
        }
        
        if ( contact.getTimestamp() == null ) {
        	contact.setTimestamp(new Date());
        }
        
        contact.setCallsign(contact.getCallsign().toUpperCase());
        contact.setLogbook(logbook);
        
        contactService.save(contact);
        
        // Set up the next contact. We can use this to populate default fields or to
        // copy over details from the last contact to the next one.
        Contact nextContact = new Contact();
        nextContact.setBand(contact.getBand());
        nextContact.setFrequency(contact.getFrequency());
        nextContact.setMode(contact.getMode());
        nextContact.setOpName(contact.getOpName());
        
        if ( nextContact.getMode().equals("CW") ) {
        	nextContact.setRstr("599");
        	nextContact.setRsts("599");
        } else {
        	nextContact.setRstr("59");
        	nextContact.setRsts("59");
        }
        
        return this.home(model, contact, logbook, response, Optional.empty(), Optional.empty(), Optional.empty());
    }
    
    @GetMapping("/new")
    public String newLogbook(@RequestParam String logbookName, Model model, HttpServletResponse response) {
    	User user = userService.getUser(SecurityContextHolder.getContext().getAuthentication().getName());
    	Logbook logbook = logbookService.createLogbook(logbookName, user.getLocator());
    	logbookService.save(logbook);
    	Logbook savedLogbook = logbookService.getLogbookById(logbook.getId()).get();
    	logbookService.grantEntitlement(savedLogbook, user, Entitlement.FULL);
    	return "redirect:/logbook/" + savedLogbook.getId();
    }

	
    @GetMapping("/delete/{logbook}")
    public String delete(@PathVariable Logbook logbook, Model model, HttpServletResponse response) throws IOException {
        
    	// Check that the currently logged in user has access to the requested logbook
		if ( !logbookService.getUserEntitlement(logbook, userService.getThisUser(), Entitlement.FULL) ) {
        	response.sendError(response.SC_FORBIDDEN, "Sorry, you do not have DELETE access to the logbook " + logbook.getName());
        }
		
    	logbookService.delete(logbook);
        
        return "redirect:/profile";
    }
	
    @GetMapping("/move/{fromLogbook}")
    public String move(@PathVariable Logbook fromLogbook, @RequestParam Logbook toLogbook, @RequestParam Boolean deleteAfterMove, Model model, HttpServletResponse response) throws IOException {
    	
    	// Check that the currently logged in user has access to the source logbook
		if ( !logbookService.getUserEntitlement(fromLogbook, userService.getThisUser(), Entitlement.FULL) ) {
        	response.sendError(response.SC_FORBIDDEN, "Sorry, you do not have DELETE access to the logbook " + fromLogbook.getName());
        }
		
		// Check that the currently logged in user has access to the requested logbook
		if ( !logbookService.getUserEntitlement(toLogbook, userService.getThisUser(), Entitlement.ADD) ) {
        	response.sendError(response.SC_FORBIDDEN, "Sorry, you do not have ADD access to the logbook " + toLogbook.getName());
        }
    	
    	logbookService.moveContacts(fromLogbook, toLogbook, deleteAfterMove);
    	
        return "redirect:/profile";
    }
    
    @GetMapping("/rename/{logbook}")
    public String delete(@PathVariable Logbook logbook, @RequestParam String name, Model model, HttpServletResponse response) throws IOException {
    	// Check that the currently logged in user has access to the requested logbook
		if ( !logbookService.getUserEntitlement(logbook, userService.getThisUser(), Entitlement.UPDATE) ) {
        	response.sendError(response.SC_FORBIDDEN, "Sorry, you do not have UPDATE access to the logbook " + logbook.getName());
        }
		        
    	logbook.setName(name);
        logbookService.save(logbook);
        
        return "redirect:/profile";
    }
	
    
	
}
