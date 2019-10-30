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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import weblog.UserRepository;
import weblog.exception.UsernameAlreadyExistsException;
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
    	return user.getLogbooks();
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
    
    public void associateUserWithLogbook(User user, Logbook logbook) {
    	user.associateWithLogbook(logbook);
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
    	Logbook logbook = logbookService.createLogbook(user.getUsername());
    	logbookService.associateLogbookWithUser(logbook, user);
    	this.associateUserWithLogbook(user, logbook);
    	
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
    	
    	final File jarFile = new File(getClass().getProtectionDomain().getCodeSource().getLocation().getPath());
    	
    	logger.info("JAR File: " + jarFile.getAbsoluteFile().toString());

    	if(jarFile.isFile()) {  // Run with JAR file
    	    final JarFile jar = new JarFile(jarFile);
    	    final Enumeration<JarEntry> entries = jar.entries(); //gives ALL entries in jar
    	    while(entries.hasMoreElements()) {
    	        final String name = entries.nextElement().getName();
    	        if (name.startsWith("src/main/resources/" + dir + "/")) { //filter according to the path
    	            fileList.add(name);
    	        }
    	    }
    	    jar.close();
    	} else { // Run with IDE
    	    final URL url = weblog.Application.class.getResource("/" + dir);
    	    if (url != null) {
    	        try {
    	            final File apps = new File(url.toURI());
    	            for (File app : apps.listFiles()) {
    	                fileList.add(app.getName());
    	            }
    	        } catch (URISyntaxException ex) {
    	            // never happens
    	        }
    	    } else {
    	    	fileList.add("Error.err");
    	    }
    	}
	    return fileList;
    }
    
}
