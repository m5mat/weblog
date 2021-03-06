package weblog.service;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import weblog.UserRepository;
import weblog.exception.UsernameAlreadyExistsException;
import weblog.model.Entitlement;
import weblog.model.Logbook;
import weblog.model.User;

@Service
public class UserService {
	
	Logger logger = LoggerFactory.getLogger(this.getClass());
	
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private LogbookService logbookService;
    
    @Autowired
    private RoleService roleService;
    
    public Page<User> getPaginatedArticles(Pageable pageable) {
        return userRepository.findAllByOrderByUsernameAsc(pageable);
    }
    
    public User getUser(String username) {
    	return userRepository.findByUsername(username);
    }
    
    public User getThisUser() {
    	Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    	return this.getUser(authentication.getName());
    }
    
    public Collection<Logbook> getThisUserLogbookList() {
    	User user = this.getThisUser();
    	return user.getLogbooks(Entitlement.VIEW);
    }
    
    public Optional<User> getById(long id) {
    	return userRepository.findById(id);
    }
    
    public void enable(User user) {
    	user.setEnabled(true);
    	save(user);
    }
    
    public void disable(User user) {
    	user.setEnabled(false);
    	save(user);
    }
    
    public String resetPassword(User user) {
    	String password = this.generatePassword(5);
    	user.setPassword(this.encodePassword(password));
    	save(user);
    	return password;
    }
    
    public void save(User user) {
    	userRepository.save(user);
    }
    
    public void delete(User user) {
    	userRepository.delete(user);
    }
    
    public void associateUserWithLogbook(User user, Logbook logbook, int entitlementLevel) {
    	Entitlement entitlement = new Entitlement();
    	entitlement.setEntitlement(entitlementLevel);
    	user.addEntitlement(entitlement);
    	logbook.addEntitlement(entitlement);
    	save(user);
    }
    
    public String addUser(User user) throws UsernameAlreadyExistsException {
    	
    	// Check if the username already exists
    	if ( getUser(user.getUsername()) != null ) {
    		throw new UsernameAlreadyExistsException();
    	}
    	
    	// Generate a password and save the user using this password
    	String generatedPassword = this.generatePassword(5);
    	user.setPassword(this.encodePassword(generatedPassword));
    	this.save(user);
    	
    	// Grant default user role to the user
    	user.addRole(roleService.getByName("ROLE_USER"));
    	save(user);
    	
    	// Create a default logbook for the user
    	// TODO: use correct locator here
    	Logbook logbook = logbookService.createLogbook(user.getUsername().toUpperCase(), user.getLocator());
    	
    	Entitlement viewEntitlement = new Entitlement();
    	viewEntitlement.setEntitlement(Entitlement.VIEW);
    	
    	Entitlement addEntitlement = new Entitlement();
    	addEntitlement.setEntitlement(Entitlement.ADD);
    	
    	Entitlement updateEntitlement = new Entitlement();
    	updateEntitlement.setEntitlement(Entitlement.UPDATE);
    	
    	Entitlement deleteEntitlement = new Entitlement();
    	deleteEntitlement.setEntitlement(Entitlement.FULL);
    	
    	logbook.addEntitlement(viewEntitlement);
    	logbook.addEntitlement(addEntitlement);
    	logbook.addEntitlement(updateEntitlement);
    	logbook.addEntitlement(deleteEntitlement);
    	logbookService.save(logbook);
    	
    	user.addEntitlement(viewEntitlement);
    	user.addEntitlement(addEntitlement);
    	user.addEntitlement(updateEntitlement);
    	user.addEntitlement(deleteEntitlement);
    	save(user);
    	
    	// Return the generated (not-encoded) password for info
    	return generatedPassword;
    }
    
    public void makeAdmin(User user) {
    	user.setAdmin(true);
    	save(user);
    }
    
    public void makeNonAdmin(User user) {
    	user.setAdmin(false);
    	save(user);
    }
    
    public String generatePassword(int length) {
    	String validChars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    	SecureRandom random = new SecureRandom();
    	
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {

			int rndCharAt = random.nextInt(validChars.length());
            char rndChar = validChars.charAt(rndCharAt);

            sb.append(rndChar);
        }

        return sb.toString();
    }
    
    public String encodePassword(String password) {
    	PasswordEncoder encoder = new BCryptPasswordEncoder(11);
    	return encoder.encode(password);
    }
    
    public List<String> listThemeNames() {
    	List<String> themes = new ArrayList<String>();
    	try {
			for ( String filename : this.listFiles("static/css/themes") ) {
				themes.add(filename.substring(0, filename.lastIndexOf('.')));
			}
		} catch (URISyntaxException | IOException e) {
			e.printStackTrace();
		}
    	return themes;
    }
    
    /*
    public List<String> listFilesUsingJavaIO(String dir) {
        return Stream.of(new File(dir).listFiles())
          .filter(file -> !file.isDirectory())
          .map(File::getName)
          .collect(Collectors.toList());
    }
    */
    public List<String> listFiles(String dir) throws URISyntaxException, IOException {
    	
    	List<String> fileList = new ArrayList<String>();
    	
    	ClassLoader cl = this.getClass().getClassLoader(); 
    	ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver(cl);
    	Resource[] resources = resolver.getResources("classpath*:/" + dir + "/*.css") ;
    	
    	for (Resource resource: resources){
    	    fileList.add(resource.getFilename());
    	}

    	return fileList;
    }

    public List<String> listFolders(String dir) throws URISyntaxException, IOException {
    	
    	List<String> folderList = new ArrayList<String>();
    	
    	ClassLoader cl = this.getClass().getClassLoader(); 
    	ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver(cl);
    	Resource[] resources = resolver.getResources("classpath*:/" + dir + "/*") ;
    	
    	for (Resource resource: resources){
    	    folderList.add(resource.getFilename());
    	}
    	
    	logger.debug("Listing discovered themes...");
    	logger.debug(Arrays.toString(folderList.toArray()));

    	return folderList;
    }

	public List<String> listBlogThemeNames() {
		List<String> names = new ArrayList<String>();
    	try {
			names = this.listFolders("templates/blogthemes");
			logger.info(names.toString());
		} catch (URISyntaxException | IOException e) {
			e.printStackTrace();
		}
    	return names;
	}
    
}
